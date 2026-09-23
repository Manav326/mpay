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
    private val repository = ClientRepository.getInstance(application)

    private val _state = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)
    val state = _state.asStateFlow()

    fun createOrder(amountText: String, provider: String = "razorpay") {
        if (_state.value is PaymentUiState.CreatingOrder || _state.value is PaymentUiState.Verifying) return
        val amount = amountText.toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            _state.value = PaymentUiState.Error("Enter a valid amount")
            return
        }
        if (amount < BigDecimal("1.00")) {
            _state.value = PaymentUiState.Error("Minimum add-money amount is ₹1")
            return
        }
        if (amount > BigDecimal("50000.00")) {
            _state.value = PaymentUiState.Error("Maximum add-money amount is ₹50,000")
            return
        }

        _state.value = PaymentUiState.CreatingOrder
        viewModelScope.launch {
            repository.createPaymentOrder(
                amount = amount.setScale(2),
                clientRequestId = "ANDROID-${UUID.randomUUID()}",
                provider = provider
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

    fun verifyPayment(provider: String, paymentId: String?, orderId: String, signature: String?) {
        if (_state.value is PaymentUiState.Verifying) return
        if (orderId.isBlank()) {
            _state.value = PaymentUiState.Error("Payment verification data is incomplete")
            return
        }

        _state.value = PaymentUiState.Verifying
        viewModelScope.launch {
            repository.verifyPayment(
                VerifyPaymentRequest(
                    provider = provider,
                    paymentId = paymentId?.takeIf { it.isNotBlank() },
                    orderId = orderId,
                    signature = signature?.takeIf { it.isNotBlank() }
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
                .onFailure { paymentFailed(it.message ?: "Unable to generate PayU payment hash") }
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
