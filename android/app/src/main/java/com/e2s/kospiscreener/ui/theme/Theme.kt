package com.e2s.kospiscreener.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 한국 증시 관례: 상승 = 빨강, 하락 = 파랑
val UpRed = Color(0xFFD32F2F)
val DownBlue = Color(0xFF1976D2)

// 이동평균선 색 (짧은 기간 → 긴 기간)
val MaColors = listOf(
    Color(0xFFFF6B6B), // MA10
    Color(0xFFFFA726), // MA20
    Color(0xFFFFD54F), // MA60
    Color(0xFF26A69A), // MA120
    Color(0xFF42A5F5), // MA200
)
val MaLabels = listOf("MA10", "MA20", "MA60", "MA120", "MA200")

private val Light = lightColorScheme(
    primary = Color(0xFF1B4965),
    secondary = Color(0xFF5FA8D3),
    tertiary = Color(0xFFCAE9FF),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF62B6CB),
    secondary = Color(0xFF5FA8D3),
    tertiary = Color(0xFF1B4965),
)

@Composable
fun KospiScreenerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
