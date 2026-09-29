package com.recharge.client.features.voice

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import kotlinx.coroutines.launch

class IncomingCallActivity : ComponentActivity() {
    companion object {
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
            lifecycleScope.launch {
                repository.decline(callId)
            }
            finish()
        }
    }

    private var callId: String = ""
    private var callerName: String = "mPay Support"
    private var accepted = false
    private var service: VoiceCallService? = null
    private var bound = false
    private var engineState by mutableStateOf(VoiceCallEngineState())
    private val repository by lazy { VoiceCallRepository(applicationContext) }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? VoiceCallService.LocalBinder)?.service()
            bound = service != null
            service?.let { svc ->
                lifecycleScope.launch {
                    svc.state.collect {
                        engineState = it
                        if (it.phase == VoiceCallPhase.ENDED || it.phase == VoiceCallPhase.ERROR) {
                            kotlinx.coroutines.delay(500)
                            if (!isFinishing) finish()
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
                    engineState = engineState,
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
        if (accepted) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        answerCall()
    }

    private fun answerCall() {
        if (accepted) return
        accepted = true
        lifecycleScope.launch {
            repository.accept(callId).onSuccess {
                CallNotificationManager.cancelIncoming(this@IncomingCallActivity, callId)
                val intent = Intent(this@IncomingCallActivity, VoiceCallService::class.java)
                    .putExtra(EXTRA_CALL_ID, callId)
                    .putExtra(VoiceCallService.EXTRA_OTHER_NAME, callerName)
                androidx.core.content.ContextCompat.startForegroundService(this@IncomingCallActivity, intent)
                bindService(intent, serviceConnection, BIND_AUTO_CREATE)
            }.onFailure {
                accepted = false
                finish()
            }
        }
    }

    private fun declineCall() {
        lifecycleScope.launch {
            repository.decline(callId)
            CallNotificationManager.cancelIncoming(this@IncomingCallActivity, callId)
            finish()
        }
    }

    private fun hangUp() {
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
    engineState: VoiceCallEngineState,
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
                    text = when {
                        !accepted -> "You choose whether to answer this call."
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onDecline,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE5484D))
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "Decline call", tint = Color.White)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Decline",
                                style = MaterialTheme.typography.labelLarge,
                                color = AppColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onAccept,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF16A34A))
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Answer call", tint = Color.White)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Answer",
                                style = MaterialTheme.typography.labelLarge,
                                color = AppColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
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
