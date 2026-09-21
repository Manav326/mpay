package com.recharge.client.core.repository

import android.content.Context
import android.net.Uri
import com.recharge.client.core.model.CreatePaymentOrderRequest
import com.recharge.client.core.model.CurrentUserResponse
import com.recharge.client.core.model.OperatorCheckRequest
import com.recharge.client.core.model.PaymentOrderResponse
import com.recharge.client.core.model.PayUHashRequest
import com.recharge.client.core.model.OperatorCheckResponse
import com.recharge.client.core.model.RechargePlan
import com.recharge.client.core.model.RechargeRequest
import com.recharge.client.core.model.RechargeResponse
import com.recharge.client.core.model.RechargeTransactionStatusResponse
import com.recharge.client.core.model.RechargeHistoryResponse
import com.recharge.client.core.model.RechargeCommissionSummaryResponse
import com.recharge.client.core.model.VerifyPaymentRequest
import com.recharge.client.core.model.WalletResponse
import com.recharge.client.core.model.WalletHistoryResponse
import com.recharge.client.core.model.WithdrawMoneyRequest
import com.recharge.client.core.model.WithdrawMoneyResponse
import com.recharge.client.core.model.ProfileUpdateRequest
import com.recharge.client.core.network.ApiError
import com.recharge.client.core.network.ClientApi
import com.recharge.client.core.network.NetworkModule
import com.recharge.client.core.cache.ProfileCacheStore
import java.math.BigDecimal
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ClientRepository(context: Context) {
    private val appContext = context.applicationContext
    private val api: ClientApi = NetworkModule.clientApi(context.applicationContext)
    private val profileCache = ProfileCacheStore(appContext)

    suspend fun currentUser(): Result<CurrentUserResponse> {
        val result = runCatching {
            val response = api.me()
            if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
            response.body()!!
        }
        result.onSuccess { profileCache.save(it) }
        return result.recoverCatching { profileCache.get() ?: throw it }
    }

    suspend fun wallet(): Result<WalletResponse> = runCatching {
        val response = api.wallet()
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }


    suspend fun profile(): Result<CurrentUserResponse> {
        val result = runCatching {
            val response = api.profile()
            if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
            response.body()!!
        }
        result.onSuccess { profileCache.save(it) }
        return result.recoverCatching { profileCache.get() ?: throw it }
    }

    suspend fun updateProfile(name: String?, email: String?): Result<CurrentUserResponse> = runCatching {
        val response = api.updateProfile(ProfileUpdateRequest(name, email))
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!.also { profileCache.save(it) }
    }

    suspend fun uploadProfileImage(uri: Uri): Result<CurrentUserResponse> = runCatching {
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri)?.lowercase() ?: "image/jpeg"
        require(mime in setOf("image/jpeg", "image/png", "image/webp")) { "Please select a JPG, PNG or WebP image." }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read selected image")
        require(bytes.size <= 5 * 1024 * 1024) { "Profile image must be 5 MB or smaller." }
        val body = bytes.toRequestBody(mime.toMediaType())
        val part = MultipartBody.Part.createFormData("image", "profile-image", body)
        val response = api.uploadProfileImage(part)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!.also { profileCache.save(it) }
    }

    suspend fun deleteProfileImage(): Result<CurrentUserResponse> = runCatching {
        val response = api.deleteProfileImage()
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!.also { profileCache.save(it) }
    }

    suspend fun createPaymentOrder(amount: BigDecimal, clientRequestId: String): Result<PaymentOrderResponse> = runCatching {
        val response = api.createPaymentOrder(CreatePaymentOrderRequest(amount, clientRequestId))
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun verifyPayment(request: VerifyPaymentRequest): Result<com.recharge.client.core.model.PaymentVerificationResponse> = runCatching {
        val response = api.verifyPayment(request)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun createRechargePaymentOrder(request: RechargeRequest): Result<PaymentOrderResponse> = runCatching {
        val response = api.createRechargePaymentOrder(request)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun generatePayUHash(hashName: String, hashString: String, postSalt: String?, hashType: String?): Result<String> = runCatching {
        val response = api.payuHash(PayUHashRequest(hashName, hashString, postSalt, hashType))
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!.hash
    }

    suspend fun detectOperator(mobile: String): Result<OperatorCheckResponse> = runCatching {
        val response = api.operator(OperatorCheckRequest(mobile))
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun plans(
        mobile: String,
        operator: String,
        circle: String,
        providerOperator: String? = null,
        providerCircle: String? = null
    ): Result<List<RechargePlan>> = runCatching {
        val response = api.plans(mobile, operator, circle, providerOperator, providerCircle)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }
    suspend fun recharge(
        mobileNumber: String,
        operator: String,
        circle: String,
        planId: String,
        clientRequestId: String
    ): Result<RechargeResponse> = runCatching {
        val response = api.recharge(
            RechargeRequest(
                mobileNumber = mobileNumber,
                operator = operator,
                circle = circle,
                planId = planId,
                clientRequestId = clientRequestId
            )
        )
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun rechargeStatus(transactionId: String): Result<RechargeTransactionStatusResponse> = runCatching {
        val response = api.rechargeStatus(transactionId)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun rechargeHistory(page: Int = 0, size: Int = 20, from: String? = null, to: String? = null): Result<RechargeHistoryResponse> = runCatching {
        val response = api.rechargeHistory(page, size, from, to)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun rechargeCommissionSummary(): Result<RechargeCommissionSummaryResponse> = runCatching {
        val response = api.rechargeCommissionSummary()
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun walletHistory(page: Int = 0, size: Int = 20, kind: String? = null, from: String? = null, to: String? = null): Result<WalletHistoryResponse> = runCatching {
        val response = api.walletHistory(page, size, kind, from, to)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun withdraw(amount: BigDecimal, upiId: String, provider: String): Result<WithdrawMoneyResponse> = runCatching {
        val response = api.withdraw(
            WithdrawMoneyRequest(
                amount = amount.setScale(2),
                provider = provider,
                clientRequestId = UUID.randomUUID().toString(),
                upiId = upiId.trim()
            )
        )
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

}
