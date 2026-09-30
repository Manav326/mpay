
package com.recharge.client.features.support

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recharge.client.core.model.SupportTicketMessageResponse
import com.recharge.client.core.model.SupportTicketResponse
import com.recharge.client.core.model.SupportTicketSummaryResponse
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.viewmodel.CustomerCareUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val customerCareCategories = listOf(
    "ACCOUNT", "WALLET", "RECHARGE", "WITHDRAWAL", "RENTAL", "PAYMENT", "VOICE_CALL", "OTHER"
)

@Composable
fun CustomerCareInlineCard(
    state: CustomerCareUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: (String, String, String) -> Unit,
    onReply: (String) -> Unit,
    onClose: () -> Unit,
    onClearSelected: () -> Unit,
    onClearError: () -> Unit
) {
    var showCreate by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) onLoad()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.Primary.copy(alpha = .11f)
                ) {
                    Icon(
                        Icons.Default.SupportAgent,
                        contentDescription = null,
                        tint = AppColors.PrimaryDark,
                        modifier = Modifier.padding(9.dp).size(22.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Help & Support", fontWeight = FontWeight.Bold)
                    Text(
                        "Chat with mPay support and manage your cases",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse help and support" else "Expand help and support",
                    tint = AppColors.PrimaryDark
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = Color(0xFFF1ECE5))

                    state.error?.let {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFF4F0)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    it,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.Error
                                )
                                TextButton(onClick = onClearError) {
                                    Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    if (state.selected != null) {
                        CustomerCareInlineChat(
                            ticket = state.selected,
                            saving = state.saving,
                            onReply = onReply,
                            onClose = onClose,
                            onBack = onClearSelected,
                            onRefresh = { onOpen(state.selected.ticketId) }
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    state.tickets.firstOrNull { it.status != "CLOSED" }?.let { onOpen(it.ticketId) }
                                        ?: run { showCreate = true }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.PrimaryDark,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Message, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Chat with support", fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = onRefresh,
                                enabled = !state.loading
                            ) {
                                Icon(Icons.Default.SupportAgent, contentDescription = "Refresh support", tint = AppColors.PrimaryDark)
                            }
                        }

                        if (state.loading && state.tickets.isEmpty()) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = AppColors.PrimaryDark
                            )
                            Text(
                                "Loading your support cases…",
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextSecondary
                            )
                        } else if (state.tickets.isEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = AppColors.SurfaceWarm
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text("No support cases yet", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Start a chat with support and your conversation will stay here.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppColors.TextSecondary
                                    )
                                }
                            }
                        } else {
                            val recentTickets = state.tickets.take(4)
                            recentTickets.forEach { ticket ->
                                CustomerCareCompactTicketRow(
                                    ticket = ticket,
                                    onClick = { onOpen(ticket.ticketId) }
                                )
                            }
                            if (state.tickets.size > recentTickets.size) {
                                Text(
                                    "Showing your 4 most recent cases.",
                                    modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.TextSecondary
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showCreate = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Help, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start a new support case")
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateSupportCaseDialog(
            saving = state.saving,
            onDismiss = { if (!state.saving) showCreate = false },
            onCreate = { category, subject, message ->
                showCreate = false
                onCreate(category, subject, message)
            }
        )
    }
}

@Composable
private fun CustomerCareCompactTicketRow(
    ticket: SupportTicketSummaryResponse,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = ticketStatusBackground(ticket.status)
        ) {
            Icon(
                if (ticket.status == "RESOLVED" || ticket.status == "CLOSED") Icons.Default.CheckCircle else Icons.Default.Message,
                contentDescription = null,
                tint = ticketStatusColor(ticket.status),
                modifier = Modifier.padding(8.dp).size(18.dp)
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ticket.subject,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                SupportPriorityPill(ticket.priority)
            }
            Text(
                ticket.ticketId + " · " + statusLabel(ticket.status),
                style = MaterialTheme.typography.labelSmall,
                color = ticketStatusColor(ticket.status)
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.TextSecondary)
    }
}

@Composable
private fun CustomerCareInlineChat(
    ticket: SupportTicketResponse,
    saving: Boolean,
    onReply: (String) -> Unit,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    var draft by rememberSaveable(ticket.ticketId) { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(ticket.ticketId, ticket.messages.size) {
        if (ticket.messages.isNotEmpty()) {
            listState.scrollToItem(ticket.messages.lastIndex)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back to support cases")
        }
        Column(Modifier.weight(1f)) {
            Text(ticket.subject, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                statusLabel(ticket.status),
                style = MaterialTheme.typography.labelSmall,
                color = ticketStatusColor(ticket.status),
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(onClick = onRefresh, enabled = !saving) {
            Icon(Icons.Default.SupportAgent, contentDescription = "Refresh conversation", tint = AppColors.PrimaryDark)
        }
        if (ticket.status != "CLOSED") {
            IconButton(onClick = onClose, enabled = !saving) {
                Icon(Icons.Default.Close, contentDescription = "Close case")
            }
        }
    }

    Text(
        ticket.ticketId + " · " + ticket.category.replace('_', ' '),
        modifier = Modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = AppColors.TextSecondary
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 170.dp, max = 320.dp)
            .supportChatBackground(),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ticket.messages, key = { it.id }) { message ->
            CustomerCareMessageBubble(message)
        }
    }

    if (ticket.status != "CLOSED") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 8000) draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Reply to Customer Care…") },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(13.dp)
            )
            Spacer(Modifier.width(7.dp))
            IconButton(
                onClick = {
                    val message = draft.trim()
                    if (message.isNotBlank()) {
                        onReply(message)
                        draft = ""
                    }
                },
                enabled = !saving && draft.trim().isNotEmpty()
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Send, contentDescription = "Send reply", tint = AppColors.PrimaryDark)
                }
            }
        }
    } else {
        Text(
            "This support case is closed. Start a new case for another issue.",
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextSecondary
        )
    }
}

private fun Modifier.supportChatBackground(): Modifier =
    this
        .background(Color(0xFFFBF8F2))
        .drawBehind {
            val min = size.minDimension
            drawCircle(
                color = AppColors.Primary.copy(alpha = .055f),
                radius = min * .70f,
                center = Offset(size.width * .06f, size.height * .10f)
            )
            drawCircle(
                color = AppColors.PrimaryDark.copy(alpha = .035f),
                radius = min * .55f,
                center = Offset(size.width * .94f, size.height * .88f)
            )
            drawCircle(
                color = Color(0xFFD9C4A1).copy(alpha = .035f),
                radius = min * .38f,
                center = Offset(size.width * .54f, size.height * .42f)
            )
        }

@Composable
fun CustomerCareScreen(
    state: CustomerCareUiState,
    onLoad: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: (String, String, String) -> Unit,
    onReply: (String) -> Unit,
    onClose: () -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { onLoad() }

    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.Background) {
        if (state.selected != null) {
            CustomerCareDetail(
                ticket = state.selected,
                saving = state.saving,
                draft = draft,
                onDraftChange = { draft = it },
                onReply = {
                    val text = draft.trim()
                    if (text.isNotBlank()) {
                        onReply(text)
                        draft = ""
                    }
                },
                onClose = onClose,
                onBack = onBack
            )
        } else {
            CustomerCareInbox(
                tickets = state.tickets,
                loading = state.loading,
                onRefresh = onLoad,
                onOpen = onOpen,
                onCreate = { showCreate = true },
                onBack = onBack
            )
        }
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text("Customer Care") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = onClearError) { Text("OK") } }
        )
    }

    if (showCreate) {
        CreateSupportCaseDialog(
            saving = state.saving,
            onDismiss = { if (!state.saving) showCreate = false },
            onCreate = { category, subject, message ->
                onCreate(category, subject, message)
                showCreate = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerCareInbox(
    tickets: List<SupportTicketSummaryResponse>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit
) {
    val activeCount = tickets.count { it.status != "CLOSED" }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Customer Care", fontWeight = FontWeight.Bold)
                        Text(
                            activeCount.toString() + " active " + if (activeCount == 1) "case" else "cases",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !loading) {
                        Icon(Icons.Default.SupportAgent, contentDescription = "Refresh support")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                containerColor = AppColors.PrimaryDark,
                contentColor = Color.White,
                shape = RoundedCornerShape(17.dp)
            ) {
                Icon(Icons.Default.Help, contentDescription = "Create support case")
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFFF8E7)
                ) {
                    Row(modifier = Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(13.dp), color = AppColors.Primary.copy(alpha = .13f)) {
                            Icon(
                                Icons.Default.SupportAgent,
                                contentDescription = null,
                                tint = AppColors.PrimaryDark,
                                modifier = Modifier.padding(10.dp).size(23.dp)
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Need help?", fontWeight = FontWeight.Bold, color = AppColors.PrimaryDark)
                            Text(
                                "Start a case and keep the full conversation in one place.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                }
            }

            if (loading && tickets.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                }
            } else if (tickets.isEmpty()) {
                item { SupportEmptyState() }
            } else {
                items(tickets, key = { it.ticketId }) { ticket ->
                    CustomerCareTicketCard(ticket = ticket, onClick = { onOpen(ticket.ticketId) })
                }
            }
        }
    }
}

@Composable
private fun CustomerCareTicketCard(ticket: SupportTicketSummaryResponse, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ticketStatusBackground(ticket.status)
            ) {
                Icon(
                    if (ticket.status == "RESOLVED" || ticket.status == "CLOSED") Icons.Default.CheckCircle else Icons.Default.Help,
                    contentDescription = null,
                    tint = ticketStatusColor(ticket.status),
                    modifier = Modifier.padding(9.dp).size(20.dp)
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ticket.subject, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    SupportPriorityPill(ticket.priority)
                }
                Text(
                    ticket.ticketId + " · " + ticket.category.replace('_', ' '),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary
                )
                Text(
                    statusLabel(ticket.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = ticketStatusColor(ticket.status),
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.TextSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerCareDetail(
    ticket: SupportTicketResponse,
    saving: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onReply: () -> Unit,
    onClose: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(ticket.messages.size) {
        if (ticket.messages.isNotEmpty()) listState.animateScrollToItem(ticket.messages.lastIndex)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ticket.ticketId, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(statusLabel(ticket.status), style = MaterialTheme.typography.labelSmall, color = ticketStatusColor(ticket.status), fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (ticket.status != "CLOSED") {
                        IconButton(onClick = onClose, enabled = !saving) {
                            Icon(Icons.Default.Close, contentDescription = "Close case")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.White
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ticket.subject, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        SupportPriorityPill(ticket.priority)
                    }
                    Text(ticket.category.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .supportChatBackground(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ticket.messages, key = { it.id }) { message ->
                    CustomerCareMessageBubble(message)
                }
            }

            if (ticket.status != "CLOSED") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { if (it.length <= 8000) onDraftChange(it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Reply to Customer Care…") },
                            maxLines = 4,
                            shape = RoundedCornerShape(15.dp),
                            singleLine = false
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = onReply, enabled = !saving && draft.trim().isNotEmpty()) {
                            if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Send, contentDescription = "Send reply", tint = AppColors.PrimaryDark)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerCareMessageBubble(message: SupportTicketMessageResponse) {
    val customer = message.authorRole.equals("CLIENT", true)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (customer) Alignment.Start else Alignment.End) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomEnd = if (customer) 16.dp else 5.dp,
                bottomStart = if (customer) 5.dp else 16.dp
            ),
            color = if (customer) Color.White else AppColors.SurfaceWarm,
            tonalElevation = 1.dp
        ) {
            Column(Modifier.widthIn(max = 330.dp).padding(11.dp)) {
                Text(
                    if (customer) "You" else (message.authorName ?: "mPay Customer Care"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (customer) AppColors.TextPrimary else AppColors.PrimaryDark
                )
                Spacer(Modifier.height(3.dp))
                Text(message.body, style = MaterialTheme.typography.bodySmall, color = AppColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(formatSupportTime(message.createdAt), style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun SupportPriorityPill(value: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = when (value) {
            "URGENT" -> Color(0xFFFFECEC)
            "HIGH" -> Color(0xFFFFF0E4)
            else -> Color(0xFFF4F0EB)
        }
    ) {
        Text(
            value,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = when (value) {
                "URGENT" -> Color(0xFFB23838)
                "HIGH" -> Color(0xFFA5571B)
                else -> AppColors.TextSecondary
            },
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SupportEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(18.dp), color = AppColors.SurfaceWarm) {
            Icon(Icons.Default.Help, null, tint = AppColors.PrimaryDark, modifier = Modifier.padding(13.dp).size(27.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("No support cases yet", fontWeight = FontWeight.Bold)
        Text("Create a case and our team will reply here.", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
    }
}

@Composable
private fun CreateSupportCaseDialog(
    saving: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var category by remember { mutableStateOf(customerCareCategories.first()) }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var categoryOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New support case", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "Tell us what happened. Keep sensitive payment credentials or passwords out of the message.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary
                )
                Box {
                    OutlinedButton(
                        onClick = { categoryOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(category.replace('_', ' '), modifier = Modifier.weight(1f))
                        Text("Change", style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = categoryOpen, onDismissRequest = { categoryOpen = false }) {
                        customerCareCategories.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.replace('_', ' ')) },
                                onClick = {
                                    category = item
                                    categoryOpen = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = subject,
                    onValueChange = { if (it.length <= 180) subject = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Subject") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 8000) message = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    label = { Text("Describe the issue") },
                    minLines = 5,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(category, subject.trim(), message.trim()) },
                enabled = !saving && subject.trim().isNotEmpty() && message.trim().isNotEmpty(),
                shape = RoundedCornerShape(11.dp)
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Create case")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

private fun statusLabel(value: String): String =
    value.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

private fun formatSupportTime(value: String): String =
    runCatching {
        val instant = Instant.parse(value)
        DateTimeFormatter.ofPattern("dd MMM · HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrDefault("—")

private fun ticketStatusColor(value: String): Color = when (value) {
    "OPEN" -> Color(0xFF9A640B)
    "IN_PROGRESS" -> Color(0xFF35679B)
    "WAITING_FOR_CUSTOMER" -> Color(0xFF7256A5)
    "RESOLVED" -> Color(0xFF2F7744)
    else -> Color(0xFF766D64)
}

private fun ticketStatusBackground(value: String): Color = when (value) {
    "OPEN" -> Color(0xFFFFF2D4)
    "IN_PROGRESS" -> Color(0xFFEAF3FF)
    "WAITING_FOR_CUSTOMER" -> Color(0xFFF2ECFF)
    else -> Color(0xFFF0EDE9)
}
