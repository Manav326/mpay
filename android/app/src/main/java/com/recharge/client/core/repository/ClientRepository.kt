package com.recharge.client.core.repository

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
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
import com.recharge.client.core.model.RentalVendorUpdateRequest
import com.recharge.client.core.model.RechargeHistoryResponse
import com.recharge.client.core.model.RechargeCommissionSummaryResponse
import com.recharge.client.core.model.VerifyPaymentRequest
import com.recharge.client.core.model.WalletResponse
import com.recharge.client.core.model.WalletHistoryResponse
import com.recharge.client.core.model.WithdrawMoneyRequest
import com.recharge.client.core.model.WithdrawMoneyResponse
import com.recharge.client.core.model.WithdrawalHistoryResponse
import com.recharge.client.core.model.RentalVehicleCalendarResponse
import com.recharge.client.core.model.RentalVehicleUnavailabilityResponse
import com.recharge.client.core.model.RentalVehicleUnavailabilityRequest
import com.recharge.client.core.model.RentalBookingQuoteRequest
import com.recharge.client.core.model.RentalBookingQuoteResponse
import com.recharge.client.core.model.ProfileUpdateRequest
import com.recharge.client.core.network.ApiError
import com.recharge.client.core.network.ClientApi
import com.recharge.client.core.network.NetworkModule
import com.recharge.client.core.cache.ProfileCacheStore
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ClientRepository(context: Context) {
    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    companion object {
        @Volatile
        private var INSTANCE: ClientRepository? = null

        fun getInstance(context: Context): ClientRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ClientRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
    private val appContext = context.applicationContext
    private val api: ClientApi = NetworkModule.clientApi(context.applicationContext)
    private val profileCache = ProfileCacheStore(appContext)
    private val gson = Gson()

    suspend fun currentUser(): Result<CurrentUserResponse> {
        val result = apiCall {
            val response = api.me()
            if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
            response.body()!!
        }
        result.onSuccess { profileCache.save(it) }
        return result.recoverCatching { profileCache.get() ?: throw it }
    }

    suspend fun wallet(): Result<WalletResponse> = apiCall {
        val response = api.wallet()
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun profile(): Result<CurrentUserResponse> {
        val result = apiCall {
            val response = api.profile()
            if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
            response.body()!!
        }
        result.onSuccess { profileCache.save(it) }
        return result.recoverCatching { profileCache.get() ?: throw it }
    }

    suspend fun updateProfile(name: String?, email: String?): Result<CurrentUserResponse> = apiCall {
        val response = api.updateProfile(ProfileUpdateRequest(name, email))
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!.also { profileCache.save(it) }
    }

    suspend fun uploadProfileImage(uri: Uri): Result<CurrentUserResponse> = apiCall {
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

    suspend fun deleteProfileImage(): Result<CurrentUserResponse> = apiCall {
        val response = api.deleteProfileImage()
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!.also { profileCache.save(it) }
    }

    suspend fun createPaymentOrder(amount: BigDecimal, clientRequestId: String, provider: String = "razorpay"): Result<PaymentOrderResponse> = apiCall {
        val response = api.createPaymentOrder(CreatePaymentOrderRequest(amount = amount, provider = provider, clientRequestId = clientRequestId))
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun verifyPayment(request: VerifyPaymentRequest): Result<com.recharge.client.core.model.PaymentVerificationResponse> = apiCall {
        val response = api.verifyPayment(request)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun createRechargePaymentOrder(request: RechargeRequest): Result<PaymentOrderResponse> = apiCall {
        val response = api.createRechargePaymentOrder(request)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun generatePayUHash(hashName: String, hashString: String, postSalt: String?, hashType: String?): Result<String> = apiCall {
        val response = api.payuHash(PayUHashRequest(hashName, hashString, postSalt, hashType))
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!.hash
    }

    suspend fun detectOperator(mobile: String): Result<OperatorCheckResponse> = apiCall {
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
    ): Result<List<RechargePlan>> = apiCall {
        val response = api.plans(mobile, operator, circle, providerOperator, providerCircle)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun recharge(
        mobileNumber: String,
        operator: String,
        circle: String,
        planId: String,
        clientRequestId: String,
        recipientName: String? = null
    ): Result<RechargeResponse> = apiCall {
        val response = api.recharge(
            RechargeRequest(
                mobileNumber = mobileNumber,
                operator = operator,
                circle = circle,
                planId = planId,
                clientRequestId = clientRequestId,
                recipientName = recipientName?.trim()?.takeIf { it.isNotBlank() }
            )
        )
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun rechargeStatus(transactionId: String): Result<RechargeTransactionStatusResponse> = apiCall {
        val response = api.rechargeStatus(transactionId)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun rechargeHistory(page: Int = 0, size: Int = 20, from: String? = null, to: String? = null): Result<RechargeHistoryResponse> = apiCall {
        val response = api.rechargeHistory(page, size, from, to)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun rechargeCommissionSummary(): Result<RechargeCommissionSummaryResponse> = apiCall {
        val response = api.rechargeCommissionSummary()
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun walletHistory(page: Int = 0, size: Int = 20, kind: String? = null, from: String? = null, to: String? = null): Result<WalletHistoryResponse> = apiCall {
        val response = api.walletHistory(page, size, kind, from, to)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun withdraw(amount: BigDecimal, upiId: String, provider: String): Result<WithdrawMoneyResponse> = apiCall {
        val request = WithdrawMoneyRequest(
            amount = amount.setScale(2),
            provider = provider,
            clientRequestId = UUID.randomUUID().toString(),
            upiId = upiId.trim()
        )
        val json = gson.toJson(request)
        require(json.isNotBlank() && json != "{}") { "Unable to create withdrawal request body" }
        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val response = api.withdraw(requestBody)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun withdrawalHistory(page: Int = 0, size: Int = 20): Result<WithdrawalHistoryResponse> = apiCall {
        val response = api.withdrawalHistory(page, size)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun withdrawal(withdrawalId: String): Result<WithdrawMoneyResponse> = apiCall {
        val response = api.withdrawal(withdrawalId)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }


    suspend fun rentalVendor(): Result<com.recharge.client.core.model.RentalVendorResponse> = apiCall {
        val response = api.rentalVendor()
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun onboardRentalVendor(request: com.recharge.client.core.model.RentalVendorOnboardingRequest): Result<com.recharge.client.core.model.RentalVendorResponse> = apiCall {
        val response = api.onboardRentalVendor(request)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun updateRentalVendor(request: RentalVendorUpdateRequest): Result<com.recharge.client.core.model.RentalVendorResponse> = apiCall {
        val response = api.updateRentalVendor(request)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun rentalCars(
        startDate: String? = null,
        endDate: String? = null,
        location: String? = null
    ): Result<List<com.recharge.client.core.model.RentalCarResponse>> = apiCall {
        val response = api.rentalCars(startDate, endDate, location)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }


    suspend fun uploadRentalDriverPhoto(
        driverId: String,
        uri: Uri
    ): Result<com.recharge.client.core.model.RentalCarResponse> = apiCall {
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri)?.lowercase() ?: "image/jpeg"
        require(mime in setOf("image/jpeg", "image/png", "image/webp")) {
            "Please select a JPG, PNG or WebP image."
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read selected driver photo")
        require(bytes.size <= 5 * 1024 * 1024) { "Driver photo must be 5 MB or smaller." }
        val body = bytes.toRequestBody(mime.toMediaType())
        val part = MultipartBody.Part.createFormData("photo", "driver-photo", body)
        val response = api.uploadRentalDriverPhoto(driverId, part)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun uploadRentalVehiclePhoto(
        carId: String,
        slot: Int,
        uri: Uri
    ): Result<com.recharge.client.core.model.RentalCarResponse> = apiCall {
        require(slot in 0..3) { "Vehicle photo slot must be between 0 and 3" }
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri)?.lowercase() ?: "image/jpeg"
        require(mime in setOf("image/jpeg", "image/png", "image/webp")) {
            "Please select a JPG, PNG or WebP image."
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read selected vehicle photo")
        require(bytes.size <= 5 * 1024 * 1024) { "Vehicle photo must be 5 MB or smaller." }
        val body = bytes.toRequestBody(mime.toMediaType())
        val part = MultipartBody.Part.createFormData("photo", "vehicle-photo-${slot}", body)
        val response = api.uploadRentalVehiclePhoto(carId, slot, part)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun rentalVendorEarnings(): Result<com.recharge.client.core.model.RentalVendorEarningsResponse> = apiCall {
        val response = api.rentalVendorEarnings()
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun rentalVendorPayouts(): Result<List<com.recharge.client.core.model.RentalVendorPayoutResponse>> = apiCall {
        val response = api.rentalVendorPayouts()
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun rentalVendorVehicles(): Result<List<com.recharge.client.core.model.RentalCarResponse>> = apiCall {
        val response = api.rentalVendorVehicles()
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun onboardRentalVehicle(request: com.recharge.client.core.model.RentalVehicleOnboardingRequest): Result<com.recharge.client.core.model.RentalCarResponse> = apiCall {
        val response = api.onboardRentalVehicle(request)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun resubmitRentalVehicle(carId: String, request: com.recharge.client.core.model.RentalVehicleUpdateRequest): Result<com.recharge.client.core.model.RentalCarResponse> = apiCall {
        val response = api.resubmitRentalVehicle(carId, request)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun takeRentalVehicleOffMarket(
        carId: String,
        request: RentalVehicleUnavailabilityRequest
    ): Result<RentalVehicleUnavailabilityResponse> = apiCall {
        val response = api.takeRentalVehicleOffMarket(carId, request)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun rentalVehicleUnavailability(carId: String): Result<List<RentalVehicleUnavailabilityResponse>> = apiCall {
        val response = api.rentalVehicleUnavailability(carId)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun restoreRentalVehicleToMarket(carId: String, unavailableId: String): Result<Unit> = apiCall {
        val response = api.restoreRentalVehicleToMarket(carId, unavailableId)
        if (!response.isSuccessful) error(ApiError.message(response))
        Unit
    }

    suspend fun rentalVehicleCalendar(carId: String, year: Int, month: Int): Result<RentalVehicleCalendarResponse> = apiCall {
        val response = api.rentalVehicleCalendar(carId, year, month)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun rentalBookingQuote(request: RentalBookingQuoteRequest): Result<RentalBookingQuoteResponse> =
        apiCall {
            val response = api.rentalBookingQuote(request)
            if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
            response.body()!!
        }

    suspend fun createRentalBooking(request: com.recharge.client.core.model.RentalBookingRequest): Result<com.recharge.client.core.model.RentalBookingResponse> = apiCall {
        val response = api.createRentalBooking(request)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun rentalBookings(page: Int = 0, size: Int = 25): Result<com.recharge.client.core.model.RentalBookingPageResponse> = apiCall {
        val response = api.rentalBookings(page, size)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun cancelRentalBooking(bookingId: String): Result<com.recharge.client.core.model.RentalBookingResponse> = apiCall {
        val response = api.cancelRentalBooking(bookingId)
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }
}