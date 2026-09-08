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
)

class ScreenerViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val repo = Repository(settings)

    private val _list = MutableStateFlow(ListUiState(filters = settings.loadFilters(), baseUrl = settings.baseUrl))
    val list: StateFlow<ListUiState> = _list

    private val _detail = MutableStateFlow(DetailUiState())
    val detail: StateFlow<DetailUiState> = _detail

    init { refresh() }

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

    fun openDetail(code: String) {
        val r = _list.value.all.firstOrNull { it.code == code }
        _detail.value = DetailUiState(loading = true, result = r)
        viewModelScope.launch {
            runCatching { repo.loadPrices(code) }
                .onSuccess { p -> _detail.update { it.copy(loading = false, prices = p) } }
                .onFailure { e -> _detail.update { it.copy(loading = false, error = e.message ?: "차트 데이터 실패") } }
        }
    }
}
