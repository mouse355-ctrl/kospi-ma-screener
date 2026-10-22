package com.e2s.kospiscreener.ui

import java.text.NumberFormat
import java.util.Locale

private val won: NumberFormat = NumberFormat.getIntegerInstance(Locale.KOREA)

fun fmtPrice(v: Double?): String = if (v == null) "-" else won.format(Math.round(v))

fun fmtPct(v: Double?): String = when {
    v == null -> "-"
    v > 0 -> "+%.2f%%".format(v)
    else -> "%.2f%%".format(v)
}

/** 원 단위 금액을 억/조 단위로 축약 (예: 1조 2,340억) */
fun fmtKrwShort(v: Double?): String {
    if (v == null || v <= 0) return "-"
    val eok = v / 1e8
    return when {
        eok >= 10_000 -> {
            val jo = (eok / 10_000).toLong()
            val rest = (eok % 10_000).toLong()
            if (rest == 0L) "${jo}조" else "${jo}조 ${won.format(rest)}억"
        }
        eok >= 1 -> "${won.format(eok.toLong())}억"
        else -> "${won.format((v / 1e4).toLong())}만"
    }
}

/** 거래일 수를 "8개월", "1년 2개월" 처럼 표시. capped 면 "이상" 의미로 + 를 붙인다. */
fun fmtDryPeriod(days: Int, capped: Boolean = false): String {
    if (days <= 0) return "-"
    val suffix = if (capped) "+" else ""
    if (days < 21) return "${days}일$suffix"
    val months = days / 21
    if (months < 12) return "${months}개월$suffix"
    val years = months / 12
    val rest = months % 12
    return if (rest == 0) "${years}년$suffix" else "${years}년 ${rest}개월$suffix"
}

fun fmtVolume(v: Long?): String = if (v == null) "-" else won.format(v)
