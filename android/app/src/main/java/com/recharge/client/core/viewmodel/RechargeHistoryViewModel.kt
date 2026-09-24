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
    val pageSize: Int = 20,
    val totalItems: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val commission: RechargeCommissionSummaryResponse? = null,
    val commissionLoading: Boolean = false,
    val commissionError: String? = null,
    val filter: HistoryFilter = HistoryFilter.TODAY,
    val statusFilter: String = "ALL",
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

    fun setToday() { val end = todayIndia(); setRange(HistoryFilter.TODAY, end, end) }
    fun setLast7Days() { val end = todayIndia(); setRange(HistoryFilter.LAST_7_DAYS, end.minusDays(6), end) }
    fun setThisMonth() { val end = todayIndia(); setRange(HistoryFilter.THIS_MONTH, end.withDayOfMonth(1), end) }
    fun setCustom(from: LocalDate, to: LocalDate) { val today = todayIndia(); setRange(HistoryFilter.CUSTOM, minOf(from, to), minOf(maxOf(from, to), today)) }

    private fun setRange(filter: HistoryFilter, from: LocalDate, to: LocalDate) {
        _state.value = _state.value.copy(filter = filter, fromDate = from, toDate = to)
        loadPage(0)
    }

    fun setStatus(status: String) {
        val normalized = status.trim().uppercase().ifBlank { "ALL" }
        if (_state.value.statusFilter == normalized) return
        _state.value = _state.value.copy(statusFilter = normalized)
        loadPage(0)
    }

    fun setPageSize(size: Int) {
        val normalized = when (size) {
            10, 20, 50 -> size
            else -> 20
        }
        if (_state.value.pageSize == normalized) return
        _state.value = _state.value.copy(pageSize = normalized)
        loadPage(0)
    }

    fun goToPage(page: Int) {
        val target = page.coerceAtLeast(0)
        if (target == _state.value.page || _state.value.loading || _state.value.refreshing) return
        loadPage(target)
    }

    fun load(refresh: Boolean = true) {
        loadPage(if (refresh) 0 else _state.value.page)
    }

    fun loadMore() {
        if (_state.value.hasNext) goToPage(_state.value.page + 1)
    }

    private fun loadPage(targetPage: Int) {
        historyJob?.cancel()
        val current = _state.value
        val fromDate = current.fromDate.toString()
        val toDate = current.toDate.toString()
        val requestedPage = targetPage.coerceAtLeast(0)
        val status = current.statusFilter.takeIf { it != "ALL" }
        val generation = ++historyRequestGeneration

        historyJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = requestedPage != 0,
                loadingMore = false,
                refreshing = requestedPage == 0,
                error = null
            )

            repository.rechargeHistory(
                page = requestedPage,
                size = current.pageSize,
                from = fromDate,
                to = toDate,
                status = status
            )
                .onSuccess { response ->
                    if (generation != historyRequestGeneration) return@onSuccess
                    _state.value = _state.value.copy(
                        items = response.items,
                        page = response.page,
                        totalItems = response.totalItems,
                        totalPages = response.totalPages,
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

    fun refreshHistory() { loadPage(0) }
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
