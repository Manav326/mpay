package com.recharge.client.features.commission

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recharge.client.core.model.ClientCommissionOverviewResponse
import com.recharge.client.core.model.ClientReferralMemberResponse
import com.recharge.client.core.model.ClientSearchResultResponse
import com.recharge.client.core.model.ClientUpstreamCommissionHistoryItem
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
                    Text("LEVEL ${overview.level}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
                Text("Your client network", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Metric("Your commission", "${formatMoney(overview.baseCommissionPercent)}%", Modifier.weight(1f))
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
            Icon(Icons.Default.CheckCircle, null, tint = AppColors.PrimaryDark)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Complete a recharge first", fontWeight = FontWeight.Bold)
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
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, null, tint = AppColors.PrimaryDark)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Add a verified client", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Search by name, mobile number, email or public account ID.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = state.searchQuery, onValueChange = onSearchQuery, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Search client") })
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onSearch, enabled = !state.searching) {
                    if (state.searching) CircularProgressIndicator(Modifier.padding(9.dp))
                    else Icon(Icons.Default.Search, "Search")
                }
            }
            state.searchError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            state.searchResults.forEach { result -> SearchResultRow(result, state.addingClientId == result.publicUserId, onAddClient) }
        }
    }
}

@Composable
private fun SearchResultRow(result: ClientSearchResultResponse, adding: Boolean, onAddClient: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(result.name?.takeIf { it.isNotBlank() } ?: "mPay client", fontWeight = FontWeight.SemiBold)
                Text(result.mobile, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text("ID ${result.publicUserId}", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = { onAddClient(result.publicUserId) }, enabled = !adding) {
                if (adding) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                else Text("Add")
            }
        }
    }
}

@Composable
private fun DirectClientCard(client: ClientReferralMemberResponse) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(client.name?.takeIf { it.isNotBlank() } ?: "mPay client", fontWeight = FontWeight.Bold)
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
                    Text("${item.childMobile} • recharge ₹${formatMoney(item.rechargeAmount)}", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Text("+₹${formatMoney(item.commissionAmount)}", color = AppColors.Success, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            Text("${formatMoney(item.commissionPercent)}% upstream • ${item.rechargeTransactionId}", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
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
