package com.recharge.client.features.recharge

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recharge.client.core.model.RechargeHistoryItem
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.ui.HistoryPagination
import com.recharge.client.core.ui.MpayEmptyState
import com.recharge.client.core.ui.formatExactTimestamp
import com.recharge.client.core.viewmodel.HistoryFilter
import com.recharge.client.core.viewmodel.RechargeHistoryUiState
import java.time.LocalDate
import java.util.Calendar
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RechargeHistoryScreen(
    state: RechargeHistoryUiState,
    onFilterToday: () -> Unit,
    onFilterLast7: () -> Unit,
    onFilterMonth: () -> Unit,
    onFilterCustom: (LocalDate, LocalDate) -> Unit,
    onStatusFilter: (String) -> Unit,
    onPageSizeChange: (Int) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onLoadHistoryPdfAccess: () -> Unit,
    onRequestHistoryPdfAccess: (String) -> Unit,
    onDownloadHistoryPdf: ((ByteArray, String) -> Unit) -> Unit
) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var pendingFrom by remember { mutableStateOf<LocalDate?>(null) }
    var showPdfRequest by rememberSaveable { mutableStateOf(false) }
    var pdfReason by rememberSaveable { mutableStateOf("") }
    var pendingPdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val pdfWriter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        val bytes = pendingPdfBytes
        if (uri != null && bytes != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Unable to save PDF.")
                android.widget.Toast.makeText(context, "PDF saved successfully.", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                android.widget.Toast.makeText(context, it.message ?: "Unable to save PDF.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        pendingPdfBytes = null
    }

    LaunchedEffect(Unit) { onLoadHistoryPdfAccess() }

    if (showFromPicker) {
        FutureSafeDatePicker(state.fromDate) { date ->
            pendingFrom = date
            showFromPicker = false
            showToPicker = true
        }
    }
    if (showToPicker) {
        FutureSafeDatePicker(state.toDate.coerceAtLeast(pendingFrom ?: state.toDate)) { date ->
            val from = (pendingFrom ?: date).coerceAtMost(date)
            onFilterCustom(from, date)
            pendingFrom = null
            showToPicker = false
        }
    }

    if (showPdfRequest) {
        AlertDialog(
            onDismissRequest = { if (!state.historyPdfBusy) showPdfRequest = false },
            title = { Text("Request history PDF access") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("An administrator must approve downloadable statements for your account.")
                    OutlinedTextField(
                        value = pdfReason,
                        onValueChange = { if (it.length <= 1000) pdfReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Reason for request") },
                        placeholder = { Text("Explain why you need a downloadable recharge statement.") },
                        minLines = 4,
                        maxLines = 6,
                        supportingText = { Text("${pdfReason.length}/1000 · minimum 20 characters") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRequestHistoryPdfAccess(pdfReason.trim())
                        showPdfRequest = false
                    },
                    enabled = !state.historyPdfBusy && pdfReason.trim().length >= 20
                ) { Text("Submit request") }
            },
            dismissButton = {
                TextButton(onClick = { showPdfRequest = false }, enabled = !state.historyPdfBusy) { Text("Cancel") }
            }
        )
    }

    Column(
        Modifier.fillMaxSize().widthIn(max = 1000.dp).padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Back", maxLines = 1, softWrap = false) }
            Text("Recharge history", style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
            IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                Icon(Icons.Default.Refresh, "Refresh history")
            }
        }

        RechargeHistoryPdfAccessCard(
            state = state,
            onRequest = { showPdfRequest = true },
            onDownload = { onDownloadHistoryPdf { bytes, fileName ->
                pendingPdfBytes = bytes
                pdfWriter.launch(fileName)
            } }
        )

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            FilterButton(HistoryFilter.TODAY, state.filter, "Today", onFilterToday)
            FilterButton(HistoryFilter.LAST_7_DAYS, state.filter, "7 days", onFilterLast7)
            FilterButton(HistoryFilter.THIS_MONTH, state.filter, "Month", onFilterMonth)
            FilterButton(HistoryFilter.CUSTOM, state.filter, "Custom", { showFromPicker = true })
        }

        Text(
            state.fromDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)) +
                " → " +
                state.toDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)),
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "ALL" to "All",
                "SUCCESS" to "Success",
                "PENDING" to "Pending",
                "FAILED" to "Failed"
            ).forEach { (value, label) ->
                FilterChip(
                    selected = state.statusFilter == value,
                    onClick = { onStatusFilter(value) },
                    label = { Text(label, maxLines = 1, softWrap = false) },
                    modifier = Modifier.height(34.dp)
                )
            }
        }

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (state.loading && state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        if (!state.loading && state.items.isEmpty()) {
            MpayEmptyState(
                title = "No recharge transactions",
                message = "There are no recharge records for the selected period and status."
            )
            return
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.items, key = { it.transactionId }) { item ->
                RechargeHistoryCard(item)
            }
            if (state.loading) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp))
                    }
                }
            }
            item {
                HistoryPagination(
                    page = state.page,
                    totalItems = state.totalItems,
                    totalPages = state.totalPages,
                    pageSize = state.pageSize,
                    onPageSizeChange = onPageSizeChange,
                    onPrevious = onPreviousPage,
                    onNext = onNextPage
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleChoiceSegmentedButtonRowScope.FilterButton(
    filter: HistoryFilter,
    selected: HistoryFilter,
    label: String,
    onClick: () -> Unit
) {
    SegmentedButton(
        selected = selected == filter,
        onClick = onClick,
        shape = SegmentedButtonDefaults.itemShape(index = filter.ordinal, count = 4)
    ) {
        Text(label, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun FutureSafeDatePicker(initial: LocalDate, onSelected: (LocalDate) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        val today = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(initial.year, initial.monthValue - 1, initial.dayOfMonth)
        }
        val dialog = DatePickerDialog(
            context,
            { _, year, month, day ->
                val selected = LocalDate.of(year, month + 1, day)
                if (!selected.isAfter(LocalDate.now())) onSelected(selected)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.maxDate = today.timeInMillis
        dialog.show()
        onDispose { dialog.dismiss() }
    }
}


@Composable
private fun RechargeHistoryPdfAccessCard(
    state: RechargeHistoryUiState,
    onRequest: () -> Unit,
    onDownload: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recharge statement", style = MaterialTheme.typography.titleMedium)
            when (state.historyPdfAccessStatus) {
                "APPROVED" -> {
                    Text(
                        state.fromDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)) +
                            " → " +
                            state.toDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)) +
                            " · " + if (state.statusFilter == "ALL") "All statuses" else state.statusFilter,
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Up to 1 year or 5,000 records per statement.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onDownload, enabled = !state.historyPdfBusy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.historyPdfBusy) "Generating…" else "Download recharge PDF")
                    }
                }
                "PENDING" -> {
                    Text("Your request is under administrator review.", color = AppColors.TextSecondary)
                    state.historyPdfAccessRequestReason?.let { Text("Reason: " + it, style = MaterialTheme.typography.bodySmall) }
                }
                "REJECTED", "REVOKED" -> {
                    Text("PDF export is not currently enabled.", color = AppColors.TextSecondary)
                    state.historyPdfAccessReviewNote?.let { Text("Admin note: " + it, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = onRequest, enabled = !state.historyPdfBusy) { Text("Request access") }
                }
                else -> {
                    Text("Request administrator approval to download this recharge history as a statement.", color = AppColors.TextSecondary)
                    Button(onClick = onRequest, enabled = !state.historyPdfBusy) { Text("Request access") }
                }
            }
            state.historyPdfError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
