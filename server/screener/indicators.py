"""이동평균 및 정배열 판별 로직 (순수 함수 — 외부 의존 없음, 단위 테스트 대상)."""
from __future__ import annotations

from dataclasses import dataclass

import pandas as pd

MA_WINDOWS = (10, 20, 60, 120, 200)


def add_moving_averages(df: pd.DataFrame, windows=MA_WINDOWS) -> pd.DataFrame:
    """Close 컬럼 기준 단순이동평균 컬럼(ma10, ma20, ...)을 추가한 사본을 반환."""
    out = df.copy()
    for w in windows:
        out[f"ma{w}"] = out["Close"].rolling(window=w, min_periods=w).mean()
    return out


def alignment_series(df: pd.DataFrame, windows=MA_WINDOWS) -> pd.Series:
    """각 날짜별 정배열 여부(bool). ma10 > ma20 > ma60 > ma120 > ma200 을 모두 만족해야 True.

    이동평균이 아직 계산되지 않은 구간(NaN)은 False.
    """
    cols = [f"ma{w}" for w in windows]
    if any(c not in df.columns for c in cols):
        df = add_moving_averages(df, windows)
    aligned = pd.Series(True, index=df.index)
    for shorter, longer in zip(cols, cols[1:]):
        aligned &= df[shorter] > df[longer]
    aligned &= df[cols].notna().all(axis=1)
    return aligned.fillna(False).astype(bool)


def consecutive_true_tail(s: pd.Series) -> int:
    """시리즈 끝에서부터 연속으로 True 인 개수."""
    n = 0
    for v in reversed(s.tolist()):
        if not v:
            break
        n += 1
    return n


@dataclass
class ScreenRow:
    code: str
    name: str
    close: float
    change_pct: float
    volume: int
    ma10: float
    ma20: float
    ma60: float
    ma120: float
    ma200: float
    days_aligned: int
    is_new: bool
    avg_trading_value_20: float  # 원 단위 (근사: 종가 × 거래량 20일 평균)
    market_cap: float | None  # 원 단위, 없으면 None

    def to_record(self, run_date: str) -> dict:
        return {
            "run_date": run_date,
            "code": self.code,
            "name": self.name,
            "close": self.close,
            "change_pct": round(self.change_pct, 2),
            "volume": int(self.volume),
            "ma10": round(self.ma10, 2),
            "ma20": round(self.ma20, 2),
            "ma60": round(self.ma60, 2),
            "ma120": round(self.ma120, 2),
            "ma200": round(self.ma200, 2),
            "days_aligned": int(self.days_aligned),
            "is_new": bool(self.is_new),
            "avg_trading_value_20": round(self.avg_trading_value_20),
            "market_cap": None if self.market_cap is None else round(self.market_cap),
        }


def evaluate_stock(
    code: str,
    name: str,
    ohlcv: pd.DataFrame,
    market_cap: float | None = None,
) -> ScreenRow | None:
    """단일 종목 일봉(Open/High/Low/Close/Volume, DatetimeIndex 오름차순)을 평가.

    정배열이 아니거나 데이터가 부족하면 None.
    """
    if ohlcv is None or len(ohlcv) < max(MA_WINDOWS):
        return None
    df = add_moving_averages(ohlcv.sort_index())
    aligned = alignment_series(df)
    if not bool(aligned.iloc[-1]):
        return None

    last = df.iloc[-1]
    prev_close = df["Close"].iloc[-2] if len(df) >= 2 else last["Close"]
    change_pct = (last["Close"] / prev_close - 1.0) * 100.0 if prev_close else 0.0
    trading_value = (df["Close"] * df["Volume"]).rolling(20, min_periods=1).mean().iloc[-1]
    days = consecutive_true_tail(aligned)

    return ScreenRow(
        code=code,
        name=name,
        close=float(last["Close"]),
        change_pct=float(change_pct),
        volume=int(last["Volume"]),
        ma10=float(last["ma10"]),
        ma20=float(last["ma20"]),
        ma60=float(last["ma60"]),
        ma120=float(last["ma120"]),
        ma200=float(last["ma200"]),
        days_aligned=days,
        is_new=(days == 1),
        avg_trading_value_20=float(trading_value),
        market_cap=market_cap,
    )
