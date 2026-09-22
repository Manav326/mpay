package com.recharge.client.features.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recharge.client.core.model.CurrentUserResponse
import com.recharge.client.core.model.RechargeCommissionSummaryResponse
import com.recharge.client.core.model.RechargeHistoryItem
import com.recharge.client.core.model.WalletResponse
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.ui.ProfileAvatar
import com.recharge.client.core.viewmodel.WalletUiState
import com.recharge.client.features.wallet.WithdrawDialog
import com.recharge.client.core.ui.formatAsOf
import com.recharge.client.core.ui.formatMoney
import com.recharge.client.core.ui.formatPeriod
import com.recharge.client.features.recharge.RechargeHistoryCard
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.time.LocalTime

@Composable
fun HomeScreen(
    user: CurrentUserResponse?, wallet: WalletResponse?, loading: Boolean,
    commission: RechargeCommissionSummaryResponse?, latestRecharge: RechargeHistoryItem?, error: String?, isVisible: Boolean,
    onRefresh: () -> Unit, onRefreshBalance: () -> Unit, onRefreshEarnings: () -> Unit,
    onRecharge: () -> Unit, onAddMoney: () -> Unit, onWithdraw: (String, String, String) -> Unit,
    onClearWithdrawMessage: () -> Unit, walletUiState: WalletUiState,
    onRechargeHistory: () -> Unit, onMarketplace: () -> Unit, onRentalBookings: () -> Unit, onCarRental: () -> Unit
) {
    var showWithdraw by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isVisible) { if (isVisible) onRefresh() }
    var greetingVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isVisible) {
        if (isVisible) { greetingVisible = false; delay(80); greetingVisible = true }
    }
    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Good night" }
    val displayName = user?.name?.takeIf { it.isNotBlank() } ?: "there"

    if (showWithdraw) {
        WithdrawDialog(
            state = walletUiState,
            onDismiss = { showWithdraw = false },
            onWithdraw = onWithdraw,
            onClearMessage = onClearWithdrawMessage
        )
    }

    LazyColumn(Modifier.fillMaxSize().widthIn(max = 1000.dp).padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    AnimatedVisibility(visible = greetingVisible, enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 })) {
                        Column {
                            Text(greeting, style = MaterialTheme.typography.titleMedium, color = AppColors.TextSecondary)
                            Text(displayName, style = MaterialTheme.typography.headlineSmall, softWrap = true)
                        }
                    }
                }
                ProfileAvatar(user, 52.dp)
            }
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Primary)) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Wallet balance", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f), modifier = Modifier.weight(1f))
                        IconButton(onClick = onRefreshBalance, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh balance", tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                    Text(if (loading) "Loading…" else "₹${formatMoney(wallet?.availableBalance ?: BigDecimal.ZERO)}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1, softWrap = false)
                    if ((wallet?.reservedBalance ?: BigDecimal.ZERO) > BigDecimal.ZERO) {
                        Spacer(Modifier.height(6.dp))
                        Text("₹${formatMoney(wallet?.reservedBalance ?: BigDecimal.ZERO)} reserved in pending transactions", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onAddMoney, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimary, contentColor = AppColors.Primary), modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add Money", maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = { onClearWithdrawMessage(); showWithdraw = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Send, null); Spacer(Modifier.width(6.dp)); Text("Withdraw to UPI", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
        error?.let { item { Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodySmall) } }
        item { Text("Quick actions", style = MaterialTheme.typography.titleLarge) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard("Mobile Recharge", Icons.Default.PhoneAndroid, AppColors.Success, onRecharge, Modifier.weight(1f))
                ActionCard("Add Money", Icons.Default.Add, AppColors.PrimaryDark, onAddMoney, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onMarketplace) { Text("Marketplace") }
                TextButton(onClick = onRentalBookings) { Text("My Bookings") }
                TextButton(onClick = onRechargeHistory) { Text("Recharge History") }
            }
        }
        item {
            Card(
                onClick = onCarRental,
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF8FF))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(15.dp), color = Color(0xFFDDF1FF)) {
                        Icon(Icons.Default.DirectionsCar, null, tint = Color(0xFF1677B8), modifier = Modifier.padding(11.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Car Rental", style = MaterialTheme.typography.titleLarge)
                        Text("Chauffeur-driven cars, available directly from here.", color = AppColors.TextSecondary)
                    }
                    Text("Explore", color = Color(0xFF1677B8), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (latestRecharge != null) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Latest recharge", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onRechargeHistory) { Text("View all") }
                }
            }
            item { RechargeHistoryCard(latestRecharge) }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Today’s earnings", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onRefreshEarnings) { Icon(Icons.Default.Refresh, "Refresh today's earnings") }
            }
        }
        item { EarningsPeriodCard(commission?.daily, isToday = true) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Monthly earnings", style = MaterialTheme.typography.titleLarge)
            }
        }
        item { EarningsPeriodCard(commission?.monthly, isToday = false) }
    }
}

@Composable
private fun ActionCard(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconTint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = iconTint.copy(alpha = .12f)) {
                Icon(icon, null, modifier = Modifier.padding(10.dp), tint = iconTint)
            }
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun EarningsPeriodCard(period: com.recharge.client.core.model.CommissionPeriodSummary?, isToday: Boolean) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (isToday) formatAsOf(period?.to) else formatPeriod(period?.from, period?.to), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Commission earned", color = AppColors.TextSecondary)
                    Text("₹${formatMoney(period?.commission ?: BigDecimal.ZERO)}", style = MaterialTheme.typography.headlineSmall, color = AppColors.Success, fontWeight = FontWeight.Bold)
                    Text("${period?.successfulRechargeCount ?: 0} successful recharges", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Recharge volume", color = AppColors.TextSecondary)
                    Text("₹${formatMoney(period?.successfulRechargeAmount ?: BigDecimal.ZERO)}", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
