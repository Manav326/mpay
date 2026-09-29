package com.recharge.client.features.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Smartphone
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SupportTopicOption(
    val title: String,
    val description: String,
    val message: String,
    val steps: List<String>
)

private val supportTopicOptions = listOf(
    SupportTopicOption(
        "Add money",
        "Payment completed but wallet not updated",
        "I need help with adding money to my mPay wallet.",
        listOf("Open Wallet and check the latest transaction.", "Confirm whether the payment shows completed, pending or failed.", "If money was paid but the wallet is still unchanged, continue to chat with mPay Support.")
    ),
    SupportTopicOption(
        "Withdrawal",
        "UPI withdrawal, status or failed request",
        "I need help with a wallet withdrawal.",
        listOf("Open Wallet → Withdrawals and check the latest status.", "Confirm the UPI ID used for the request.", "If the request is stuck, failed or the wallet amount needs clarification, continue to chat with mPay Support.")
    ),
    SupportTopicOption(
        "Mobile recharge",
        "Recharge failed, pending or wrong plan",
        "I need help with a mobile recharge.",
        listOf("Open Recharge History and select the affected recharge.", "Check the mobile number, operator, amount and transaction status.", "If the recharge is still unresolved, continue to chat with mPay Support before retrying.")
    ),
    SupportTopicOption(
        "Car rental",
        "Booking, cancellation or payment issue",
        "I need help with an mPay car rental booking.",
        listOf("Open My Bookings and select the affected booking.", "Check its status, trip dates and wallet payment details.", "If the booking or refund issue remains, continue to chat with mPay Support.")
    ),
    SupportTopicOption(
        "Wallet & transactions",
        "Balance, debit, refund or transaction history",
        "I need help with a wallet transaction.",
        listOf("Open Wallet or Transaction History and select the transaction.", "Check the amount, status, reference and description.", "If the ledger entry still needs explanation, continue to chat with mPay Support.")
    ),
    SupportTopicOption(
        "Account & profile",
        "Profile, login or account access",
        "I need help with my mPay account or profile.",
        listOf("Check Profile and Account Settings for the affected detail.", "Confirm that the account is active and your profile information is current.", "If you still cannot complete the action, continue to chat with mPay Support.")
    ),
    SupportTopicOption(
        "Something else",
        "Another issue not covered above",
        "I need help with an mPay issue that is not covered by the support topics.",
        listOf("Choose this option when your issue does not match the topics above.", "Describe what happened, including any relevant transaction, booking or error details.", "mPay Support can take over the conversation and help investigate the issue.")
    )
)

@Composable
fun CustomerSupportScreen(
    context: Context,
    onBack: () -> Unit,
    onOpenChat: () -> Unit
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
                        onOpenChat = onOpenChat,
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

    if (chatOpen) {
        SupportChatDialog(
            chat = chat,
            loading = chatLoading,
            busy = chatBusy,
            draft = chatDraft,
            error = chatError,
            onDraftChange = { chatDraft = it.take(4000) },
            onSend = { sendChatMessage() },
            onChooseTopic = { topic -> guidedTopic = topic },
            onStartChat = {
                guidedTopic?.let {
                    guidedTopic = null
                    sendChatMessage(it.message)
                }
            },
            onRequestCallback = ::requestCallbackFromChat,
            onCancelCallback = ::cancelChatCallback,
            callbackBusy = callbackBusy,
            guidedTopic = guidedTopic,
            onBackToTopics = { guidedTopic = null },
            onDismiss = { chatOpen = false },
            onRefresh = { scope.launch { loadChat() } }
        )
    }
}

@Composable
private fun SupportHero(
    enabled: Boolean,
    onOpenChat: () -> Unit,
    pending: SupportCallRequestResponse?,
    busy: Boolean,
    onRequest: () -> Unit,
    reason: String,
    onReasonChange: (String) -> Unit,
    onCancel: (SupportCallRequestResponse) -> Unit
) {
    Card(
        onClick = onOpenChat,
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
                    Text("Chat with mPay Support or request a callback when you need a voice conversation.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = onOpenChat,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.HeadsetMic, contentDescription = null)
                Spacer(Modifier.size(7.dp))
                Text("Open support chat", fontWeight = FontWeight.Bold)
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

@Composable
private fun SupportGuidedHelp(
    topic: SupportTopicOption,
    onBack: () -> Unit,
    onStartChat: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(topic.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Try these steps first", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            topic.steps.forEachIndexed { index, step ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Surface(shape = RoundedCornerShape(9.dp), color = AppColors.Primary.copy(alpha = .11f)) {
                        Text((index + 1).toString(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = AppColors.PrimaryDark, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.size(9.dp))
                    Text(step, modifier = Modifier.weight(1f), color = AppColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFFBF3)) {
                Text("Still stuck? A real mPay support member can continue from here.", modifier = Modifier.padding(11.dp), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = onStartChat, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                Icon(Icons.Default.HeadsetMic, contentDescription = null)
                Spacer(Modifier.size(7.dp))
                Text("Chat with mPay Support", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SupportTopicOptionCard(
    topic: SupportTopicOption,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = AppColors.Primary.copy(alpha = .10f)
            ) {
                Icon(
                    when {
                        topic.title.contains("money", true) || topic.title.contains("transaction", true) -> Icons.Default.AccountBalanceWallet
                        topic.title.contains("recharge", true) -> Icons.Default.Smartphone
                        topic.title.contains("rental", true) -> Icons.Default.DirectionsCar
                        topic.title.contains("account", true) -> Icons.Default.Person
                        else -> Icons.Default.HeadsetMic
                    },
                    contentDescription = null,
                    tint = AppColors.PrimaryDark,
                    modifier = Modifier.padding(9.dp).size(20.dp)
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(topic.title, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Text(topic.description, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.TextSecondary)
        }
    }
}

private fun formatSupportDate(value: String): String =
    runCatching {
        DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(value))
    }.getOrDefault(value)
