package com.recharge.client.features.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recharge.client.core.model.CustomerSupportOverviewResponse
import com.recharge.client.core.model.CreateSupportCallRequest
import com.recharge.client.core.model.SupportCallRequestResponse
import com.recharge.client.core.model.SupportInteractionResponse
import com.recharge.client.core.network.NetworkModule
import com.recharge.client.core.theme.AppColors
import android.content.Context
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CustomerSupportScreen(
    context: Context,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var overview by remember { mutableStateOf<CustomerSupportOverviewResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        runCatching {
            NetworkModule.clientApi(context).customerSupportOverview()
        }.onSuccess { response ->
            if (response.isSuccessful) {
                overview = response.body()
            } else {
                error = "Unable to load your support history."
            }
        }.onFailure {
            error = it.message ?: "Unable to load your support history."
        }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    fun requestCall() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            runCatching {
                NetworkModule.clientApi(context).requestCustomerSupportCall(
                    CreateSupportCallRequest(reason.trim().ifBlank { null })
                )
            }.onSuccess { response ->
                if (response.isSuccessful) {
                    reason = ""
                    load()
                } else {
                    error = "mPay could not create the callback request."
                }
            }.onFailure {
                error = it.message ?: "Unable to request a support call."
            }
            busy = false
        }
    }

    fun cancelRequest(request: SupportCallRequestResponse) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching {
                NetworkModule.clientApi(context).cancelCustomerSupportCall(request.requestId)
            }.onSuccess { response ->
                if (response.isSuccessful) load() else error = "Unable to cancel the callback request."
            }.onFailure {
                error = it.message ?: "Unable to cancel the callback request."
            }
            busy = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.Background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Help & Support",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { scope.launch { load() } }, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }

            if (loading && overview == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.PrimaryDark)
                }
                return@Surface
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                error?.let { message ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                item {
                    SupportHero(
                        enabled = overview?.callbackRequestEnabled == true,
                        pending = overview?.pendingRequest,
                        busy = busy,
                        onRequest = ::requestCall,
                        reason = reason,
                        onReasonChange = { reason = it.take(500) },
                        onCancel = { request -> cancelRequest(request) }
                    )
                }

                item {
                    Text(
                        "My Support",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                }

                if (overview?.cases.isNullOrEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AppColors.Primary.copy(alpha = .10f)
                                ) {
                                    Icon(
                                        Icons.Default.HeadsetMic,
                                        contentDescription = null,
                                        tint = AppColors.PrimaryDark,
                                        modifier = Modifier.padding(10.dp).size(24.dp)
                                    )
                                }
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("No support cases yet", fontWeight = FontWeight.Bold)
                                    Text("Your calls and future support conversations will appear here.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                } else {
                    items(overview?.cases.orEmpty(), key = { it.caseId }) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (item.status == "CLOSED" || item.status == "RESOLVED") Color(0xFFEFFAF2) else AppColors.SurfaceWarm
                                ) {
                                    Icon(
                                        if (item.status == "CLOSED" || item.status == "RESOLVED") Icons.Default.CheckCircle else Icons.Default.History,
                                        contentDescription = null,
                                        tint = if (item.status == "CLOSED" || item.status == "RESOLVED") Color(0xFF15803D) else AppColors.PrimaryDark,
                                        modifier = Modifier.padding(10.dp).size(24.dp)
                                    )
                                }
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(item.subject, fontWeight = FontWeight.Bold)
                                    Text(item.category + " · " + item.status, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    Text(formatSupportDate(item.updatedAt), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.TextSecondary)
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Recent support activity",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (overview?.interactions.isNullOrEmpty() && overview?.customerNotes.isNullOrEmpty()) {
                    item {
                        Text("No calls or support notes recorded yet.", color = AppColors.TextSecondary)
                    }
                } else {
                    items(overview?.interactions.orEmpty(), key = { it.interactionId }) { interaction ->
                        SupportInteractionCard(interaction)
                    }
                    items(overview?.customerNotes.orEmpty(), key = { "note-" + it.id }) { note ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("Support note", fontWeight = FontWeight.Bold)
                                Text(note.note, color = AppColors.TextSecondary)
                                Text(formatSupportDate(note.createdAt), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                item { Spacer(Modifier.size(12.dp)) }
            }
        }
    }
}

@Composable
private fun SupportHero(
    enabled: Boolean,
    pending: SupportCallRequestResponse?,
    busy: Boolean,
    onRequest: () -> Unit,
    reason: String,
    onReasonChange: (String) -> Unit,
    onCancel: (SupportCallRequestResponse) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(13.dp), color = AppColors.Primary.copy(alpha = .14f)) {
                    Icon(Icons.Default.HeadsetMic, contentDescription = null, tint = AppColors.PrimaryDark, modifier = Modifier.padding(10.dp).size(24.dp))
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Talk to mPay Support", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text("Request a callback. A support team member will decide when to place the call.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }

            when {
                pending != null -> {
                    Surface(
                        color = Color.White.copy(alpha = .75f),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Callback requested", color = AppColors.PrimaryDark, fontWeight = FontWeight.Bold)
                            Text("Status: " + pending.status.replace('_', ' '), fontWeight = FontWeight.SemiBold)
                            Text("Requested " + formatSupportDate(pending.requestedAt), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                            pending.reason?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = { onCancel(pending) }, enabled = !busy, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.CallEnd, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("Cancel request")
                            }
                        }
                    }
                }
                !enabled -> {
                    Text(
                        "Callback requests are currently unavailable for this account. You can still review previous support activity here.",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = onReasonChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("What do you need help with? (optional)") },
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = onRequest,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(Modifier.size(7.dp))
                        Text(if (busy) "Requesting…" else "Request a support call", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportInteractionCard(item: SupportInteractionResponse) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(11.dp), color = AppColors.Primary.copy(alpha = .10f)) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = null,
                    tint = AppColors.PrimaryDark,
                    modifier = Modifier.padding(9.dp).size(21.dp)
                )
            }
            Spacer(Modifier.size(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.channel.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() } + " support", fontWeight = FontWeight.Bold)
                Text(
                    (item.status.replace('_', ' ')) + " · " + (item.durationLabel ?: "No connected audio"),
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                item.outcome?.takeIf { it.isNotBlank() }?.let {
                    Text(it.replace('_', ' '), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                Text(formatSupportDate(item.startedAt), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatSupportDate(value: String): String =
    runCatching {
        DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(value))
    }.getOrDefault(value)
