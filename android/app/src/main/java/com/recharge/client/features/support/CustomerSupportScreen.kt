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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Description
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
                    CreateSupportCallRequest(null)
                )
            }.onSuccess { response ->
                if (response.isSuccessful) {
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

                SupportActions(
                    enabled = overview?.callbackRequestEnabled == true,
                    onOpenChat = onOpenChat,
                    pending = overview?.pendingRequest,
                    busy = busy,
                    onRequest = ::requestCall,
                    onCancel = ::cancelRequest,
                    currentTicket = overview?.cases.orEmpty().firstOrNull()
                )
            }
        }
    }

}

@Composable
private fun SupportActions(
    enabled: Boolean,
    onOpenChat: () -> Unit,
    pending: SupportCallRequestResponse?,
    busy: Boolean,
    onRequest: () -> Unit,
    onCancel: (SupportCallRequestResponse) -> Unit,
    currentTicket: com.recharge.client.core.model.SupportCaseResponse?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceWarm),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SupportActionCard(
                icon = Icons.Default.ChatBubbleOutline,
                title = "Chat with mPay Support",
                subtitle = "Continue with customer care chat and keep the issue history together.",
                onClick = onOpenChat
            )

            if (enabled || pending != null) {
                SupportActionCard(
                    icon = Icons.Default.Call,
                    title = if (pending == null) "Request a callback" else "Callback requested",
                    subtitle = when {
                        pending != null -> "Your callback request is ${pending.status.replace('_', ' ').lowercase()}. You can cancel it while it is pending."
                        else -> "Ask mPay Support to call you about an issue that needs direct assistance."
                    },
                    trailing = {
                        if (pending != null) {
                            OutlinedButton(
                                onClick = { onCancel(pending) },
                                enabled = !busy,
                                shape = RoundedCornerShape(11.dp)
                            ) {
                                Icon(Icons.Default.CallEnd, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(5.dp))
                                Text(if (busy) "Cancelling…" else "Cancel")
                            }
                        } else {
                            OutlinedButton(
                                onClick = onRequest,
                                enabled = !busy,
                                shape = RoundedCornerShape(11.dp)
                            ) {
                                Icon(Icons.Default.Call, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(5.dp))
                                Text(if (busy) "Requesting…" else "Request callback")
                            }
                        }
                    }
                )
            }

            SupportTicketCard(currentTicket)
        }
    }
}
@Composable
private fun SupportActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AppColors.Primary.copy(alpha = .10f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = AppColors.PrimaryDark,
                    modifier = Modifier.padding(9.dp).size(21.dp)
                )
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(title, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Text(
                    subtitle,
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun SupportTicketCard(
    ticket: com.recharge.client.core.model.SupportCaseResponse?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.Primary.copy(alpha = .10f)
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = AppColors.PrimaryDark,
                        modifier = Modifier.padding(9.dp).size(21.dp)
                    )
                }
                Spacer(Modifier.size(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Your Support Issue", fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                    Text(
                        "Latest support status",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (ticket == null) {
                Text(
                    "No active support issue",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Start a support chat and mPay Support will create the issue summary here.",
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(ticket.subject, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (ticket.status == "RESOLVED" || ticket.status == "CLOSED") {
                        Color(0xFFEAF7EE)
                    } else {
                        AppColors.Primary.copy(alpha = .12f)
                    }
                ) {
                    Text(
                        ticket.status.replace('_', ' '),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        color = if (ticket.status == "RESOLVED" || ticket.status == "CLOSED") {
                            Color(0xFF15803D)
                        } else {
                            AppColors.PrimaryDark
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Last updated", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        Text(
                            formatSupportDate(ticket.lastMeaningfulUpdateAt ?: ticket.updatedAt),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Expected resolution", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        Text(
                            ticket.expectedResolutionAt?.let(::formatSupportDate) ?: "Not available",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }            }
        }
    }
}
