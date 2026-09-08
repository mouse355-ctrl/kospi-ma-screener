package com.e2s.kospiscreener.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.e2s.kospiscreener.data.ScreenResult
import com.e2s.kospiscreener.ui.theme.DownBlue
import com.e2s.kospiscreener.ui.theme.MaColors
import com.e2s.kospiscreener.ui.theme.UpRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(state: DetailUiState, onBack: () -> Unit) {
    val r = state.result
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(r?.name ?: "종목", fontWeight = FontWeight.Bold)
                        if (r != null) Text(r.code, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (r != null) PriceHeader(r)
            when {
                state.loading -> Column(Modifier.fillMaxWidth().height(320.dp)) { Loading() }
                state.error != null -> Text("차트 로드 실패: ${state.error}", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                else -> StockChart(state.prices, Modifier.fillMaxWidth().height(340.dp).padding(horizontal = 8.dp))
            }
            if (r != null) {
                Spacer(Modifier.height(8.dp))
                MaTable(r)
                Spacer(Modifier.height(8.dp))
                MetricsCard(r)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PriceHeader(r: ScreenResult) {
    val pct = r.changePct ?: 0.0
    val color = if (pct > 0) UpRed else if (pct < 0) DownBlue else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Bottom) {
        Text(fmtPrice(r.close), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.width(12.dp))
        Text(fmtPct(r.changePct), style = MaterialTheme.typography.titleMedium, color = color)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            if (r.isNew) Badge("오늘 신규 진입", UpRed)
            Text("정배열 ${r.daysAligned}일째", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MaTable(r: ScreenResult) {
    val rows = listOf("MA10" to r.ma10, "MA20" to r.ma20, "MA60" to r.ma60, "MA120" to r.ma120, "MA200" to r.ma200)
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("이동평균 (정배열: MA10 > MA20 > MA60 > MA120 > MA200)", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            rows.forEachIndexed { i, (label, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = MaColors[i], fontWeight = FontWeight.SemiBold)
                    val gap = v?.let { (r.close / it - 1) * 100 }
                    Text("${fmtPrice(v)}   (이격 ${fmtPct(gap)})", style = MaterialTheme.typography.bodyMedium)
                }
                if (i < rows.lastIndex) HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun MetricsCard(r: ScreenResult) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Metric("시가총액", fmtKrwShort(r.marketCap))
            Metric("20일 평균 거래대금", fmtKrwShort(r.avgTradingValue20))
            Metric("당일 거래량", fmtVolume(r.volume))
            Metric("기준일", r.runDate)
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
