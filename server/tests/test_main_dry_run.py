"""가짜 데이터 제공자로 main.run 을 end-to-end 실행 (JSON 저장 확인)."""
import json
from datetime import date

import numpy as np
import pandas as pd

from screener import main as m
from screener.data_sources import StockInfo


class FakeProvider:
    name = "fake"

    def list_stocks(self, market, asof):
        return [
            StockInfo("000001", "상승주", 1e12),
            StockInfo("000002", "하락주", 2e12),
            StockInfo("000003", "상승우", 3e12),  # 우선주 → 제외되어야 함
            StockInfo("000004", "실패주", 4e12),
        ]

    def fetch_ohlcv(self, code, start, end):
        idx = pd.bdate_range(end=date(2026, 9, 4), periods=300)
        if code == "000001":
            c = np.linspace(100, 300, 300)
        elif code == "000002":
            c = np.linspace(300, 100, 300)
        elif code == "000004":
            raise ConnectionError("timeout")
        else:
            c = np.linspace(100, 300, 300)
        return pd.DataFrame({"Open": c, "High": c, "Low": c, "Close": c, "Volume": 1000}, index=idx)


def _run(tmp_path, monkeypatch):
    monkeypatch.setattr(m, "get_provider", lambda: FakeProvider())
    monkeypatch.setenv("EXCLUDE_PREFERRED", "true")
    return m.run(m.argparse.Namespace(market="KOSPI", limit=0, out_dir=str(tmp_path)))


def test_json_output(tmp_path, monkeypatch):
    summary = _run(tmp_path, monkeypatch)
    latest = json.loads((tmp_path / "latest.json").read_text(encoding="utf-8"))
    assert summary["run_date"] == "2026-09-04"
    assert summary["total_screened"] == 3          # 우선주 1개 제외
    assert summary["failed"] == 1
    assert summary["aligned_count"] == 1
    assert latest["results"][0]["code"] == "000001"
    assert latest["results"][0]["market_cap"] == 1e12
    assert (tmp_path / "history" / "2026-09-04.json").exists()
    prices = json.loads((tmp_path / "prices" / "000001.json").read_text(encoding="utf-8"))
    assert prices["columns"][0] == "date" and len(prices["bars"]) == 300
    assert prices["bars"][-1][0] == "2026-09-04"
    assert not (tmp_path / "prices" / "000002.json").exists()


def test_rerun_same_day_keeps_is_new(tmp_path, monkeypatch):
    _run(tmp_path, monkeypatch)
    # 이전 결과를 '신규' 로 조작한 뒤 재실행 → 재실행에서도 신규 유지
    p = tmp_path / "latest.json"
    d = json.loads(p.read_text(encoding="utf-8"))
    d["results"][0]["is_new"] = True
    p.write_text(json.dumps(d), encoding="utf-8")
    summary = _run(tmp_path, monkeypatch)
    d2 = json.loads(p.read_text(encoding="utf-8"))
    assert d2["results"][0]["is_new"] is True and summary["new_count"] == 1
