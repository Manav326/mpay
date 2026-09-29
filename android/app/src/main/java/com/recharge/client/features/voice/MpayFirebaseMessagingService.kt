package com.recharge.client.features.voice

import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MpayFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i("MpayFirebaseMessaging", "FCM token refreshed; registering with mPay backend.")
        VoiceCallPushRegistrar.sync(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val event = message.data["event"].orEmpty()
        Log.i(
            "MpayFirebaseMessaging",
            "FCM message received. event=" + event +
                " callId=" + message.data["callId"] +
                " priority=" + message.priority +
                " appNotificationsEnabled=" + NotificationManagerCompat.from(this).areNotificationsEnabled()
        )
        when (event) {
            "CALL_INCOMING" -> {
                if (message.priority != RemoteMessage.PRIORITY_HIGH) {
                    Log.w(
                        "MpayFirebaseMessaging",
                        "Incoming call FCM was not delivered at HIGH priority. priority=" +
                            message.priority + " callId=" + message.data["callId"]
                    )
                }
                val callId = message.data["callId"].orEmpty()
                if (callId.isNotBlank()) {
                    Log.i(
                        "MpayFirebaseMessaging",
                        "Dispatching incoming call alert. callId=" + callId + " expiresAt=" + message.data["expiresAt"]
                    )
                    CallNotificationManager.showIncoming(
                        this,
                        callId,
                        message.data["callerName"].orEmpty().ifBlank { "mPay Support" },
                        expiresAt = message.data["expiresAt"],
                        persistentRinging = true
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
                    val remoteEndIntent = Intent(this, VoiceCallService::class.java)
                        .setAction(VoiceCallService.ACTION_REMOTE_END)
                        .putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId)
                    runCatching {
                        startService(remoteEndIntent)
                    }.onFailure {
                        runCatching {
                            ContextCompat.startForegroundService(this, remoteEndIntent)
                        }
                    }
                    IncomingCallActivity.finishRemoteCall(callId)
                }
            }
        }
    }
}
