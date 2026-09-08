"""결과를 정적 JSON 파일로 저장 (GitHub 저장소의 docs/data/ 아래).

docs/data/
  latest.json            ← 최신 실행 요약 + 정배열 종목 목록 (앱이 읽는 파일)
  history/YYYY-MM-DD.json ← 날짜별 보관 (최근 60개 유지)
  prices/<code>.json     ← 정배열 종목의 최근 320거래일 일봉 (차트용)
"""
from __future__ import annotations

import json
import logging
import shutil
from pathlib import Path

import pandas as pd

log = logging.getLogger(__name__)


def _dump(path: Path, obj) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


class JsonStore:
    def __init__(self, root: Path, keep_days: int = 320, keep_history: int = 60):
        self.root = Path(root)
        self.keep_days = keep_days
        self.keep_history = keep_history

    def previous_new_codes(self) -> tuple[str | None, set[str]]:
        """직전 latest.json 의 (run_date, 신규 종목 코드 집합). 같은 날 재실행 시 신규 판정 유지에 사용."""
        p = self.root / "latest.json"
        if not p.exists():
            return None, set()
        try:
            d = json.loads(p.read_text(encoding="utf-8"))
            return d["summary"]["run_date"], {r["code"] for r in d["results"] if r.get("is_new")}
        except Exception:  # noqa: BLE001
            return None, set()

    def write(self, summary: dict, records: list[dict], price_frames: dict[str, pd.DataFrame]) -> None:
        payload = {"summary": summary, "results": records}
        _dump(self.root / "latest.json", payload)
        _dump(self.root / "history" / f"{summary['run_date']}.json", payload)

        prices_dir = self.root / "prices"
        if prices_dir.exists():
            shutil.rmtree(prices_dir)  # 정배열에서 빠진 종목의 차트 파일 정리
        for code, df in price_frames.items():
            tail = df.tail(self.keep_days)
            bars = [
                [idx.strftime("%Y-%m-%d"), float(r.Open), float(r.High), float(r.Low), float(r.Close), int(r.Volume)]
                for idx, r in tail.iterrows()
            ]
            _dump(prices_dir / f"{code}.json", {"code": code, "columns": ["date", "open", "high", "low", "close", "volume"], "bars": bars})

        hist = sorted((self.root / "history").glob("*.json"))
        for old in hist[: max(0, len(hist) - self.keep_history)]:
            old.unlink()
        log.info("JSON 저장 완료: %s (정배열 %d, 차트 %d)", self.root, len(records), len(price_frames))
