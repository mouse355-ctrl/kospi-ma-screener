package com.e2s.kospiscreener.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.e2s.kospiscreener.data.PriceBar
import com.e2s.kospiscreener.ui.theme.DownBlue
import com.e2s.kospiscreener.ui.theme.MaColors
import com.e2s.kospiscreener.ui.theme.MaLabels
import com.e2s.kospiscreener.ui.theme.UpRed

val MA_WINDOWS = intArrayOf(10, 20, 60, 120, 200)

/** 단순이동평균. 데이터 부족 구간은 NaN. */
fun sma(values: DoubleArray, window: Int): DoubleArray {
    val out = DoubleArray(values.size) { Double.NaN }
    if (values.size < window) return out
    var sum = 0.0
    for (i in values.indices) {
        sum += values[i]
        if (i >= window) sum -= values[i - window]
        if (i >= window - 1) out[i] = sum / window
    }
    return out
}

class ChartData(bars: List<PriceBar>, visibleBars: Int) {
    val all: List<PriceBar> = bars.sortedBy { it.date }
    private val closes = DoubleArray(all.size) { all[it].close }
    val mas: List<DoubleArray> = MA_WINDOWS.map { sma(closes, it) }
    val start: Int = (all.size - visibleBars).coerceAtLeast(0)
    val visible: List<PriceBar> get() = all.subList(start, all.size)
}

@Composable
fun StockChart(bars: List<PriceBar>, modifier: Modifier = Modifier, visibleBars: Int = 120) {
    if (bars.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("차트 데이터가 부족합니다") }
        return
    }
    val data = remember(bars, visibleBars) { ChartData(bars, visibleBars) }
    var selected by remember(bars) { mutableIntStateOf(-1) }  // visible 인덱스
    val vis = data.visible
    val n = vis.size

    val highs = vis.map { it.high ?: it.close }
    val lows = vis.map { it.low ?: it.close }
    val maVisible = data.mas.map { it.copyOfRange(data.start, data.all.size) }
    val maxY = maxOf(highs.max(), maVisible.flatMap { it.filter { v -> !v.isNaN() } }.maxOrNull() ?: 0.0)
    val minY = minOf(lows.min(), maVisible.flatMap { it.filter { v -> !v.isNaN() } }.minOrNull() ?: Double.MAX_VALUE)
    val pad = (maxY - minY) * 0.05
    val top = maxY + pad
    val bottom = (minY - pad).coerceAtLeast(0.0)

    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier) {
        Legend(if (selected in 0 until n) selected else n - 1, vis, maVisible)
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(bars) {
                    detectTapGestures(onTap = { selected = -1 })
                }
                .pointerInput(bars) {
                    val axisPx = 64.dp.toPx()
                    detectDragGestures(
                        onDragStart = { o -> selected = xToIndex(o.x, size.width - axisPx, n) },
                        onDrag = { change, _ -> selected = xToIndex(change.position.x, size.width - axisPx, n) },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            val rightAxis = 64.dp.toPx()
            val plotW = w - rightAxis
            val slot = plotW / n
            val candleW = (slot * 0.65f).coerceAtLeast(1f)
            fun y(v: Double): Float = ((top - v) / (top - bottom) * h).toFloat()
            fun x(i: Int): Float = slot * i + slot / 2f

            // 그리드 + 우측 가격 축
            val paint = android.graphics.Paint().apply {
                color = textColor.toArgb(); textSize = 10.sp.toPx(); isAntiAlias = true
            }
            val steps = 5
            for (k in 0..steps) {
                val v = bottom + (top - bottom) * k / steps
                val yy = y(v)
                drawLine(gridColor, Offset(0f, yy), Offset(plotW, yy), strokeWidth = 1f)
                drawContext.canvas.nativeCanvas.drawText(fmtPrice(v), plotW + 6.dp.toPx(), yy + 4.dp.toPx(), paint)
            }

            // 캔들
            vis.forEachIndexed { i, b ->
                val o = b.open ?: b.close
                val c = b.close
                val hi = b.high ?: maxOf(o, c)
                val lo = b.low ?: minOf(o, c)
                val col = if (c >= o) UpRed else DownBlue
                val cx = x(i)
                drawLine(col, Offset(cx, y(hi)), Offset(cx, y(lo)), strokeWidth = 1f)
                val topY = y(maxOf(o, c))
                val botY = y(minOf(o, c))
                drawRect(col, topLeft = Offset(cx - candleW / 2, topY), size = androidx.compose.ui.geometry.Size(candleW, (botY - topY).coerceAtLeast(1f)))
            }

            // 이동평균선
            maVisible.forEachIndexed { k, arr ->
                drawMaLine(arr, MaColors[k], ::x, ::y)
            }

            // 선택 커서
            if (selected in 0 until n) {
                val cx = x(selected)
                drawLine(textColor, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.dp.toPx())
            }
        }
        // X축 날짜 라벨 (처음/중간/끝)
        Row(Modifier.fillMaxWidth().padding(end = 64.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(vis.first(), vis[n / 2], vis.last()).forEach {
                Text(it.date.substring(2), style = MaterialTheme.typography.labelSmall, color = textColor)
            }
        }
    }
}

private fun xToIndex(x: Float, plotW: Float, n: Int): Int {
    val slot = plotW / n
    return (x / slot).toInt().coerceIn(0, n - 1)
}

private fun DrawScope.drawMaLine(arr: DoubleArray, color: Color, x: (Int) -> Float, y: (Double) -> Float) {
    val path = Path()
    var started = false
    for (i in arr.indices) {
        val v = arr[i]
        if (v.isNaN()) { started = false; continue }
        if (!started) { path.moveTo(x(i), y(v)); started = true } else path.lineTo(x(i), y(v))
    }
    drawPath(path, color, style = Stroke(width = 1.5.dp.toPx()))
}

@Composable
private fun Legend(idx: Int, vis: List<PriceBar>, mas: List<DoubleArray>) {
    val b = vis[idx]
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(
            "${b.date}  시 ${fmtPrice(b.open)}  고 ${fmtPrice(b.high)}  저 ${fmtPrice(b.low)}  종 ${fmtPrice(b.close)}  량 ${fmtVolume(b.volume)}",
            style = MaterialTheme.typography.labelSmall,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            mas.forEachIndexed { k, arr ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(8.dp)) { drawCircle(MaColors[k]) }
                    Spacer(Modifier.width(3.dp))
                    val v = arr[idx]
                    Text("${MaLabels[k]} ${if (v.isNaN()) "-" else fmtPrice(v)}", style = MaterialTheme.typography.labelSmall, color = MaColors[k])
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
