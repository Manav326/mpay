package com.recharge.client.features.support

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.recharge.client.core.model.CreateSupportCallRequest
import com.recharge.client.core.model.CreateSupportMessageRequest
import com.recharge.client.core.model.SupportCallRequestResponse
import com.recharge.client.core.model.SupportChatResponse
import com.recharge.client.core.network.NetworkModule
import com.recharge.client.core.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private data class FloatingSupportTopic(
    val code: String,
    val title: String,
    val description: String,
    val message: String,
    val steps: List<String>
)

private val floatingSupportTopics = listOf(
    FloatingSupportTopic("ADD_MONEY", "Add money", "Payment completed but wallet not updated", "I need help with adding money to my mPay wallet.", listOf(
        "Open Wallet and check the latest transaction.",
        "Confirm whether the payment shows completed, pending or failed.",
        "If money was paid but the wallet is still unchanged, continue to chat with mPay Support."
    )),
    FloatingSupportTopic("WITHDRAWAL", "Withdrawal", "UPI withdrawal, status or failed request", "I need help with a wallet withdrawal.", listOf(
        "Open Wallet → Withdrawals and check the latest status.",
        "Confirm the UPI ID used for the request.",
        "If the request is stuck, failed or the wallet amount needs clarification, continue to chat with mPay Support."
    )),
    FloatingSupportTopic("RECHARGE", "Mobile recharge", "Recharge failed, pending or wrong plan", "I need help with a mobile recharge.", listOf(
        "Open Recharge History and select the affected recharge.",
        "Check the mobile number, operator, amount and transaction status.",
        "If the recharge is still unresolved, continue to chat with mPay Support before retrying."
    )),
    FloatingSupportTopic("CAR_RENTAL", "Car rental", "Booking, cancellation or payment issue", "I need help with an mPay car rental booking.", listOf(
        "Open My Bookings and select the affected booking.",
        "Check its status, trip dates and wallet payment details.",
        "If the booking or refund issue remains, continue to chat with mPay Support."
    )),
    FloatingSupportTopic("WALLET", "Wallet & transactions", "Balance, debit, refund or transaction history", "I need help with a wallet transaction.", listOf(
        "Open Wallet or Transaction History and select the transaction.",
        "Check the amount, status, reference and description.",
        "If the ledger entry still needs explanation, continue to chat with mPay Support."
    )),
    FloatingSupportTopic("ACCOUNT", "Account & profile", "Profile, login or account access", "I need help with my mPay account or profile.", listOf(
        "Check Profile and Account Settings for the affected detail.",
        "Confirm that the account is active and your profile information is current.",
        "If you still cannot complete the action, continue to chat with mPay Support."
    )),
    FloatingSupportTopic("OTHER", "Something else", "Another issue not covered above", "I need help with an mPay issue that is not covered by the support topics.", listOf(
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

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    var chat by remember { mutableStateOf<SupportChatResponse?>(null) }
    var loading by remember { mutableStateOf(false) }
    var chatRequestInFlight by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var guidedTopicCode by rememberSaveable { mutableStateOf<String?>(null) }
    val guidedTopic = floatingSupportTopics.firstOrNull { it.code == guidedTopicCode }
    var supportIntakeMode by rememberSaveable { mutableStateOf(false) }
    var callbackBusy by remember { mutableStateOf(false) }
    var minimized by rememberSaveable { mutableStateOf(false) }
    var widthDp by rememberSaveable { mutableStateOf(390f) }
    var heightDp by rememberSaveable { mutableStateOf(360f) }
    var autoSizeEnabled by rememberSaveable { mutableStateOf(true) }
    var offsetX by rememberSaveable { mutableStateOf(12f) }
    var offsetY by rememberSaveable { mutableStateOf(72f) }
    var minimizedDragging by remember { mutableStateOf(false) }
    var dismissTargetActive by remember { mutableStateOf(false) }
    var minimizedDragMoved by remember { mutableStateOf(false) }

    val minWidth = 300f
    val minHeight = 320f
    val horizontalMargin = 8f
    val bottomMargin = 8f

    suspend fun loadChat() {
        if (chatRequestInFlight) return
        chatRequestInFlight = true
        loading = true
        error = null
        try {
            runCatching { NetworkModule.clientApi(context).customerSupportChat() }
                .onSuccess { response ->
                    if (response.isSuccessful) chat = response.body()
                    else error = "Unable to load the support chat."
                }
                .onFailure { error = it.message ?: "Unable to load the support chat." }
        } finally {
            loading = false
            chatRequestInFlight = false
        }
    }

    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        loadChat()
        while (isActive) {
            delay(5000)
            loadChat()
        }
    }

    LaunchedEffect(chat?.items?.size, chat?.messages?.size, guidedTopic, supportIntakeMode) {
        if (!minimized && autoSizeEnabled) {
            val timelineCount = chat?.items?.size?.takeIf { it > 0 }
                ?: (chat?.messages?.size ?: 0)
            val targetHeight = when {
                guidedTopic != null -> 500f
                supportIntakeMode -> 600f
                timelineCount <= 0 -> 360f
                timelineCount <= 2 -> 390f
                timelineCount <= 4 -> 460f
                timelineCount <= 7 -> 540f
                else -> 600f
            }
            heightDp = targetHeight.coerceAtMost(
                configuration.screenHeightDp.toFloat().coerceAtLeast(minHeight)
            )
        }
    }

    fun sendMessage(messageOverride: String? = null, topicCode: String? = null) {
        val message = (messageOverride ?: draft).trim()
        if (message.isBlank() || busy) return
        busy = true
        error = null
        scope.launch {
            runCatching {
                NetworkModule.clientApi(context).sendCustomerSupportChatMessage(
                    CreateSupportMessageRequest(message = message, topic = topicCode)
                )
            }.onSuccess { response ->
                if (response.isSuccessful) {
                    draft = ""
                    supportIntakeMode = response.body()?.restartSupportIntake == true
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

    if (open) {
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val availableWidth = maxWidth.value
        val availableHeight = maxHeight.value

        fun clampPosition() {
            val currentWidth = if (minimized) 56f else widthDp
            val currentHeight = if (minimized) 56f else heightDp
            offsetX = offsetX.coerceIn(
                horizontalMargin,
                (availableWidth - currentWidth - horizontalMargin).coerceAtLeast(horizontalMargin)
            )
            offsetY = offsetY.coerceIn(
                horizontalMargin,
                (availableHeight - currentHeight - bottomMargin).coerceAtLeast(horizontalMargin)
            )
        }

        val maxContentWidth = (availableWidth - horizontalMargin * 2).coerceAtLeast(56f)
        val maxContentHeight = (availableHeight - horizontalMargin - bottomMargin).coerceAtLeast(56f)

        fun isOverDismissTarget(x: Float, y: Float): Boolean {
            val bubbleCenterX = x + 28f
            val bubbleCenterY = y + 28f
            val targetWidth = 88f
            val targetHeight = 72f
            val targetLeft = (availableWidth - targetWidth) / 2f
            val targetTop = availableHeight - targetHeight - 14f
            return bubbleCenterX in targetLeft..(targetLeft + targetWidth) &&
                bubbleCenterY in targetTop..(targetTop + targetHeight)
        }
        val boundedWidth = if (minimized) 56f else {
            widthDp.coerceIn(minWidth.coerceAtMost(maxContentWidth), maxContentWidth)
        }
        val boundedHeight = if (minimized) 56f else {
            heightDp.coerceIn(minHeight.coerceAtMost(maxContentHeight), maxContentHeight)
        }

        LaunchedEffect(minimized, availableWidth, availableHeight, boundedWidth, boundedHeight) {
            clampPosition()
        }

        if (minimizedDragging) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-12).dp)
                    .size(46.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close support window",
                    tint = if (dismissTargetActive) Color(0xFFFF5A5A) else Color(0xFFE34242),
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(with(density) { offsetX.dp.roundToPx() }, with(density) { offsetY.dp.roundToPx() }) }
        ) {
            FloatingChatWindow(
            modifier = Modifier
                .width(boundedWidth.dp)
                .height(boundedHeight.dp),
            chat = chat,
            loading = loading,
            busy = busy,
            draft = draft,
            error = error,
            guidedTopic = guidedTopic,
            supportIntakeMode = supportIntakeMode,
            minimized = minimized,
            callbackBusy = callbackBusy,
            onDismiss = onDismiss,
            onMinimize = {
                if (!minimized) {
                    offsetX = (availableWidth - 56f - horizontalMargin)
                        .coerceAtLeast(horizontalMargin)
                    offsetY = (availableHeight - 56f - bottomMargin)
                        .coerceAtLeast(horizontalMargin)
                }
                minimized = !minimized
            },
            onRefresh = { scope.launch { loadChat() } },
            onDraftChange = { draft = it.take(4000) },
            onSend = { sendMessage() },
            onChooseTopic = { guidedTopicCode = it?.code },
            onStartChat = {
                guidedTopic?.let {
                    guidedTopicCode = null
                    supportIntakeMode = false
                    sendMessage(it.message, it.code)
                }
            },
            onRequestCallback = ::requestCallback,
            onCancelCallback = ::cancelCallback,
            onDragStart = {
                if (minimized) {
                    minimizedDragging = true
                    minimizedDragMoved = false
                    dismissTargetActive = false
                }
            },
            onDrag = { dx, dy ->
                offsetX += dx
                offsetY += dy
                if (minimized) {
                    minimizedDragMoved = true
                    dismissTargetActive = isOverDismissTarget(offsetX, offsetY)
                }
                clampPosition()
            },
            onDragEnd = {
                if (minimized) {
                    val shouldDismiss = minimizedDragMoved && dismissTargetActive
                    minimizedDragging = false
                    dismissTargetActive = false
                    if (shouldDismiss) {
                        minimizedDragMoved = false
                        onDismiss()
                    } else if (!minimizedDragMoved) {
                        minimizedDragMoved = false
                        minimized = false
                    } else {
                        minimizedDragMoved = false
                    }
                }
            },
            onResize = { dx, dy ->
                if (!minimized) {
                    val maxW = maxContentWidth
                    val maxH = maxContentHeight
                    autoSizeEnabled = false
                    widthDp = (widthDp + dx).coerceIn(minWidth.coerceAtMost(maxW), maxW)
                    heightDp = (heightDp + dy).coerceIn(minHeight.coerceAtMost(maxH), maxH)
                    clampPosition()
                }
            }
        )
    }
    }
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
    supportIntakeMode: Boolean,
    minimized: Boolean,
    callbackBusy: Boolean,
    onDismiss: () -> Unit,
    onMinimize: () -> Unit,
    onRefresh: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onChooseTopic: (FloatingSupportTopic?) -> Unit,
    onStartChat: () -> Unit,
    onRequestCallback: () -> Unit,
    onCancelCallback: (SupportCallRequestResponse) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onResize: (Float, Float) -> Unit
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val supportPresent = chat?.let { support ->
        support.status == "OPEN" &&
            support.messages.any { it.senderType == "STAFF" || it.senderType == "AI" }
    } == true
    val liveTransition = rememberInfiniteTransition(label = "supportPresence")
    val liveAlpha by liveTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "supportPresencePulse"
    )
    val hasTimeline = !chat?.items.isNullOrEmpty() || !chat?.messages.isNullOrEmpty()
    val hasStaffTimeline = chat?.items?.any { it.senderType == "STAFF" } == true ||
        chat?.messages?.any { it.senderType == "STAFF" } == true

    LaunchedEffect(chat?.items?.size, chat?.messages?.size) {
        val size = if (!chat?.items.isNullOrEmpty()) {
            chat.items.size
        } else {
            chat?.messages?.size ?: 0
        }
        if (size > 0) listState.animateScrollToItem(size - 1)
    }

    if (minimized) {
        Surface(
            modifier = modifier
                .size(56.dp)
                .clip(CircleShape)
                .clickable(onClick = onMinimize)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            onDragStart()
                        },
                        onDragEnd = {
                            onDragEnd()
                        },
                        onDragCancel = {
                            onDragEnd()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(
                                with(density) { dragAmount.x.toDp().value },
                                with(density) { dragAmount.y.toDp().value }
                            )
                        }
                    )
                },
            shape = CircleShape,
            color = Color(0xFF171B20),
            shadowElevation = 18.dp,
            tonalElevation = 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF39414A))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = CircleShape,
                    color = Color(0xFF2A241A)
                ) {
                    Icon(
                        Icons.Default.HeadsetMic,
                        contentDescription = "Open mPay Support",
                        tint = AppColors.Primary,
                        modifier = Modifier.padding(11.dp).size(28.dp)
                    )
                }
                if (supportPresent) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                            .alpha(liveAlpha)
                    )
                }
            }
        }
        return
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF14181D),
        shadowElevation = 20.dp,
        tonalElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF303840))
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(Color(0xFF1B2026))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(
                                with(density) { dragAmount.x.toDp().value },
                                with(density) { dragAmount.y.toDp().value }
                            )
                        }
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFFFFEFCF)) {
                    Icon(Icons.Default.HeadsetMic, null, tint = AppColors.PrimaryDark, modifier = Modifier.padding(8.dp).size(20.dp))
                }
                Spacer(Modifier.size(9.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("mPay Support", fontWeight = FontWeight.Bold, color = Color(0xFFF3F5F7))
                            if (supportPresent) {
                                Spacer(Modifier.size(7.dp))
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E))
                                        .alpha(liveAlpha)
                                )
                            }
                        }
                        Text(
                            if (chat?.status == "OPEN") "Your support conversation is active" else "Private support conversation",
                            color = Color(0xFFAAB3BF),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                IconButton(onClick = onRefresh, enabled = !loading) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFFC7CED7))
                }
                IconButton(onClick = onMinimize) {
                    Text("−", color = Color(0xFFE6EAF0), style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = Color(0xFFC7CED7))
                }
            }

            if (!minimized) {
                if (error != null) {
                    Surface(color = Color(0xFF321D1D), modifier = Modifier.fillMaxWidth()) {
                        Text(error, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color(0xFFFCA5A5), style = MaterialTheme.typography.labelSmall)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .drawBehind {
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF11181E),
                                        Color(0xFF0E1419),
                                        Color(0xFF11171C)
                                    )
                                )
                            )
                            val tile = 96.dp.toPx()
                            val line = 1.dp.toPx()
                            var x = -tile
                            var column = 0
                            while (x < size.width + tile) {
                                var y = -tile
                                var row = 0
                                while (y < size.height + tile) {
                                    val accent = Color(0xFFA86408).copy(alpha = 0.10f)
                                    val soft = Color.White.copy(alpha = 0.035f)
                                    val bubbleX = x + 12.dp.toPx()
                                    val bubbleY = y + 15.dp.toPx()
                                    drawRoundRect(
                                        color = accent,
                                        topLeft = Offset(bubbleX, bubbleY),
                                        size = Size(25.dp.toPx(), 18.dp.toPx()),
                                        cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                                        style = Stroke(width = line)
                                    )
                                    drawLine(
                                        color = accent.copy(alpha = 0.72f),
                                        start = Offset(bubbleX + 7.dp.toPx(), bubbleY + 18.dp.toPx()),
                                        end = Offset(bubbleX + 4.dp.toPx(), bubbleY + 23.dp.toPx()),
                                        strokeWidth = line
                                    )
                                    drawCircle(
                                        color = accent.copy(alpha = 0.62f),
                                        radius = 1.4.dp.toPx(),
                                        center = Offset(bubbleX + 12.dp.toPx(), bubbleY + 9.dp.toPx())
                                    )
                                    drawCircle(
                                        color = soft,
                                        radius = 11.dp.toPx(),
                                        center = Offset(x + 69.dp.toPx(), y + 67.dp.toPx())
                                    )
                                    drawLine(
                                        color = soft,
                                        start = Offset(x + 69.dp.toPx(), y + 59.dp.toPx()),
                                        end = Offset(x + 69.dp.toPx(), y + 75.dp.toPx()),
                                        strokeWidth = line
                                    )
                                    drawLine(
                                        color = soft,
                                        start = Offset(x + 61.dp.toPx(), y + 67.dp.toPx()),
                                        end = Offset(x + 77.dp.toPx(), y + 67.dp.toPx()),
                                        strokeWidth = line
                                    )
                                    drawCircle(
                                        color = accent.copy(alpha = 0.48f),
                                        radius = 1.2.dp.toPx(),
                                        center = Offset(x + 82.dp.toPx(), y + 22.dp.toPx())
                                    )
                                    row++
                                    y += tile
                                }
                                column++
                                x += tile
                            }
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.035f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.5f, 0f),
                                    radius = size.width * 0.82f
                                )
                            )
                        },
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (!hasTimeline && !supportIntakeMode) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF2A241A)) {
                                    Icon(Icons.Default.HeadsetMic, null, tint = AppColors.Primary, modifier = Modifier.padding(10.dp).size(24.dp))
                                }
                                Text("How can we help?", fontWeight = FontWeight.Bold, color = Color(0xFFF3F5F7))
                                Text("Choose a topic to start with guided help.", color = Color(0xFFAAB3BF), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        items(floatingSupportTopics, key = { it.title }) { topic ->
                            Card(
                                onClick = { onChooseTopic(topic) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(15.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2026)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(10.dp), color = AppColors.Primary.copy(alpha = .10f)) {
                                        Icon(Icons.Default.HeadsetMic, null, tint = AppColors.PrimaryDark, modifier = Modifier.padding(8.dp).size(19.dp))
                                    }
                                    Spacer(Modifier.size(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(topic.title, fontWeight = FontWeight.SemiBold, color = Color(0xFFF1F4F7))
                                        Text(topic.description, color = Color(0xFFAAB3BF), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF8C97A4))
                                }
                            }
                        }
                    } else {
                        if (!chat?.items.isNullOrEmpty()) {
                            items(chat?.items.orEmpty(), key = { it.itemId }) { item ->
                                if (item.type == "VOICE_CALL") {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(.84f),
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
                                                    Text(listOfNotNull(item.actorName ?: "mPay Support", item.outcome ?: item.status, item.durationLabel).joinToString(" · "), color = Color(0xFFAAB3BF), style = MaterialTheme.typography.labelSmall)
                                                }
                                                Text(formatSupportDate(item.createdAt), color = Color(0xFF8F9AA7), style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                } else {
                                    val mine = item.senderType == "CUSTOMER"
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(.84f),
                                            shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp, bottomStart = if (mine) 15.dp else 4.dp, bottomEnd = if (mine) 15.dp else 4.dp),
                                            color = if (mine) AppColors.Primary.copy(alpha = .22f) else Color(0xFF232930)
                                        ) {
                                            Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
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
                                                Text(item.message.orEmpty(), color = Color(0xFFF0F3F6), style = MaterialTheme.typography.bodySmall)
                                                Text(formatSupportDate(item.createdAt), color = Color(0xFF8F9AA7), style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            items(chat?.messages.orEmpty(), key = { it.messageId }) { item ->
                                val mine = item.senderType == "CUSTOMER"
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(.84f),
                                        shape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp, bottomStart = if (mine) 15.dp else 4.dp, bottomEnd = if (mine) 15.dp else 4.dp),
                                        color = if (mine) AppColors.Primary.copy(alpha = .22f) else Color(0xFF232930)
                                    ) {
                                        Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
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
                                            Text(item.message, color = Color(0xFFF0F3F6), style = MaterialTheme.typography.bodySmall)
                                            Text(formatSupportDate(item.createdAt), color = Color(0xFF8F9AA7), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                        if (supportIntakeMode) {
                            item {
                                if (guidedTopic != null) {
                                    GuidedFloatingHelp(guidedTopic, onBack = { onChooseTopic(null) }, onStartChat = onStartChat)
                                } else {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("What can we help you with?", fontWeight = FontWeight.Bold, color = Color(0xFFF1F4F7))
                                        Text("Choose a topic to start this support request.", color = Color(0xFFAAB3BF), style = MaterialTheme.typography.labelSmall)
                                        floatingSupportTopics.forEach { topic ->
                                            Card(onClick = { onChooseTopic(topic) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2026))) {
                                                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(topic.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Color(0xFFF1F4F7))
                                                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF8C97A4))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (hasStaffTimeline || chat?.pendingCallbackRequest != null) {
                    Surface(color = Color(0xFF181D23), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (chat?.pendingCallbackRequest != null) {
                                Text("Callback requested", color = Color(0xFFFFC65A), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text("Customer Care will handle the voice request from this same support case.", color = Color(0xFFAAB3BF), style = MaterialTheme.typography.labelSmall)
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

                if (hasTimeline) {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF171C21)).padding(horizontal = 9.dp, vertical = 4.dp),
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
                        Spacer(Modifier.size(6.dp))
                        IconButton(onClick = onSend, enabled = draft.isNotBlank() && !busy, modifier = Modifier.size(46.dp)) {
                            Icon(Icons.Default.Send, "Send", tint = AppColors.Primary)
                        }
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(Color(0xFF171C21))
                ) {
                    Text(
                        "Drag header to move · drag corner to resize",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF7F8A97),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .align(Alignment.BottomEnd)
                            .pointerInput("supportResize") {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onResize(
                                            with(density) { dragAmount.x.toDp().value },
                                            with(density) { dragAmount.y.toDp().value }
                                        )
                                    }
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2026)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(topic.title, fontWeight = FontWeight.Bold, color = Color(0xFFF3F5F7))
                    Text("Try these steps first", color = Color(0xFFAAB3BF), style = MaterialTheme.typography.labelSmall)
                }
            }
            topic.steps.forEachIndexed { index, step ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Surface(shape = RoundedCornerShape(8.dp), color = AppColors.Primary.copy(alpha = .11f)) {
                        Text((index + 1).toString(), modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp), color = AppColors.PrimaryDark, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(step, modifier = Modifier.weight(1f), color = Color(0xFFE1E6EC), style = MaterialTheme.typography.bodySmall)
                }
            }
            Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFF20262D)) {
                Text("Still stuck? A real mPay support member can continue from here.", modifier = Modifier.padding(10.dp), color = Color(0xFFAAB3BF), style = MaterialTheme.typography.labelSmall)
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
