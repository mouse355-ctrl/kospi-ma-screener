package com.e2s.kospiscreener.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.e2s.kospiscreener.alerts.DailyCheckWorker
import com.e2s.kospiscreener.data.Filters
import com.e2s.kospiscreener.data.PriceBar
import com.e2s.kospiscreener.data.Repository
import com.e2s.kospiscreener.data.ScreenResult
import com.e2s.kospiscreener.data.ScreenRun
import com.e2s.kospiscreener.data.Settings
import com.e2s.kospiscreener.data.applyFilters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val run: ScreenRun? = null,
    val all: List<ScreenResult> = emptyList(),
    val filters: Filters = Filters(),
    val baseUrl: String = "",
) {
    val visible: List<ScreenResult> get() = all.applyFilters(filters)
    val configured: Boolean get() = baseUrl.startsWith("http")
}

data class DetailUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val result: ScreenResult? = null,
    val prices: List<PriceBar> = emptyList(),
    /** 자동 전환 대상 목록에서의 현재 위치 (0-base) */
    val index: Int = 0,
    val total: Int = 0,
    /** 자동 전환 중인지 */
    val autoPlay: Boolean = false,
    /** 자동 전환 간격(초) — 3 또는 5 */
    val intervalSec: Int = 3,
) {
    val position: String get() = if (total > 0) "${index + 1} / $total" else ""
    val canNavigate: Boolean get() = total > 1
}

/** 목록 끝에서 다음으로 넘어가면 처음으로 돌아온다 (순환). 순수 함수 — 단위 테스트 대상. */
fun wrapIndex(i: Int, size: Int): Int = if (size <= 0) 0 else ((i % size) + size) % size

class ScreenerViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val repo = Repository(settings)

    private val _list = MutableStateFlow(ListUiState(filters = settings.loadFilters(), baseUrl = settings.baseUrl))
    val list: StateFlow<ListUiState> = _list

    private val _detail = MutableStateFlow(DetailUiState(intervalSec = settings.autoPlayIntervalSec))
    val detail: StateFlow<DetailUiState> = _detail

    /** 상세 화면을 열 때 고정되는 순회 대상 (그때의 필터·정렬이 적용된 목록) */
    private var playlist: List<ScreenResult> = emptyList()

    init { refresh() }

    // ------------------------------------------------------------------ 목록
    fun refresh() {
        if (!settings.isConfigured) {
            _list.update { it.copy(loading = false, error = null) }
            return
        }
        _list.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.loadLatest() }
                .onSuccess { p ->
                    _list.update { it.copy(loading = false, run = p.summary, all = p.results) }
                    DailyCheckWorker.checkAndNotify(getApplication(), settings, p)
                }
                .onFailure { e -> _list.update { it.copy(loading = false, error = e.message ?: "불러오기 실패") } }
        }
    }

    fun setBaseUrl(url: String) {
        settings.baseUrl = url
        _list.update { it.copy(baseUrl = settings.baseUrl, run = null, all = emptyList()) }
        refresh()
    }

    fun updateFilters(transform: (Filters) -> Filters) {
        _list.update { st ->
            val f = transform(st.filters)
            settings.saveFilters(f)
            st.copy(filters = f)
        }
    }

    // ------------------------------------------------------------------ 상세 / 자동 전환
    fun openDetail(code: String) {
        playlist = _list.value.visible
        val idx = playlist.indexOfFirst { it.code == code }.coerceAtLeast(0)
        _detail.update {
            it.copy(
                index = idx,
                total = playlist.size,
                autoPlay = false,
                intervalSec = settings.autoPlayIntervalSec,
            )
        }
        loadAt(idx)
    }

    fun showIndex(i: Int) {
        if (playlist.isEmpty()) return
        val idx = wrapIndex(i, playlist.size)
        _detail.update { it.copy(index = idx) }
        loadAt(idx)
    }

    fun next() = showIndex(_detail.value.index + 1)

    fun prev() = showIndex(_detail.value.index - 1)

    fun setAutoPlay(on: Boolean) = _detail.update { it.copy(autoPlay = on && it.canNavigate) }

    fun toggleAutoPlay() = setAutoPlay(!_detail.value.autoPlay)

    fun setIntervalSec(sec: Int) {
        settings.autoPlayIntervalSec = sec
        _detail.update { it.copy(intervalSec = sec) }
    }

    private fun loadAt(idx: Int) {
        val r = playlist.getOrNull(idx) ?: return
        _detail.update { it.copy(loading = true, error = null, result = r, prices = emptyList()) }
        viewModelScope.launch {
            runCatching { repo.loadPrices(r.code) }
                .onSuccess { p ->
                    // 로딩 중 사용자가 다른 종목으로 넘어갔다면 결과를 버린다
                    if (_detail.value.result?.code == r.code) {
                        _detail.update { it.copy(loading = false, prices = p) }
                    }
                }
                .onFailure { e ->
                    if (_detail.value.result?.code == r.code) {
                        _detail.update { it.copy(loading = false, error = e.message ?: "차트 데이터 실패") }
                    }
                }
        }
    }
}
