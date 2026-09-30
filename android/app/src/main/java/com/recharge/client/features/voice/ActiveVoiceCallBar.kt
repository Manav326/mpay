package com.recharge.client.features.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.recharge.client.core.model.VoiceCallResponse
import com.recharge.client.core.theme.AppColors
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.roundToInt

@Composable
fun ActiveVoiceCallBar(
    call: VoiceCallResponse,
    modifier: Modifier = Modifier,
    onEnded: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val callId = call.callId
    val squareSize = 108.dp
    var dragOffset by remember(callId, density) {
        mutableStateOf(
            Offset(
                x = with(density) { -16.dp.toPx() },
                y = with(density) { 16.dp.toPx() }
            )
        )
    }
    var containerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var boundService by remember(callId) { mutableStateOf<VoiceCallService?>(null) }
    var engineState by remember(callId) { mutableStateOf(VoiceCallEngineState()) }
    var showAudioPicker by remember(callId) { mutableStateOf(false) }
    var pendingAudioRoute by remember(callId) { mutableStateOf<String?>(null) }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val route = pendingAudioRoute
        pendingAudioRoute = null
        val service = boundService
        if (granted && service != null && route != null) {
            service.refreshAudioOutputs()
            service.setAudioOutput(route)
        }
    }

    DisposableEffect(callId) {
        var isBound = false
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                boundService = (binder as? VoiceCallService.LocalBinder)?.service()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
            }
        }

        isBound = runCatching {
            context.bindService(
                Intent(context, VoiceCallService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
        }.getOrDefault(false)

        onDispose {
            if (isBound) {
                runCatching { context.unbindService(connection) }
            }
            boundService = null
        }
    }

    LaunchedEffect(boundService, callId) {
        val service = boundService ?: return@LaunchedEffect
        service.refreshAudioOutputs()
        service.state.collect { current ->
            engineState = current
        }
    }

    var elapsedSeconds by remember(callId, call.connectedAt) { mutableStateOf(0L) }

    LaunchedEffect(callId, call.connectedAt, call.status) {
        val connectedAt = call.connectedAt?.let {
            runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
        }
        if (connectedAt == null || call.status != "CONNECTED") {
            elapsedSeconds = 0L
            return@LaunchedEffect
        }

        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - connectedAt) / 1000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { containerSize = it.size }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                .size(squareSize)
                .background(
                    color = AppColors.Background.copy(alpha = 0.98f),
                    shape = RoundedCornerShape(22.dp)
                )
                .border(
                    width = 1.dp,
                    color = AppColors.Primary.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(7.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(callId, containerSize) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val barPx = with(density) { squareSize.toPx() }
                                    val horizontalInset = with(density) { 8.dp.toPx() }
                                    val minX = (-(containerSize.width - barPx)).coerceAtMost(0f) - horizontalInset
                                    val maxX = -horizontalInset
                                    val maxY = (containerSize.height - barPx).coerceAtLeast(horizontalInset) - horizontalInset
                                    dragOffset = Offset(
                                        x = (dragOffset.x + dragAmount.x).coerceIn(minX, maxX),
                                        y = (dragOffset.y + dragAmount.y).coerceIn(horizontalInset, maxY)
                                    )
                                }
                            )
                        }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(AppColors.Primary.copy(alpha = 0.12f), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "Active mPay call",
                            tint = AppColors.PrimaryDark,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(Modifier.size(4.dp))

                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = formatVoiceDuration(elapsedSeconds),
                            color = AppColors.TextPrimary,
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                        Text(
                            text = if (call.status == "CONNECTED") "Live" else "Connecting",
                            color = AppColors.TextSecondary,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (engineState.audioOutputs.isEmpty()) {
                                boundService?.refreshAudioOutputs()
                            }
                            showAudioPicker = true
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                engineState.audioOutputId == "BLUETOOTH" -> Icons.Default.Bluetooth
                                engineState.audioOutputId == "SPEAKER" -> Icons.Default.SpeakerPhone
                                engineState.audioOutputId == "WIRED" -> Icons.Default.Headset
                                else -> Icons.Default.PhoneInTalk
                            },
                            contentDescription = "Audio output",
                            tint = AppColors.TextPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val next = !engineState.muted
                            boundService?.setMuted(next)
                            sendVoiceMuteCommand(context, callId, next)
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (engineState.muted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (engineState.muted) "Unmute call" else "Mute call",
                            tint = if (engineState.muted) AppColors.PrimaryDark else AppColors.TextPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            sendVoiceHangupCommand(context, callId)
                            onEnded?.invoke()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = "End call",
                            tint = Color(0xFFD63B45),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        if (showAudioPicker) {
            AlertDialog(
                onDismissRequest = { showAudioPicker = false },
                title = { Text("Audio output") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        engineState.audioOutputs.forEach { output ->
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    if (
                                        output.id == "BLUETOOTH" &&
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.BLUETOOTH_CONNECT
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        pendingAudioRoute = output.id
                                        bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                    } else {
                                        boundService?.setAudioOutput(output.id)
                                    }
                                    showAudioPicker = false
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = when (output.id) {
                                        "BLUETOOTH" -> Icons.Default.Bluetooth
                                        "SPEAKER" -> Icons.Default.SpeakerPhone
                                        "EARPIECE" -> Icons.Default.PhoneInTalk
                                        else -> Icons.Default.Headset
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(Modifier.size(9.dp))
                                Text(output.label, modifier = Modifier.weight(1f))
                            }
                        }
                        if (engineState.audioOutputs.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Phone and speaker audio are available.")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioPicker = false }) { Text("Close") }
                }
            )
        }
    }
}

private fun sendVoiceMuteCommand(context: Context, callId: String, muted: Boolean) {
    runCatching {
        context.startService(
            Intent(context, VoiceCallService::class.java)
                .setAction(VoiceCallService.ACTION_SET_MUTED)
                .putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId)
                .putExtra(VoiceCallService.EXTRA_MUTED, muted)
        )
    }
}

private fun sendVoiceHangupCommand(context: Context, callId: String) {
    runCatching {
        context.startService(
            Intent(context, VoiceCallService::class.java)
                .setAction(VoiceCallService.ACTION_HANGUP)
                .putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId)
        )
    }
}

private fun formatVoiceDuration(totalSeconds: Long): String {
    val minutes = (totalSeconds / 60L).toString().padStart(2, '0')
    val seconds = (totalSeconds % 60L).toString().padStart(2, '0')
    return minutes + ":" + seconds
}
