package com.recharge.client.features.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        val callId = intent.getStringExtra(IncomingCallActivity.EXTRA_CALL_ID).orEmpty()
        if (callId.isBlank()) {
            pending.finish()
            return
        }

        if (intent.action == CallNotificationManager.ACTION_HANGUP) {
            appContext.startService(
                Intent(appContext, VoiceCallService::class.java)
                    .setAction(VoiceCallService.ACTION_HANGUP)
                    .putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId)
            )
            pending.finish()
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { VoiceCallRepository(appContext).decline(callId) }
            CallNotificationManager.cancelIncoming(appContext, callId)
            pending.finish()
        }
    }
}
