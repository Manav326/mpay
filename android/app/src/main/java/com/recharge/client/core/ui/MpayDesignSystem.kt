package com.recharge.client.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.recharge.client.core.theme.AppColors
import java.math.BigDecimal

fun statusColor(status: String): Color = when (status.uppercase().replace('-', '_')) {
    "SUCCESS", "COMPLETED", "CONFIRMED", "APPROVED", "VERIFIED", "PAID", "REFUNDED", "ACTIVE" -> AppColors.Success
    "FAILED", "CANCELLED", "REJECTED", "REVERSED", "EXPIRED" -> AppColors.Error
    "PENDING", "PROCESSING" -> AppColors.Warning
    "IN_PROGRESS" -> AppColors.Info
    else -> AppColors.TextSecondary
}

@Composable
fun MpayStatusPill(
    status: String,
    modifier: Modifier = Modifier
) {
    val normalized = status.replace('_', ' ').uppercase()
    val tint = statusColor(status)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = tint.copy(alpha = .10f)
    ) {
        Text(
            normalized,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun MpayPageHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
        }
        trailing?.invoke()
    }
}

@Composable
fun MpayEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = { Icon(Icons.Default.Info, null, tint = AppColors.Primary, modifier = Modifier.size(28.dp)) },
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceWarm.copy(alpha = .62f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = AppColors.SurfaceWarm) {
                Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(message, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction, shape = RoundedCornerShape(12.dp)) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun MpayFinancialAmount(
    amount: BigDecimal,
    credit: Boolean,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    val color = if (credit) AppColors.Success else AppColors.Error
    Text(
        (if (credit) "+" else "-") + "₹" + formatMoney(amount),
        modifier = modifier,
        color = color,
        style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false
    )
}

@Composable
fun MpayProviderSelector(
    provider: String,
    enabled: Boolean,
    onChange: (String) -> Unit
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val providers = listOf("mock" to "Mock", "razorpay" to "Razorpay", "payu" to "PayU")
        providers.forEachIndexed { index, item ->
            SegmentedButton(
                selected = provider.equals(item.first, true),
                onClick = { onChange(item.first) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, providers.size)
            ) { Text(item.second, maxLines = 1) }
        }
    }
}
