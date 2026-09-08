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
}
