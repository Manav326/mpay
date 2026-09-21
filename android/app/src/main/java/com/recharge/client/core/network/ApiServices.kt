package com.recharge.client.core.network

import com.recharge.client.core.model.*
import retrofit2.Response
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<LoginResponse>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequest): Response<LoginResponse>

    @POST("api/v1/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<ForgotPasswordResponse>

    @POST("api/v1/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<ResetPasswordResponse>
}

interface ClientApi {
    @GET("api/v1/me")
    suspend fun me(): Response<CurrentUserResponse>

    @GET("api/v1/wallet")
    suspend fun wallet(): Response<WalletResponse>

    @GET("api/v1/profile")
    suspend fun profile(): Response<CurrentUserResponse>

    @PATCH("api/v1/profile")
    suspend fun updateProfile(@Body request: ProfileUpdateRequest): Response<CurrentUserResponse>

    @Multipart
    @PUT("api/v1/profile/image")
    suspend fun uploadProfileImage(@Part image: MultipartBody.Part): Response<CurrentUserResponse>

    @DELETE("api/v1/profile/image")
    suspend fun deleteProfileImage(): Response<CurrentUserResponse>

    @POST("api/v1/payments/orders")
    suspend fun createPaymentOrder(@Body request: CreatePaymentOrderRequest): Response<PaymentOrderResponse>

    @POST("api/v1/payments/verify")
    suspend fun verifyPayment(@Body request: VerifyPaymentRequest): Response<PaymentVerificationResponse>

    @POST("api/v1/recharge/payment-order")
    suspend fun createRechargePaymentOrder(@Body request: RechargeRequest): Response<PaymentOrderResponse>

    @POST("api/v1/payments/payu/hash")
    suspend fun payuHash(@Body request: PayUHashRequest): Response<PayUHashResponse>

    @POST("api/v1/recharge/operator")
    suspend fun operator(@Body request: OperatorCheckRequest): Response<OperatorCheckResponse>

    @GET("api/v1/recharge/plans")
    suspend fun plans(
        @Query("mobile") mobile: String,
        @Query("operator") operator: String,
        @Query("circle") circle: String,
        @Query("providerOperator") providerOperator: String? = null,
        @Query("providerCircle") providerCircle: String? = null
    ): Response<List<RechargePlan>>

    @POST("api/v1/recharge")
    suspend fun recharge(@Body request: RechargeRequest): Response<RechargeResponse>

    @GET("api/v1/recharge/{transactionId}")
    suspend fun rechargeStatus(@retrofit2.http.Path("transactionId") transactionId: String): Response<RechargeTransactionStatusResponse>

    @GET("api/v1/recharge/history")
    suspend fun rechargeHistory(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<RechargeHistoryResponse>

    @GET("api/v1/recharge/commission-summary")
    suspend fun rechargeCommissionSummary(): Response<RechargeCommissionSummaryResponse>

    @GET("api/v1/wallet/history")
    suspend fun walletHistory(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("kind") kind: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<WalletHistoryResponse>

    @POST("api/v1/wallet/withdraw")
    suspend fun withdraw(@Body request: WithdrawMoneyRequest): Response<WithdrawMoneyResponse>
}
