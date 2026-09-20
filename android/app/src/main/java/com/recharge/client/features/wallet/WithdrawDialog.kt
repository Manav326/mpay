package com.recharge.client.features.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.recharge.client.core.viewmodel.WalletUiState

@Composable
fun WithdrawDialog(state: WalletUiState, onDismiss: () -> Unit, onWithdraw: (String, String) -> Unit, onClearMessage: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    val busy = state.withdrawing
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Withdraw to UPI") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("This is a mocked UPI withdrawal. Your wallet balance is updated immediately.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(amount, { if (it.length <= 10 && it.all { c -> c.isDigit() || c == '.' }) amount = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Amount (INR)") }, prefix = { Text("₹") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !busy)
                OutlinedTextField(upiId, { if (it.length <= 120) upiId = it }, modifier = Modifier.fillMaxWidth(), label = { Text("UPI ID") }, placeholder = { Text("name@upi") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), enabled = !busy)
                state.withdrawError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.withdrawSuccess?.let { Text(it, color = com.recharge.client.core.theme.AppColors.Success) }
            }
        },
        confirmButton = {
            if (state.withdrawSuccess == null) {
                Button(onClick = { onWithdraw(amount, upiId) }, enabled = !busy) { Text(if (busy) "Processing…" else "Withdraw") }
            }
        },
        dismissButton = { TextButton(onClick = { onClearMessage(); onDismiss() }, enabled = !busy) { Text("Close") } }
    )
}
