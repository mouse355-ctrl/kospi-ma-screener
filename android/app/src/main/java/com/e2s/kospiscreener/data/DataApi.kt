package com.e2s.kospiscreener.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * GitHub 에 올라간 정적 JSON 을 읽는 아주 단순한 클라이언트.
 *   <baseUrl>/latest.json
 *   <baseUrl>/prices/<code>.json
 */
class DataApi(private val client: OkHttpClient = defaultClient()) {

    fun fetchLatest(baseUrl: String): LatestPayload =
        json.decodeFromString(LatestPayload.serializer(), get("${baseUrl.trimEnd('/')}/latest.json"))

    fun fetchPrices(baseUrl: String, code: String): List<PriceBar> =
        parsePrices(get("${baseUrl.trimEnd('/')}/prices/$code.json"))

    private fun get(url: String): String {
        val req = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.Builder().noCache().build())   // raw.githubusercontent 캐시 우회
            .header("Accept", "application/json")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} — $url")
            resp.body?.string() ?: throw IOException("빈 응답 — $url")
        }
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /** {"bars":[["2026-09-04",o,h,l,c,v], ...]} 파싱 (순수 함수 — 단위 테스트 대상) */
        fun parsePrices(text: String): List<PriceBar> {
            val root = json.parseToJsonElement(text).jsonObject
            val bars = root["bars"]?.jsonArray ?: return emptyList()
            return bars.mapNotNull { el ->
                val a = el.jsonArray
                if (a.size < 5) return@mapNotNull null
                val close = a[4].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                PriceBar(
                    date = a[0].jsonPrimitive.contentOrNull ?: return@mapNotNull null,
                    open = a[1].jsonPrimitive.doubleOrNull,
                    high = a[2].jsonPrimitive.doubleOrNull,
                    low = a[3].jsonPrimitive.doubleOrNull,
                    close = close,
                    volume = a.getOrNull(5)?.jsonPrimitive?.longOrNull,
                )
            }
        }
    }
}
