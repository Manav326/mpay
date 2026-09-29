package com.recharge.client.features.voice

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MpayFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        VoiceCallPushRegistrar.sync(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        when (message.data["event"]) {
            "CALL_INCOMING" -> {
                val callId = message.data["callId"].orEmpty()
                if (callId.isNotBlank()) {
                    CallNotificationManager.showIncoming(
                        this,
                        callId,
                        message.data["callerName"].orEmpty().ifBlank { "mPay Support" },
                        persistentRinging = message.priority == RemoteMessage.PRIORITY_HIGH
                    )
                }
            }
            "CALL_ENDED" -> {
                val callId = message.data["callId"].orEmpty()
                if (callId.isNotBlank()) {
                    CallNotificationManager.cancelIncoming(this, callId)
                    CallNotificationManager.cancelActive(this, callId)
                    // The backend has already made the terminal state authoritative.
                    // Tear down any local WebRTC service and visible call screen immediately.
                    stopService(
                        Intent(this, VoiceCallService::class.java)
                            .putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId)
                    )
                    IncomingCallActivity.finishRemoteCall(callId)
                }
            }
        }
    }
}
