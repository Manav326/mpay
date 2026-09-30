package com.recharge.client.features.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recharge.client.core.model.CustomerSupportOverviewResponse
import com.recharge.client.core.model.CreateSupportCallRequest
import com.recharge.client.core.model.SupportCallRequestResponse
import com.recharge.client.core.network.NetworkModule
import com.recharge.client.core.theme.AppColors
import android.content.Context
import kotlinx.coroutines.launch

@Composable
fun CustomerSupportScreen(
    context: Context,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    embedded: Boolean = false,
    modifier: Modifier = Modifier
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

    Surface(
        modifier = if (embedded) {
            modifier.fillMaxWidth().wrapContentHeight()
        } else {
            modifier.fillMaxSize()
        },
        color = AppColors.Background
    ) {
        Column(
            modifier = if (embedded) Modifier.wrapContentHeight() else Modifier.fillMaxSize()
        ) {
            if (!embedded) {
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
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFFC7CED7))
                }
            }
            }

            if (loading && overview == null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(if (embedded) Modifier.height(180.dp) else Modifier.fillMaxHeight()),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppColors.PrimaryDark)
                }
                return@Surface
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (embedded) {
                            Modifier
                        } else {
                            Modifier.verticalScroll(rememberScrollState())
                        }
                    )
                    .padding(
                        horizontal = if (embedded) 12.dp else 18.dp,
                        vertical = if (embedded) 4.dp else 6.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                error?.let { message ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }

                SupportHero(
                    enabled = overview?.callbackRequestEnabled == true,
                    onOpenChat = onOpenChat,
                    showHeading = !embedded,
                    pending = overview?.pendingRequest,
                    busy = busy,
                    onRequest = ::requestCall,
                    reason = reason,
                    onReasonChange = { reason = it.take(500) },
                    onCancel = { request -> cancelRequest(request) }
                )

                Text(
                    "Your Support Issue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )

                if (overview?.cases.isNullOrEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(18.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("No active support issue", fontWeight = FontWeight.Bold)
                            Text(
                                "Start a support chat and mPay Support will create the issue summary here.",
                                color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    val item = overview?.cases.orEmpty().first()
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(18.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(item.subject, fontWeight = FontWeight.Bold)
                            Text(
                                item.status.replace('_', ' '),
                                color = if (item.status == "RESOLVED" || item.status == "CLOSED") Color(0xFF15803D) else AppColors.PrimaryDark,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        "Last updated",
                                        color = AppColors.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        formatSupportDate(item.lastMeaningfulUpdateAt ?: item.updatedAt),
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        "Expected resolution",
                                        color = AppColors.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        formatSupportDate(item.expectedResolutionAt ?: item.updatedAt),
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.size(12.dp))
            }
        }
    }

}

@Composable
private fun SupportHero(
    enabled: Boolean,
    onOpenChat: () -> Unit,
    showHeading: Boolean,
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
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showHeading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(13.dp), color = AppColors.Primary.copy(alpha = .14f)) {
                    Icon(Icons.Default.HeadsetMic, null, tint = AppColors.PrimaryDark, modifier = Modifier.padding(10.dp).size(24.dp))
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Help & Support", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text("Choose how you want mPay Support to help.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenChat, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                    Icon(Icons.Default.HeadsetMic, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Chat with Support", fontWeight = FontWeight.Bold)
                }
                if (pending != null) {
                    OutlinedButton(onClick = { onCancel(pending) }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                        Icon(Icons.Default.CallEnd, null)
                        Spacer(Modifier.size(6.dp))
                        Text("Cancel callback")
                    }
                } else if (enabled) {
                    OutlinedButton(onClick = onRequest, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                        Icon(Icons.Default.Call, null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (busy) "Requesting…" else "Request a callback")
                    }
                }
            }

            if (pending != null) {
                Text("Callback requested · " + pending.status.replace('_', ' '), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
