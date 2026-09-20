package com.recharge.client.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recharge.client.core.model.RechargeHistoryItem
import com.recharge.client.core.model.RechargeTransactionStatusResponse
import com.recharge.client.core.model.WalletHistoryItem
import com.recharge.client.core.repository.ClientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

enum class WalletHistoryFilter { ALL, RECHARGE, ADD_MONEY, WITHDRAWN }
enum class WalletDateFilter { TODAY, LAST_7_DAYS, THIS_MONTH, CUSTOM }

data class WalletUiState(
    val items: List<WalletHistoryItem> = emptyList(),
    val filter: WalletHistoryFilter = WalletHistoryFilter.ALL,
    val dateFilter: WalletDateFilter = WalletDateFilter.TODAY,
    val fromDate: LocalDate = todayIndia(),
    val toDate: LocalDate = todayIndia(),
    val loadingHistory: Boolean = false,
    val refreshing: Boolean = false,
    val hasNext: Boolean = false,
    val page: Int = 0,
    val historyError: String? = null,
    val withdrawing: Boolean = false,
    val withdrawSuccess: String? = null,
    val withdrawError: String? = null,
    val selectedRecharge: RechargeHistoryItem? = null,
    val selectedWalletItem: WalletHistoryItem? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null
)

private fun todayIndia(): LocalDate = LocalDate.now(ZoneId.of("Asia/Kolkata"))

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClientRepository(application)
    private val _state = MutableStateFlow(WalletUiState())
    val state = _state.asStateFlow()

    fun selectFilter(filter: WalletHistoryFilter) {
        _state.value = _state.value.copy(filter = filter)
        loadHistory(refresh = true)
    }

    fun setToday() {
        val end = todayIndia(); setDateRange(WalletDateFilter.TODAY, end, end)
    }

    fun setLast7Days() {
        val end = todayIndia(); setDateRange(WalletDateFilter.LAST_7_DAYS, end.minusDays(6), end)
    }

    fun setThisMonth() {
        val end = todayIndia(); setDateRange(WalletDateFilter.THIS_MONTH, end.withDayOfMonth(1), end)
    }

    fun setCustom(from: LocalDate, to: LocalDate) {
        val today = todayIndia()
        val normalizedFrom = minOf(from, to).coerceAtMost(today)
        val normalizedTo = minOf(maxOf(from, to), today)
        setDateRange(WalletDateFilter.CUSTOM, normalizedFrom, normalizedTo)
    }

    private fun setDateRange(filter: WalletDateFilter, from: LocalDate, to: LocalDate) {
        _state.value = _state.value.copy(dateFilter = filter, fromDate = from, toDate = to)
        loadHistory(refresh = true)
    }

    fun refreshHistory() = loadHistory(true)

    fun loadMore() {
        if (!_state.value.hasNext || _state.value.loadingHistory || _state.value.refreshing) return
        loadHistory(false)
    }

    private fun selectedKind(): String = when (_state.value.filter) {
        WalletHistoryFilter.RECHARGE -> "RECHARGE"
        WalletHistoryFilter.ADD_MONEY -> "ADD_MONEY"
        WalletHistoryFilter.WITHDRAWN -> "WITHDRAWN"
        WalletHistoryFilter.ALL -> "ALL"
    }

    private fun loadHistory(refresh: Boolean) {
        if (_state.value.loadingHistory || _state.value.refreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingHistory = !refresh, refreshing = refresh, historyError = null)
            val page = if (refresh) 0 else _state.value.page + 1
            repository.walletHistory(
                page = page, size = 20, kind = selectedKind(),
                from = _state.value.fromDate.toString(), to = _state.value.toDate.toString()
            ).onSuccess { response ->
                val items = if (refresh) response.items else _state.value.items + response.items
                _state.value = _state.value.copy(
                    items = items.distinctBy { it.id }, page = response.page, hasNext = response.hasNext,
                    loadingHistory = false, refreshing = false, historyError = null
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(loadingHistory = false, refreshing = false, historyError = e.message ?: "Unable to load wallet history.")
            }
        }
    }

    fun openDetails(item: WalletHistoryItem) {
        _state.value = _state.value.copy(
            selectedWalletItem = item, selectedRecharge = null, detailLoading = item.referenceType.equals("RECHARGE", true), detailError = null
        )
        if (!item.referenceType.equals("RECHARGE", true)) return
        val transactionId = item.referenceId?.takeIf { it.isNotBlank() }
        if (transactionId == null) {
            _state.value = _state.value.copy(detailLoading = false, detailError = "Recharge transaction details are unavailable.")
            return
        }
        viewModelScope.launch {
            repository.rechargeStatus(transactionId)
                .onSuccess { tx -> _state.value = _state.value.copy(selectedRecharge = tx.toHistoryItem(), detailLoading = false) }
                .onFailure { e -> _state.value = _state.value.copy(detailLoading = false, detailError = e.message ?: "Unable to load recharge details.") }
        }
    }

    fun closeDetails() {
        _state.value = _state.value.copy(selectedRecharge = null, selectedWalletItem = null, detailLoading = false, detailError = null)
    }

    fun withdraw(amountText: String, upiId: String) {
        val amount = amountText.toBigDecimalOrNull()
        if (amount == null || amount < BigDecimal("1.00")) {
            _state.value = _state.value.copy(withdrawError = "Enter a valid withdrawal amount of at least ₹1")
            return
        }
        if (!Regex("^[A-Za-z0-9._-]+@[A-Za-z]{2,}$").matches(upiId.trim())) {
            _state.value = _state.value.copy(withdrawError = "Enter a valid UPI ID")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(withdrawing = true, withdrawError = null, withdrawSuccess = null)
            repository.withdraw(amount, upiId)
                .onSuccess { response ->
                    _state.value = _state.value.copy(withdrawing = false, withdrawSuccess = "₹${response.amount.setScale(2)} will be sent to ${response.upiId} (mocked).")
                    loadHistory(true)
                }
                .onFailure { e -> _state.value = _state.value.copy(withdrawing = false, withdrawError = e.message ?: "Unable to withdraw money.") }
        }
    }

    fun clearWithdrawMessage() { _state.value = _state.value.copy(withdrawError = null, withdrawSuccess = null) }
}

private fun RechargeTransactionStatusResponse.toHistoryItem() = RechargeHistoryItem(
    transactionId = transactionId, clientRequestId = clientRequestId, mobileNumber = mobileNumber,
    operator = operator, circle = circle, planId = planId, planDescription = planDescription, planValidity = planValidity,
    amount = amount, walletDebitAmount = walletDebitAmount, status = status, provider = provider,
    providerReference = providerReference, providerOrderId = providerOrderId, walletLedgerRef = walletLedgerRef,
    completedAt = completedAt, clientCommission = clientCommission, companyCommission = companyCommission,
    message = message, createdAt = completedAt ?: "", updatedAt = completedAt ?: ""
)
