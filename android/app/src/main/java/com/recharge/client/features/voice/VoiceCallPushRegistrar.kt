package com.recharge.client.features.voice

import android.content.Context
import com.recharge.client.MpayFirebase
import com.recharge.client.core.model.CallPushTokenRequest
import com.recharge.client.core.network.NetworkModule
import com.recharge.client.core.security.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object VoiceCallPushRegistrar {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sync(context: Context) {
        val appContext = context.applicationContext
        if (!MpayFirebase.isConfigured()) return
        if (TokenStore(appContext).accessToken().isNullOrBlank()) return

        MpayFirebase.fetchToken(appContext) { token ->
            if (token.isNullOrBlank()) return@fetchToken
            scope.launch {
                runCatching {
                    NetworkModule.clientApi(appContext).registerCallPushToken(
                        CallPushTokenRequest(token = token, platform = "ANDROID")
                    )
                }
            }
        }
    }
}
