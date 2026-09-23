package com.recharge.client.features.recharge

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recharge.client.core.model.RechargeHistoryItem
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.ui.formatExactTimestamp
import com.recharge.client.core.ui.formatMoney
import com.recharge.client.core.viewmodel.HistoryFilter
import com.recharge.client.core.viewmodel.RechargeHistoryUiState
import java.time.LocalDate
import java.util.Calendar
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RechargeHistoryScreen(state: RechargeHistoryUiState, onFilterToday: () -> Unit, onFilterLast7: () -> Unit, onFilterMonth: () -> Unit, onFilterCustom: (LocalDate, LocalDate) -> Unit, onRefresh: () -> Unit, onLoadMore: () -> Unit, onBack: () -> Unit) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var pendingFrom by remember { mutableStateOf<LocalDate?>(null) }

    if (showFromPicker) {
        FutureSafeDatePicker(state.fromDate) { date -> pendingFrom = date; showFromPicker = false; showToPicker = true }
    }
    if (showToPicker) {
        FutureSafeDatePicker(state.toDate.coerceAtLeast(pendingFrom ?: state.toDate)) { date ->
            val from = (pendingFrom ?: date).coerceAtMost(date)
            onFilterCustom(from, date)
            pendingFrom = null
            showToPicker = false
        }
    }

    Column(Modifier.fillMaxSize().widthIn(max = 1000.dp).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back", maxLines = 1, softWrap = false) }
            Text("Recharge history", style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
            IconButton(onClick = onRefresh, enabled = !state.refreshing) { Icon(Icons.Default.Refresh, "Refresh history") }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            FilterButton(HistoryFilter.TODAY, state.filter, "Today", onFilterToday)
            FilterButton(HistoryFilter.LAST_7_DAYS, state.filter, "7 days", onFilterLast7)
            FilterButton(HistoryFilter.THIS_MONTH, state.filter, "Month", onFilterMonth)
            FilterButton(HistoryFilter.CUSTOM, state.filter, "Custom", { showFromPicker = true })
        }
        Text(state.fromDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)) + " → " + state.toDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 6.dp)) }

        if (state.loading && state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No recharge transactions for this period.", color = AppColors.TextSecondary) }
            return
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(state.items, key = { _, item -> item.transactionId }) { index, item ->
                RechargeHistoryCard(item)
                if (index == state.items.lastIndex && state.hasNext) LaunchedLoadMore(onLoadMore)
            }
            if (state.loadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SingleChoiceSegmentedButtonRowScope.FilterButton(filter: HistoryFilter, selected: HistoryFilter, label: String, onClick: () -> Unit) {
    SegmentedButton(selected = selected == filter, onClick = onClick, shape = SegmentedButtonDefaults.itemShape(index = filter.ordinal, count = 4)) { Text(label, maxLines = 1, softWrap = false) }
}

@Composable
private fun FutureSafeDatePicker(initial: LocalDate, onSelected: (LocalDate) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        val today = Calendar.getInstance()
        val cal = Calendar.getInstance().apply { set(initial.year, initial.monthValue - 1, initial.dayOfMonth) }
        val dialog = DatePickerDialog(context, { _, year, month, day ->
            val selected = LocalDate.of(year, month + 1, day)
            if (!selected.isAfter(LocalDate.now())) onSelected(selected)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        dialog.datePicker.maxDate = today.timeInMillis
        dialog.setOnCancelListener { }
        dialog.show()
        onDispose { dialog.dismiss() }
    }
}

@Composable private fun LaunchedLoadMore(onLoadMore: () -> Unit) { LaunchedEffect(Unit) { onLoadMore() } }

