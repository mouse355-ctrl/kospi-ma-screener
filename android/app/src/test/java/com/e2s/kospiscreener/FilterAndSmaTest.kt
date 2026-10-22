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
    private fun r(
        code: String, days: Int, isNew: Boolean, cap: Double, tv: Double, pct: Double, dry: Int = 0,
    ) = ScreenResult(
        runDate = "2026-09-04", code = code, name = "종목$code", close = 1000.0, changePct = pct,
        daysAligned = days, isNew = isNew, marketCap = cap, avgTradingValue20 = tv, dryDays = dry,
    )

    private val rows = listOf(
        r("A", 30, false, 5e12, 5e10, 1.0, dry = 10),    // 짧은 이탈 후 재진입
        r("B", 1, true, 5e11, 5e9, -2.0, dry = 300),     // 1년 넘게 공백 후 오늘 진입
        r("C", 5, false, 2e13, 2e11, 3.5, dry = 130),    // 6개월 공백 후 진입
    )

    @Test fun newOnly() {
        assertEquals(listOf("B"), rows.applyFilters(Filters(newOnly = true)).map { it.code })
    }

    @Test fun marketCapAndTradingValue() {
        assertEquals(listOf("A", "C"), rows.applyFilters(Filters(minMarketCap = 1_000_000_000_000L)).map { it.code })
        assertEquals(listOf("C"), rows.applyFilters(Filters(minTradingValue = 100L * 100_000_000L)).map { it.code })
    }

    @Test fun daysAlignedRange() {
        // 하한만: 20일 이상
        assertEquals(listOf("A"), rows.applyFilters(Filters(minDaysAligned = 20)).map { it.code })
        // 상한만: 10일 이하
        assertEquals(listOf("C", "B"), rows.applyFilters(Filters(maxDaysAligned = 10)).map { it.code })
        // 범위: 1~5일
        assertEquals(listOf("C", "B"), rows.applyFilters(Filters(minDaysAligned = 1, maxDaysAligned = 5)).map { it.code })
        // 범위: 1~3일 → B(1일)만
        assertEquals(listOf("B"), rows.applyFilters(Filters(minDaysAligned = 1, maxDaysAligned = 3)).map { it.code })
        // 0 은 제한 없음
        assertEquals(3, rows.applyFilters(Filters()).size)
    }

    @Test fun daysCombinesWithOtherFilters() {
        val f = Filters(minDaysAligned = 1, maxDaysAligned = 10, minMarketCap = 1_000_000_000_000L)
        assertEquals(listOf("C"), rows.applyFilters(f).map { it.code })
    }

    @Test fun dryDaysFilter() {
        // 6개월(126거래일) 이상 공백 → B, C
        assertEquals(listOf("C", "B"), rows.applyFilters(Filters(minDryDays = 126)).map { it.code })
        // 1년(252거래일) 이상 공백 → B 만
        assertEquals(listOf("B"), rows.applyFilters(Filters(minDryDays = 252)).map { it.code })
        // 0 은 제한 없음
        assertEquals(3, rows.applyFilters(Filters(minDryDays = 0)).size)
    }

    @Test fun freshBreakoutCombination() {
        // 6개월 이상 정배열이 없다가 최근 5일 이내에 시작된 종목
        val f = Filters(minDryDays = 126, minDaysAligned = 1, maxDaysAligned = 5)
        assertEquals(listOf("C", "B"), rows.applyFilters(f).map { it.code })
        // A 는 유지 30일 + 공백 10일이라 양쪽 조건 모두 불만족
        assertTrue(rows.applyFilters(f).none { it.code == "A" })
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

class WrapIndexTest {
    @Test fun wrapsAround() {
        assertEquals(0, com.e2s.kospiscreener.ui.wrapIndex(5, 5))   // 마지막 다음 → 처음
        assertEquals(4, com.e2s.kospiscreener.ui.wrapIndex(-1, 5))  // 처음 이전 → 마지막
        assertEquals(2, com.e2s.kospiscreener.ui.wrapIndex(2, 5))
        assertEquals(1, com.e2s.kospiscreener.ui.wrapIndex(6, 5))
        assertEquals(0, com.e2s.kospiscreener.ui.wrapIndex(3, 0))   // 빈 목록
    }
}

class DryPeriodFormatTest {
    @Test fun formatsDaysMonthsYears() {
        assertEquals("-", com.e2s.kospiscreener.ui.fmtDryPeriod(0))
        assertEquals("10일", com.e2s.kospiscreener.ui.fmtDryPeriod(10))
        assertEquals("6개월", com.e2s.kospiscreener.ui.fmtDryPeriod(126))
        assertEquals("6개월+", com.e2s.kospiscreener.ui.fmtDryPeriod(126, capped = true))
        assertEquals("1년", com.e2s.kospiscreener.ui.fmtDryPeriod(252))
        assertEquals("1년 2개월", com.e2s.kospiscreener.ui.fmtDryPeriod(294))
    }
}
