package com.e2s.kospiscreener

import com.e2s.kospiscreener.data.Filters
import com.e2s.kospiscreener.data.ScreenResult
import com.e2s.kospiscreener.data.SortKey
import com.e2s.kospiscreener.data.applyFilters
import com.e2s.kospiscreener.ui.sma
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterAndSmaTest {
    private fun r(code: String, days: Int, isNew: Boolean, cap: Double, tv: Double, pct: Double) = ScreenResult(
        runDate = "2026-09-04", code = code, name = "종목$code", close = 1000.0, changePct = pct,
        daysAligned = days, isNew = isNew, marketCap = cap, avgTradingValue20 = tv,
    )

    private val rows = listOf(
        r("A", 30, false, 5e12, 5e10, 1.0),
        r("B", 1, true, 5e11, 5e9, -2.0),
        r("C", 5, false, 2e13, 2e11, 3.5),
    )

    @Test fun newOnly() {
        assertEquals(listOf("B"), rows.applyFilters(Filters(newOnly = true)).map { it.code })
    }

    @Test fun marketCapAndTradingValue() {
        assertEquals(listOf("A", "C"), rows.applyFilters(Filters(minMarketCap = 1_000_000_000_000L)).map { it.code })
        assertEquals(listOf("C"), rows.applyFilters(Filters(minTradingValue = 100L * 100_000_000L)).map { it.code })
    }

    @Test fun sorting() {
        assertEquals(listOf("C", "A", "B"), rows.applyFilters(Filters(sort = SortKey.CHANGE_PCT)).map { it.code })
        assertEquals(listOf("A", "C", "B"), rows.applyFilters(Filters(sort = SortKey.DAYS_ALIGNED)).map { it.code })
    }

    @Test fun smaBasic() {
        val v = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val m = sma(v, 3)
        assertTrue(m[0].isNaN() && m[1].isNaN())
        assertEquals(2.0, m[2], 1e-9)
        assertEquals(4.0, m[4], 1e-9)
    }
}

class PricesParserTest {
    @Test fun parsesBars() {
        val text = """{"code":"005930","columns":["date","open","high","low","close","volume"],
            "bars":[["2026-09-03",70000,71000,69500,70500,12345678],["2026-09-04",70500,72000,70000,71800,9876543]]}"""
        val bars = com.e2s.kospiscreener.data.DataApi.parsePrices(text)
        assertEquals(2, bars.size)
        assertEquals("2026-09-04", bars[1].date)
        assertEquals(71800.0, bars[1].close, 1e-9)
        assertEquals(9876543L, bars[1].volume)
    }
}
