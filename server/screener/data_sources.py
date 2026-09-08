"""시세 데이터 제공자.

우선순위:
  1. NaverProvider  — 네이버 금융 (무료, 로그인 불필요). 종목 목록/시총은 모바일 API,
                      일봉은 FinanceDataReader(fchart) → 실패 시 api.stock.naver.com 차트 API.
  2. PykrxProvider  — KRX 정보데이터시스템. pykrx 1.2.x 는 KRX 계정(KRX_ID / KRX_PW)이 필요.

환경변수 DATA_PROVIDER=naver|pykrx|auto (기본 auto: naver 시도 후 실패하면 pykrx).
"""
from __future__ import annotations

import logging
import os
import re
import time
from dataclasses import dataclass
from datetime import date, timedelta
from typing import Protocol

import pandas as pd
import requests

log = logging.getLogger(__name__)

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) KospiMaScreener/1.0"}


@dataclass
class StockInfo:
    code: str
    name: str
    market_cap: float | None = None  # 원
    market: str = "KOSPI"


class Provider(Protocol):
    name: str

    def list_stocks(self, market: str, asof: date) -> list[StockInfo]: ...

    def fetch_ohlcv(self, code: str, start: date, end: date) -> pd.DataFrame: ...


def _to_number(v) -> float | None:
    if v is None:
        return None
    if isinstance(v, (int, float)):
        return float(v)
    s = str(v).replace(",", "").strip()
    if s in ("", "-", "N/A"):
        return None
    try:
        return float(s)
    except ValueError:
        return None


def _first(d: dict, *keys):
    for k in keys:
        if k in d and d[k] not in (None, ""):
            return d[k]
    return None


def _normalize_ohlcv(df: pd.DataFrame) -> pd.DataFrame:
    """컬럼을 Open/High/Low/Close/Volume 으로 통일하고 DatetimeIndex 오름차순으로 정렬."""
    rename = {}
    for c in df.columns:
        lc = str(c).lower()
        if lc in ("open", "시가", "openprice"):
            rename[c] = "Open"
        elif lc in ("high", "고가", "highprice"):
            rename[c] = "High"
        elif lc in ("low", "저가", "lowprice"):
            rename[c] = "Low"
        elif lc in ("close", "종가", "closeprice"):
            rename[c] = "Close"
        elif lc in ("volume", "거래량", "accumulatedtradingvolume"):
            rename[c] = "Volume"
    df = df.rename(columns=rename)
    need = ["Open", "High", "Low", "Close", "Volume"]
    missing = [c for c in need if c not in df.columns]
    if missing:
        raise ValueError(f"OHLCV 컬럼 누락: {missing}")
    df = df[need].apply(pd.to_numeric, errors="coerce").dropna(subset=["Close"])
    df.index = pd.to_datetime(df.index)
    df = df[~df.index.duplicated(keep="last")].sort_index()
    # 거래정지 등으로 거래량 0 인 날은 종가만 유지 (이평 계산에는 종가만 필요)
    df["Volume"] = df["Volume"].fillna(0).astype("int64")
    return df


# --------------------------------------------------------------------------- Naver
class NaverProvider:
    name = "naver"
    LIST_URL = "https://m.stock.naver.com/api/stocks/marketValue/{market}"
    CHART_URL = "https://api.stock.naver.com/chart/domestic/item/{code}/day"

    def __init__(self, session: requests.Session | None = None, sleep: float = 0.15):
        self.s = session or requests.Session()
        self.s.headers.update(UA)
        self.sleep = sleep

    def list_stocks(self, market: str, asof: date) -> list[StockInfo]:
        out: list[StockInfo] = []
        page, page_size = 1, 100
        while True:
            r = self.s.get(
                self.LIST_URL.format(market=market.upper()),
                params={"page": page, "pageSize": page_size},
                timeout=20,
            )
            r.raise_for_status()
            body = r.json()
            items = body.get("stocks") or body.get("result") or []
            if not items:
                break
            for it in items:
                code = _first(it, "itemCode", "code", "reutersCode")
                name = _first(it, "stockName", "name", "itemName")
                if not code or not name:
                    continue
                code = str(code)[-6:]
                # marketValue 는 억원 단위 문자열("4,268,000")로 내려오는 것으로 알려져 있음
                mv = _to_number(_first(it, "marketValue", "marketCap"))
                market_cap = mv * 1e8 if mv is not None else None
                out.append(StockInfo(code=code, name=str(name), market_cap=market_cap, market=market))
            total = _to_number(body.get("totalCount"))
            if total is not None and page * page_size >= total:
                break
            if len(items) < page_size:
                break
            page += 1
            time.sleep(self.sleep)
        if not out:
            raise RuntimeError("네이버 종목 목록이 비어 있습니다 (API 응답 형식 변경 가능성)")
        return out

    def fetch_ohlcv(self, code: str, start: date, end: date) -> pd.DataFrame:
        try:
            return self._via_fdr(code, start, end)
        except Exception as e:  # noqa: BLE001
            log.debug("fdr 실패 %s: %s — 네이버 차트 API 로 재시도", code, e)
            return self._via_chart_api(code, start, end)

    def _via_fdr(self, code: str, start: date, end: date) -> pd.DataFrame:
        import FinanceDataReader as fdr  # 지연 import (설치 안 된 환경 대비)

        df = fdr.DataReader(code, start.isoformat(), end.isoformat())
        if df is None or df.empty:
            raise ValueError("빈 데이터")
        return _normalize_ohlcv(df)

    def _via_chart_api(self, code: str, start: date, end: date) -> pd.DataFrame:
        r = self.s.get(
            self.CHART_URL.format(code=code),
            params={"startDateTime": start.strftime("%Y%m%d") + "0000",
                    "endDateTime": end.strftime("%Y%m%d") + "0000"},
            timeout=20,
        )
        r.raise_for_status()
        rows = r.json()
        if isinstance(rows, dict):
            rows = rows.get("priceInfos") or rows.get("result") or []
        recs = []
        for it in rows:
            d = _first(it, "localDate", "localDateTime", "date")
            if not d:
                continue
            recs.append({
                "date": pd.to_datetime(str(d)[:8], format="%Y%m%d"),
                "Open": _to_number(_first(it, "openPrice")),
                "High": _to_number(_first(it, "highPrice")),
                "Low": _to_number(_first(it, "lowPrice")),
                "Close": _to_number(_first(it, "closePrice")),
                "Volume": _to_number(_first(it, "accumulatedTradingVolume", "volume")),
            })
        if not recs:
            raise ValueError(f"네이버 차트 API 빈 응답: {code}")
        return _normalize_ohlcv(pd.DataFrame(recs).set_index("date"))


# --------------------------------------------------------------------------- pykrx
class PykrxProvider:
    name = "pykrx"

    def __init__(self):
        if not (os.getenv("KRX_ID") and os.getenv("KRX_PW")):
            log.warning("KRX_ID / KRX_PW 미설정 — pykrx 1.2.x 는 KRX 로그인 없이는 조회가 실패할 수 있습니다")
        from pykrx import stock  # noqa: WPS433

        self.stock = stock

    def list_stocks(self, market: str, asof: date) -> list[StockInfo]:
        d = asof.strftime("%Y%m%d")
        tickers = self.stock.get_market_ticker_list(d, market=market.upper())
        caps = self.stock.get_market_cap(d, market=market.upper())
        out = []
        for t in tickers:
            cap = None
            if t in caps.index and "시가총액" in caps.columns:
                cap = float(caps.loc[t, "시가총액"])
            out.append(StockInfo(code=t, name=self.stock.get_market_ticker_name(t), market_cap=cap, market=market))
        return out

    def fetch_ohlcv(self, code: str, start: date, end: date) -> pd.DataFrame:
        df = self.stock.get_market_ohlcv(start.strftime("%Y%m%d"), end.strftime("%Y%m%d"), code)
        if df is None or df.empty:
            raise ValueError(f"pykrx 빈 데이터: {code}")
        return _normalize_ohlcv(df)


# --------------------------------------------------------------------------- 선택
def get_provider() -> Provider:
    pref = os.getenv("DATA_PROVIDER", "auto").lower()
    if pref == "naver":
        return NaverProvider()
    if pref == "pykrx":
        return PykrxProvider()
    # auto: 네이버 목록 조회로 연결 확인 후 결정
    try:
        p = NaverProvider()
        p.list_stocks("KOSPI", date.today() - timedelta(days=1))
        return p
    except Exception as e:  # noqa: BLE001
        log.warning("네이버 제공자 사용 불가(%s) — pykrx 로 전환", e)
        return PykrxProvider()
