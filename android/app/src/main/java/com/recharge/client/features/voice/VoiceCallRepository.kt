package com.recharge.client.features.voice

import android.content.Context
import com.recharge.client.core.model.VoiceCallResponse
import com.recharge.client.core.model.VoiceCallSignalingTokenRequest
import com.recharge.client.core.model.VoiceCallSignalingTokenResponse
import com.recharge.client.core.network.NetworkModule
import retrofit2.Response

class VoiceCallRepository(context: Context) {
    private val api = NetworkModule.clientApi(context.applicationContext)

    suspend fun getCall(callId: String): Result<VoiceCallResponse> = result(api.voiceCall(callId))
    suspend fun accept(callId: String): Result<VoiceCallResponse> = result(api.acceptVoiceCall(callId))
    suspend fun decline(callId: String): Result<VoiceCallResponse> = result(api.declineVoiceCall(callId))
    suspend fun end(callId: String): Result<VoiceCallResponse> = result(api.endVoiceCall(callId))

    suspend fun signalingToken(callId: String): Result<VoiceCallSignalingTokenResponse> =
        result(api.voiceCallSignalingToken(VoiceCallSignalingTokenRequest(callId)))

    private fun <T> result(response: Response<T>): Result<T> {
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) return Result.success(body)
        }
        return Result.failure(
            IllegalStateException(
                response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    ?: ("Voice call request failed (" + response.code() + ")")
            )
        )
    }
}
