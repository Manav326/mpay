package com.recharge.client.features.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.recharge.client.core.ui.MpayProviderSelector
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.recharge.client.core.viewmodel.PaymentUiState

@Composable
fun AddMoneyDialog(
    paymentState: PaymentUiState,
    onDismiss: () -> Unit,
    onCreateOrder: (String, String) -> Unit,
    onClearMessage: () -> Unit
) {
    var amount by remember { mutableStateOf("100") }
    var provider by remember { mutableStateOf("mock") }

    val busy = paymentState is PaymentUiState.CreatingOrder || paymentState is PaymentUiState.Verifying
    val error = (paymentState as? PaymentUiState.Error)?.message
    val success = (paymentState as? PaymentUiState.Success)?.message

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Add money") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Choose how to fund the wallet. Mock is for development/testing; Razorpay and PayU use their configured test gateways.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                MpayProviderSelector(provider, !busy) { provider = it }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { input ->
                        if (input.length <= 10 && input.all { it.isDigit() || it == '.' }) amount = input
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    label = { Text("Amount (INR)") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(8.dp))
                Text("Minimum ₹1 · Maximum ₹50,000", style = MaterialTheme.typography.bodySmall)
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                success?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = com.recharge.client.core.theme.AppColors.Success)
                }
            }
        },
        confirmButton = {
            if (success == null) {
                Button(
                    onClick = { onCreateOrder(amount, provider) },
                    enabled = !busy
                ) {
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (paymentState is PaymentUiState.CreatingOrder) "Preparing…" else "Verifying…")
                    } else {
                        Text("Continue")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onClearMessage()
                onDismiss()
            }, enabled = !busy) {
                Text("Close")
            }
        }
    )
}
