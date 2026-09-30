package com.recharge.client.features.voice

import android.content.Context
import android.util.Log
import com.recharge.client.MpayFirebase
import com.recharge.client.core.model.CallPushTokenRequest
import com.recharge.client.core.network.NetworkModule
import com.recharge.client.core.security.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object VoiceCallPushRegistrar {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    suspend fun revoke(context: Context) {
        val appContext = context.applicationContext
        if (!MpayFirebase.isConfigured()) return
        if (TokenStore(appContext).accessToken().isNullOrBlank()) return

        val token = suspendCancellableCoroutine<String?> { continuation ->
            MpayFirebase.fetchToken(appContext) { value ->
                if (continuation.isActive) {
                    continuation.resume(value)
                }
            }
        }

        if (token.isNullOrBlank()) {
            Log.w("VoiceCallPushRegistrar", "Firebase token unavailable; voice-call push token revoke skipped.")
            return
        }

        runCatching {
            val response = NetworkModule.clientApi(appContext).revokeCallPushToken(
                CallPushTokenRequest(token = token, platform = "ANDROID")
            )
            Log.i(
                "VoiceCallPushRegistrar",
                "Voice-call push token revoke response. http=" + response.code() +
                    " success=" + response.isSuccessful
            )
        }.onFailure {
            Log.e("VoiceCallPushRegistrar", "Voice-call push token revoke failed.", it)
        }
    }

    fun sync(context: Context) {
        val appContext = context.applicationContext
        if (!MpayFirebase.isConfigured()) return
        if (TokenStore(appContext).accessToken().isNullOrBlank()) return

        MpayFirebase.fetchToken(appContext) { token ->
            if (token.isNullOrBlank()) {
                Log.w("VoiceCallPushRegistrar", "Firebase token unavailable; voice-call push registration skipped.")
                return@fetchToken
            }
            scope.launch {
                runCatching {
                    val response = NetworkModule.clientApi(appContext).registerCallPushToken(
                        CallPushTokenRequest(token = token, platform = "ANDROID")
                    )
                    Log.i(
                        "VoiceCallPushRegistrar",
                        "Voice-call push token registration response. http=" + response.code() +
                            " success=" + response.isSuccessful
                    )
                }.onFailure {
                    Log.e("VoiceCallPushRegistrar", "Voice-call push token registration failed.", it)
                }
            }
        }
    }
}
