package com.recharge.client.core.network

import android.content.Context
import com.google.gson.Gson
import com.recharge.client.core.model.LoginResponse
import com.recharge.client.core.model.RefreshTokenRequest
import com.recharge.client.core.security.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Request
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.accessToken()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}

/**
 * Automatically refreshes an expired access token once, then retries the request.
 * A separate non-authenticated Retrofit client is used for the refresh call.
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val authApiProvider: () -> AuthApi
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization").isNullOrBlank()) return null
        if (responseCount(response) >= 2) return null

        synchronized(this) {
            val currentToken = tokenStore.accessToken()
            val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (!currentToken.isNullOrBlank() && currentToken != failedToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = tokenStore.refreshToken() ?: return null
            val refreshed: LoginResponse = try {
                runBlocking {
                    val refreshResponse = authApiProvider().refresh(RefreshTokenRequest(refreshToken))
                    if (!refreshResponse.isSuccessful || refreshResponse.body() == null) return@runBlocking null
                    refreshResponse.body()
                } ?: return null
            } catch (_: Exception) {
                return null
            }

            tokenStore.save(refreshed.accessToken, refreshed.refreshToken)
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${refreshed.accessToken}")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

object NetworkModule {
    fun authApi(context: Context): AuthApi = createAuthApi(context.applicationContext)

    fun clientApi(context: Context): ClientApi {
        val appContext = context.applicationContext
        val tokenStore = TokenStore(appContext)
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(TokenAuthenticator(tokenStore) { createAuthApi(appContext) })
            .build()

        return Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(ClientApi::class.java)
    }

    private fun createAuthApi(context: Context): AuthApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(AuthApi::class.java)
    }
}
