package com.recharge.client.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recharge.client.core.model.OperatorCheckResponse
import com.recharge.client.core.model.RechargePlan
import com.recharge.client.core.model.RechargeResponse
import com.recharge.client.core.model.RechargeTransactionStatusResponse
import com.recharge.client.core.model.PaymentOrderResponse
import com.recharge.client.core.model.PaymentVerificationResponse
import com.recharge.client.core.repository.ClientRepository
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RechargeActionState {
    data object Idle : RechargeActionState
    data object Submitting : RechargeActionState
    data class Success(val response: RechargeResponse) : RechargeActionState
    data class Pending(val response: RechargeResponse) : RechargeActionState
    data class Failure(val message: String) : RechargeActionState
}

data class RechargeUiState(
    val mobile: String = "",
    val recipientName: String = "",
    val operator: OperatorCheckResponse? = null,
    val plans: List<RechargePlan> = emptyList(),
    val selectedPlan: RechargePlan? = null,
    val detecting: Boolean = false,
    val loadingPlans: Boolean = false,
    val refreshingWallet: Boolean = false,
    val executing: Boolean = false,
    val error: String? = null,
    val walletBalance: BigDecimal? = null,
    val action: RechargeActionState = RechargeActionState.Idle,
    val transactionStatus: RechargeTransactionStatusResponse? = null,
    val gatewayOrder: PaymentOrderResponse? = null
)

class RechargeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClientRepository.getInstance(application)

    private val _state = MutableStateFlow(RechargeUiState())
    val state = _state.asStateFlow()

    private var pollingJob: Job? = null

    fun setMobile(value: String, contactName: String? = null) {
        val normalized = value.filter(Char::isDigit).take(10)
        val current = _state.value
        if (current.mobile == normalized) {
            contactName?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
                _state.value = current.copy(recipientName = name.take(120))
            }
            return
        }

        pollingJob?.cancel()
        _state.value = current.copy(
            mobile = normalized,
            recipientName = contactName?.trim()?.takeIf { it.isNotBlank() } ?: "",
            operator = null,
            plans = emptyList(),
            selectedPlan = null,
            detecting = false,
            loadingPlans = false,
            error = null,
            action = RechargeActionState.Idle,
            transactionStatus = null,
            gatewayOrder = null
        )
    }

    fun setRecipientName(value: String) {
        _state.value = _state.value.copy(recipientName = value.take(120))
    }

    fun detectAndLoad() {
        val mobile = _state.value.mobile
        if (!isValidIndianMobile(mobile)) {
            _state.value = _state.value.copy(
                operator = null,
                plans = emptyList(),
                selectedPlan = null,
                error = "Enter a valid 10-digit Indian mobile number"
            )
            return
        }

        pollingJob?.cancel()
        viewModelScope.launch {
            _state.value = _state.value.copy(
                detecting = true,
                loadingPlans = false,
                error = null,
                operator = null,
                plans = emptyList(),
                selectedPlan = null,
                action = RechargeActionState.Idle,
                transactionStatus = null
            )

            repository.detectOperator(mobile)
                .onSuccess { detected ->
                    if (detected.pending) {
                        _state.value = _state.value.copy(
                            operator = null,
                            detecting = false,
                            loadingPlans = false,
                            plans = emptyList(),
                            selectedPlan = null,
                            error = friendlyRechargeError(detected.message)
                                .ifBlank { "Operator detection is still processing. Please try again later." }
                        )
                        return@onSuccess
                    }

                    if (detected.type?.equals("POSTPAID", ignoreCase = true) == true) {
                        _state.value = _state.value.copy(
                            operator = detected,
                            detecting = false,
                            loadingPlans = false,
                            plans = emptyList(),
                            selectedPlan = null,
                            error = "Personalized recharge offers are currently available only for prepaid numbers."
                        )
                        return@onSuccess
                    }

                    _state.value = _state.value.copy(
                        operator = detected,
                        detecting = false,
                        loadingPlans = true,
                        error = null
                    )

                    repository.plans(mobile, detected.operator, detected.circle, detected.providerOperator, detected.providerCircle)
                        .onSuccess { plans ->
                            _state.value = _state.value.copy(
                                loadingPlans = false,
                                plans = plans.sortedWith(compareBy<RechargePlan> { it.amount }.thenBy { it.id }),
                                selectedPlan = null,
                                error = if (plans.isEmpty()) "No recharge offers are available for this number right now." else null
                            )
                        }
                        .onFailure { failure ->
                            _state.value = _state.value.copy(
                                loadingPlans = false,
                                plans = emptyList(),
                                selectedPlan = null,
                                error = friendlyRechargeError(failure.message)
                            )
                        }
                }
                .onFailure { failure ->
                    _state.value = _state.value.copy(
                        detecting = false,
                        loadingPlans = false,
                        operator = null,
                        plans = emptyList(),
                        selectedPlan = null,
                        error = friendlyRechargeError(failure.message)
                    )
                }
        }
    }

    fun refreshPlans() {
        detectAndLoad()
    }

    fun refreshWallet() {
        val current = _state.value
        if (current.refreshingWallet) return
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshingWallet = true)
            repository.wallet()
                .onSuccess { wallet ->
                    _state.value = _state.value.copy(
                        refreshingWallet = false,
                        walletBalance = wallet.availableBalance
                    )
                }
                .onFailure { failure ->
                    _state.value = _state.value.copy(refreshingWallet = false)
                }
        }
    }

    fun selectPlan(plan: RechargePlan) {
        _state.value = _state.value.copy(
            selectedPlan = plan,
            error = null,
            action = RechargeActionState.Idle,
            transactionStatus = null
        )
        refreshWallet()
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedPlan = null, action = RechargeActionState.Idle, error = null)
    }

    fun startGatewayRechargePayment() {
        val current = _state.value
        if (current.executing) return
        val plan = current.selectedPlan ?: run {
            _state.value = current.copy(error = "Select a recharge plan first.")
            return
        }
        val operator = current.operator ?: run {
            _state.value = current.copy(error = "Detect the operator before continuing.")
            return
        }

        _state.value = current.copy(executing = true, error = null, gatewayOrder = null)
        viewModelScope.launch {
            repository.createRechargePaymentOrder(
                com.recharge.client.core.model.RechargeRequest(
                    mobileNumber = current.mobile,
                    operator = operator.operator,
                    circle = operator.circle,
                    planId = plan.id,
                    clientRequestId = "ANDROID-RECHARGE-PAY-" + UUID.randomUUID(),
                    recipientName = current.recipientName.trim().takeIf { it.isNotBlank() }
                )
            ).onSuccess { order ->
                _state.value = _state.value.copy(executing = false, gatewayOrder = order)
            }.onFailure { failure ->
                _state.value = _state.value.copy(
                    executing = false,
                    gatewayOrder = null,
                    action = RechargeActionState.Failure(friendlyRechargeError(failure.message))
                )
            }
        }
    }

    fun generatePayUHash(
        hashName: String,
        hashString: String,
        postSalt: String? = null,
        hashType: String? = null,
        onGenerated: (String) -> Unit
    ) {
        viewModelScope.launch {
            repository.generatePayUHash(hashName, hashString, postSalt, hashType)
                .onSuccess(onGenerated)
                .onFailure { gatewayPaymentFailed(it.message ?: "Unable to generate PayU payment hash") }
        }
    }

    fun gatewayPaymentFailed(message: String?) {
        _state.value = _state.value.copy(
            executing = false,
            gatewayOrder = null,
            action = RechargeActionState.Failure(
                message?.takeIf { it.isNotBlank() } ?: "Gateway payment was cancelled or failed."
            )
        )
    }

    fun verifyGatewayPayment(provider: String, paymentId: String?, orderId: String?, signature: String?) {
        val current = _state.value
        if (current.executing) return
        if (orderId.isNullOrBlank()) {
            _state.value = current.copy(
                executing = false,
                gatewayOrder = null,
                action = RechargeActionState.Failure("Payment verification data is incomplete.")
            )
            return
        }

        _state.value = current.copy(executing = true, gatewayOrder = null, error = null)
        viewModelScope.launch {
            repository.verifyPayment(
                com.recharge.client.core.model.VerifyPaymentRequest(
                    provider = provider,
                    paymentId = paymentId,
                    orderId = orderId,
                    signature = signature
                )
            ).onSuccess { verification ->
                val response = gatewayVerificationToRechargeResponse(verification)
                _state.value = _state.value.copy(
                    executing = false,
                    walletBalance = verification.availableBalance,
                    action = when (verification.rechargeStatus?.uppercase()) {
                        "SUCCESS" -> RechargeActionState.Success(response)
                        "FAILED" -> RechargeActionState.Failure(verification.message ?: "Recharge failed after payment verification.")
                        else -> RechargeActionState.Pending(response)
                    }
                )
                if (response.transactionId.isNotBlank() && verification.rechargeStatus?.uppercase() == "PENDING") {
                    startPolling(response.transactionId)
                }
            }.onFailure { failure ->
                _state.value = _state.value.copy(
                    executing = false,
                    gatewayOrder = null,
                    action = RechargeActionState.Failure(friendlyRechargeError(failure.message))
                )
                refreshWallet()
            }
        }
    }

    private fun gatewayVerificationToRechargeResponse(verification: PaymentVerificationResponse): RechargeResponse {
        val amount = verification.amount ?: _state.value.selectedPlan?.amount ?: BigDecimal.ZERO
        return RechargeResponse(
            transactionId = verification.transactionId.orEmpty(),
            status = verification.rechargeStatus ?: verification.status.orEmpty(),
            amount = amount,
            commission = verification.commission ?: BigDecimal.ZERO,
            walletDebitAmount = verification.walletDebitAmount ?: amount,
            walletBalance = verification.balance
        )
    }

    fun executeSelectedPlan() {
        val state = _state.value
        if (state.executing) return
        val plan = state.selectedPlan ?: run {
            _state.value = state.copy(error = "Select a recharge plan first.")
            return
        }
        val operator = state.operator ?: run {
            _state.value = state.copy(error = "Detect the operator before continuing.")
            return
        }
        val balance = state.walletBalance ?: run {
            refreshWallet()
            _state.value = state.copy(error = "Checking your wallet balance. Please try again in a moment.")
            return
        }

        _state.value = state.copy(
            executing = true,
            error = null,
            action = RechargeActionState.Submitting
        )
        viewModelScope.launch {

            repository.recharge(
                mobileNumber = state.mobile,
                operator = operator.operator,
                circle = operator.circle,
                planId = plan.id,
                clientRequestId = "ANDROID-RECHARGE-${UUID.randomUUID()}"
            )
                .onSuccess { response ->
                    _state.value = _state.value.copy(
                        executing = false,
                        walletBalance = response.walletAvailableBalance,
                        action = when (response.status.uppercase()) {
                            "SUCCESS" -> RechargeActionState.Success(response)
                            "FAILED" -> RechargeActionState.Failure("Recharge failed. Your wallet balance has not been permanently deducted.")
                            else -> RechargeActionState.Pending(response)
                        }
                    )

                    if (response.status.uppercase() == "PENDING") {
                        startPolling(response.transactionId)
                    }
                }
                .onFailure { failure ->
                    _state.value = _state.value.copy(
                        executing = false,
                        action = RechargeActionState.Failure(friendlyRechargeError(failure.message))
                    )
                    refreshWallet()
                }
        }
    }

    private fun startPolling(transactionId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            repeat(15) {
                delay(2000)
                repository.rechargeStatus(transactionId)
                    .onSuccess { status ->
                        _state.value = _state.value.copy(
                            transactionStatus = status,
                            walletBalance = status.walletAvailableBalance
                        )

                        when (status.status.uppercase()) {
                            "SUCCESS" -> {
                                _state.value = _state.value.copy(action = RechargeActionState.Success(
                                    RechargeResponse(
                                        transactionId = status.transactionId,
                                        status = status.status,
                                        amount = status.amount,
                                        commission = status.clientCommission,
                                        walletDebitAmount = status.walletDebitAmount,
                                        walletBalance = status.walletBalance
                                    )
                                ))
                                refreshWallet()
                                pollingJob?.cancel()
                            }
                            "FAILED" -> {
                                _state.value = _state.value.copy(action = RechargeActionState.Failure(
                                    status.message ?: "Recharge failed."
                                ))
                                refreshWallet()
                                pollingJob?.cancel()
                            }
                        }
                    }
            }
        }
    }

    fun dismissResult() {
        _state.value = _state.value.copy(
            action = RechargeActionState.Idle,
            transactionStatus = null,
            error = null,
            executing = false
        )
        refreshWallet()
    }

    fun clear() {
        pollingJob?.cancel()
        _state.value = RechargeUiState()
    }

    private fun isValidIndianMobile(mobile: String): Boolean =
        mobile.matches(Regex("[6-9][0-9]{9}"))

    private fun friendlyRechargeError(message: String?): String {
        val raw = message?.trim().orEmpty()
        return when {
            raw.contains("R-Offer", ignoreCase = true) &&
                (raw.contains("timed out", ignoreCase = true) ||
                    raw.contains("could not be reached", ignoreCase = true)) ->
                "Recharge offers service is taking too long to respond. Please try again later."
            raw.contains("R-Offer", ignoreCase = true) &&
                raw.contains("rate limit", ignoreCase = true) ->
                "Recharge offers service is temporarily busy. Please try again later."
            raw.contains("R-Offer", ignoreCase = true) &&
                raw.contains("insufficient balance", ignoreCase = true) ->
                "The recharge offers provider account has insufficient balance. Please try again later."
            raw.contains("timed out", ignoreCase = true) ||
                raw.contains("could not be reached", ignoreCase = true) ->
                "Operator detection service is taking too long to respond. Please try again later."
            raw.contains("rate limit", ignoreCase = true) ->
                "Operator detection service is temporarily busy. Please try again later."
            raw.contains("insufficient balance", ignoreCase = true) ->
                "The operator provider account has insufficient balance. Please try again later."
            raw.contains("no api access", ignoreCase = true) ->
                "The operator detection service is not enabled for this account."
            raw.contains("No configured recharge plan provider supports", ignoreCase = true) ->
                "Recharge plans are currently available only for Airtel and Vi numbers."
            raw.contains("Personalized R-Offers", ignoreCase = true) ->
                "Personalized offers through Way2API are currently supported only for Airtel and Vi numbers."
            raw.contains("no recharge offers", ignoreCase = true) ->
                "No personalized recharge offers are available for this number right now."
            raw.contains("available balance", ignoreCase = true) ||
                raw.contains("insufficient", ignoreCase = true) ->
                "Your wallet balance is not sufficient for this recharge."
            raw.isBlank() -> "Something went wrong. Please try again."
            else -> raw
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }
}
