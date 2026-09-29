package com.recharge.client.features.voice

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.os.Looper

class IncomingCallRingtoneService : Service() {
    companion object {
        private const val TAG = "IncomingCallRingtoneService"
        const val ACTION_START = "com.recharge.client.voice.RING_START"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_CALLER_NAME = "caller_name"
        private const val STOP_AFTER_MS = 40_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var callId: String = ""
    private var callerName: String = "mPay Support"

    private val timeout = Runnable { stopRinging() }

    override fun onCreate() {
        super.onCreate()
        CallNotificationManager.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY

        val incomingId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        if (incomingId.isBlank()) return START_NOT_STICKY

        callId = incomingId
        callerName = intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty().ifBlank { "mPay Support" }

        return runCatching {
            val notification = CallNotificationManager.buildIncomingNotification(this, callId, callerName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    CallNotificationManager.incomingNotificationId(callId),
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(CallNotificationManager.incomingNotificationId(callId), notification)
            }

            startRinging()
            handler.removeCallbacks(timeout)
            handler.postDelayed(timeout, STOP_AFTER_MS)
            START_NOT_STICKY
        }.getOrElse { error ->
            Log.e(TAG, "Unable to start incoming-call ringtone service. callId=$callId", error)
            ringtone?.stop()
            ringtone = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            START_NOT_STICKY
        }
    }

    private fun startRinging() {
        ringtone?.stop()
        val sound = RingtoneManager.getRingtone(
            this,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ) ?: return

        sound.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            sound.isLooping = true
        }
        ringtone = sound
        sound.play()
    }

    private fun stopRinging() {
        handler.removeCallbacks(timeout)
        ringtone?.stop()
        ringtone = null
        if (callId.isNotBlank()) {
            CallNotificationManager.cancelIncoming(this, callId)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        ringtone?.stop()
        ringtone = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
