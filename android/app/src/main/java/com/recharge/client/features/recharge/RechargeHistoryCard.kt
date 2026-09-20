package com.recharge.client.features.recharge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recharge.client.core.model.RechargeHistoryItem
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.ui.formatExactTimestamp
import com.recharge.client.core.ui.formatMoney
import kotlinx.coroutines.delay

@Composable
fun RechargeHistoryCard(item: RechargeHistoryItem) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    val status = item.status.uppercase()
    val statusColor = when (status) { "SUCCESS" -> AppColors.Success; "FAILED" -> AppColors.Error; else -> MaterialTheme.colorScheme.primary }
    val text = buildString {
        appendLine("Recharge history")
        appendLine("Amount: ₹${formatMoney(item.amount)}")
        appendLine("Wallet debit: ₹${formatMoney(item.walletDebitAmount)}")
        appendLine("Mobile: ${item.mobileNumber}")
        appendLine("Operator: ${operatorLabel(item.operator)}")
        appendLine("Circle: ${item.circle}")
        appendLine("Plan ID: ${item.planId}")
        item.planDescription?.takeIf { it.isNotBlank() }?.let { appendLine("Plan: $it") }
        item.planValidity?.takeIf { it.isNotBlank() }?.let { appendLine("Validity: $it") }
        appendLine("Transaction ID: ${item.transactionId}")
        appendLine("Client Request ID: ${item.clientRequestId}")
        item.providerReference?.takeIf { it.isNotBlank() }?.let { appendLine("Provider reference: $it") }
        item.providerOrderId?.takeIf { it.isNotBlank() }?.let { appendLine("Provider order ID: $it") }
        item.walletLedgerRef?.takeIf { it.isNotBlank() }?.let { appendLine("Wallet ledger reference: $it") }
        appendLine("Provider: ${item.provider}")
        appendLine("Status: $status")
        item.message?.takeIf { it.isNotBlank() }?.let { appendLine("Message: $it") }
        appendLine("Date & time: ${formatExactTimestamp(item.completedAt ?: item.createdAt)}")
        if (status == "SUCCESS") appendLine("Commission earned: ₹${formatMoney(item.clientCommission)}")
    }

    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("₹${formatMoney(item.amount)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${operatorLabel(item.operator)} • ${item.mobileNumber}", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    item.planDescription?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2) }
                    item.planValidity?.takeIf { it.isNotBlank() }?.let { Text(it, color = AppColors.PrimaryDark, style = MaterialTheme.typography.labelMedium) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status, color = statusColor, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { clipboard.setText(AnnotatedString(text.trimEnd())); copied = true }) {
                        Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, if (copied) "Copied" else "Copy all recharge data", tint = if (copied) AppColors.Success else MaterialTheme.colorScheme.primary)
                    }
                }
            }
            HorizontalDivider()
            Text("Wallet debit: ₹${formatMoney(item.walletDebitAmount)}", style = MaterialTheme.typography.bodyMedium)
            Text("Transaction ID: ${item.transactionId}", style = MaterialTheme.typography.bodySmall)
            Text("Reference: ${item.clientRequestId}", style = MaterialTheme.typography.bodySmall)
            item.providerReference?.takeIf { it.isNotBlank() }?.let { Text("Provider ref: $it", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
            Text(formatExactTimestamp(item.completedAt ?: item.createdAt), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (status == "SUCCESS") Text("Commission earned: ₹${formatMoney(item.clientCommission)}", color = AppColors.Success, fontWeight = FontWeight.Bold)
            item.message?.takeIf { it.isNotBlank() }?.let { Text(it, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

fun operatorLabel(operator: String): String = when (operator.uppercase()) {
    "AIRTEL" -> "Airtel"
    "JIO" -> "Jio"
    "VI", "VODAFONE", "VODAFONE IDEA" -> "Vodafone Idea (VI)"
    "BSNL" -> "BSNL"
    else -> operator.ifBlank { "Operator" }
}

fun operatorColor(operator: String): androidx.compose.ui.graphics.Color = when (operator.uppercase()) {
    "AIRTEL" -> androidx.compose.ui.graphics.Color(0xFFE11D48)
    "JIO" -> androidx.compose.ui.graphics.Color(0xFF2563EB)
    "VI", "VODAFONE", "VODAFONE IDEA" -> androidx.compose.ui.graphics.Color(0xFFE11D48)
    "BSNL" -> androidx.compose.ui.graphics.Color(0xFF0EA5E9)
    else -> androidx.compose.ui.graphics.Color(0xFF64748B)
}
