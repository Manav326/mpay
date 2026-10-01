package com.recharge.client.features.commission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recharge.client.core.model.ClientCommissionOverviewResponse
import com.recharge.client.core.model.ClientReferralMemberResponse
import com.recharge.client.core.model.ClientSearchResultResponse
import com.recharge.client.core.model.ClientUpstreamCommissionHistoryItem
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.recharge.client.core.network.ApiConfig
import com.recharge.client.core.security.TokenStore
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.ui.formatExactTimestamp
import com.recharge.client.core.ui.formatMoney
import com.recharge.client.core.viewmodel.ClientCommissionUiState

@Composable
fun ClientCommissionScreen(
    state: ClientCommissionUiState,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onSearchQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onAddClient: (String) -> Unit,
    onPreviousHistoryPage: () -> Unit,
    onNextHistoryPage: () -> Unit,
    onClearMessages: () -> Unit,
    isVisible: Boolean
) {
    LaunchedEffect(isVisible) {
        if (isVisible) onLoad()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Client network", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Build your client network and track upstream earnings.", color = AppColors.TextSecondary)
                }
                IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
            }
        }
        state.error?.let { item { MessageCard(it, true, onClearMessages) } }
        state.actionMessage?.let { item { MessageCard(it, false, onClearMessages) } }
        item { NetworkOverviewCard(state.overview) }
        item {
            when {
                state.overview == null -> Unit
                state.overview.canAddClients -> AddClientCard(state, onSearchQuery, onSearch, onAddClient)
                else -> RequirementCard()
            }
        }
        item {
            SectionTitle(Icons.Default.People, "My direct clients", "${state.clients.size} direct client${if (state.clients.size == 1) "" else "s"}")
        }
        if (state.clients.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Text("No clients have been added to your network yet.", modifier = Modifier.padding(16.dp), color = AppColors.TextSecondary)
                }
            }
        } else {
            items(state.clients, key = { it.publicUserId }) { DirectClientCard(it) }
        }
        item { SectionTitle(Icons.Default.TrendingUp, "Upstream earnings", "Credited only after a direct client's recharge succeeds.") }
        if (state.loadingHistory && state.upstreamHistory.isEmpty()) {
            item { LoadingCard() }
        } else if (state.upstreamHistory.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Text("No upstream commission has been credited yet.", modifier = Modifier.padding(16.dp), color = AppColors.TextSecondary)
                }
            }
        } else {
            items(state.upstreamHistory, key = { it.walletLedgerRef }) { UpstreamHistoryCard(it) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onPreviousHistoryPage, enabled = state.upstreamPage > 0) { Text("Previous") }
                    Text("Page ${state.upstreamPage + 1}", color = AppColors.TextSecondary)
                    OutlinedButton(onClick = onNextHistoryPage, enabled = state.upstreamHasNext) { Text("Next") }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(11.dp), color = AppColors.SurfaceWarm) {
            Icon(icon, null, tint = AppColors.PrimaryDark, modifier = Modifier.padding(8.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NetworkOverviewCard(overview: ClientCommissionOverviewResponse?) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Primary)) {
        if (overview == null) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .14f)) {
                        Icon(Icons.Default.People, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(if (overview.level > 0) "LEVEL ${overview.level}" else "NETWORK NOT ACTIVE", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
                Text(if (overview.level > 0) "Build your client network and unlock upstream earnings." else "Start with a recharge attempt to activate your client network.", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Metric("Recharge commission rate", "${formatMoney(overview.baseCommissionPercent)}%", Modifier.weight(1f))
                    Metric("Direct clients", "${overview.directClientCount}/${overview.level2DirectClientThreshold}", Modifier.weight(1f))
                }
                if (overview.level >= 2) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .12f)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Upstream commission", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
                                Text("${formatMoney(overview.upstreamCommissionPercent)}% on successful direct-client recharges", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    val remaining = (overview.level2DirectClientThreshold - overview.directClientCount).coerceAtLeast(0)
                    Text("${remaining} more direct client${if (remaining == 1) "" else "s"} needed for Level 2.", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Today upstream", "₹${formatMoney(overview.todayUpstreamCommission)}", Modifier.weight(1f))
                    Metric("This month", "₹${formatMoney(overview.monthUpstreamCommission)}", Modifier.weight(1f))
                }
            }
        }
    }
}
@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RequirementCard() {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceWarm)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, tint = AppColors.PrimaryDark)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Activate your client network", fontWeight = FontWeight.Bold)
                Text("After your first recharge attempt, this network opens and you can add verified clients.", color = AppColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun AddClientCard(
    state: ClientCommissionUiState,
    onSearchQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onAddClient: (String) -> Unit
) {
    val expandedClientId = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    Card(shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.Primary.copy(alpha = .10f)
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        null,
                        tint = AppColors.PrimaryDark,
                        modifier = Modifier.padding(9.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        "Add a verified client",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Search using the client's unique mPay Client ID. Review the matched account before adding it to your network.",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Client ID") },
                placeholder = { Text("Enter unique Client ID") },
                leadingIcon = {
                    Icon(Icons.Default.Badge, contentDescription = null)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Client ID")
                        }
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onSearch() }
                ),
                supportingText = {
                    Text(
                        "Client ID remains unique for the account's full lifecycle.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )

            Button(
                onClick = onSearch,
                enabled = !state.searching && state.searchQuery.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp)
            ) {
                if (state.searching) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Verifying…")
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Find client")
                }
            }

            if (state.searching) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            state.searchError?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .72f)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            state.searchMessage?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.SurfaceWarm
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = AppColors.PrimaryDark
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            it,
                            color = AppColors.PrimaryDark,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            state.searchResults.forEach { result ->
                SearchResultCard(
                    result = result,
                    expanded = expandedClientId.value == result.publicUserId,
                    adding = state.addingClientId == result.publicUserId,
                    onToggle = {
                        expandedClientId.value =
                            if (expandedClientId.value == result.publicUserId) null else result.publicUserId
                    },
                    onAddClient = onAddClient
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: ClientSearchResultResponse,
    expanded: Boolean,
    adding: Boolean,
    onToggle: () -> Unit,
    onAddClient: (String) -> Unit
) {
    Card(
        onClick = { if (!adding) onToggle() },
        enabled = !adding,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClientAccountAvatar(result)
                Spacer(Modifier.width(11.dp))
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        result.name?.takeIf { it.isNotBlank() } ?: "mPay client",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Client ID  ${result.publicUserId}",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    StatusBadge(
                        text = if (result.canBeAdded) "Verified client" else "Not eligible for network add",
                        positive = result.canBeAdded
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide client details" else "View client details",
                    tint = AppColors.TextSecondary
                )
            }

            if (expanded) {
                HorizontalDivider()

                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Account details",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DetailTile(
                            "Account type",
                            "Client",
                            Modifier.weight(1f)
                        )
                        DetailTile(
                            "Status",
                            if (result.accountActive) "Active" else "Inactive",
                            Modifier.weight(1f)
                        )
                    }

                    DetailLine("Full name", result.name?.takeIf { it.isNotBlank() } ?: "Not provided")
                    DetailLine("Client ID", result.publicUserId)
                    DetailLine("Mobile", result.mobile)
                    DetailLine("Email", result.email?.takeIf { it.isNotBlank() } ?: "Not provided")
                    DetailLine("Member since", formatExactTimestamp(result.createdAt))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DetailTile(
                            "Client level",
                            if (result.clientLevel > 0) "Level ${result.clientLevel}" else "Not active",
                            Modifier.weight(1f)
                        )
                        DetailTile(
                            "Direct clients",
                            result.directClientCount.toString(),
                            Modifier.weight(1f)
                        )
                    }

                    StatusBadge(
                        text = if (result.mobileVerified) "Mobile verified" else "Mobile not verified",
                        positive = result.mobileVerified
                    )

                    if (result.canBeAdded) {
                        Text(
                            "This account is eligible to become your direct client.",
                            color = AppColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Button(
                            onClick = { onAddClient(result.publicUserId) },
                            enabled = !adding,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            if (adding) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Adding…")
                            } else {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add client")
                            }
                        }
                    } else {
                        ClientCannotBeAddedCard(result)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientCannotBeAddedCard(result: ClientSearchResultResponse) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .75f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(9.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    "Can't be added",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    result.unavailableReason ?: "This account is not eligible for network assignment.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ClientAccountAvatar(result: ClientSearchResultResponse) {
    val context = LocalContext.current
    val token = TokenStore(context).accessToken()
    val imageUrl = result.profileImageUrl?.let {
        val base = if (it.startsWith("http")) it else ApiConfig.BASE_URL.trimEnd('/') + it
        result.profileImageVersion?.let { version ->
            base + (if (base.contains("?")) "&" else "?") + "v=" + version
        } ?: base
    }

    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .memoryCacheKey("client-profile:${result.publicUserId}:${result.profileImageVersion}")
                    .diskCacheKey("client-profile:${result.publicUserId}:${result.profileImageVersion}")
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .apply {
                        if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
                    }
                    .crossfade(true)
                    .build(),
                contentDescription = "Client profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(CircleShape)
            )
        } else {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(27.dp)
            )
        }
    }
}

@Composable
private fun StatusBadge(text: String, positive: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (positive) AppColors.Success.copy(alpha = .10f)
        else MaterialTheme.colorScheme.errorContainer.copy(alpha = .7f)
    ) {
        Text(
            text = text,
            color = if (positive) AppColors.Success else MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun DetailTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = AppColors.SurfaceWarm
    ) {
        Column(
            Modifier.fillMaxWidth().padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall
        )
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
@Composable
private fun DirectClientCard(client: ClientReferralMemberResponse) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(client.name?.takeIf { it.isNotBlank() } ?: "mPay client", fontWeight = FontWeight.Bold)
            Text("✓ Verified client", color = AppColors.Success, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(client.mobile, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text("Added ${formatExactTimestamp(client.assignedAt)}", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun UpstreamHistoryCard(item: ClientUpstreamCommissionHistoryItem) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(item.childName?.takeIf { it.isNotBlank() } ?: "Direct client", fontWeight = FontWeight.Bold)
                    Text("Successful recharge • ₹${formatMoney(item.rechargeAmount)}", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Text("+₹${formatMoney(item.commissionAmount)}", color = AppColors.Success, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            Text("${formatMoney(item.commissionPercent)}% upstream", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            Text(formatExactTimestamp(item.createdAt), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MessageCard(message: String, error: Boolean, onClear: () -> Unit) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (error) MaterialTheme.colorScheme.errorContainer else AppColors.SurfaceWarm)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), color = if (error) MaterialTheme.colorScheme.onErrorContainer else AppColors.PrimaryDark)
            OutlinedButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("Dismiss") }
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(Modifier.height(22.dp))
        }
    }
}
