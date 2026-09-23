package com.recharge.client.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recharge.client.core.model.RechargeHistoryItem
import com.recharge.client.core.model.RechargeTransactionStatusResponse
import com.recharge.client.core.model.WalletHistoryItem
import com.recharge.client.core.repository.ClientRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

enum class WalletHistoryFilter { ALL, RECHARGE, ADD_MONEY, WITHDRAWN, RENTAL }
enum class WalletDateFilter { TODAY, LAST_7_DAYS, THIS_MONTH, CUSTOM }

data class WalletUiState(
    val items: List<WalletHistoryItem> = emptyList(),
    val withdrawals: List<com.recharge.client.core.model.WithdrawalHistoryItem> = emptyList(),
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
    private val repository = ClientRepository.getInstance(application)
    private val _state = MutableStateFlow(WalletUiState())
    val state = _state.asStateFlow()

    private var historyJob: Job? = null
    private var detailJob: Job? = null
    private var historyRequestGeneration = 0L
    private var detailRequestGeneration = 0L

    fun resetSession() {
        historyJob?.cancel()
        detailJob?.cancel()
        historyJob = null
        detailJob = null
        historyRequestGeneration++
        detailRequestGeneration++
        viewModelScope.coroutineContext.cancelChildren()
        _state.value = WalletUiState()
    }

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
        WalletHistoryFilter.RENTAL -> "RENTAL"
        WalletHistoryFilter.ALL -> "ALL"
    }

    private fun loadHistory(refresh: Boolean) {
        val current = _state.value
        if (!refresh && (current.loadingHistory || current.refreshing)) return

        if (refresh) historyJob?.cancel()

        val filter = current.filter
        val fromDate = current.fromDate.toString()
        val toDate = current.toDate.toString()
        val page = if (refresh) 0 else current.page + 1
        val selectedKind = when (filter) {
            WalletHistoryFilter.RECHARGE -> "RECHARGE"
            WalletHistoryFilter.ADD_MONEY -> "ADD_MONEY"
            WalletHistoryFilter.WITHDRAWN -> "WITHDRAWN"
            WalletHistoryFilter.RENTAL -> "RENTAL"
            WalletHistoryFilter.ALL -> "ALL"
        }
        val generation = ++historyRequestGeneration

        historyJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                loadingHistory = !refresh,
                refreshing = refresh,
                historyError = null
            )

            val historyRequest = async {
                repository.walletHistory(
                    page = page,
                    size = 20,
                    kind = selectedKind,
                    from = fromDate,
                    to = toDate
                )
            }
            val withdrawalsRequest = if (refresh) {
                async { repository.withdrawalHistory(page = 0, size = 20) }
            } else null

            val walletResult = historyRequest.await()
            val withdrawalResult = withdrawalsRequest?.await()

            if (generation != historyRequestGeneration) return@launch

            withdrawalResult?.onSuccess { response ->
                _state.value = _state.value.copy(withdrawals = response.items)
            }

            walletResult.onSuccess { response ->
                val items = if (refresh) response.items else _state.value.items + response.items
                _state.value = _state.value.copy(
                    items = items.distinctBy { it.id },
                    page = response.page,
                    hasNext = response.hasNext,
                    loadingHistory = false,
                    refreshing = false,
                    historyError = null
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    loadingHistory = false,
                    refreshing = false,
                    historyError = e.message ?: "Unable to load wallet history."
                )
            }
        }
    }

    fun openDetails(item: WalletHistoryItem) {
        detailJob?.cancel()
        val generation = ++detailRequestGeneration
        _state.value = _state.value.copy(
            selectedWalletItem = item,
            selectedRecharge = null,
            detailLoading = item.referenceType.equals("RECHARGE", true),
            detailError = null
        )
        if (!item.referenceType.equals("RECHARGE", true)) return

        val transactionId = item.referenceId?.takeIf { it.isNotBlank() }
        if (transactionId == null) {
            _state.value = _state.value.copy(
                detailLoading = false,
                detailError = "Recharge transaction details are unavailable."
            )
            return
        }

        detailJob = viewModelScope.launch {
            repository.rechargeStatus(transactionId)
                .onSuccess { tx ->
                    if (generation == detailRequestGeneration && _state.value.selectedWalletItem?.id == item.id) {
                        _state.value = _state.value.copy(selectedRecharge = tx.toHistoryItem(), detailLoading = false)
                    }
                }
                .onFailure { e ->
                    if (generation == detailRequestGeneration && _state.value.selectedWalletItem?.id == item.id) {
                        _state.value = _state.value.copy(
                            detailLoading = false,
                            detailError = e.message ?: "Unable to load recharge details."
                        )
                    }
                }
        }
    }

    fun closeDetails() {
        detailJob?.cancel()
        ++detailRequestGeneration
        _state.value = _state.value.copy(
            selectedRecharge = null,
            selectedWalletItem = null,
            detailLoading = false,
            detailError = null
        )
    }

    fun withdraw(amountText: String, upiId: String, provider: String = "razorpay") {
        if (_state.value.withdrawing) return
        val amount = amountText.toBigDecimalOrNull()
        if (amount == null || amount < BigDecimal("1.00")) {
            _state.value = _state.value.copy(withdrawError = "Enter a valid withdrawal amount of at least ₹1")
            return
        }
        val normalizedUpi = upiId.trim()
        if (normalizedUpi.isBlank()) {
            _state.value = _state.value.copy(withdrawError = "UPI ID is required")
            return
        }
        if (!Regex("^[^\\s@]+@[^\\s@]+$").matches(normalizedUpi)) {
            _state.value = _state.value.copy(withdrawError = "Enter a valid UPI ID")
            return
        }
        val normalizedAmount = amount.setScale(2)
        _state.value = _state.value.copy(withdrawing = true, withdrawError = null, withdrawSuccess = null)
        viewModelScope.launch {
            repository.withdraw(normalizedAmount, normalizedUpi, provider)
                .onSuccess { response ->
                    _state.value = _state.value.copy(
                        withdrawing = false,
                        withdrawSuccess = response.message
                            ?: when (response.status.uppercase()) {
                                "SUCCESS" -> "₹${response.amount.setScale(2)} was sent to ${response.upiId}."
                                "PROCESSING", "PENDING" -> "₹${response.amount.setScale(2)} withdrawal is processing for ${response.upiId}."
                                else -> "Withdrawal status: ${response.status.lowercase()}."
                            }
                    )
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
