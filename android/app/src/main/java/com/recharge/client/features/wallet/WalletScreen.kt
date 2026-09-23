package com.recharge.client.features.wallet

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recharge.client.core.model.RechargeCommissionSummaryResponse
import com.recharge.client.core.model.RechargeHistoryItem
import com.recharge.client.core.model.WalletResponse
import com.recharge.client.core.model.WalletHistoryItem
import com.recharge.client.core.model.WithdrawalHistoryItem
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.ui.formatAsOf
import com.recharge.client.core.ui.formatExactTimestamp
import com.recharge.client.core.ui.formatMoney
import com.recharge.client.core.ui.formatPeriod
import com.recharge.client.core.ui.MpayStatusPill
import com.recharge.client.core.ui.MpayEmptyState
import com.recharge.client.core.viewmodel.WalletDateFilter
import com.recharge.client.core.viewmodel.WalletHistoryFilter
import com.recharge.client.core.viewmodel.WalletUiState
import com.recharge.client.features.recharge.RechargeHistoryCard
import com.recharge.client.features.recharge.operatorColor
import com.recharge.client.features.recharge.operatorLabel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Calendar
import kotlinx.coroutines.delay

@Composable
fun WalletScreen(
    wallet: WalletResponse?, loading: Boolean, commission: RechargeCommissionSummaryResponse?, commissionLoading: Boolean,
    walletUiState: WalletUiState, onRefresh: () -> Unit, onRefreshBalance: () -> Unit, onAddMoney: () -> Unit, onViewRechargeHistory: () -> Unit,
    onRefreshCommission: () -> Unit, onSelectWalletHistoryFilter: (WalletHistoryFilter) -> Unit,
    onSetWalletHistoryToday: () -> Unit, onSetWalletHistoryLast7: () -> Unit, onSetWalletHistoryMonth: () -> Unit, onSetWalletHistoryCustom: (LocalDate, LocalDate) -> Unit,
    onRefreshWalletHistory: () -> Unit, onLoadMoreWalletHistory: () -> Unit,
    onWithdraw: (String, String, String) -> Unit, onClearWithdrawMessage: () -> Unit, onOpenWalletDetail: (WalletHistoryItem) -> Unit, onCloseWalletDetail: () -> Unit,
    isVisible: Boolean
) {
    var showWithdraw by rememberSaveable { mutableStateOf(false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var pendingFrom by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(isVisible) { if (isVisible) { onRefresh(); onRefreshCommission(); onRefreshWalletHistory() } }
    if (showWithdraw) WithdrawDialog(walletUiState, wallet?.availableBalance ?: BigDecimal.ZERO, { showWithdraw = false }, onWithdraw, onClearWithdrawMessage)

    if (showFromPicker) WalletDatePicker(walletUiState.fromDate) { date -> pendingFrom = date; showFromPicker = false; showToPicker = true }
    if (showToPicker) WalletDatePicker(maxOf(walletUiState.toDate, pendingFrom ?: walletUiState.toDate)) { date ->
        val from = (pendingFrom ?: date).coerceAtMost(date)
        onSetWalletHistoryCustom(from, date); pendingFrom = null; showToPicker = false
    }

    when {
        walletUiState.selectedRecharge != null -> {
            AlertDialog(
                onDismissRequest = onCloseWalletDetail,
                title = { Text("Recharge details") },
                text = { RechargeHistoryCard(walletUiState.selectedRecharge) },
                confirmButton = { Button(onClick = onCloseWalletDetail) { Text("Okay") } }
            )
        }
        walletUiState.selectedWalletItem != null && walletUiState.detailLoading -> {
            AlertDialog(
                onDismissRequest = onCloseWalletDetail,
                title = { Text(walletTransactionTitle(walletUiState.selectedWalletItem)) },
                text = { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                confirmButton = { TextButton(onClick = onCloseWalletDetail) { Text("Cancel") } }
            )
        }
        walletUiState.selectedWalletItem != null -> {
            AlertDialog(
                onDismissRequest = onCloseWalletDetail,
                title = { Text(walletTransactionTitle(walletUiState.selectedWalletItem)) },
                text = {
                    if (walletUiState.detailError != null) Text(walletUiState.detailError, color = MaterialTheme.colorScheme.error)
                    else WalletTransactionDetailCard(walletUiState.selectedWalletItem, walletUiState.selectedWithdrawal)
                },
                confirmButton = { Button(onClick = onCloseWalletDetail) { Text("Okay") } }
            )
        }
    }

    LazyColumn(Modifier.fillMaxSize().widthIn(max = 1000.dp).padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Wallet", style = MaterialTheme.typography.headlineSmall)
            Text("Balance, earnings and wallet activity", color = AppColors.TextSecondary)
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Primary)) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Available balance", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f), modifier = Modifier.weight(1f))
                        IconButton(onClick = onRefreshBalance, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh balance", tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                    Text(if (loading) "Loading…" else "₹${formatMoney(wallet?.availableBalance ?: BigDecimal.ZERO)}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1, softWrap = false)
                    if (!loading) {
                        Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Total ₹${formatMoney(wallet?.balance ?: BigDecimal.ZERO)}", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall)
                            Text("Reserved ₹${formatMoney(wallet?.reservedBalance ?: BigDecimal.ZERO)}", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if ((wallet?.reservedBalance ?: BigDecimal.ZERO) > BigDecimal.ZERO) {
                        Spacer(Modifier.height(6.dp))
                        Text("₹${formatMoney(wallet?.reservedBalance ?: BigDecimal.ZERO)} reserved in pending transactions", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onAddMoney, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimary, contentColor = AppColors.Primary), modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add Money", maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(onClick = { onClearWithdrawMessage(); showWithdraw = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary), modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                            Icon(Icons.Default.Send, null); Spacer(Modifier.width(6.dp)); Text("Withdraw to UPI", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), onClick = onViewRechargeHistory) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = AppColors.SurfaceWarm) {
                        Icon(Icons.Default.History, null, modifier = Modifier.padding(10.dp), tint = AppColors.PrimaryDark)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Recharge History", style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                }
            }
        }
        item { EarningsBlock(commission, commissionLoading, onRefreshCommission) }
        item { WithdrawalHistoryCard(walletUiState.withdrawals) }
        item {
            WalletActivityCard(
                state = walletUiState, onSelectFilter = onSelectWalletHistoryFilter,
                onSetToday = onSetWalletHistoryToday, onSetLast7 = onSetWalletHistoryLast7, onSetMonth = onSetWalletHistoryMonth, onSetCustom = { showFromPicker = true },
                onRefresh = onRefreshWalletHistory, onLoadMore = onLoadMoreWalletHistory, onOpenDetail = onOpenWalletDetail
            )
        }
    }
}

@Composable
private fun WithdrawalHistoryCard(items: List<WithdrawalHistoryItem>) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Withdrawal history", style = MaterialTheme.typography.titleLarge)
                    Text("Recent UPI payout requests and their current status.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.Send, "Withdrawals", tint = AppColors.TextSecondary)
            }
            if (items.isEmpty()) {
                Text("No withdrawal requests yet.", color = AppColors.TextSecondary)
            } else {
                items.take(10).forEach { item ->
                    val status = item.status.uppercase()
                    val statusColor = when (status) {
                        "SUCCESS" -> AppColors.Success
                        "FAILED", "REVERSED" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("₹" + formatMoney(item.amount) + " → " + item.upiId, fontWeight = FontWeight.SemiBold)
                                Text(item.provider.uppercase() + " • " + formatExactTimestamp(item.createdAt), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                Text(item.withdrawalId, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                item.failureReason?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            }
                            MpayStatusPill(status)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EarningsBlock(commission: RechargeCommissionSummaryResponse?, loading: Boolean, onRefresh: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Earnings", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh earnings") }
            }
            WalletEarningsPeriod("Today", commission?.daily, true)
            HorizontalDivider()
            WalletEarningsPeriod("This month", commission?.monthly, false)
        }
    }
}

@Composable
private fun WalletEarningsPeriod(title: String, period: com.recharge.client.core.model.CommissionPeriodSummary?, isToday: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(if (isToday) formatAsOf(period?.to) else formatPeriod(period?.from, period?.to), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Commission earned", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text("₹${formatMoney(period?.commission ?: BigDecimal.ZERO)}", style = MaterialTheme.typography.headlineSmall, color = AppColors.Success)
                Text("${period?.successfulRechargeCount ?: 0} successful recharges", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Recharge volume", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text("₹${formatMoney(period?.successfulRechargeAmount ?: BigDecimal.ZERO)}", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletActivityCard(
    state: WalletUiState, onSelectFilter: (WalletHistoryFilter) -> Unit, onSetToday: () -> Unit, onSetLast7: () -> Unit,
    onSetMonth: () -> Unit, onSetCustom: () -> Unit, onRefresh: () -> Unit, onLoadMore: () -> Unit, onOpenDetail: (WalletHistoryItem) -> Unit
) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFEAF8EE))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wallet history", style = MaterialTheme.typography.titleLarge)
                    Text(state.fromDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)) + " → " + state.toDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onRefresh, enabled = !state.refreshing) { Icon(Icons.Default.Refresh, "Refresh wallet history") }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WalletFilterChip(WalletHistoryFilter.ALL, state.filter, "All", onSelectFilter)
                WalletFilterChip(WalletHistoryFilter.RECHARGE, state.filter, "Recharge", onSelectFilter)
                WalletFilterChip(WalletHistoryFilter.ADD_MONEY, state.filter, "Add money", onSelectFilter)
                WalletFilterChip(WalletHistoryFilter.WITHDRAWN, state.filter, "Withdrawn", onSelectFilter)
                WalletFilterChip(WalletHistoryFilter.RENTAL, state.filter, "Rental", onSelectFilter)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                WalletDateButton(WalletDateFilter.TODAY, state.dateFilter, "Today", onSetToday, 0, 4)
                WalletDateButton(WalletDateFilter.LAST_7_DAYS, state.dateFilter, "7 days", onSetLast7, 1, 4)
                WalletDateButton(WalletDateFilter.THIS_MONTH, state.dateFilter, "Month", onSetMonth, 2, 4)
                WalletDateButton(WalletDateFilter.CUSTOM, state.dateFilter, "Custom", onSetCustom, 3, 4)
            }
            when {
                state.loadingHistory && state.items.isEmpty() -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(22.dp)) }
                state.items.isEmpty() -> MpayEmptyState(title = "No wallet activity", message = "There are no wallet transactions for the selected filters.")
                else -> {
                    state.items.take(5).forEach { item -> WalletHistoryRow(item, onOpenDetail) }
                    if (state.hasNext) TextButton(onClick = onLoadMore, enabled = !state.loadingHistory) { Text("Load more") }
                }
            }
            state.historyError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun WalletFilterChip(
    filter: WalletHistoryFilter,
    selected: WalletHistoryFilter,
    label: String,
    onSelect: (WalletHistoryFilter) -> Unit
) {
    FilterChip(
        selected = selected == filter,
        onClick = { onSelect(filter) },
        label = { Text(label, maxLines = 1, softWrap = false) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleChoiceSegmentedButtonRowScope.WalletDateButton(filter: WalletDateFilter, selected: WalletDateFilter, label: String, onSelect: () -> Unit, index: Int, count: Int) {
    SegmentedButton(selected = selected == filter, onClick = onSelect, shape = SegmentedButtonDefaults.itemShape(index, count)) { Text(label, maxLines = 1, softWrap = false) }
}

@Composable
private fun WalletHistoryRow(item: WalletHistoryItem, onClick: (WalletHistoryItem) -> Unit) {
    val isAdd = item.referenceType.equals("ADD_MONEY", true)
    val isRecharge = item.referenceType.equals("RECHARGE", true)
    val isWithdraw = item.referenceType.equals("WITHDRAWAL", true) || item.type.equals("WITHDRAW", true)
    val isRental = item.referenceType.equals("RENTAL_PAYMENT", true) || item.referenceType.equals("RENTAL_REFUND", true)
    val isCredit = item.type.equals("CREDIT", true)
    val label = when {
        item.referenceType.equals("RENTAL_REFUND", true) -> "Car rental refund"
        isRental -> "Car rental payment"
        isRecharge -> "Recharge"
        isAdd -> "Added money"
        isWithdraw -> "Withdrawn money"
        else -> item.description ?: item.type
    }
    val amountColor = when {
        isRental && isCredit -> Color(0xFF16A34A)
        isRental && !isCredit -> Color(0xFFDC2626)
        isAdd || isCredit -> Color(0xFF16A34A)
        isRecharge || isWithdraw -> Color(0xFFDC2626)
        else -> AppColors.TextPrimary
    }
    val signedPrefix = if (isCredit) "+" else "-"
    val operator = if (isRecharge) item.operator else null
    Surface(Modifier.fillMaxWidth().clickable { onClick(item) }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isRecharge) {
                val op = operator.orEmpty()
                Surface(shape = RoundedCornerShape(12.dp), color = operatorColor(op).copy(alpha = .12f)) { Text(op.firstOrNull()?.uppercase() ?: "R", modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = operatorColor(op), fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                if (isRental) Text(item.referenceId ?: "Booking reference", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                if (isRecharge) Text("${operator ?: "Operator"} • ${item.referenceId.orEmpty()}", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text(formatExactTimestamp(item.createdAt), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text(signedPrefix + "₹${formatMoney(item.amount)}", color = amountColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WalletTransactionDetailCard(item: WalletHistoryItem, withdrawal: WithdrawalHistoryItem? = null) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    val isAdd = item.referenceType.equals("ADD_MONEY", true)
    val isRecharge = item.referenceType.equals("RECHARGE", true)
    val isWithdraw = item.referenceType.equals("WITHDRAWAL", true) || item.type.equals("WITHDRAW", true)
    val isRental = item.referenceType.equals("RENTAL_PAYMENT", true) || item.referenceType.equals("RENTAL_REFUND", true)
    val isCredit = item.type.equals("CREDIT", true)
    val title = when {
        item.referenceType.equals("RENTAL_REFUND", true) -> "Car rental refund"
        isRental -> "Car rental payment"
        isAdd -> "Added money"
        isWithdraw -> "Withdrawn money"
        else -> item.referenceType ?: item.type
    }
    val copyText = buildString {
        appendLine("Wallet transaction")
        appendLine("Type: $title")
        appendLine("Amount: ${(if (isCredit) "+" else "-")}₹${formatMoney(item.amount)}")
        appendLine("Status: ${item.status}")
        appendLine("Reference type: ${item.referenceType ?: "—"}")
        appendLine("Reference ID: ${item.referenceId ?: "—"}")
        appendLine("External reference: ${item.externalRef}")
        item.description?.let { appendLine("Description: $it") }
        appendLine("Date & time: ${formatExactTimestamp(item.createdAt)}")
    }
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${if (isCredit) "+" else "-"}₹${formatMoney(item.amount)}", style = MaterialTheme.typography.headlineSmall, color = if (isCredit) Color(0xFF16A34A) else Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(copyText.trimEnd())); copied = true }) { Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, if (copied) "Copied" else "Copy details", tint = if (copied) AppColors.Success else MaterialTheme.colorScheme.primary) }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Status", color = AppColors.TextSecondary)
                MpayStatusPill(item.status)
            }
            Text("Reference: ${item.referenceId ?: "—"}")
            if (item.mobileNumber != null) Text("Mobile: ${item.mobileNumber}")
            if (item.operator != null) Text("Operator: ${operatorLabel(item.operator)}")
            Text("External reference: ${item.externalRef}")
            item.provider?.takeIf { it.isNotBlank() }?.let {
                Text("Provider: ${it.uppercase()}", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            item.description?.let { Text(it, color = AppColors.TextSecondary) }
            Text(formatExactTimestamp(item.createdAt), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (isWithdraw) Text("UPI ID: ${withdrawal?.upiId ?: "—"}", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (isRecharge) Text("Recharge transaction: ${item.referenceId ?: "—"}", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WalletDatePicker(initial: LocalDate, onSelected: (LocalDate) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        val today = Calendar.getInstance()
        val cal = Calendar.getInstance().apply { set(initial.year, initial.monthValue - 1, initial.dayOfMonth) }
        val dialog = DatePickerDialog(context, { _, year, month, day ->
            val selected = LocalDate.of(year, month + 1, day)
            if (!selected.isAfter(LocalDate.now())) onSelected(selected)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        dialog.datePicker.maxDate = today.timeInMillis
        dialog.show()
        onDispose { dialog.dismiss() }
    }
}

private fun walletTransactionTitle(item: WalletHistoryItem) = when {
    item.referenceType.equals("ADD_MONEY", true) -> "Added money details"
    item.referenceType.equals("WITHDRAWAL", true) || item.type.equals("WITHDRAW", true) -> "Withdrawal details"
    else -> "Wallet transaction details"
}
