package com.recharge.client.features.recharge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.recharge.client.core.model.OperatorCheckResponse
import com.recharge.client.core.model.RechargePlan
import com.recharge.client.core.viewmodel.RechargeActionState
import com.recharge.client.core.viewmodel.RechargeUiState
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.ui.formatMoney
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun RechargeScreen(
    state: RechargeUiState,
    commissionRate: BigDecimal?,
    onMobileChange: (String) -> Unit,
    onRecipientNameChange: (String) -> Unit,
    onChooseContact: () -> Unit,
    onDetect: () -> Unit,
    onRefreshPlans: () -> Unit,
    onSelectPlan: (RechargePlan) -> Unit,
    onExecuteRecharge: () -> Unit,
    onGatewayPay: () -> Unit,
    onDone: () -> Unit,
    onAddMoney: () -> Unit,
    onRefreshWallet: () -> Unit,
    onExit: () -> Unit
) {
    var showConfirmation by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { onExit() } }
    LaunchedEffect(Unit) { onRefreshWallet() }
    LaunchedEffect(state.mobile, state.operator?.providerOrderId) {
        if (state.operator != null && state.plans.isNotEmpty()) onRefreshWallet()
    }
    LaunchedEffect(state.action) {
        if (state.action is RechargeActionState.Success || state.action is RechargeActionState.Pending || state.action is RechargeActionState.Failure) {
            showConfirmation = false
        }
    }

    if (showConfirmation && state.selectedPlan != null) {
        RechargeConfirmationDialog(
            state = state,
            commissionRate = commissionRate,
            onDismiss = { showConfirmation = false },
            onConfirm = { showConfirmation = false; onExecuteRecharge() },
            onGatewayPay = { showConfirmation = false; onGatewayPay() },
            onAddMoney = { showConfirmation = false; onAddMoney() }
        )
    }

    when (val action = state.action) {
        is RechargeActionState.Success -> RechargeResultDialog(
            title = "Recharge successful",
            body = "₹${formatMoney(action.response.amount)} recharge completed. Wallet charged ₹${formatMoney(action.response.walletDebitAmount)} after commission.",
            detail = listOfNotNull(state.recipientName.takeIf { it.isNotBlank() }, state.mobile, operatorLabel(state.operator?.operator.orEmpty())).joinToString(" • "),
            positive = true,
            onDismiss = onDone
        )
        is RechargeActionState.Pending -> RechargeResultDialog(
            title = "Recharge is processing",
            body = "Your recharge has been accepted and is still being processed.",
            detail = "Wallet debit reserved: ₹${formatMoney(action.response.walletDebitAmount)}",
            positive = false,
            onDismiss = onDone
        )
        is RechargeActionState.Failure -> RechargeResultDialog(
            title = "Recharge not completed",
            body = action.message,
            detail = "No permanent wallet debit is made for a failed recharge.",
            positive = false,
            onDismiss = onDone
        )
        else -> Unit
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Mobile recharge", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Recharge any supported prepaid number securely from your wallet.", color = AppColors.TextSecondary)
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceWarm)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Recharge number", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.mobile,
                        onValueChange = onMobileChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("10-digit mobile number") },
                        placeholder = { Text("e.g. 7070107483") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.SimCard, null) },
                        trailingIcon = {
                            IconButton(onClick = onChooseContact) {
                                Icon(Icons.Default.Contacts, contentDescription = "Choose number from phone")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.recipientName,
                        onValueChange = onRecipientNameChange,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        label = { Text("Recipient name (optional)") },
                        placeholder = { Text("Phonebook name") },
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = onDetect,
                        enabled = state.mobile.length == 10 && !state.detecting && !state.loadingPlans,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        if (state.detecting || state.loadingPlans) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.detecting) "Checking operator…" else "Loading offers…")
                        } else {
                            Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Check operator & offers")
                        }
                    }
                }
            }
        }
        state.error?.let { message ->
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        state.operator?.let { detected -> item { OperatorSummaryCard(detected, state.walletBalance, state.refreshingWallet, state.recipientName, onRefreshWallet) } }

        if (state.operator != null && state.plans.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Personalized offers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Offers returned for this number", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onRefreshPlans, enabled = !state.loadingPlans) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Refresh")
                    }
                }
            }
            items(state.plans, key = { it.id }) { plan ->
                RechargePlanCard(plan, state.selectedPlan?.id == plan.id, !state.executing) { onSelectPlan(plan) }
            }
        } else if (state.operator != null && state.loadingPlans) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }

        if (state.operator != null && state.plans.isNotEmpty()) {
            item {
                val selected = state.selectedPlan
                val balance = state.walletBalance ?: BigDecimal.ZERO
                val rate = commissionRate?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
                val commission = selected?.amount?.multiply(rate)?.divide(BigDecimal(100), 2, RoundingMode.HALF_UP) ?: BigDecimal.ZERO
                val walletDebit = selected?.amount?.subtract(commission)?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
                val insufficient = selected != null && balance < walletDebit
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), tonalElevation = 2.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Your wallet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(balance.let { "₹${formatMoney(it)}" }, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onRefreshWallet, enabled = !state.refreshingWallet, modifier = Modifier.align(Alignment.End)) { Text("Refresh") }
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        if (selected == null) {
                            Text("Select a plan to continue", color = AppColors.TextSecondary)
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("Selected plan", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
                                    Text("₹${formatMoney(selected.amount)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                }
                                if (insufficient) Text("Add money required", color = AppColors.Error, style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.height(10.dp))
                            RechargePriceBreakdown(selected.amount, commission, walletDebit)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { showConfirmation = true }, enabled = !state.executing, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Default.Wallet, null); Spacer(Modifier.width(8.dp)); Text("Choose payment method")
                            }
                            if (insufficient) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = onAddMoney, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                    Text("Add money to wallet")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OperatorSummaryCard(
    response: OperatorCheckResponse,
    balance: BigDecimal?,
    refreshing: Boolean,
    recipientName: String,
    onRefreshWallet: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).background(AppColors.SurfaceWarm, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    operatorInitial(response.operator),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.PrimaryDark
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(operatorLabel(response.operator), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${response.circle} • ${response.type ?: "Prepaid"}",
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                Text(
                    listOfNotNull(recipientName.takeIf { it.isNotBlank() }, response.mobileNumber).joinToString(" • "),
                    color = AppColors.PrimaryDark,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(shape = RoundedCornerShape(9.dp), color = ColorLightGreen) {
                    Row(
                        Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, "Detected", tint = AppColors.Success, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Detected", color = AppColors.Success, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    balance?.let { "₹${formatMoney(it)}" } ?: "Checking…",
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private val ColorLightGreen = androidx.compose.ui.graphics.Color(0xFFEAF8EE)

@Composable
private fun RechargePriceBreakdown(amount: BigDecimal, commission: BigDecimal, walletDebit: BigDecimal) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PriceRow("Recharge amount", "₹${formatMoney(amount)}", false)
            PriceRow("Commission", "₹${formatMoney(commission)}", true)
            HorizontalDivider()
            PriceRow("Wallet will be debited", "₹${formatMoney(walletDebit)}", false, bold = true)
        }
    }
}

@Composable
private fun PriceRow(label: String, value: String, green: Boolean, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AppColors.TextSecondary)
        Text(value, color = if (green) AppColors.Success else AppColors.TextPrimary, fontWeight = if (bold || green) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun RechargePlanCard(plan: RechargePlan, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) AppColors.Primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        color = if (selected) AppColors.SurfaceWarm else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 2.dp else 1.dp
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("₹" + formatMoney(plan.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    plan.validity?.takeIf { it.isNotBlank() }?.let {
                        Surface(shape = RoundedCornerShape(7.dp), color = AppColors.Primary.copy(alpha = .08f)) {
                            Text(it, color = AppColors.PrimaryDark, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                    }
                }
                Text(plan.description?.takeIf { it.isNotBlank() } ?: "Recharge offer", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2)
            }
            if (selected) {
                Surface(shape = CircleShape, color = ColorLightGreen) {
                    Icon(Icons.Default.CheckCircle, "Selected", tint = AppColors.Success, modifier = Modifier.padding(2.dp).size(21.dp))
                }
            }
        }
    }
}


@Composable
private fun RechargeConfirmationDialog(
    state: RechargeUiState,
    commissionRate: BigDecimal?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onGatewayPay: () -> Unit,
    onAddMoney: () -> Unit
) {
    val plan = state.selectedPlan ?: return
    val balance = state.walletBalance ?: BigDecimal.ZERO
    val rate = commissionRate?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
    val commission = plan.amount.multiply(rate).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    val walletDebit = plan.amount.subtract(commission).max(BigDecimal.ZERO)
    val insufficient = balance < walletDebit
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm recharge") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                ConfirmRow("Mobile", state.mobile)
                state.recipientName.takeIf { it.isNotBlank() }?.let { ConfirmRow("Name", it) }
                ConfirmRow("Operator", operatorLabel(state.operator?.operator.orEmpty()))
                ConfirmRow("Circle", state.operator?.circle.orEmpty())
                ConfirmRow("Plan", plan.validity ?: "Recharge offer")
                HorizontalDivider()
                ConfirmRow("Recharge amount", "₹${formatMoney(plan.amount)}", emphasized = true)
                ConfirmRow("Commission", "₹${formatMoney(commission)}", valueColor = AppColors.Success, emphasized = true)
                ConfirmRow("Wallet will be debited", "₹${formatMoney(walletDebit)}", emphasized = true)
                ConfirmRow("Wallet after", "₹${formatMoney(balance.subtract(walletDebit).max(BigDecimal.ZERO))}")
                if (insufficient) Text("Your available wallet balance is not sufficient.", color = AppColors.Error)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onGatewayPay) {
                    Text("UPI / Bank")
                }
                Button(onClick = if (insufficient) onAddMoney else onConfirm) {
                    Text(if (insufficient) "Add money" else "Use wallet")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ConfirmRow(label: String, value: String, emphasized: Boolean = false, valueColor: androidx.compose.ui.graphics.Color = AppColors.TextPrimary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AppColors.TextSecondary)
        Spacer(Modifier.width(12.dp))
        Text(value, color = valueColor, fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun RechargeResultDialog(title: String, body: String, detail: String, positive: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (positive) Icons.Default.CheckCircle else Icons.Default.Refresh, null, tint = if (positive) AppColors.Success else AppColors.Primary) },
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(body); Text(detail, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

private fun operatorInitial(operator: String): String = when (operator.uppercase()) {
    "AIRTEL" -> "A"
    "JIO" -> "J"
    "VI", "VODAFONE", "VODAFONE IDEA" -> "V"
    "BSNL" -> "B"
    else -> operator.firstOrNull()?.uppercase() ?: "?"
}
