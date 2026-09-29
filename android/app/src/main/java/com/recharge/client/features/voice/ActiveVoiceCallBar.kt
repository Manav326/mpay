package com.recharge.client.features.voice

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.recharge.client.core.model.VoiceCallResponse
import com.recharge.client.core.theme.AppColors
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun ActiveVoiceCallBar(
    call: VoiceCallResponse,
    modifier: Modifier = Modifier,
    onOpenCall: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val callId = call.callId
    var muted by remember(callId) { mutableStateOf(false) }
    var elapsedSeconds by remember(callId, call.connectedAt) { mutableStateOf(0L) }

    LaunchedEffect(callId, call.connectedAt, call.status) {
        val connectedAt = call.connectedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        if (connectedAt == null || call.status != "CONNECTED") {
            elapsedSeconds = 0L
            return@LaunchedEffect
        }

        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - connectedAt) / 1000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(
                color = AppColors.TextPrimary,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(AppColors.Primary.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Call,
                contentDescription = null,
                tint = AppColors.Primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.size(9.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = onOpenCall != null) { onOpenCall?.invoke() },
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = call.calleeName ?: call.callerName ?: "mPay Support",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = if (call.status == "CONNECTED") {
                    "Connected  " + formatVoiceDuration(elapsedSeconds)
                } else {
                    "Connecting securely…"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1
            )
        }

        IconButton(
            onClick = {
                muted = !muted
                sendVoiceMuteCommand(context, callId, muted)
            }
        ) {
            Icon(
                imageVector = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (muted) "Unmute call" else "Mute call",
                tint = if (muted) AppColors.Primary else Color.White,
                modifier = Modifier.size(21.dp)
            )
        }

        IconButton(
            onClick = {
                sendVoiceHangupCommand(context, callId)
            }
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "End call",
                tint = Color(0xFFFF7076),
                modifier = Modifier.size(21.dp)
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
