package com.recharge.client.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import com.recharge.client.core.theme.AppColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryPagination(
    page: Int,
    totalItems: Long,
    totalPages: Int,
    pageSize: Int,
    onPageSizeChange: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalItems <= 10L) return

    val safeTotalPages = totalPages.coerceAtLeast(1)
    val currentPage = (page + 1).coerceIn(1, safeTotalPages)
    val first = (page.toLong() * pageSize + 1).coerceAtMost(totalItems)
    val last = minOf(totalItems, (page.toLong() + 1L) * pageSize)

    Column(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Showing $first–$last of $totalItems",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SingleChoiceSegmentedButtonRow {
                listOf(10, 20, 50).forEachIndexed { index, size ->
                    SegmentedButton(
                        selected = pageSize == size,
                        onClick = { if (pageSize != size) onPageSizeChange(size) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                    ) {
                        Text(size.toString())
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = page > 0) {
                    Icon(Icons.Default.ChevronLeft, "Previous page")
                }
                Text("$currentPage / $safeTotalPages", style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = onNext, enabled = page + 1 < safeTotalPages) {
                    Icon(Icons.Default.ChevronRight, "Next page")
                }
            }
        }
    }
}
