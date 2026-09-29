package com.recharge.client.features.voice

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
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
        private const val TAG = "VoiceCallService"
        const val ACTION_START = "com.recharge.client.voice.START"
        const val ACTION_HANGUP = "com.recharge.client.voice.HANGUP"
        const val ACTION_REMOTE_END = "com.recharge.client.voice.REMOTE_END"
        const val EXTRA_OTHER_NAME = "extra_other_name"
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
    private var callMonitorJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        CallNotificationManager.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val incomingCallId = intent?.getStringExtra(IncomingCallActivity.EXTRA_CALL_ID).orEmpty()
        otherName = intent?.getStringExtra(EXTRA_OTHER_NAME).orEmpty().ifBlank { "mPay customer" }

        if (intent?.action == ACTION_REMOTE_END) {
            if (incomingCallId.isNotBlank() && callId != null && callId != incomingCallId) {
                Log.w(TAG, "Ignoring remote end for different call. active=$callId remote=$incomingCallId")
                return START_STICKY
            }
            Log.i(TAG, "Remote call end received. callId=" + (incomingCallId.ifBlank { callId ?: "" }))
            stateFlow.value = VoiceCallEngineState(
                phase = VoiceCallPhase.ENDED,
                muted = stateFlow.value.muted,
                speaker = stateFlow.value.speaker,
                message = "Call ended"
            )
            stopCall()
            return START_NOT_STICKY
        }

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
            if (!startAsForeground(connected = false)) {
                scope.launch {
                    runCatching { VoiceCallRepository(applicationContext).end(incomingCallId) }
                    stopCall()
                }
                return START_NOT_STICKY
            }
            startVoiceCall(incomingCallId)
        }

        return START_STICKY
    }

    private suspend fun monitorCallState(incomingCallId: String) {
        val repository = VoiceCallRepository(applicationContext)
        while (kotlinx.coroutines.currentCoroutineContext().isActive && callId == incomingCallId) {
            kotlinx.coroutines.delay(2000)
            val result = repository.getCall(incomingCallId)
            result.onSuccess { current ->
                if (current.status in setOf("DECLINED", "MISSED", "CANCELLED", "ENDED")) {
                    Log.i(TAG, "Authoritative call state became terminal: " + current.status + " callId=" + incomingCallId)
                    stateFlow.value = stateFlow.value.copy(
                        phase = VoiceCallPhase.ENDED,
                        message = "Call ended"
                    )
                    stopCall()
                }
            }.onFailure { error ->
                // A transient network failure must not end a live call. WebSocket/FCM
                // normally delivers the remote end immediately; polling is the safety net.
                Log.d(TAG, "Call-state fallback check failed for callId=" + incomingCallId + ": " + error.message)
            }
        }
    }

    private fun startVoiceCall(incomingCallId: String) {
        startupJob?.cancel()
        startupJob = scope.launch {
            val repository = VoiceCallRepository(applicationContext)
            Log.i(TAG, "Starting WebRTC call engine. callId=$incomingCallId")
            val call = repository.getCall(incomingCallId).getOrElse { error ->
                Log.e(TAG, "Unable to load accepted call. callId=$incomingCallId", error)
                stateFlow.value = VoiceCallEngineState(VoiceCallPhase.ERROR, message = "Call is no longer available")
                stopCall()
                return@launch
            }
            val token = repository.signalingToken(incomingCallId).getOrElse { error ->
                Log.e(TAG, "Unable to obtain call signaling token. callId=$incomingCallId", error)
                stateFlow.value = VoiceCallEngineState(VoiceCallPhase.ERROR, message = "Unable to secure call signaling")
                stopCall()
                return@launch
            }

            val newEngine = VoiceCallEngine(applicationContext)
            engine = newEngine
            observerJob?.cancel()
            callMonitorJob?.cancel()
            callMonitorJob = scope.launch {
                monitorCallState(incomingCallId)
            }
            observerJob = scope.launch {
                newEngine.state.collect { state ->
                    stateFlow.value = state
                    updateForegroundNotification(call, state)
                    if (state.phase == VoiceCallPhase.ENDED || state.phase == VoiceCallPhase.ERROR) {
                        if (state.phase == VoiceCallPhase.ERROR) {
                            VoiceCallRepository(applicationContext).end(incomingCallId)
                                .onFailure { error ->
                                    Log.e(TAG, "Failed to end call after client error. callId=$incomingCallId", error)
                                }
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
                .onFailure { error -> Log.e(TAG, "Failed to end call. callId=$id", error) }
            stopCall()
        }
    }

    private fun startAsForeground(connected: Boolean): Boolean {
        val id = callId ?: return false
        return runCatching {
            val notification = CallNotificationManager.buildActiveNotification(this, id, otherName, connected)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    CallNotificationManager.ACTIVE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        }.getOrElse { error ->
            Log.e(TAG, "Unable to promote voice call service to foreground. callId=$id", error)
            stateFlow.value = VoiceCallEngineState(
                phase = VoiceCallPhase.ERROR,
                message = "Unable to start the call microphone service"
            )
            false
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
        runCatching { manager.notify(CallNotificationManager.ACTIVE_NOTIFICATION_ID, notification) }
    }

    private fun stopCall() {
        startupJob?.cancel()
        startupJob = null
        observerJob?.cancel()
        observerJob = null
        callMonitorJob?.cancel()
        callMonitorJob = null
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
