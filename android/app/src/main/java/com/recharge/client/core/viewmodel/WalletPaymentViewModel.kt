package com.recharge.client.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recharge.client.core.model.PaymentOrderResponse
import com.recharge.client.core.model.VerifyPaymentRequest
import com.recharge.client.core.repository.ClientRepository
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PaymentUiState {
    data object Idle : PaymentUiState
    data object CreatingOrder : PaymentUiState
    data class OrderCreated(val order: PaymentOrderResponse) : PaymentUiState
    data object Verifying : PaymentUiState
    data class Success(val message: String) : PaymentUiState
    data class Error(val message: String) : PaymentUiState
}

class WalletPaymentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClientRepository(application)

    private val _state = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)
    val state = _state.asStateFlow()

    fun createOrder(amountText: String) {
        val amount = amountText.toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            _state.value = PaymentUiState.Error("Enter a valid amount")
            return
        }
        if (amount < BigDecimal("10.00")) {
            _state.value = PaymentUiState.Error("Minimum add-money amount is ₹10")
            return
        }
        if (amount > BigDecimal("50000.00")) {
            _state.value = PaymentUiState.Error("Maximum add-money amount is ₹50,000")
            return
        }

        viewModelScope.launch {
            _state.value = PaymentUiState.CreatingOrder
            repository.createPaymentOrder(
                amount = amount.setScale(2),
                clientRequestId = "ANDROID-${UUID.randomUUID()}"
            ).onSuccess {
                if (it.orderId.isBlank() || it.keyId.isBlank()) {
                    _state.value = PaymentUiState.Error("Payment order response is incomplete")
                } else {
                    _state.value = PaymentUiState.OrderCreated(it)
                }
            }.onFailure {
                _state.value = PaymentUiState.Error(it.message ?: "Unable to create payment order")
            }
        }
    }

    fun verifyPayment(paymentId: String, orderId: String, signature: String) {
        if (paymentId.isBlank() || orderId.isBlank() || signature.isBlank()) {
            _state.value = PaymentUiState.Error("Payment verification data is incomplete")
            return
        }

        viewModelScope.launch {
            _state.value = PaymentUiState.Verifying
            repository.verifyPayment(
                VerifyPaymentRequest(
                    provider = "razorpay",
                    paymentId = paymentId,
                    orderId = orderId,
                    signature = signature
                )
            ).onSuccess {
                _state.value = PaymentUiState.Success("Payment successful. Wallet is being updated.")
            }.onFailure {
                _state.value = PaymentUiState.Error(
                    it.message ?: "Payment was received but verification failed. Please refresh your wallet."
                )
            }
        }
    }

    fun paymentFailed(message: String?) {
        _state.value = PaymentUiState.Error(
            message?.takeIf { it.isNotBlank() } ?: "Payment was cancelled or failed"
        )
    }

    fun reset() {
        _state.value = PaymentUiState.Idle
    }
}
