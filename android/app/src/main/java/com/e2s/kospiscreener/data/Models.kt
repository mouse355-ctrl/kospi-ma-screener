package com.e2s.kospiscreener.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** docs/data/latest.json 의 summary */
@Serializable
data class ScreenRun(
    @SerialName("run_date") val runDate: String? = null,
    val market: String = "KOSPI",
    @SerialName("total_screened") val totalScreened: Int = 0,
    val failed: Int = 0,
    @SerialName("aligned_count") val alignedCount: Int = 0,
    @SerialName("new_count") val newCount: Int = 0,
    val provider: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ScreenResult(
    @SerialName("run_date") val runDate: String,
    val code: String,
    val name: String,
    val close: Double,
    @SerialName("change_pct") val changePct: Double? = null,
    val volume: Long? = null,
    val ma10: Double? = null,
    val ma20: Double? = null,
    val ma60: Double? = null,
    val ma120: Double? = null,
    val ma200: Double? = null,
    @SerialName("days_aligned") val daysAligned: Int,
    @SerialName("is_new") val isNew: Boolean = false,
    /** 현재 정배열 시작 직전, 정배열이 아니었던 연속 거래일 수 */
    @SerialName("dry_days") val dryDays: Int = 0,
    /** 수집 데이터 한계로 dryDays 가 잘렸는지 (실제로는 더 길 수 있음) */
    @SerialName("dry_capped") val dryCapped: Boolean = false,
    @SerialName("avg_trading_value_20") val avgTradingValue20: Double? = null,
    @SerialName("market_cap") val marketCap: Double? = null,
)

/** latest.json 전체 */
@Serializable
data class LatestPayload(
    val summary: ScreenRun = ScreenRun(),
    val results: List<ScreenResult> = emptyList(),
)

/** prices/<code>.json 의 bars([date, open, high, low, close, volume]) 한 줄 — DataApi 에서 파싱 */
data class PriceBar(
    val date: String,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val close: Double,
    val volume: Long?,
)

/** 리스트 필터 상태. 금액 단위는 원. 0 = 제한 없음 */
data class Filters(
    val newOnly: Boolean = false,
    val minMarketCap: Long = 0L,
    val minTradingValue: Long = 0L,
    /** 정배열 유지일수 하한 (0 = 제한 없음) */
    val minDaysAligned: Int = 0,
    /** 정배열 유지일수 상한 (0 = 제한 없음) */
    val maxDaysAligned: Int = 0,
    /** 직전 비정배열 기간 하한, 거래일 수 (0 = 제한 없음) */
    val minDryDays: Int = 0,
    val sort: SortKey = SortKey.DAYS_ALIGNED,
)

enum class SortKey(val label: String) {
    DAYS_ALIGNED("유지일수"),
    CHANGE_PCT("등락률"),
    MARKET_CAP("시가총액"),
    TRADING_VALUE("거래대금"),
    NAME("종목명"),
}

object Presets {
    val marketCap: List<Pair<String, Long>> = listOf(
        "제한 없음" to 0L,
        "1,000억+" to 1_000L * 100_000_000L,
        "3,000억+" to 3_000L * 100_000_000L,
        "5,000억+" to 5_000L * 100_000_000L,
        "1조+" to 10_000L * 100_000_000L,
        "5조+" to 50_000L * 100_000_000L,
    )
    val tradingValue: List<Pair<String, Long>> = listOf(
        "제한 없음" to 0L,
        "10억+" to 10L * 100_000_000L,
        "50억+" to 50L * 100_000_000L,
        "100억+" to 100L * 100_000_000L,
        "500억+" to 500L * 100_000_000L,
    )

    /** 직전 비정배열 기간 하한 (라벨, 거래일 수). 1개월 ≈ 21거래일 */
    val dryDays: List<Pair<String, Int>> = listOf(
        "제한 없음" to 0,
        "1개월+" to 21,
        "3개월+" to 63,
        "6개월+" to 126,
        "1년+" to 252,
    )

    /** 정배열 유지일수 범위 (라벨, 하한, 상한). 0 = 제한 없음 */
    val daysAligned: List<Triple<String, Int, Int>> = listOf(
        Triple("제한 없음", 0, 0),
        Triple("1~3일", 1, 3),
        Triple("1~5일", 1, 5),
        Triple("1~10일", 1, 10),
        Triple("1~20일", 1, 20),
        Triple("20일+", 20, 0),
        Triple("60일+", 60, 0),
        Triple("120일+", 120, 0),
    )
}
