package com.recharge.client.core.repository

import android.content.Context
import com.recharge.client.core.model.*
import com.recharge.client.core.network.ApiError
import com.recharge.client.core.network.AuthApi
import com.recharge.client.core.network.NetworkModule
import com.recharge.client.core.security.TokenStore
import kotlinx.coroutines.CancellationException

class AuthRepository(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    private val appContext = context.applicationContext
    private val api: AuthApi = NetworkModule.authApi(appContext)
    private val tokenStore = TokenStore(appContext)

    fun isLoggedIn(): Boolean = !tokenStore.accessToken().isNullOrBlank()

    suspend fun login(mobile: String, password: String): Result<LoginResponse> = apiCall {
        val response = api.login(LoginRequest(mobile, password))
        if (!response.isSuccessful || response.body() == null) {
            error(ApiError.message(response))
        }
        response.body()!!.also { tokenStore.save(it.accessToken, it.refreshToken) }
    }

    suspend fun register(name: String, email: String, mobile: String, password: String): Result<LoginResponse> = apiCall {
        val response = api.register(RegisterRequest(name.trim().takeIf { it.isNotBlank() }, email.trim().takeIf { it.isNotBlank() }, mobile, password))
        if (!response.isSuccessful || response.body() == null) {
            error(ApiError.message(response))
        }
        response.body()!!.also { tokenStore.save(it.accessToken, it.refreshToken) }
    }

    suspend fun forgotPassword(mobile: String): Result<ForgotPasswordResponse> = apiCall {
        val response = api.forgotPassword(ForgotPasswordRequest(mobile))
        if (!response.isSuccessful || response.body() == null) error(ApiError.message(response))
        response.body()!!
    }

    suspend fun resetPassword(mobile: String, otp: String, newPassword: String): Result<Unit> = apiCall {
        val response = api.resetPassword(ResetPasswordRequest(mobile, otp, newPassword))
        if (!response.isSuccessful) error(ApiError.message(response))
        Unit
    }

    suspend fun refresh(): Boolean {
        val refreshToken = tokenStore.refreshToken() ?: return false
        return try {
            val response = api.refresh(RefreshTokenRequest(refreshToken))
            if (!response.isSuccessful || response.body() == null) {
                false
            } else {
                response.body()!!.also { tokenStore.save(it.accessToken, it.refreshToken) }
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    fun logout() = tokenStore.clear()
}
