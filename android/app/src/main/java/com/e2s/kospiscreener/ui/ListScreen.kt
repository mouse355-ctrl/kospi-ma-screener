package com.e2s.kospiscreener.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.e2s.kospiscreener.data.Filters
import com.e2s.kospiscreener.data.Presets
import com.e2s.kospiscreener.data.ScreenResult
import com.e2s.kospiscreener.data.SortKey
import com.e2s.kospiscreener.ui.theme.DownBlue
import com.e2s.kospiscreener.ui.theme.UpRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    state: ListUiState,
    onRefresh: () -> Unit,
    onFilters: ((Filters) -> Filters) -> Unit,
    onSaveBaseUrl: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    var showSettings by remember { mutableStateOf(!state.configured) }
    if (showSettings) {
        SettingsDialog(
            current = state.baseUrl,
            canDismiss = state.configured,
            onSave = { onSaveBaseUrl(it); showSettings = false },
            onDismiss = { showSettings = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("KOSPI 정배열", fontWeight = FontWeight.Bold)
                        val run = state.run
                        Text(
                            text = if (run == null) "데이터 없음"
                            else "${run.runDate} 기준 · 정배열 ${run.alignedCount} / 신규 ${run.newCount} · 대상 ${run.totalScreened}종목",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "새로고침") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = "설정") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FilterBar(state.filters, onFilters)
            HorizontalDivider()
            PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                when {
                    !state.configured -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("우측 상단 톱니바퀴에서 데이터 주소를 입력하세요.", textAlign = TextAlign.Center)
                    }
                    state.error != null && state.all.isEmpty() -> ErrorBox(state.error, onRefresh)
                    !state.loading && state.visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (state.all.isEmpty()) "아직 스크리닝 결과가 없습니다.\n서버 작업이 실행된 후 다시 확인하세요." else "조건에 맞는 종목이 없습니다.",
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> ResultList(state.visible, onOpen)
                }
            }
        }
    }
}

@Composable
private fun FilterBar(f: Filters, onFilters: ((Filters) -> Filters) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = f.newOnly,
            onClick = { onFilters { it.copy(newOnly = !it.newOnly) } },
            label = { Text("신규 진입만") },
        )
        PresetChip(
            prefix = "시총",
            presets = Presets.marketCap,
            value = f.minMarketCap,
            onSelect = { v -> onFilters { it.copy(minMarketCap = v) } },
        )
        PresetChip(
            prefix = "거래대금",
            presets = Presets.tradingValue,
            value = f.minTradingValue,
            onSelect = { v -> onFilters { it.copy(minTradingValue = v) } },
        )
        SortChip(f.sort) { s -> onFilters { it.copy(sort = s) } }
    }
}

@Composable
private fun PresetChip(prefix: String, presets: List<Pair<String, Long>>, value: Long, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = presets.firstOrNull { it.second == value }?.first ?: "제한 없음"
    Box {
        FilterChip(selected = value > 0, onClick = { open = true }, label = { Text("$prefix $label") })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            presets.forEach { (name, v) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(v); open = false })
            }
        }
    }
}

@Composable
private fun SortChip(sort: SortKey, onSelect: (SortKey) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = false, onClick = { open = true }, label = { Text("정렬: ${sort.label}") })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortKey.entries.forEach { s ->
                DropdownMenuItem(text = { Text(s.label) }, onClick = { onSelect(s); open = false })
            }
        }
    }
}

@Composable
private fun ResultList(rows: List<ScreenResult>, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { it.code }) { r ->
            ResultRow(r) { onOpen(r.code) }
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

@Composable
private fun ResultRow(r: ScreenResult, onClick: () -> Unit) {
    val pct = r.changePct ?: 0.0
    val pctColor = if (pct > 0) UpRed else if (pct < 0) DownBlue else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                if (r.isNew) Badge("NEW", UpRed)
            }
            Text(
                "${r.code} · 시총 ${fmtKrwShort(r.marketCap)} · 거래대금 ${fmtKrwShort(r.avgTradingValue20)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(fmtPrice(r.close), style = MaterialTheme.typography.titleMedium)
            Text(fmtPct(r.changePct), color = pctColor, style = MaterialTheme.typography.bodySmall)
            Text("정배열 ${r.daysAligned}일", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Text(
        text,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
private fun ErrorBox(msg: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("불러오기 실패", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(msg, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("다시 시도") }
    }
}

@Composable
private fun SettingsDialog(current: String, canDismiss: Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(current) { mutableStateOf(current) }
    val valid = text.trim().startsWith("http")
    AlertDialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        title = { Text("데이터 주소") },
        text = {
            Column {
                Text(
                    "GitHub 저장소의 docs/data 폴더 주소를 입력하세요.\n예) https://raw.githubusercontent.com/아이디/kospi-ma-screener/main/docs/data",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = false,
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }, enabled = valid) { Text("저장") } },
        dismissButton = { if (canDismiss) TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
