package com.e2s.kospiscreener.data

import android.content.Context
import android.content.SharedPreferences
import com.e2s.kospiscreener.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 앱 설정 (데이터 주소, 필터, 마지막 알림 기준일) — SharedPreferences */
class Settings(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = sp.getString("baseUrl", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DATA_BASE_URL
        set(v) = sp.edit().putString("baseUrl", v.trim().trimEnd('/')).apply()

    val isConfigured: Boolean get() = baseUrl.startsWith("http")

    /** 마지막으로 알림을 보낸 기준일 (중복 알림 방지) */
    var lastNotifiedRunDate: String?
        get() = sp.getString("lastNotifiedRunDate", null)
        set(v) = sp.edit().putString("lastNotifiedRunDate", v).apply()

    fun loadFilters(): Filters = Filters(
        newOnly = sp.getBoolean("newOnly", false),
        minMarketCap = sp.getLong("minMarketCap", 0L),
        minTradingValue = sp.getLong("minTradingValue", 0L),
        sort = runCatching { SortKey.valueOf(sp.getString("sort", null) ?: "") }.getOrDefault(SortKey.DAYS_ALIGNED),
    )

    fun saveFilters(f: Filters) = sp.edit()
        .putBoolean("newOnly", f.newOnly)
        .putLong("minMarketCap", f.minMarketCap)
        .putLong("minTradingValue", f.minTradingValue)
        .putString("sort", f.sort.name)
        .apply()
}

class Repository(private val settings: Settings, private val api: DataApi = DataApi()) {

    suspend fun loadLatest(): LatestPayload = withContext(Dispatchers.IO) {
        check(settings.isConfigured) { "데이터 주소가 설정되지 않았습니다. 우측 상단 톱니바퀴에서 입력하세요." }
        api.fetchLatest(settings.baseUrl)
    }

    suspend fun loadPrices(code: String): List<PriceBar> = withContext(Dispatchers.IO) {
        api.fetchPrices(settings.baseUrl, code)
    }

    /** 동기 버전 — WorkManager 워커에서 사용 */
    fun loadLatestBlocking(): LatestPayload = api.fetchLatest(settings.baseUrl)
}

/** 필터 + 정렬 적용 (순수 함수 — 단위 테스트 대상) */
fun List<ScreenResult>.applyFilters(f: Filters): List<ScreenResult> {
    val filtered = filter { r ->
        (!f.newOnly || r.isNew) &&
            (f.minMarketCap <= 0 || (r.marketCap ?: 0.0) >= f.minMarketCap) &&
            (f.minTradingValue <= 0 || (r.avgTradingValue20 ?: 0.0) >= f.minTradingValue)
    }
    return when (f.sort) {
        SortKey.DAYS_ALIGNED -> filtered.sortedWith(compareByDescending<ScreenResult> { it.daysAligned }.thenBy { it.name })
        SortKey.CHANGE_PCT -> filtered.sortedByDescending { it.changePct ?: Double.NEGATIVE_INFINITY }
        SortKey.MARKET_CAP -> filtered.sortedByDescending { it.marketCap ?: 0.0 }
        SortKey.TRADING_VALUE -> filtered.sortedByDescending { it.avgTradingValue20 ?: 0.0 }
        SortKey.NAME -> filtered.sortedBy { it.name }
    }
}
