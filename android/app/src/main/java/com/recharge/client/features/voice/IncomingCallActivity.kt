package com.recharge.client.features.voice

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.theme.RechargeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IncomingCallActivity : ComponentActivity() {
    companion object {
        private const val TAG = "IncomingCallActivity"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_ACTION = "call_action"
        const val ACTION_ANSWER = "answer"
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

        accepted = savedInstanceState?.getBoolean("voice_call_accepted", false) == true
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
                    onSpeaker = { service?.toggleSpeaker() }
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
        if (accepted) {
            bindService(
                Intent(this, VoiceCallService::class.java),
                serviceConnection,
                BIND_AUTO_CREATE
            )
        }
    }

    override fun onStop() {
        if (bound) {
            runCatching { unbindService(serviceConnection) }
            bound = false
            service = null
        }
        super.onStop()
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
        lifecycleScope.launch {
            repository.decline(callId)
            CallNotificationManager.cancelIncoming(this@IncomingCallActivity, callId)
            finish()
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
    onSpeaker: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.Background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
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
                Spacer(Modifier.height(24.dp))
                Text(
                    text = if (accepted) "mPay Support" else "Incoming mPay call",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(28.dp))

                if (!accepted) {
                    Text(
                        text = if (answering) "Connecting…" else "Swipe to choose",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    SwipeCallAction(
                        label = "Slide to answer",
                        hint = "Answer call",
                        icon = Icons.Default.Call,
                        tint = Color(0xFF15803D),
                        trackTint = Color(0xFFECFDF3),
                        direction = SwipeActionDirection.RIGHT,
                        enabled = !answering,
                        onTriggered = onAccept
                    )
                    Spacer(Modifier.height(12.dp))
                    SwipeCallAction(
                        label = "Slide to decline",
                        hint = "Decline call",
                        icon = Icons.Default.CallEnd,
                        tint = Color(0xFFDC2626),
                        trackTint = Color(0xFFFFF1F2),
                        direction = SwipeActionDirection.LEFT,
                        enabled = !answering,
                        onTriggered = onDecline
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CallControlButton(
                            label = if (engineState.muted) "Unmute" else "Mute",
                            selected = engineState.muted,
                            icon = if (engineState.muted) Icons.Default.MicOff else Icons.Default.Mic,
                            onClick = onMute
                        )
                        Spacer(Modifier.size(18.dp))
                        CallControlButton(
                            label = if (engineState.speaker) "Speaker" else "Earpiece",
                            selected = engineState.speaker,
                            icon = if (engineState.speaker) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            onClick = onSpeaker
                        )
                    }
                    Spacer(Modifier.height(30.dp))
                    Button(
                        onClick = onHangUp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5484D))
                    ) {
                        Icon(Icons.Default.CallEnd, null)
                        Spacer(Modifier.size(8.dp))
                        Text("End call", fontWeight = FontWeight.Bold)
                    }
                }

                if (!accepted) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "mPay never records this call.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary
                    )
                }
            }
        }
    }
}

private enum class SwipeActionDirection { LEFT, RIGHT }

@Composable
private fun SwipeCallAction(
    label: String,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    trackTint: Color,
    direction: SwipeActionDirection,
    enabled: Boolean,
    onTriggered: () -> Unit
) {
    val directionSign = if (direction == SwipeActionDirection.RIGHT) 1 else -1
    val offset = remember(direction) { Animatable(0f) }
    val gestureScope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val maxTravel = with(density) { 206.dp.toPx() }
    val triggerTravel = maxTravel * 0.70f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(trackTint)
            .pointerInput(direction, enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val intended = dragAmount * directionSign
                        offset.snapTo((offset.value + intended).coerceIn(0f, maxTravel))
                    },
                    onDragCancel = {
                        gestureScope.launch {
                            offset.animateTo(0f, androidx.compose.animation.core.tween(220))
                        }
                    },
                    onDragEnd = {
                        gestureScope.launch {
                            if (offset.value >= triggerTravel) {
                                offset.animateTo(maxTravel, androidx.compose.animation.core.tween(120))
                                onTriggered()
                            } else {
                                offset.animateTo(0f, androidx.compose.animation.core.tween(220))
                            }
                        }
                    }
                )
            }
            .padding(horizontal = 7.dp, vertical = 7.dp)
    ) {
        Text(
            text = if (offset.value >= triggerTravel) "Release to continue" else label,
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = if (direction == SwipeActionDirection.RIGHT) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .offset { IntOffset((directionSign * offset.value).toInt(), 0) }
                    .clip(RoundedCornerShape(17.dp))
                    .background(tint),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = hint, tint = Color.White, modifier = Modifier.size(23.dp))
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
                .size(52.dp)
                .clip(CircleShape)
                .background(if (selected) AppColors.Primary.copy(alpha = 0.16f) else AppColors.NeutralTint)
        ) {
            Icon(icon, contentDescription = label, tint = if (selected) AppColors.PrimaryDark else AppColors.TextPrimary)
        }
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
    }
}
