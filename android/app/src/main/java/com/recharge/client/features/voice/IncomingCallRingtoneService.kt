package com.recharge.client.features.voice

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Handler
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.os.Looper
import java.time.Instant

class IncomingCallRingtoneService : Service() {
    companion object {
        private const val TAG = "IncomingCallRingtoneService"
        const val ACTION_START = "com.recharge.client.voice.RING_START"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_EXPIRES_AT = "expires_at"
        private const val DEFAULT_RINGING_MS = 30_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var callId: String = ""
    private var callerName: String = "mPay Support"
    private var vibrator: Vibrator? = null
    private var ringSessionActive = false
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: kotlinx.coroutines.Job? = null

    private val timeout = Runnable { stopRinging() }

    override fun onCreate() {
        super.onCreate()
        CallNotificationManager.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY

        val incomingId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        if (incomingId.isBlank()) return START_NOT_STICKY

        val expiryText = intent.getStringExtra(EXTRA_EXPIRES_AT).orEmpty()
        val remainingMs = runCatching {
            if (expiryText.isBlank()) DEFAULT_RINGING_MS
            else Instant.parse(expiryText).toEpochMilli() - System.currentTimeMillis()
        }.getOrDefault(DEFAULT_RINGING_MS)

        if (remainingMs <= 0L) {
            stopRinging()
            return START_NOT_STICKY
        }

        val sameCallAlreadyRinging = callId == incomingId && ringSessionActive

        callId = incomingId
        callerName = intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty().ifBlank { "mPay Support" }

        return runCatching {
            val notification = CallNotificationManager.buildIncomingNotification(
                this,
                callId,
                callerName,
                timeoutMillis = remainingMs
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    CallNotificationManager.incomingNotificationId(callId),
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(CallNotificationManager.incomingNotificationId(callId), notification)
            }

            if (!sameCallAlreadyRinging) startRinging()
            startCallStateMonitor()
            handler.removeCallbacks(timeout)
            handler.postDelayed(timeout, remainingMs)
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

    private fun startCallStateMonitor() {
        monitorJob?.cancel()
        val monitoredCallId = callId
        monitorJob = monitorScope.launch {
            val repository = VoiceCallRepository(applicationContext)
            while (isActive && ringSessionActive && callId == monitoredCallId) {
                repository.getCall(monitoredCallId).onSuccess { current ->
                    when (current.status) {
                        "RINGING" -> Unit
                        "ACCEPTED", "CONNECTED", "DECLINED", "MISSED", "CANCELLED", "ENDED" -> {
                            handler.post { stopRinging() }
                        }
                    }
                }
                delay(1500L)
            }
        }
    }

    private fun startRinging() {
        ringSessionActive = true
        ringtone?.stop()
        vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator

        val sound = RingtoneManager.getRingtone(
            this,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        )
        if (sound != null) {
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
            runCatching {
                sound.play()
                Log.i(TAG, "Ringtone playback started. callId=" + callId + " playing=" + sound.isPlaying)
            }.onFailure { error ->
                Log.e(TAG, "Ringtone playback failed. callId=" + callId, error)
            }
        } else {
            Log.w(TAG, "No default ringtone available. callId=" + callId)
        }

        startVibrating()
    }

    private fun startVibrating() {
        val pattern = longArrayOf(0L, 500L, 250L, 500L)
        val deviceVibrator = vibrator
        if (deviceVibrator?.hasVibrator() != true) {
            Log.w(TAG, "Device has no vibrator. callId=" + callId)
            return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                deviceVibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                deviceVibrator.vibrate(pattern, 0)
            }
            Log.i(TAG, "Vibration started. callId=" + callId)
        }.onFailure { error ->
            Log.e(TAG, "Vibration failed. callId=" + callId, error)
        }
    }

    private fun stopRinging() {
        handler.removeCallbacks(timeout)
        ringSessionActive = false
        monitorJob?.cancel()
        monitorJob = null
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        if (callId.isNotBlank()) {
            CallNotificationManager.cancelIncoming(this, callId)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        ringSessionActive = false
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        monitorJob?.cancel()
        monitorJob = null
        monitorScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
