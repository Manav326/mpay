package com.recharge.client.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recharge.client.core.model.RechargeHistoryItem
import com.recharge.client.core.model.RechargeTransactionStatusResponse
import com.recharge.client.core.model.WalletHistoryItem
import com.recharge.client.core.model.WithdrawMoneyResponse
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
    val page: Int = 0,
    val pageSize: Int = 20,
    val totalItems: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
    val loadingWithdrawals: Boolean = false,
    val withdrawalPage: Int = 0,
    val withdrawalTotalItems: Long = 0,
    val withdrawalTotalPages: Int = 0,
    val historyError: String? = null,
    val withdrawing: Boolean = false,
    val withdrawSuccess: String? = null,
    val withdrawError: String? = null,
    val selectedRecharge: RechargeHistoryItem? = null,
    val selectedWalletItem: WalletHistoryItem? = null,
    val selectedWithdrawal: WithdrawMoneyResponse? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null
)

private fun todayIndia(): LocalDate = LocalDate.now(ZoneId.of("Asia/Kolkata"))

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClientRepository.getInstance(application)
    private val _state = MutableStateFlow(WalletUiState())
    val state = _state.asStateFlow()

    private var historyJob: Job? = null
    private var withdrawalJob: Job? = null
    private var detailJob: Job? = null
    private var historyRequestGeneration = 0L
    private var detailRequestGeneration = 0L

    fun resetSession() {
        historyJob?.cancel()
        withdrawalJob?.cancel()
        detailJob?.cancel()
        historyJob = null
        withdrawalJob = null
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

    fun refreshHistory() {
        loadHistoryPage(0)
        loadWithdrawalPage(0)
    }

    fun setHistoryPageSize(size: Int) {
        val normalized = when (size) {
            10, 20, 50 -> size
            else -> 20
        }
        if (_state.value.pageSize == normalized) return
        _state.value = _state.value.copy(pageSize = normalized, page = 0, withdrawalPage = 0)
        refreshHistory()
    }

    fun goToPage(page: Int) {
        val current = _state.value
        val target = page.coerceIn(0, (current.totalPages - 1).coerceAtLeast(0))
        if (target == current.page || current.loadingHistory || current.refreshing) return
        loadHistoryPage(target)
    }

    fun goToWithdrawalPage(page: Int) {
        val current = _state.value
        val target = page.coerceIn(0, (current.withdrawalTotalPages - 1).coerceAtLeast(0))
        if (target == current.withdrawalPage || current.loadingWithdrawals) return
        loadWithdrawalPage(target)
    }

    fun loadMore() {
        if (_state.value.hasNext) goToPage(_state.value.page + 1)
    }

    private fun selectedKind(): String = when (_state.value.filter) {
        WalletHistoryFilter.RECHARGE -> "RECHARGE"
        WalletHistoryFilter.ADD_MONEY -> "ADD_MONEY"
        WalletHistoryFilter.WITHDRAWN -> "WITHDRAWN"
        WalletHistoryFilter.RENTAL -> "RENTAL"
        WalletHistoryFilter.ALL -> "ALL"
    }

    private fun loadHistory(refresh: Boolean) {
        loadHistoryPage(if (refresh) 0 else _state.value.page + 1)
    }

    private fun loadHistoryPage(targetPage: Int) {
        historyJob?.cancel()

        val current = _state.value
        val requestedPage = targetPage.coerceAtLeast(0)
        val fromDate = current.fromDate.toString()
        val toDate = current.toDate.toString()
        val selectedKind = selectedKind()
        val generation = ++historyRequestGeneration

        historyJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                loadingHistory = requestedPage != 0,
                refreshing = requestedPage == 0,
                historyError = null
            )

            repository.walletHistory(
                page = requestedPage,
                size = current.pageSize,
                kind = selectedKind,
                from = fromDate,
                to = toDate
            )
                .onSuccess { response ->
                    if (generation != historyRequestGeneration) return@onSuccess
                    _state.value = _state.value.copy(
                        items = response.items,
                        page = response.page,
                        totalItems = response.totalItems,
                        totalPages = response.totalPages,
                        hasNext = response.hasNext,
                        loadingHistory = false,
                        refreshing = false,
                        historyError = null
                    )
                }
                .onFailure { e ->
                    if (generation != historyRequestGeneration) return@onFailure
                    _state.value = _state.value.copy(
                        loadingHistory = false,
                        refreshing = false,
                        historyError = e.message ?: "Unable to load wallet history."
                    )
                }
        }
    }

    private fun loadWithdrawalPage(targetPage: Int) {
        withdrawalJob?.cancel()
        val current = _state.value
        val requestedPage = targetPage.coerceAtLeast(0)
        val generation = historyRequestGeneration

        withdrawalJob = viewModelScope.launch {
            _state.value = _state.value.copy(loadingWithdrawals = true, historyError = null)
            repository.withdrawalHistory(page = requestedPage, size = current.pageSize)
                .onSuccess { response ->
                    if (generation != historyRequestGeneration) return@onSuccess
                    _state.value = _state.value.copy(
                        withdrawals = response.items,
                        withdrawalPage = response.page,
                        withdrawalTotalItems = response.totalItems,
                        withdrawalTotalPages = response.totalPages,
                        loadingWithdrawals = false
                    )
                }
                .onFailure { e ->
                    if (generation != historyRequestGeneration) return@onFailure
                    _state.value = _state.value.copy(
                        loadingWithdrawals = false,
                        historyError = e.message ?: "Unable to load withdrawal history."
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
            selectedWithdrawal = null,
            detailLoading = item.referenceType.equals("RECHARGE", true) || item.referenceType.equals("WITHDRAWAL", true),
            detailError = null
        )
        if (item.referenceType.equals("WITHDRAWAL", true)) {
            val withdrawalId = item.referenceId?.takeIf { it.isNotBlank() }
            if (withdrawalId == null) {
                _state.value = _state.value.copy(
                    detailLoading = false,
                    detailError = "Withdrawal details are unavailable."
                )
                return
            }
            detailJob = viewModelScope.launch {
                repository.withdrawal(withdrawalId)
                    .onSuccess { withdrawal ->
                        if (generation == detailRequestGeneration && _state.value.selectedWalletItem?.id == item.id) {
                            _state.value = _state.value.copy(
                                selectedWithdrawal = withdrawal,
                                detailLoading = false
                            )
                        }
                    }
                    .onFailure { e ->
                        if (generation == detailRequestGeneration && _state.value.selectedWalletItem?.id == item.id) {
                            _state.value = _state.value.copy(
                                detailLoading = false,
                                detailError = e.message ?: "Unable to load withdrawal details."
                            )
                        }
                    }
            }
            return
        }

        if (!item.referenceType.equals("RECHARGE", true)) {
            _state.value = _state.value.copy(detailLoading = false)
            return
        }

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
            selectedWithdrawal = null,
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
        if (!Regex("^[A-Za-z0-9]+@[A-Za-z]+$").matches(normalizedUpi)) {
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
