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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
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
    val code: String,
    val title: String,
    val description: String,
    val message: String,
    val steps: List<String>
)

private val supportTopicOptions = listOf(
    SupportTopicOption(
        "ADD_MONEY",
        "Add money",
        "Payment completed but wallet not updated",
        "I need help with adding money to my mPay wallet.",
        listOf("Open Wallet and check the latest transaction.", "Confirm whether the payment shows completed, pending or failed.", "If money was paid but the wallet is still unchanged, continue to chat with mPay Support.")
    ),
    SupportTopicOption(
        "WITHDRAWAL",
        "Withdrawal",
        "UPI withdrawal, status or failed request",
        "I need help with a wallet withdrawal.",
        listOf("Open Wallet → Withdrawals and check the latest status.", "Confirm the UPI ID used for the request.", "If the request is stuck, failed or the wallet amount needs clarification, continue to chat with mPay Support.")
    ),
    SupportTopicOption(
        "RECHARGE",
        "Mobile recharge",
        "Recharge failed, pending or wrong plan",
        "I need help with a mobile recharge.",
        listOf("Open Recharge History and select the affected recharge.", "Check the mobile number, operator, amount and transaction status.", "If the recharge is still unresolved, continue to chat with mPay Support before retrying.")
    ),
    SupportTopicOption(
        "CAR_RENTAL",
        "Car rental",
        "Booking, cancellation or payment issue",
        "I need help with an mPay car rental booking.",
        listOf("Open My Bookings and select the affected booking.", "Check its status, trip dates and wallet payment details.", "If the booking or refund issue remains, continue to chat with mPay Support.")
    ),
    SupportTopicOption(
        "WALLET",
        "Wallet & transactions",
        "Balance, debit, refund or transaction history",
        "I need help with a wallet transaction.",
        listOf("Open Wallet or Transaction History and select the transaction.", "Check the amount, status, reference and description.", "If the ledger entry still needs explanation, continue to chat with mPay Support.")
    ),
    SupportTopicOption(
        "ACCOUNT",
        "Account & profile",
        "Profile, login or account access",
        "I need help with my mPay account or profile.",
        listOf("Check Profile and Account Settings for the affected detail.", "Confirm that the account is active and your profile information is current.", "If you still cannot complete the action, continue to chat with mPay Support.")
    ),
    SupportTopicOption(
        "OTHER",
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
    var chatOpen by remember { mutableStateOf(false) }
    var chatLoading by remember { mutableStateOf(false) }
    var chatBusy by remember { mutableStateOf(false) }
    var chatDraft by remember { mutableStateOf("") }
    var chatError by remember { mutableStateOf<String?>(null) }
    var chat by remember { mutableStateOf<com.recharge.client.core.model.SupportChatResponse?>(null) }
    var chatRequestInFlight by remember { mutableStateOf(false) }
    var guidedTopic by remember { mutableStateOf<SupportTopicOption?>(null) }
    var supportIntakeMode by remember { mutableStateOf(false) }
    var callbackBusy by remember { mutableStateOf(false) }

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

    suspend fun loadChat() {
        if (chatRequestInFlight) return
        chatRequestInFlight = true
        chatLoading = true
        chatError = null
        try {
            runCatching {
                NetworkModule.clientApi(context).customerSupportChat()
            }.onSuccess { response ->
                if (response.isSuccessful) {
                    chat = response.body()
                } else {
                    chatError = "Unable to load the support chat."
                }
            }.onFailure {
                chatError = it.message ?: "Unable to load the support chat."
            }
        } finally {
            chatLoading = false
            chatRequestInFlight = false
        }
    }

    LaunchedEffect(chatOpen) {
        if (chatOpen) {
            while (isActive) {
                loadChat()
                delay(5000)
            }
        }
    }

    fun openChat() {
        chatOpen = true
        scope.launch { loadChat() }
    }

    fun sendChatMessage(messageOverride: String? = null, topicCode: String? = null) {
        val message = (messageOverride ?: chatDraft).trim()
        if (message.isBlank() || chatBusy) return
        chatBusy = true
        chatError = null
        scope.launch {
            runCatching {
                NetworkModule.clientApi(context).sendCustomerSupportChatMessage(
                    com.recharge.client.core.model.CreateSupportMessageRequest(message = message, topic = topicCode)
                )
            }.onSuccess { response ->
                if (response.isSuccessful) {
                    chatDraft = ""
                    supportIntakeMode = response.body()?.restartSupportIntake == true
                    loadChat()
                } else {
                    chatError = "Unable to send your message."
                }
            }.onFailure {
                chatError = it.message ?: "Unable to send your message."
            }
            chatBusy = false
        }
    }

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

    fun requestCallbackFromChat() {
        if (callbackBusy || chat?.pendingCallbackRequest != null || chat?.callbackRequestEnabled != true) return
        callbackBusy = true
        chatError = null
        scope.launch {
            runCatching {
                NetworkModule.clientApi(context).requestCustomerSupportCall(CreateSupportCallRequest(null))
            }.onSuccess { response ->
                if (response.isSuccessful) {
                    loadChat()
                    load()
                } else {
                    chatError = "mPay could not create the callback request."
                }
            }.onFailure {
                chatError = it.message ?: "Unable to request a support callback."
            }
            callbackBusy = false
        }
    }

    fun cancelChatCallback(request: SupportCallRequestResponse) {
        if (callbackBusy) return
        callbackBusy = true
        chatError = null
        scope.launch {
            runCatching {
                NetworkModule.clientApi(context).cancelCustomerSupportCall(request.requestId)
            }.onSuccess { response ->
                if (response.isSuccessful) {
                    loadChat()
                    load()
                } else {
                    chatError = "Unable to cancel the callback request."
                }
            }.onFailure {
                chatError = it.message ?: "Unable to cancel the callback request."
            }
            callbackBusy = false
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
                    supportIntakeMode = false
                    sendChatMessage(it.message, it.code)
                }
            },
            onRequestCallback = ::requestCallbackFromChat,
            onCancelCallback = ::cancelChatCallback,
            callbackBusy = callbackBusy,
            guidedTopic = guidedTopic,
            supportIntakeMode = supportIntakeMode,
            onBackToTopics = { guidedTopic = null; supportIntakeMode = true },
            onDismiss = { chatOpen = false },
            onRefresh = { scope.launch { loadChat() } }
        )
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


@Composable
private fun SupportChatDialog(
    chat: com.recharge.client.core.model.SupportChatResponse?,
    loading: Boolean,
    busy: Boolean,
    draft: String,
    error: String?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onChooseTopic: (SupportTopicOption) -> Unit,
    onStartChat: () -> Unit,
    onRequestCallback: () -> Unit,
    onCancelCallback: (SupportCallRequestResponse) -> Unit,
    callbackBusy: Boolean,
    guidedTopic: SupportTopicOption?,
    supportIntakeMode: Boolean,
    onBackToTopics: () -> Unit,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(chat?.messages?.size) {
        val size = chat?.messages?.size ?: 0
        if (size > 0) {
            listState.animateScrollToItem(size - 1)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.82f),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF14181D),
            tonalElevation = 4.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2A241A)
                    ) {
                        Icon(
                            Icons.Default.HeadsetMic,
                            contentDescription = null,
                            tint = AppColors.PrimaryDark,
                            modifier = Modifier.padding(9.dp).size(21.dp)
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("mPay Support", fontWeight = FontWeight.Bold, color = Color(0xFFF3F5F7), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (chat?.status == "OPEN") "Usually replies through this chat" else "Support conversation",
                            color = Color(0xFFAAB3BF),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFC7CED7))
                    }
                }

                androidx.compose.material3.HorizontalDivider(color = Color(0xFF303840))

                if (error != null) {
                    Text(
                        error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color(0xFFFCA5A5),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0E1419))
                        .drawBehind {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White.copy(alpha = .035f), Color.Transparent),
                                    center = Offset(size.width * .5f, 0f),
                                    radius = size.width * .9f
                                )
                            )
                        },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (chat?.items.isNullOrEmpty() && chat?.messages.isNullOrEmpty() && !supportIntakeMode) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(shape = RoundedCornerShape(14.dp), color = AppColors.Primary.copy(alpha = .10f)) {
                                    Icon(Icons.Default.HeadsetMic, null, tint = AppColors.PrimaryDark, modifier = Modifier.padding(11.dp).size(27.dp))
                                }
                                Text("How can we help?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("Choose a topic to start your support request.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        items(supportTopicOptions, key = { it.title }) { topic ->
                            SupportTopicOptionCard(topic = topic, onClick = { onChooseTopic(topic) })
                        }
                    } else {
                        items(chat?.items.orEmpty(), key = { it.itemId }) { item ->
                            if (item.type == "VOICE_CALL") {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(0.84f),
                                        color = Color(0xFF232930),
                                        shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp, bottomStart = 15.dp, bottomEnd = 4.dp)
                                    ) {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF2A241A)) {
                                                Icon(Icons.Default.Call, null, tint = AppColors.Primary, modifier = Modifier.padding(8.dp).size(17.dp))
                                            }
                                            Spacer(Modifier.size(9.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    when {
                                                        item.outcome == "NO_ANSWER" || item.status == "MISSED" -> "Support tried to call you"
                                                        item.status == "DECLINED" -> "Support call declined"
                                                        else -> "Support voice call"
                                                    },
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFFF0F3F6)
                                                )
                                                Text(
                                                    listOfNotNull(item.actorName ?: "mPay Support", item.outcome ?: item.status, item.durationLabel).joinToString(" · "),
                                                    color = Color(0xFFAAB3BF),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                            Text(formatSupportDate(item.createdAt), color = Color(0xFF8F9AA7), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            } else {
                                val mine = item.senderType == "CUSTOMER"
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(0.82f),
                                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (mine) 16.dp else 4.dp, bottomEnd = if (mine) 16.dp else 4.dp),
                                        color = if (mine) AppColors.Primary.copy(alpha = .22f) else Color(0xFF232930)
                                    ) {
                                        Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                                            Text(
                                                when (item.senderType) {
                                                    "CUSTOMER" -> "You"
                                                    "AI" -> "mPay AI Support"
                                                    else -> "mPay Support"
                                                },
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (mine) Color(0xFFFFC65A) else Color(0xFFD7DEE7),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Text(item.message.orEmpty(), color = Color(0xFFF0F3F6))
                                            Text(formatSupportDate(item.createdAt), color = Color(0xFF8F9AA7), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                        if (supportIntakeMode) {
                            item {
                                if (guidedTopic != null) {
                                    SupportGuidedHelp(topic = requireNotNull(guidedTopic), onBack = onBackToTopics, onStartChat = onStartChat)
                                } else {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Text("What can we help you with?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text("Choose a topic to start this support request.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                        supportTopicOptions.forEach { topic ->
                                            SupportTopicOptionCard(topic = topic, onClick = { onChooseTopic(topic) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (chat?.messages?.any { it.senderType == "STAFF" } == true || chat?.pendingCallbackRequest != null) {
                    Surface(
                        color = Color(0xFF181D23),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (chat?.pendingCallbackRequest != null) {
                                Text("Callback requested", color = Color(0xFFFFC65A), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text("Customer Care will handle the voice request from the same support case.", color = Color(0xFFAAB3BF), style = MaterialTheme.typography.labelSmall)
                                OutlinedButton(
                                    onClick = { onCancelCallback(requireNotNull(chat?.pendingCallbackRequest)) },
                                    enabled = !callbackBusy,
                                    shape = RoundedCornerShape(11.dp)
                                ) {
                                    Icon(Icons.Default.CallEnd, contentDescription = null)
                                    Spacer(Modifier.size(5.dp))
                                    Text("Cancel callback")
                                }
                            } else if (chat?.callbackRequestEnabled == true) {
                                OutlinedButton(
                                    onClick = onRequestCallback,
                                    enabled = !callbackBusy,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null)
                                    Spacer(Modifier.size(6.dp))
                                    Text(if (callbackBusy) "Requesting callback…" else "Still need help? Request a callback", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (!chat?.messages.isNullOrEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF171C21)).padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Write a message…") },
                            maxLines = 3,
                            shape = RoundedCornerShape(13.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1C2228),
                                unfocusedContainerColor = Color(0xFF1C2228),
                                focusedTextColor = Color(0xFFF0F3F6),
                                unfocusedTextColor = Color(0xFFF0F3F6),
                                focusedPlaceholderColor = Color(0xFF8F9AA7),
                                unfocusedPlaceholderColor = Color(0xFF8F9AA7),
                                focusedBorderColor = AppColors.Primary,
                                unfocusedBorderColor = Color(0xFF3A424C),
                                cursorColor = AppColors.Primary
                            )
                        )
                        Spacer(Modifier.size(5.dp))
                        IconButton(onClick = onSend, enabled = draft.isNotBlank() && !busy, modifier = Modifier.size(46.dp)) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = AppColors.Primary)
                        }
                    }
                }
            }
        }
    }
}
