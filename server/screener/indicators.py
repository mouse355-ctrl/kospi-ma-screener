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


def alignment_with_validity(df: pd.DataFrame, windows=MA_WINDOWS) -> tuple[pd.Series, pd.Series]:
    """(정배열 여부, 판정 가능 여부) 두 시리즈를 반환.

    판정 가능(valid) = 5개 이동평균이 모두 계산된 날. MA200 때문에 데이터 앞쪽
    199거래일은 판정 불가이며, 이 구간은 '정배열이 아니었던 기간'으로 세면 안 된다.
    """
    cols = [f"ma{w}" for w in windows]
    if any(c not in df.columns for c in cols):
        df = add_moving_averages(df, windows)
    valid = df[cols].notna().all(axis=1)
    aligned = pd.Series(True, index=df.index)
    for shorter, longer in zip(cols, cols[1:]):
        aligned &= df[shorter] > df[longer]
    aligned = (aligned & valid).fillna(False).astype(bool)
    return aligned, valid.fillna(False).astype(bool)


def alignment_series(df: pd.DataFrame, windows=MA_WINDOWS) -> pd.Series:
    """각 날짜별 정배열 여부(bool). ma10 > ma20 > ma60 > ma120 > ma200 을 모두 만족해야 True."""
    return alignment_with_validity(df, windows)[0]


def dry_spell_before_streak(aligned: pd.Series, valid: pd.Series) -> tuple[int, bool]:
    """현재 정배열 구간이 시작되기 직전에 '정배열이 아니었던' 연속 거래일 수.

    반환: (일수, 데이터 한계로 잘렸는지)
      - 잘림(True) 이면 실제 기간은 이 값보다 길 수 있다(수집 구간 밖).
      - 현재 정배열이 아니면 (0, False).

    예) 6개월(약 126거래일) 넘게 정배열이 없다가 오늘 처음 정배열이 되면
        days_aligned=1, dry_days>=126 이 된다.
    """
    a = aligned.tolist()
    v = valid.tolist()
    n = len(a)
    if n == 0 or not a[-1]:
        return 0, False

    # 현재 연속 정배열 구간의 시작 위치
    i = n - 1
    while i >= 0 and a[i]:
        i -= 1
    # i 는 정배열이 아닌 마지막 위치 (없으면 -1)

    dry = 0
    while i >= 0 and v[i] and not a[i]:
        dry += 1
        i -= 1

    # 판정 가능 구간의 맨 앞까지 거슬러 올라갔다면(= 이전 정배열을 못 찾음) 잘린 것
    capped = i < 0 or not v[i]
    return dry, capped


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
    dry_days: int          # 현재 정배열 시작 직전, 정배열이 아니었던 연속 거래일 수
    dry_capped: bool       # 수집 데이터 한계로 dry_days 가 잘렸는지 (실제로는 더 길 수 있음)
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
            "dry_days": int(self.dry_days),
            "dry_capped": bool(self.dry_capped),
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
    aligned, valid = alignment_with_validity(df)
    if not bool(aligned.iloc[-1]):
        return None
    dry_days, dry_capped = dry_spell_before_streak(aligned, valid)

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
        dry_days=dry_days,
        dry_capped=dry_capped,
        avg_trading_value_20=float(trading_value),
        market_cap=market_cap,
    )
