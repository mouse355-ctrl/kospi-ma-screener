"""KOSPI 정배열(MA10>20>60>120>200) 스크리너 — 하루 1회 실행 진입점.

사용 예:
  python -m screener.main                       # 전종목 스크리닝 → ../docs/data/ 에 JSON 저장
  python -m screener.main --limit 30            # 처음 30종목만 (동작 확인용)
  python -m screener.main --out-dir ./out       # 저장 위치 변경
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import date, datetime, timedelta, timezone

from pathlib import Path

import pandas as pd

from .data_sources import Provider, StockInfo, get_provider
from .filters import exclusion_reason
from .indicators import MA_WINDOWS, evaluate_stock
from .storage import JsonStore

log = logging.getLogger("screener")
KST = timezone(timedelta(hours=9))


def _fetch_with_retry(p: Provider, info: StockInfo, start: date, end: date, tries: int = 3) -> pd.DataFrame | None:
    for i in range(tries):
        try:
            return p.fetch_ohlcv(info.code, start, end)
        except Exception as e:  # noqa: BLE001
            if i == tries - 1:
                log.warning("일봉 조회 실패 %s %s: %s", info.code, info.name, e)
                return None
            time.sleep(1.0 * (i + 1))
    return None


def run(args: argparse.Namespace) -> dict:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    today = datetime.now(KST).date()
    # 200일 이평 + 연속일수 계산 여유분 → 약 2년치 요청 (거래일 ≈ 245/년)
    start = today - timedelta(days=int(os.getenv("LOOKBACK_DAYS", "730")))
    # ETF/ETN/리츠/스팩/우선주 제외 (일반 기업 보통주만 스크리닝)
    exclude_non_stock = os.getenv("EXCLUDE_NON_STOCK", "true").lower() == "true"

    provider = get_provider()
    log.info("데이터 제공자: %s", provider.name)
    stocks = provider.list_stocks(args.market, today)
    excluded: dict[str, int] = {}
    if exclude_non_stock:
        kept = []
        for s in stocks:
            reason = exclusion_reason(s.code, s.name)
            if reason is None:
                kept.append(s)
            else:
                excluded[reason] = excluded.get(reason, 0) + 1
        log.info("제외: %s (전체 %d → 대상 %d)",
                 ", ".join(f"{k} {v}" for k, v in sorted(excluded.items())) or "없음",
                 len(stocks), len(kept))
        stocks = kept
    if args.limit:
        stocks = stocks[: args.limit]
    log.info("%s 대상 종목 %d개", args.market, len(stocks))

    results, price_frames, failures = [], {}, 0
    run_date: str | None = None
    workers = int(os.getenv("WORKERS", "6"))
    with ThreadPoolExecutor(max_workers=workers) as ex:
        futs = {ex.submit(_fetch_with_retry, provider, s, start, today): s for s in stocks}
        for n, fut in enumerate(as_completed(futs), 1):
            s = futs[fut]
            df = fut.result()
            if df is None or df.empty:
                failures += 1
                continue
            last_day = df.index[-1].date().isoformat()
            # 가장 최신 거래일을 실행 기준일로 삼음 (휴장일 실행 시 직전 거래일로 저장)
            if run_date is None or last_day > run_date:
                run_date = last_day
            row = evaluate_stock(s.code, s.name, df, s.market_cap)
            if row is not None:
                results.append(row)
                price_frames[s.code] = df
            if n % 100 == 0:
                log.info("진행 %d/%d — 정배열 %d", n, len(stocks), len(results))

    if run_date is None:
        raise SystemExit("모든 종목 조회 실패 — 데이터 제공자/네트워크 확인 필요")

    # 기준일보다 오래된 데이터로 평가된 종목(거래정지 등)은 제외
    results = [r for r in results if price_frames[r.code].index[-1].date().isoformat() == run_date]
    results.sort(key=lambda r: (-r.days_aligned, r.name))
    records = [r.to_record(run_date) for r in results]
    new_records = [r for r in records if r["is_new"]]

    summary = {
        "run_date": run_date,
        "market": args.market,
        "total_screened": len(stocks),
        "failed": failures,
        "aligned_count": len(records),
        "new_count": len(new_records),
        "ma_windows": list(MA_WINDOWS),
        "provider": provider.name,
        "excluded": excluded,
        "created_at": datetime.now(timezone.utc).isoformat(),
    }
    log.info("기준일 %s: 정배열 %d종목 (신규 %d), 실패 %d", run_date, len(records), len(new_records), failures)

    store = JsonStore(Path(args.out_dir))
    prev_date, prev_new = store.previous_new_codes()
    if prev_date == run_date:
        # 같은 기준일 재실행: 이전에 신규였던 종목은 계속 신규로 표시 (알림 중복/누락 방지)
        for r in records:
            if r["code"] in prev_new:
                r["is_new"] = True
        summary["new_count"] = sum(1 for r in records if r["is_new"])

    store.write(summary, records, {c: price_frames[c] for c in (r["code"] for r in records)})
    return summary


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--market", default=os.getenv("MARKET", "KOSPI"))
    ap.add_argument("--limit", type=int, default=0, help="처음 N종목만 처리 (테스트용)")
    ap.add_argument("--out-dir", default=os.getenv("OUT_DIR", str(Path(__file__).resolve().parents[2] / "docs" / "data")),
                    help="JSON 저장 폴더 (기본: 저장소의 docs/data)")
    args = ap.parse_args(argv)
    summary = run(args)
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    sys.exit(main())
