import numpy as np
import pandas as pd
import pytest

from screener.indicators import (
    MA_WINDOWS,
    add_moving_averages,
    alignment_series,
    consecutive_true_tail,
    evaluate_stock,
)


def make_ohlcv(closes, start="2024-01-01"):
    idx = pd.bdate_range(start, periods=len(closes))
    c = np.asarray(closes, dtype=float)
    return pd.DataFrame(
        {"Open": c, "High": c * 1.01, "Low": c * 0.99, "Close": c, "Volume": 100_000},
        index=idx,
    )


def test_moving_average_values():
    df = add_moving_averages(make_ohlcv(range(1, 301)))
    # 1..300 의 마지막 10일 평균 = (291+...+300)/10 = 295.5
    assert df["ma10"].iloc[-1] == pytest.approx(295.5)
    assert df["ma200"].iloc[-1] == pytest.approx((101 + 300) / 2)
    assert df["ma200"].iloc[198] != df["ma200"].iloc[198]  # 199번째까지는 NaN


def test_steady_uptrend_is_aligned():
    df = make_ohlcv(np.linspace(100, 300, 300))
    aligned = alignment_series(df)
    assert bool(aligned.iloc[-1])
    # 단조 증가에서는 MA200 계산 가능 시점부터 계속 정배열
    assert consecutive_true_tail(aligned) == 300 - 200 + 1


def test_downtrend_not_aligned():
    df = make_ohlcv(np.linspace(300, 100, 300))
    assert not bool(alignment_series(df).iloc[-1])
    assert evaluate_stock("000000", "하락", df) is None


def test_insufficient_data_returns_none():
    assert evaluate_stock("000000", "짧음", make_ohlcv(range(1, 150))) is None


def test_new_entry_detection():
    # 오래 횡보하다가 마지막 구간에 급등 → 최근에야 정배열 성립
    closes = np.concatenate([np.full(280, 100.0), np.linspace(100, 200, 60)])
    df = make_ohlcv(closes)
    row = evaluate_stock("123456", "급등", df, market_cap=5e11)
    assert row is not None
    assert row.days_aligned >= 1
    assert row.is_new == (row.days_aligned == 1)
    rec = row.to_record("2026-09-04")
    assert rec["market_cap"] == 5e11
    assert rec["ma10"] > rec["ma20"] > rec["ma60"] > rec["ma120"] > rec["ma200"]


def test_first_day_of_alignment_flags_is_new():
    """정배열이 정확히 오늘 처음 성립하는 케이스를 구성해 is_new=True 확인."""
    closes = np.concatenate([np.full(280, 100.0), np.linspace(100, 200, 60)])
    df = make_ohlcv(closes)
    aligned = alignment_series(df)
    first_true = int(np.argmax(aligned.values))
    assert aligned.iloc[first_true]
    cut = df.iloc[: first_true + 1]
    row = evaluate_stock("123456", "첫날", cut)
    assert row is not None and row.is_new and row.days_aligned == 1


def test_consecutive_true_tail():
    assert consecutive_true_tail(pd.Series([True, False, True, True])) == 2
    assert consecutive_true_tail(pd.Series([False])) == 0
    assert consecutive_true_tail(pd.Series([], dtype=bool)) == 0
    assert len(MA_WINDOWS) == 5
