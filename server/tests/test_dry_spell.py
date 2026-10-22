"""직전 비정배열 기간(dry_days) 계산 테스트."""
import numpy as np
import pandas as pd

from screener.indicators import (
    MA_WINDOWS,
    add_moving_averages,
    alignment_with_validity,
    dry_spell_before_streak,
    evaluate_stock,
)


def make_ohlcv(closes, start="2022-01-03"):
    idx = pd.bdate_range(start, periods=len(closes))
    c = np.asarray(closes, dtype=float)
    return pd.DataFrame(
        {"Open": c, "High": c * 1.01, "Low": c * 0.99, "Close": c, "Volume": 100_000},
        index=idx,
    )


def test_valid_mask_starts_after_ma200_warmup():
    df = add_moving_averages(make_ohlcv(np.linspace(100, 300, 400)))
    _, valid = alignment_with_validity(df)
    assert not valid.iloc[198]      # 199번째까지는 MA200 없음
    assert valid.iloc[199]
    assert valid.iloc[-1]


def test_not_aligned_returns_zero():
    df = add_moving_averages(make_ohlcv(np.linspace(300, 100, 400)))
    aligned, valid = alignment_with_validity(df)
    assert dry_spell_before_streak(aligned, valid) == (0, False)


def test_long_downtrend_then_fresh_alignment():
    """오래 하락(정배열 아님) → 급반등으로 최근에야 정배열 성립."""
    closes = np.concatenate([
        np.linspace(300, 150, 500),     # 장기 하락: 정배열 아님
        np.linspace(150, 400, 120),     # 반등 후 최근 정배열 성립
    ])
    df = add_moving_averages(make_ohlcv(closes))
    aligned, valid = alignment_with_validity(df)
    assert bool(aligned.iloc[-1])

    dry, capped = dry_spell_before_streak(aligned, valid)
    # 판정 가능 구간(500+120-199)에서 정배열 직전까지 한 번도 정배열이 없었으므로 잘림
    assert capped is True
    assert dry >= 126          # 6개월(약 126거래일) 이상 정배열 없었음

    row = evaluate_stock("000010", "반등주", make_ohlcv(closes))
    assert row is not None
    assert row.dry_days == dry and row.dry_capped is True
    rec = row.to_record("2026-01-02")
    assert rec["dry_days"] == dry and rec["dry_capped"] is True


def test_short_gap_between_two_alignments():
    """정배열 → 잠깐 이탈 → 재진입 인 경우 dry_days 가 그 짧은 이탈 기간이어야 한다."""
    closes = np.concatenate([
        np.linspace(100, 300, 400),   # 상승 → 정배열 성립
        np.linspace(300, 288, 30),    # 짧은 조정 → 정배열 이탈
        np.linspace(288, 420, 150),   # 재상승 → 정배열 재진입
    ])
    df = add_moving_averages(make_ohlcv(closes))
    aligned, valid = alignment_with_validity(df)
    assert bool(aligned.iloc[-1])

    dry, capped = dry_spell_before_streak(aligned, valid)
    assert capped is False       # 앞쪽에 정배열 구간이 실제로 존재
    assert 0 < dry < 126         # 6개월 필터로 걸러져야 하는 짧은 이탈

    # 실제 공백 길이와 일치하는지 직접 확인
    a = aligned.tolist()
    i = len(a) - 1
    while a[i]:
        i -= 1
    gap = 0
    while i >= 0 and not a[i]:
        gap += 1
        i -= 1
    assert dry == gap


def test_ma_windows_unchanged():
    assert MA_WINDOWS == (10, 20, 60, 120, 200)
