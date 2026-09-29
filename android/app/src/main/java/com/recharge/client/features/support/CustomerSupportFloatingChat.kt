package com.recharge.client.features.support

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.recharge.client.core.model.CreateSupportCallRequest
import com.recharge.client.core.model.CreateSupportMessageRequest
import com.recharge.client.core.model.SupportCallRequestResponse
import com.recharge.client.core.model.SupportChatResponse
import com.recharge.client.core.network.NetworkModule
import com.recharge.client.core.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class FloatingSupportTopic(
    val title: String,
    val description: String,
    val message: String,
    val steps: List<String>
)

private val floatingSupportTopics = listOf(
    FloatingSupportTopic("Add money", "Payment completed but wallet not updated", "I need help with adding money to my mPay wallet.", listOf(
        "Open Wallet and check the latest transaction.",
        "Confirm whether the payment shows completed, pending or failed.",
        "If money was paid but the wallet is still unchanged, continue to chat with mPay Support."
    )),
    FloatingSupportTopic("Withdrawal", "UPI withdrawal, status or failed request", "I need help with a wallet withdrawal.", listOf(
        "Open Wallet → Withdrawals and check the latest status.",
        "Confirm the UPI ID used for the request.",
        "If the request is stuck, failed or the wallet amount needs clarification, continue to chat with mPay Support."
    )),
    FloatingSupportTopic("Mobile recharge", "Recharge failed, pending or wrong plan", "I need help with a mobile recharge.", listOf(
        "Open Recharge History and select the affected recharge.",
        "Check the mobile number, operator, amount and transaction status.",
        "If the recharge is still unresolved, continue to chat with mPay Support before retrying."
    )),
    FloatingSupportTopic("Car rental", "Booking, cancellation or payment issue", "I need help with an mPay car rental booking.", listOf(
        "Open My Bookings and select the affected booking.",
        "Check its status, trip dates and wallet payment details.",
        "If the booking or refund issue remains, continue to chat with mPay Support."
    )),
    FloatingSupportTopic("Wallet & transactions", "Balance, debit, refund or transaction history", "I need help with a wallet transaction.", listOf(
        "Open Wallet or Transaction History and select the transaction.",
        "Check the amount, status, reference and description.",
        "If the ledger entry still needs explanation, continue to chat with mPay Support."
    )),
    FloatingSupportTopic("Account & profile", "Profile, login or account access", "I need help with my mPay account or profile.", listOf(
        "Check Profile and Account Settings for the affected detail.",
        "Confirm that the account is active and your profile information is current.",
        "If you still cannot complete the action, continue to chat with mPay Support."
    )),
    FloatingSupportTopic("Something else", "Another issue not covered above", "I need help with an mPay issue that is not covered by the support topics.", listOf(
        "Choose this when your issue does not match the topics above.",
        "Describe what happened, including any relevant transaction, booking or error details.",
        "mPay Support can take over the conversation and help investigate the issue."
    ))
)

@Composable
fun CustomerSupportFloatingChat(
    context: Context,
    open: Boolean,
    onDismiss: () -> Unit
) {
    if (!open) return

    val scope = rememberCoroutineScope()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val maxWidth = configuration.screenWidthDp.dp
    val maxHeight = configuration.screenHeightDp.dp

    var chat by remember { mutableStateOf<SupportChatResponse?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var guidedTopic by remember { mutableStateOf<FloatingSupportTopic?>(null) }
    var callbackBusy by remember { mutableStateOf(false) }
    var minimized by rememberSaveable { mutableStateOf(false) }
    var widthDp by rememberSaveable { mutableStateOf(390f) }
    var heightDp by rememberSaveable { mutableStateOf(560f) }
    var offsetX by rememberSaveable { mutableStateOf(12f) }
    var offsetY by rememberSaveable { mutableStateOf(72f) }

    val minWidth = 300f
    val minHeight = 390f
    val horizontalMargin = 8f
    val bottomMargin = 8f

    fun clampPosition() {
        val availableWidth = configuration.screenWidthDp.toFloat()
        val availableHeight = configuration.screenHeightDp.toFloat()
        offsetX = offsetX.coerceIn(horizontalMargin, (availableWidth - widthDp - horizontalMargin).coerceAtLeast(horizontalMargin))
        offsetY = offsetY.coerceIn(horizontalMargin, (availableHeight - heightDp - bottomMargin).coerceAtLeast(horizontalMargin))
    }

    suspend fun loadChat() {
        loading = true
        error = null
        runCatching { NetworkModule.clientApi(context).customerSupportChat() }
            .onSuccess { response ->
                if (response.isSuccessful) chat = response.body()
                else error = "Unable to load the support chat."
            }
            .onFailure { error = it.message ?: "Unable to load the support chat." }
        loading = false
    }

    LaunchedEffect(Unit) {
        loadChat()
        while (isActive) {
            delay(5000)
            loadChat()
        }
    }

    LaunchedEffect(minimized) {
        if (!minimized) clampPosition()
    }

    fun sendMessage(messageOverride: String? = null) {
        val message = (messageOverride ?: draft).trim()
        if (message.isBlank() || busy) return
        busy = true
        error = null
        scope.launch {
            runCatching {
                NetworkModule.clientApi(context).sendCustomerSupportChatMessage(
                    CreateSupportMessageRequest(message)
                )
            }.onSuccess { response ->
                if (response.isSuccessful) {
                    draft = ""
                    loadChat()
                } else error = "Unable to send your message."
            }.onFailure { error = it.message ?: "Unable to send your message." }
            busy = false
        }
    }

    fun requestCallback() {
        if (callbackBusy || chat?.callbackRequestEnabled != true || chat?.pendingCallbackRequest != null) return
        callbackBusy = true
        error = null
        scope.launch {
            runCatching {
                NetworkModule.clientApi(context).requestCustomerSupportCall(CreateSupportCallRequest(null))
            }.onSuccess { response ->
                if (response.isSuccessful) loadChat()
                else error = "mPay could not create the callback request."
            }.onFailure { error = it.message ?: "Unable to request a support callback." }
            callbackBusy = false
        }
    }

    fun cancelCallback(request: SupportCallRequestResponse) {
        if (callbackBusy) return
        callbackBusy = true
        error = null
        scope.launch {
            runCatching {
                NetworkModule.clientApi(context).cancelCustomerSupportCall(request.requestId)
            }.onSuccess { response ->
                if (response.isSuccessful) loadChat()
                else error = "Unable to cancel the callback request."
            }.onFailure { error = it.message ?: "Unable to cancel the callback request." }
            callbackBusy = false
        }
    }

    val windowWidth = if (minimized) 258f else widthDp
    val windowHeight = if (minimized) 58f else heightDp

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        Popup(
            alignment = Alignment.TopStart,
            onDismissRequest = {},
            focusable = false
        ) {
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { offsetX.dp.toPx().roundToInt().let { IntOffset(it, 0) } }.let { IntOffset(it.x, with(density) { offsetY.dp.toPx().roundToInt() }) }.x.dp,
                        y = offsetY.dp
                    )
                    .width(windowWidth.coerceAtMost(maxWidth.value - 16f).dp)
                    .height(windowHeight.coerceAtMost(maxHeight.value - 16f).dp)
            )
        }

        FloatingChatWindow(
            modifier = Modifier
                .offset {
                    IntOffset(
                        with(density) { offsetX.dp.toPx().roundToInt() },
                        with(density) { offsetY.dp.toPx().roundToInt() }
                    )
                }
                .width(windowWidth.coerceAtMost(maxWidth.value - 16f).dp)
                .height(windowHeight.coerceAtMost(maxHeight.value - 16f).dp),
            chat = chat,
            loading = loading,
            busy = busy,
            draft = draft,
            error = error,
            guidedTopic = guidedTopic,
            minimized = minimized,
            callbackBusy = callbackBusy,
            onDismiss = onDismiss,
            onMinimize = { minimized = !minimized },
            onRefresh = { scope.launch { loadChat() } },
            onDraftChange = { draft = it.take(4000) },
            onSend = { sendMessage() },
            onChooseTopic = { guidedTopic = it },
            onStartChat = {
                guidedTopic?.let {
                    guidedTopic = null
                    sendMessage(it.message)
                }
            },
            onRequestCallback = ::requestCallback,
            onCancelCallback = ::cancelCallback,
            onDrag = { dx, dy ->
                if (!minimized) {
                    offsetX += dx
                    offsetY += dy
                    clampPosition()
                }
            },
            onResize = { dx, dy ->
                if (!minimized) {
                    val maxW = configuration.screenWidthDp.toFloat() - horizontalMargin * 2
                    val maxH = configuration.screenHeightDp.toFloat() - horizontalMargin * 2
                    widthDp = (widthDp + dx).coerceIn(minWidth, maxW)
                    heightDp = (heightDp + dy).coerceIn(minHeight, maxH)
                    clampPosition()
                }
            }
        )
    }
}

@Composable
private fun FloatingChatWindow(
    modifier: Modifier,
    chat: SupportChatResponse?,
    loading: Boolean,
    busy: Boolean,
    draft: String,
    error: String?,
    guidedTopic: FloatingSupportTopic?,
    minimized: Boolean,
    callbackBusy: Boolean,
    onDismiss: () -> Unit,
    onMinimize: () -> Unit,
    onRefresh: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onChooseTopic: (FloatingSupportTopic) -> Unit,
    onStartChat: () -> Unit,
    onRequestCallback: () -> Unit,
    onCancelCallback: (SupportCallRequestResponse) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(chat?.messages?.size) {
        val size = chat?.messages?.size ?: 0
        if (size > 0) listState.animateScrollToItem(size - 1)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 18.dp,
        tonalElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0E4D0))
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(Color(0xFFFFFAF1))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { _, dragAmount -> onDrag(dragAmount.x, dragAmount.y) }
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFFFFEFCF)) {
                    Icon(Icons.Default.HeadsetMic, null, tint = AppColors.PrimaryDark, modifier = Modifier.padding(8.dp).size(20.dp))
                }
                Spacer(Modifier.size(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("mPay Support", fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                    Text(
                        if (chat?.status == "OPEN") "Your support conversation is active" else "Private support conversation",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                IconButton(onClick = onRefresh, enabled = !loading) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = AppColors.TextSecondary)
                }
                IconButton(onClick = onMinimize) {
                    Text(if (minimized) "+" else "−", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = AppColors.TextSecondary)
                }
            }

            if (!minimized) {
                if (error != null) {
                    Surface(color = Color(0xFFFFF2F2), modifier = Modifier.fillMaxWidth()) {
                        Text(error, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color(0xFFB42318), style = MaterialTheme.typography.labelSmall)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (chat?.messages.isNullOrEmpty() && guidedTopic != null) {
                        item {
                            GuidedFloatingHelp(guidedTopic, onBack = { onChooseTopic(null) }, onStartChat = onStartChat)
                        }
                    } else if (chat?.messages.isNullOrEmpty()) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(shape = RoundedCornerShape(14.dp), color = AppColors.Primary.copy(alpha = .10f)) {
                                    Icon(Icons.Default.HeadsetMic, null, tint = AppColors.PrimaryDark, modifier = Modifier.padding(10.dp).size(24.dp))
                                }
                                Text("How can we help?", fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                                Text("Choose a topic to start with guided help.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        items(floatingSupportTopics, key = { it.title }) { topic ->
                            Card(
                                onClick = { onChooseTopic(topic) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(15.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(10.dp), color = AppColors.Primary.copy(alpha = .10f)) {
                                        Icon(
                                            when {
                                                topic.title.contains("money", true) || topic.title.contains("transaction", true) -> Icons.Default.AccountBalanceWallet
                                                topic.title.contains("recharge", true) -> Icons.Default.Smartphone
                                                topic.title.contains("rental", true) -> Icons.Default.DirectionsCar
                                                topic.title.contains("account", true) -> Icons.Default.Person
                                                else -> Icons.Default.HeadsetMic
                                            },
                                            null, tint = AppColors.PrimaryDark, modifier = Modifier.padding(8.dp).size(19.dp)
                                        )
                                    }
                                    Spacer(Modifier.size(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(topic.title, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                                        Text(topic.description, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = AppColors.TextSecondary)
                                }
                            }
                        }
                    } else {
                        items(chat?.messages.orEmpty(), key = { it.messageId }) { item ->
                            val mine = item.senderType == "CUSTOMER"
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(.84f),
                                    shape = RoundedCornerShape(
                                        topStart = 15.dp, topEnd = 15.dp,
                                        bottomStart = if (mine) 15.dp else 4.dp,
                                        bottomEnd = if (mine) 4.dp else 15.dp
                                    ),
                                    color = if (mine) AppColors.Primary.copy(alpha = .15f) else Color(0xFFF3F5F7)
                                ) {
                                    Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
                                        Text(if (mine) "You" else "mPay Support", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                                        Text(item.message, color = AppColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
                                        Text(formatSupportDate(item.createdAt), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                if (chat?.messages?.any { it.senderType == "STAFF" } == true || chat?.pendingCallbackRequest != null) {
                    Surface(color = Color(0xFFFFFBF3), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (chat?.pendingCallbackRequest != null) {
                                Text("Callback requested", color = AppColors.PrimaryDark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text("Customer Care will handle the voice request from this same support case.", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                OutlinedButton(onClick = { onCancelCallback(requireNotNull(chat.pendingCallbackRequest)) }, enabled = !callbackBusy, shape = RoundedCornerShape(10.dp)) {
                                    Icon(Icons.Default.CallEnd, null)
                                    Spacer(Modifier.size(5.dp))
                                    Text("Cancel callback")
                                }
                            } else if (chat?.callbackRequestEnabled == true) {
                                OutlinedButton(onClick = onRequestCallback, enabled = !callbackBusy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) {
                                    Icon(Icons.Default.Call, null)
                                    Spacer(Modifier.size(5.dp))
                                    Text(if (callbackBusy) "Requesting callback…" else "Still need help? Request a callback", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (!chat?.messages.isNullOrEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFFFFFAF1)).padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Write a message…") },
                            maxLines = 3,
                            shape = RoundedCornerShape(13.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        IconButton(onClick = onSend, enabled = draft.isNotBlank() && !busy, modifier = Modifier.size(46.dp)) {
                            Icon(Icons.Default.Send, "Send", tint = AppColors.PrimaryDark)
                        }
                    }
                }

                Box(
                    Modifier.fillMaxWidth().background(Color(0xFFFFFAF1)).padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text("Drag the header to move · drag the bottom-right corner to resize", color = Color(0xFFA1988D), style = MaterialTheme.typography.labelSmall)
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.BottomEnd)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDrag = { _, dragAmount -> onResize(dragAmount.x / 3f, dragAmount.y / 3f) }
                                )
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun GuidedFloatingHelp(
    topic: FloatingSupportTopic,
    onBack: () -> Unit,
    onStartChat: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ChevronRight, "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(topic.title, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                    Text("Try these steps first", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            topic.steps.forEachIndexed { index, step ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Surface(shape = RoundedCornerShape(8.dp), color = AppColors.Primary.copy(alpha = .11f)) {
                        Text((index + 1).toString(), modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp), color = AppColors.PrimaryDark, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(step, modifier = Modifier.weight(1f), color = AppColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFFFFFBF3)) {
                Text("Still stuck? A real mPay support member can continue from here.", modifier = Modifier.padding(10.dp), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = onStartChat, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.HeadsetMic, null)
                Spacer(Modifier.size(6.dp))
                Text("Chat with mPay Support", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatSupportDate(value: String): String =
    runCatching {
        java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.parse(value))
    }.getOrDefault(value)
