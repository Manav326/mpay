package com.recharge.client.features.voice

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.recharge.client.core.model.VoiceCallResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VoiceCallService : Service() {
    companion object {
        const val ACTION_START = "com.recharge.client.voice.START"
        const val ACTION_HANGUP = "com.recharge.client.voice.HANGUP"
        const val EXTRA_OTHER_NAME = "extra_other_name"
        private const val NOTIFICATION_ID = 59021
    }

    inner class LocalBinder : Binder() {
        fun service(): VoiceCallService = this@VoiceCallService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateFlow = MutableStateFlow(VoiceCallEngineState())
    val state: StateFlow<VoiceCallEngineState> = stateFlow

    private var callId: String? = null
    private var otherName: String = "mPay customer"
    private var engine: VoiceCallEngine? = null
    private var startupJob: kotlinx.coroutines.Job? = null
    private var observerJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        CallNotificationManager.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val incomingCallId = intent?.getStringExtra(IncomingCallActivity.EXTRA_CALL_ID).orEmpty()
        otherName = intent?.getStringExtra(EXTRA_OTHER_NAME).orEmpty().ifBlank { "mPay customer" }

        if (intent?.action == ACTION_HANGUP) {
            if (incomingCallId.isNotBlank()) {
                scope.launch {
                    VoiceCallRepository(applicationContext).end(incomingCallId)
                    stopCall()
                }
            } else {
                stopCall()
            }
            return START_NOT_STICKY
        }

        if (incomingCallId.isNotBlank() && callId != incomingCallId) {
            callId = incomingCallId
            startAsForeground(connected = false)
            startVoiceCall(incomingCallId)
        }

        return START_STICKY
    }

    private fun startVoiceCall(incomingCallId: String) {
        startupJob?.cancel()
        startupJob = scope.launch {
            val repository = VoiceCallRepository(applicationContext)
            val call = repository.getCall(incomingCallId).getOrElse {
                stateFlow.value = VoiceCallEngineState(VoiceCallPhase.ERROR, message = "Call is no longer available")
                stopCall()
                return@launch
            }
            val token = repository.signalingToken(incomingCallId).getOrElse {
                stateFlow.value = VoiceCallEngineState(VoiceCallPhase.ERROR, message = "Unable to secure call signaling")
                stopCall()
                return@launch
            }

            val newEngine = VoiceCallEngine(applicationContext)
            engine = newEngine
            observerJob?.cancel()
            observerJob = scope.launch {
                newEngine.state.collect { state ->
                    stateFlow.value = state
                    updateForegroundNotification(call, state)
                    if (state.phase == VoiceCallPhase.ENDED || state.phase == VoiceCallPhase.ERROR) {
                        if (state.phase == VoiceCallPhase.ERROR) {
                            runCatching { VoiceCallRepository(applicationContext).end(incomingCallId) }
                        }
                        kotlinx.coroutines.delay(700)
                        stopCall()
                    }
                }
            }

            newEngine.start(
                callId = incomingCallId,
                signalingToken = token.token,
                websocketPath = token.websocketPath,
                iceServers = call.iceServers
            )
        }
    }

    fun setMuted(value: Boolean) {
        engine?.setMuted(value)
    }

    fun toggleSpeaker() {
        engine?.toggleSpeaker()
    }

    fun hangUp() {
        val id = callId ?: return
        scope.launch {
            VoiceCallRepository(applicationContext).end(id)
            stopCall()
        }
    }

    private fun startAsForeground(connected: Boolean) {
        val id = callId ?: return
        val notification = CallNotificationManager.buildActiveNotification(this, id, otherName, connected)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundNotification(call: VoiceCallResponse, state: VoiceCallEngineState) {
        val id = callId ?: return
        val notification = CallNotificationManager.buildActiveNotification(
            this,
            id,
            otherName,
            state.phase == VoiceCallPhase.CONNECTED
        )
        val manager = androidx.core.app.NotificationManagerCompat.from(this)
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun stopCall() {
        startupJob?.cancel()
        startupJob = null
        observerJob?.cancel()
        observerJob = null
        engine?.stop()
        engine = null
        callId?.let { CallNotificationManager.cancelActive(this, it) }
        callId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        observerJob?.cancel()
        engine?.stop()
        scope.cancel()
        super.onDestroy()
    }
}
