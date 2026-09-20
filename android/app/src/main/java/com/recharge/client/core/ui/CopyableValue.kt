package com.recharge.client.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.recharge.client.core.theme.AppColors
import kotlinx.coroutines.delay

@Composable
fun CopyableValue(label: String, value: String, modifier: Modifier = Modifier, allowCopy: Boolean = true) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(value.ifBlank { "—" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (allowCopy && value.isNotBlank()) {
                IconButton(onClick = { clipboard.setText(AnnotatedString(value)); copied = true }) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = if (copied) "Copied" else "Copy $label",
                        tint = if (copied) AppColors.Success else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
