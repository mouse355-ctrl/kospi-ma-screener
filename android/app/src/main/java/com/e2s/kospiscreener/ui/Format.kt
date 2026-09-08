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

fun fmtVolume(v: Long?): String = if (v == null) "-" else won.format(v)
