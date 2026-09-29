package com.recharge.client.features.voice

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import java.lang.ref.WeakReference
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.theme.RechargeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.animation.core.spring
import kotlinx.coroutines.launch

class IncomingCallActivity : ComponentActivity() {
    companion object {
        private const val TAG = "IncomingCallActivity"
        private var activeActivity: WeakReference<IncomingCallActivity>? = null

        fun finishRemoteCall(callId: String) {
            activeActivity?.get()?.let { activity ->
                if (!activity.isFinishing && activity.callId == callId) {
                    activity.runOnUiThread { activity.finish() }
                }
            }
        }
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_ACTION = "call_action"
        const val ACTION_ANSWER = "answer"
        const val EXTRA_ACTIVE_CALL = "active_call"
    }

    private var pendingAudioRoute: String? = null

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val route = pendingAudioRoute
        pendingAudioRoute = null
        if (granted && route != null) {
            service?.refreshAudioOutputs()
            service?.setAudioOutput(route)
        }
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) answerCall()
        else {
            answering = false
            screenMessage = "Microphone permission is required to answer this call."
        }
    }

    private var callId: String = ""
    private var callerName: String = "mPay Support"
    private var accepted = false
    private var answering = false
    private var service: VoiceCallService? = null
    private var bound = false
    private var incomingStateJob: kotlinx.coroutines.Job? = null
    private var engineState by mutableStateOf(VoiceCallEngineState())
    private var screenMessage by mutableStateOf<String?>(null)
    private val repository by lazy { VoiceCallRepository(applicationContext) }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? VoiceCallService.LocalBinder)?.service()
            bound = service != null
            service?.let { svc ->
                lifecycleScope.launch {
                    svc.state.collect {
                        engineState = it
                        if (it.phase == VoiceCallPhase.ENDED) {
                            delay(350)
                            if (!isFinishing) finish()
                        } else if (it.phase == VoiceCallPhase.ERROR) {
                            screenMessage = it.message
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        accepted = savedInstanceState?.getBoolean("voice_call_accepted", false) == true ||
            intent.getBooleanExtra(EXTRA_ACTIVE_CALL, false)
        callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        callerName = intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty().ifBlank { "mPay Support" }
        if (callId.isBlank()) {
            finish()
            return
        }

        setContent {
            RechargeTheme {
                IncomingCallScreen(
                    callerName = callerName,
                    accepted = accepted,
                    answering = answering,
                    engineState = engineState,
                    screenMessage = screenMessage,
                    onAccept = ::requestToAnswer,
                    onDecline = ::declineCall,
                    onHangUp = ::hangUp,
                    onMute = { service?.setMuted(!engineState.muted) },
                    onAudioOutput = { routeId ->
                        if (routeId == "BLUETOOTH" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                        ) {
                            pendingAudioRoute = routeId
                            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        } else {
                            service?.setAudioOutput(routeId)
                        }
                    },
                    onOpenAudioOutput = { service?.refreshAudioOutputs() }
                )
            }
        }

        if (intent.getStringExtra(EXTRA_ACTION) == ACTION_ANSWER) {
            requestToAnswer()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val incomingId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        if (incomingId.isBlank() || incomingId != callId) return
        callerName = intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty().ifBlank { callerName }
        if (intent.getBooleanExtra(EXTRA_ACTIVE_CALL, false)) {
            accepted = true
            answering = false
            if (!bound) {
                bindService(
                    Intent(this, VoiceCallService::class.java),
                    serviceConnection,
                    BIND_AUTO_CREATE
                )
            }
        }
        if (intent.getStringExtra(EXTRA_ACTION) == ACTION_ANSWER) {
            requestToAnswer()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("voice_call_accepted", accepted)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        activeActivity = WeakReference(this)
        if (!accepted) {
            startIncomingStateMonitor()
        }
        if (accepted) {
            bindService(
                Intent(this, VoiceCallService::class.java),
                serviceConnection,
                BIND_AUTO_CREATE
            )
        }
    }

    override fun onStop() {
        incomingStateJob?.cancel()
        incomingStateJob = null
        if (activeActivity?.get() === this) {
            activeActivity = null
        }
        if (bound) {
            runCatching { unbindService(serviceConnection) }
            bound = false
            service = null
        }
        super.onStop()
    }

    private fun startIncomingStateMonitor() {
        incomingStateJob?.cancel()
        incomingStateJob = lifecycleScope.launch {
            while (isActive && !accepted && callId.isNotBlank()) {
                repository.getCall(callId).onSuccess { current ->
                    when {
                        current.status in setOf("DECLINED", "MISSED", "CANCELLED", "ENDED") -> {
                            CallNotificationManager.cancelIncoming(this@IncomingCallActivity, callId)
                            finish()
                        }
                        !answering && current.status in setOf("ACCEPTED", "CONNECTED") -> {
                            // Another signed-in device for the same customer may have answered.
                            // Never leave a stale incoming screen/ringtone running locally.
                            CallNotificationManager.cancelIncoming(this@IncomingCallActivity, callId)
                            finish()
                        }
                    }
                }
                delay(1500L)
            }
        }
    }

    private fun requestToAnswer() {
        if (accepted || answering) return
        answering = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        answerCall()
    }

    private fun answerCall() {
        if (accepted || !answering) return
        lifecycleScope.launch {
            repository.accept(callId).onSuccess {
                accepted = true
                answering = false
                screenMessage = null
                try {
                    CallNotificationManager.cancelIncoming(this@IncomingCallActivity, callId)
                    val intent = Intent(this@IncomingCallActivity, VoiceCallService::class.java)
                        .putExtra(EXTRA_CALL_ID, callId)
                        .putExtra(VoiceCallService.EXTRA_OTHER_NAME, callerName)
                    androidx.core.content.ContextCompat.startForegroundService(this@IncomingCallActivity, intent)
                    bindService(intent, serviceConnection, BIND_AUTO_CREATE)
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to start voice call service after accepting call. callId=$callId", error)
                    runCatching { repository.end(callId) }
                    accepted = false
                    answering = false
                    screenMessage = "The call could not be started on this device."
                }
            }.onFailure { error ->
                accepted = false
                answering = false
                screenMessage = error.message?.takeIf { it.isNotBlank() }
                    ?: "The call could not be answered. Please try again."
            }
        }
    }

    private fun declineCall() {
        if (accepted || answering) return
        answering = true
        screenMessage = null
        lifecycleScope.launch {
            repository.decline(callId)
                .onSuccess {
                    CallNotificationManager.cancelIncoming(this@IncomingCallActivity, callId)
                    finish()
                }
                .onFailure { error ->
                    // A concurrent admin hang-up or timeout can race the decline request.
                    // Reconcile the authoritative state before leaving a stale screen visible.
                    val current = repository.getCall(callId).getOrNull()
                    if (current?.status in setOf("DECLINED", "MISSED", "CANCELLED", "ENDED")) {
                        CallNotificationManager.cancelIncoming(this@IncomingCallActivity, callId)
                        finish()
                    } else {
                        answering = false
                        screenMessage = error.message?.takeIf { it.isNotBlank() }
                            ?: "The call could not be declined. Please try again."
                    }
                }
        }
    }

    private fun hangUp() {
        answering = false
        service?.hangUp() ?: lifecycleScope.launch { repository.end(callId) }
        CallNotificationManager.cancelIncoming(this, callId)
        finish()
    }

    override fun onDestroy() {
        if (isFinishing) {
            CallNotificationManager.cancelIncoming(this, callId)
        }
        if (activeActivity?.get() === this) {
            activeActivity = null
        }
        super.onDestroy()
    }
}

@Composable
private fun IncomingCallScreen(
    callerName: String,
    accepted: Boolean,
    answering: Boolean,
    engineState: VoiceCallEngineState,
    screenMessage: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onMute: () -> Unit,
    onAudioOutput: (String) -> Unit,
    onOpenAudioOutput: () -> Unit
) {
    var showAudioPicker by remember { mutableStateOf(false) }
    var callElapsedSeconds by remember(
        engineState.connectedAtEpochMillis,
        engineState.phase,
        engineState.endedAtEpochMillis
    ) { mutableStateOf(0L) }

    LaunchedEffect(
        engineState.connectedAtEpochMillis,
        engineState.phase,
        engineState.endedAtEpochMillis
    ) {
        val startedAt = engineState.connectedAtEpochMillis
        if (startedAt == null || engineState.phase != VoiceCallPhase.CONNECTED) {
            callElapsedSeconds = 0L
            return@LaunchedEffect
        }
        while (isActive) {
            val endAt = engineState.endedAtEpochMillis ?: System.currentTimeMillis()
            callElapsedSeconds = ((endAt - startedAt) / 1000L).coerceAtLeast(0L)
            if (engineState.endedAtEpochMillis != null) break
            delay(1000L)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.Background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(AppColors.Primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = AppColors.PrimaryDark,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    text = if (accepted) "mPay Support" else "Incoming mPay call",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = callerName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = screenMessage ?: when {
                        !accepted && answering -> "Connecting the secure call…"
                        !accepted -> "mPay Support is calling you"
                        engineState.phase == VoiceCallPhase.CONNECTED -> "Connected securely"
                        engineState.phase == VoiceCallPhase.ERROR -> engineState.message
                        else -> engineState.message
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
                if (accepted && engineState.phase == VoiceCallPhase.CONNECTED) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "Call time  " + formatDuration(callElapsedSeconds),
                        style = MaterialTheme.typography.labelLarge,
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(if (accepted) 24.dp else 26.dp))

                if (!accepted) {
                    Text(
                        text = if (answering) "Connecting…" else "Swipe up to choose",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top
                    ) {
                        VerticalSwipeCallAction(
                            label = "Decline",
                            hint = "Swipe up to decline",
                            icon = Icons.Default.CallEnd,
                            tint = Color(0xFFDC2626),
                            trackTint = Color(0xFFFFF1F2),
                            enabled = !answering,
                            onTriggered = onDecline,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.size(16.dp))
                        VerticalSwipeCallAction(
                            label = "Answer",
                            hint = "Swipe up to answer",
                            icon = Icons.Default.Call,
                            tint = Color(0xFF15803D),
                            trackTint = Color(0xFFECFDF3),
                            enabled = !answering,
                            onTriggered = onAccept,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Top
                    ) {
                        CallControlButton(
                            label = if (engineState.muted) "Unmute" else "Mute",
                            selected = engineState.muted,
                            icon = if (engineState.muted) Icons.Default.MicOff else Icons.Default.Mic,
                            onClick = onMute
                        )
                        Spacer(Modifier.size(24.dp))
                        CallControlButton(
                            label = "Audio output",
                            selected = engineState.audioOutputId == "SPEAKER",
                            icon = when {
                                engineState.audioOutput.startsWith("Bluetooth", ignoreCase = true) -> Icons.Default.Bluetooth
                                engineState.audioOutput.startsWith("Wired", ignoreCase = true) -> Icons.Default.Headset
                                engineState.audioOutputId == "SPEAKER" -> Icons.Default.SpeakerPhone
                                else -> Icons.Default.PhoneInTalk
                            },
                            onClick = {
                                onOpenAudioOutput()
                                showAudioPicker = true
                            }
                        )
                    }

                    Spacer(Modifier.height(30.dp))
                    Button(
                        onClick = onHangUp,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5484D))
                    ) {
                        Icon(Icons.Default.CallEnd, null)
                        Spacer(Modifier.size(8.dp))
                        Text("End call", fontWeight = FontWeight.Bold)
                    }
                }

                if (!accepted) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "mPay never records this call.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary
                    )
                }
            }
        }
    }

    if (showAudioPicker && accepted) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAudioPicker = false },
            title = { Text("Audio output", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    engineState.audioOutputs.forEach { output ->
                        val selected = output.id == engineState.audioOutputId
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                onAudioOutput(output.id)
                                showAudioPicker = false
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) AppColors.PrimaryDark else AppColors.NeutralTint
                            )
                        ) {
                            Icon(
                                imageVector = when {
                                    output.id == "BLUETOOTH" -> Icons.Default.Bluetooth
                                    output.id == "SPEAKER" -> Icons.Default.SpeakerPhone
                                    output.id == "EARPIECE" -> Icons.Default.PhoneInTalk
                                    else -> Icons.Default.Headset
                                },
                                contentDescription = null
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                text = output.label,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                            )
                            if (selected) Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                    if (engineState.audioOutputs.isEmpty()) {
                        Text(
                            "Speaker and phone audio are available on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showAudioPicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val minutes = (totalSeconds / 60L).toString().padStart(2, '0')
    val seconds = (totalSeconds % 60L).toString().padStart(2, '0')
    return minutes + ":" + seconds
}

@Composable
private fun VerticalSwipeCallAction(
    label: String,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    trackTint: Color,
    enabled: Boolean,
    onTriggered: () -> Unit,
    modifier: Modifier = Modifier
) {
    val offset = remember { Animatable(0f) }
    val gestureScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val trackHeight = 220.dp
    val thumbSize = 62.dp
    val maxTravel = with(density) { (trackHeight - thumbSize - 24.dp).toPx() }
    val triggerTravel = maxTravel * 0.66f

    androidx.compose.material3.Surface(
        modifier = modifier.height(trackHeight),
        shape = RoundedCornerShape(28.dp),
        color = trackTint,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    var hapticSent = false
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val next = (offset.value - dragAmount).coerceIn(0f, maxTravel)
                            if (next >= triggerTravel && !hapticSent) {
                                hapticSent = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (next < triggerTravel) hapticSent = false
                            gestureScope.launch { offset.snapTo(next) }
                        },
                        onDragCancel = {
                            gestureScope.launch {
                                offset.animateTo(
                                    0f,
                                    spring(stiffness = 650f, dampingRatio = 0.78f)
                                )
                            }
                        },
                        onDragEnd = {
                            gestureScope.launch {
                                if (offset.value >= triggerTravel) {
                                    offset.animateTo(
                                        maxTravel,
                                        spring(stiffness = 900f, dampingRatio = 0.82f)
                                    )
                                    onTriggered()
                                } else {
                                    offset.animateTo(
                                        0f,
                                        spring(stiffness = 650f, dampingRatio = 0.78f)
                                    )
                                }
                            }
                        }
                    )
                }
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = tint,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (offset.value >= triggerTravel) "Release" else "Swipe up",
                    style = MaterialTheme.typography.labelSmall,
                    color = tint.copy(alpha = 0.78f)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, -offset.value.toInt()) }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(tint),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = hint, tint = Color.White, modifier = Modifier.size(25.dp))
            }
        }
    }
}

@Composable
private fun CallControlButton(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (selected) AppColors.Primary.copy(alpha = 0.16f) else AppColors.NeutralTint)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) AppColors.PrimaryDark else AppColors.TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
