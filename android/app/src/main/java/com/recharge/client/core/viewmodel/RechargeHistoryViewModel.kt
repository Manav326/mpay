package com.recharge.client.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recharge.client.core.model.RechargeCommissionSummaryResponse
import com.recharge.client.core.model.RechargeHistoryItem
import com.recharge.client.core.repository.ClientRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

enum class HistoryFilter { TODAY, LAST_7_DAYS, THIS_MONTH, CUSTOM }

data class RechargeHistoryUiState(
    val items: List<RechargeHistoryItem> = emptyList(),
    val page: Int = 0,
    val totalItems: Long = 0,
    val hasNext: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val commission: RechargeCommissionSummaryResponse? = null,
    val commissionLoading: Boolean = false,
    val commissionError: String? = null,
    val filter: HistoryFilter = HistoryFilter.TODAY,
    val fromDate: LocalDate = java.time.ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDate(),
    val toDate: LocalDate = java.time.ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDate()
)

class RechargeHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClientRepository.getInstance(application)
    private val _state = MutableStateFlow(RechargeHistoryUiState())
    val state = _state.asStateFlow()

    private var historyJob: Job? = null
    private var historyRequestGeneration = 0L

    fun resetSession() {
        viewModelScope.coroutineContext.cancelChildren()
        historyJob = null
        historyRequestGeneration++
        _state.value = RechargeHistoryUiState()
    }

    private fun todayIndia(): LocalDate = java.time.ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDate()

    init { load(refresh = true) }

    fun setToday() { val end = todayIndia(); setRange(HistoryFilter.TODAY, end, end) }
    fun setLast7Days() { val end = todayIndia(); setRange(HistoryFilter.LAST_7_DAYS, end.minusDays(6), end) }
    fun setThisMonth() { val end = todayIndia(); setRange(HistoryFilter.THIS_MONTH, end.withDayOfMonth(1), end) }
    fun setCustom(from: LocalDate, to: LocalDate) { val today = todayIndia(); setRange(HistoryFilter.CUSTOM, minOf(from, to), minOf(maxOf(from, to), today)) }

    private fun setRange(filter: HistoryFilter, from: LocalDate, to: LocalDate) {
        _state.value = _state.value.copy(filter = filter, fromDate = from, toDate = to)
        load(refresh = true)
    }

    fun load(refresh: Boolean = true) {
        val current = _state.value
        if (!refresh && (current.loading || current.refreshing)) return
        if (refresh) historyJob?.cancel()

        val fromDate = current.fromDate.toString()
        val toDate = current.toDate.toString()
        val page = if (refresh) 0 else current.page + 1
        val generation = ++historyRequestGeneration

        historyJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = !refresh && _state.value.items.isEmpty(),
                refreshing = refresh,
                error = null
            )

            repository.rechargeHistory(page, 20, fromDate, toDate)
                .onSuccess { response ->
                    if (generation != historyRequestGeneration) return@onSuccess
                    val items = if (refresh) response.items else _state.value.items + response.items
                    _state.value = _state.value.copy(
                        items = items.distinctBy { it.transactionId },
                        page = response.page,
                        totalItems = response.totalItems,
                        hasNext = response.hasNext,
                        loading = false,
                        loadingMore = false,
                        refreshing = false,
                        error = null
                    )
                }
                .onFailure { e ->
                    if (generation != historyRequestGeneration) return@onFailure
                    _state.value = _state.value.copy(
                        loading = false,
                        loadingMore = false,
                        refreshing = false,
                        error = e.message ?: "Unable to load recharge history."
                    )
                }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasNext || current.loadingMore || current.loading || current.refreshing) return

        val page = current.page + 1
        val fromDate = current.fromDate.toString()
        val toDate = current.toDate.toString()
        val generation = historyRequestGeneration

        historyJob = viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true, error = null)
            repository.rechargeHistory(page, 20, fromDate, toDate)
                .onSuccess { response ->
                    if (generation != historyRequestGeneration) return@onSuccess
                    _state.value = _state.value.copy(
                        items = (_state.value.items + response.items).distinctBy { it.transactionId },
                        page = response.page,
                        totalItems = response.totalItems,
                        hasNext = response.hasNext,
                        loadingMore = false
                    )
                }
                .onFailure { e ->
                    if (generation != historyRequestGeneration) return@onFailure
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        error = e.message ?: "Unable to load more history."
                    )
                }
        }
    }

    fun refreshHistory() { load(refresh = true) }
    fun refreshAll() { refreshHistory(); loadCommission() }

    fun loadCommission() {
        if (_state.value.commissionLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(commissionLoading = true, commissionError = null)
            repository.rechargeCommissionSummary()
                .onSuccess { response -> _state.value = _state.value.copy(commission = response, commissionLoading = false) }
                .onFailure { e -> _state.value = _state.value.copy(commissionLoading = false, commissionError = e.message ?: "Unable to load commission summary.") }
        }
    }
}
