package com.recharge.client.features.rental

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.recharge.client.core.model.*
import com.recharge.client.core.network.ApiConfig
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.viewmodel.RentalUiState


private val rentalDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
private val rentalDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val rentalDateDisplayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
private val rentalBookingDisplayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mma", Locale.ENGLISH)
private val rentalDateTimeDisplayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

private val rentalOffMarketReasons = listOf(
    "SERVICE_MAINTENANCE" to "Service / maintenance",
    "PRIVATE_USE" to "Private use",
    "DRIVER_UNAVAILABLE" to "Driver unavailable",
    "LEGAL_DOCUMENTATION" to "Documentation / compliance",
    "PERSONAL_REASON" to "Personal reason",
    "OTHER" to "Other"
)

private fun formatRentalDate(value: String): String =
    runCatching { LocalDate.parse(value, rentalDateFormatter).format(rentalDateDisplayFormatter) }.getOrElse { value }

private fun formatRentalBookingDateTime(value: String): String =
    runCatching { LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).format(rentalBookingDisplayFormatter) }
        .getOrElse { value }

private fun rentalPhotoSlots(imageUrl: String?): List<String> {
    val values = imageUrl.orEmpty()
        .replace("\\n", "|")
        .split("|")
        .take(4)
        .map { it.trim() }
    return List(4) { index -> values.getOrNull(index).orEmpty() }
}

private fun rentalPhotoDisplayUrl(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("content://")) {
        return trimmed
    }
    return ApiConfig.BASE_URL.trimEnd('/') + "/" + trimmed.trimStart('/')
}

private fun rentalStatusColor(status: String): Color = when (status.uppercase()) {
    "CONFIRMED", "COMPLETED", "REFUNDED", "PAID" -> AppColors.Success
    "CANCELLED", "REJECTED", "FAILED", "EXPIRED" -> AppColors.Error
    "PENDING", "PROCESSING" -> Color(0xFFD97706)
    "IN_PROGRESS", "ACTIVE" -> Color(0xFF2563EB)
    else -> AppColors.TextSecondary
}

private fun rentalBookingShareText(booking: RentalBookingResponse): String = listOf(
    "mPay Car Rental Booking",
    "Booking ID: " + booking.bookingId,
    "Car: " + booking.carName,
    "From: " + booking.pickup,
    "To: " + booking.drop,
    "Start: " + booking.startDate,
    "End: " + booking.endDate,
    "Amount: ₹" + booking.total.setScale(2).toPlainString(),
    "Status: " + booking.status.uppercase()
).joinToString(" | ")

@Composable
private fun VendorField(
    label: String,
    value: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun CompactFieldRow(
    leftLabel: String,
    leftValue: String,
    onLeftChange: (String) -> Unit,
    rightLabel: String,
    rightValue: String,
    onRightChange: (String) -> Unit,
    enabled: Boolean = true
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VendorField(leftLabel, leftValue, enabled, Modifier.weight(1f), onLeftChange)
        VendorField(rightLabel, rightValue, enabled, Modifier.weight(1f), onRightChange)
    }
}

private fun showDateTimePicker(context: Context, current: String?, onSelected: (String) -> Unit) {
    val initial = runCatching {
        LocalDateTime.parse(current.orEmpty(), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }.getOrElse { LocalDateTime.now().withSecond(0).withNano(0) }

    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    onSelected(LocalDateTime.of(year, month + 1, day, hour, minute).format(rentalDateTimeFormatter))
                },
                initial.hour,
                initial.minute,
                false
            ).show()
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth
    ).show()
}

@Composable
private fun RentalDateTimeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val display = runCatching {
        LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).format(rentalDateTimeDisplayFormatter)
    }.getOrElse { "Select date & time" }

    OutlinedButton(
        onClick = { showDateTimePicker(context, value, onValueChange) },
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
            Text(display, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RentalDateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val display = if (value.isBlank()) "Select date" else formatRentalDate(value)
    OutlinedButton(
        onClick = {
            val initial = runCatching { LocalDate.parse(value, rentalDateFormatter) }.getOrElse { LocalDate.now() }
            DatePickerDialog(
                context,
                { _, year, month, day -> onValueChange(LocalDate.of(year, month + 1, day).format(rentalDateFormatter)) },
                initial.year,
                initial.monthValue - 1,
                initial.dayOfMonth
            ).show()
        },
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(11.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
            Text(display, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun VehicleOffMarketDialog(
    car: RentalCarResponse,
    startDate: String,
    endDate: String,
    reasonCode: String,
    reasonNote: String,
    saving: Boolean,
    error: String?,
    onStartDate: (String) -> Unit,
    onEndDate: (String) -> Unit,
    onReason: (String) -> Unit,
    onNote: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val selectedReason = rentalOffMarketReasons.firstOrNull { it.first == reasonCode }?.second ?: "Select a reason"
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Take " + car.name + " off market") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "Customers will not see this vehicle for the selected period. The reason is stored for admin visibility.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RentalDateField("From", startDate, onStartDate, Modifier.weight(1f))
                    RentalDateField("To", endDate, onEndDate, Modifier.weight(1f))
                }
                Box {
                    OutlinedButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(11.dp)
                    ) {
                        Text("Reason: " + selectedReason, modifier = Modifier.weight(1f))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        rentalOffMarketReasons.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { onReason(code); menuOpen = false }
                            )
                        }
                    }
                }
                VendorField("Optional note for admin", reasonNote, onValueChange = onNote)
                error?.let { Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = !saving && startDate.isNotBlank() && endDate.isNotBlank()) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Keep off market")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } }
    )
}

@Composable
private fun VehicleCalendarDialog(
    calendar: RentalVehicleCalendarResponse?,
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    val daysByDate = calendar?.days?.associateBy { it.date }.orEmpty()
    val leading = month.atDay(1).dayOfWeek.value - 1
    val rawCells: List<String?> = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it).toString() }
    val weeks = rawCells.chunked(7).map { week -> week + List(7 - week.size) { null } }
    val today = LocalDate.now()

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(calendar?.carName ?: "Vehicle calendar", style = MaterialTheme.typography.titleMedium)
                        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = onPrevious) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                    IconButton(onClick = onNext) { Text("›", style = MaterialTheme.typography.headlineSmall) }
                }
                if (calendar == null) {
                    Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                            Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                        }
                    }
                    weeks.forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                if (date == null) {
                                    Box(Modifier.weight(1f).height(34.dp))
                                } else {
                                    val parsedDate = LocalDate.parse(date)
                                    val day = daysByDate[date]
                                    val isPast = parsedDate.isBefore(today)
                                    val background = when {
                                        isPast -> Color(0xFFF1F3F5)
                                        day?.status == "BOOKED" -> Color(0xFFFEE2E2)
                                        day?.status == "OFF_MARKET" -> Color(0xFFFEF3C7)
                                        else -> Color(0xFFDCFCE7)
                                    }
                                    val foreground = when {
                                        isPast -> AppColors.TextSecondary
                                        day?.status == "BOOKED" -> AppColors.Error
                                        day?.status == "OFF_MARKET" -> Color(0xFF9A6408)
                                        else -> AppColors.Success
                                    }
                                    Surface(
                                        Modifier.weight(1f).height(34.dp).padding(1.dp),
                                        shape = RoundedCornerShape(7.dp),
                                        color = background
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(parsedDate.dayOfMonth.toString(), color = foreground, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Booked", color = AppColors.Error, style = MaterialTheme.typography.labelSmall)
                    Text("Off market", color = Color(0xFF9A6408), style = MaterialTheme.typography.labelSmall)
                    Text("Available", color = AppColors.Success, style = MaterialTheme.typography.labelSmall)
                    Text("Past", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun RentalVendorOnboardingScreen(
    state: RentalUiState,
    onSubmit: (RentalVendorOnboardingRequest, () -> Unit) -> Unit,
    onBack: () -> Unit,
    onAddVehicle: () -> Unit,
    onRefreshVehicles: () -> Unit,
    onRefreshPayouts: () -> Unit,
    onAddVehicleWithCar: (RentalCarResponse) -> Unit = {},
    onLoadVehicleAvailability: (String) -> Unit = {},
    onTakeVehicleOffMarket: (String, RentalVehicleUnavailabilityRequest, () -> Unit) -> Unit = { _, _, done -> done() },
    onRestoreVehicleToMarket: (String, String, () -> Unit) -> Unit = { _, _, done -> done() },
    onLoadVehicleCalendar: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    var fullName by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.fullName.orEmpty()) }
    var businessName by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.businessName.orEmpty()) }
    var address by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.address.orEmpty()) }
    var city by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.city.orEmpty()) }
    var stateName by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.state.orEmpty()) }
    var pin by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.pinCode.orEmpty()) }
    var pan by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.panNumber.orEmpty()) }
    var upi by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.payoutUpiId.orEmpty()) }

    var offMarketCar by remember { mutableStateOf<RentalCarResponse?>(null) }
    var offMarketStart by remember { mutableStateOf(LocalDate.now().plusDays(1).format(rentalDateFormatter)) }
    var offMarketEnd by remember { mutableStateOf(LocalDate.now().plusDays(1).format(rentalDateFormatter)) }
    var offMarketReason by remember { mutableStateOf("SERVICE_MAINTENANCE") }
    var offMarketNote by remember { mutableStateOf("") }
    var calendarCarId by remember { mutableStateOf<String?>(null) }
    var calendarMonth by remember { mutableStateOf(YearMonth.now()) }
    var detailsCar by remember { mutableStateOf<RentalCarResponse?>(null) }

    if (state.vendor?.status?.uppercase() == "VERIFIED") {
        LaunchedEffect(state.vendor?.vendorId) { onRefreshVehicles(); onRefreshPayouts() }
        LaunchedEffect(state.vendorCars.map { it.id }) { state.vendorCars.forEach { onLoadVehicleAvailability(it.id) } }
        LaunchedEffect(calendarCarId, calendarMonth) {
            calendarCarId?.let { onLoadVehicleCalendar(it, calendarMonth.year, calendarMonth.monthValue) }
        }

        offMarketCar?.let { car ->
            VehicleOffMarketDialog(
                car = car,
                startDate = offMarketStart,
                endDate = offMarketEnd,
                reasonCode = offMarketReason,
                reasonNote = offMarketNote,
                saving = state.saving,
                error = state.error,
                onStartDate = { offMarketStart = it },
                onEndDate = { offMarketEnd = it },
                onReason = { offMarketReason = it },
                onNote = { offMarketNote = it },
                onDismiss = { if (!state.saving) offMarketCar = null },
                onSubmit = {
                    onTakeVehicleOffMarket(
                        car.id,
                        RentalVehicleUnavailabilityRequest(
                            reasonCode = offMarketReason,
                            reasonNote = offMarketNote.ifBlank { null },
                            startDate = offMarketStart,
                            endDate = offMarketEnd
                        )
                    ) {
                        offMarketCar = null
                        offMarketNote = ""
                    }
                }
            )
        }

        detailsCar?.let { car ->
            RentalVehicleDetailsDialog(car = car, onDismiss = { detailsCar = null })
        }

        calendarCarId?.let { carId ->
            VehicleCalendarDialog(
                calendar = state.vehicleCalendar?.takeIf {
                    it.carId == carId && it.year == calendarMonth.year && it.month == calendarMonth.monthValue
                },
                month = calendarMonth,
                onPrevious = { calendarMonth = calendarMonth.minusMonths(1) },
                onNext = { calendarMonth = calendarMonth.plusMonths(1) },
                onDismiss = { calendarCarId = null }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text("Vendor dashboard", style = MaterialTheme.typography.headlineSmall)
                        Text("Manage your fleet, earnings and availability", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            state.vendor?.let { v ->
                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Vendor profile", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Surface(shape = RoundedCornerShape(18.dp), color = AppColors.Success.copy(alpha = .12f)) {
                                    Text("VERIFIED", color = AppColors.Success, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(v.fullName ?: "—", fontWeight = FontWeight.SemiBold)
                                    v.businessName?.takeIf { it.isNotBlank() }?.let { Text(it, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(v.vehicleCount.toString(), fontWeight = FontWeight.Bold)
                                    Text("Vehicles", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text(listOfNotBlank(v.city, v.state).joinToString(", ").ifBlank { "Location unavailable" }, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                val gross = state.payouts.fold(BigDecimal.ZERO) { total, payout -> total + payout.grossAmount }
                val fees = state.payouts.fold(BigDecimal.ZERO) { total, payout -> total + payout.platformFeeAmount }
                val net = state.payouts.filter { it.status == "PAID" }.fold(BigDecimal.ZERO) { total, payout -> total + payout.vendorNetAmount }
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Rental earnings", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            EarningsMetric("Gross", gross)
                            EarningsMetric("Platform fee", fees, alignEnd = true)
                            EarningsMetric("Paid to you", net, alignEnd = true, emphasize = true)
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("My vehicles", style = MaterialTheme.typography.titleMedium)
                        Text("See exactly when each vehicle is booked, off market or available.", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onRefreshVehicles, enabled = !state.loading) { Text("Refresh") }
                }
            }

            if (state.loading && state.vendorCars.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (state.vendorCars.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No vehicle added yet", style = MaterialTheme.typography.titleMedium)
                            Text("Add your first chauffeur-driven car to begin the admin review process.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = onAddVehicle, shape = RoundedCornerShape(12.dp)) { Text("Add vehicle") }
                        }
                    }
                }
            } else {
                items(state.vendorCars, key = { it.id }) { car ->
                    val status = car.approvalStatus?.uppercase() ?: "PENDING_REVIEW"
                    val blackouts = state.vehicleUnavailabilityByCar[car.id].orEmpty()
                    val today = LocalDate.now()
                    val activeBlackout = blackouts.firstOrNull {
                        val start = runCatching { LocalDate.parse(it.startDate, rentalDateFormatter) }.getOrNull()
                        val end = runCatching { LocalDate.parse(it.endDate, rentalDateFormatter) }.getOrNull()
                        start != null && end != null && !start.isAfter(today) && !end.isBefore(today)
                    }
                    val scheduledBlackout = blackouts.firstOrNull {
                        runCatching { LocalDate.parse(it.startDate, rentalDateFormatter) }.getOrNull()?.isAfter(today) == true
                    }
                    val displayedBlackout = activeBlackout ?: scheduledBlackout
                    val displayedOffMarket = activeBlackout != null
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        onClick = { detailsCar = car }
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(10.dp), color = AppColors.Primary.copy(alpha = .08f)) {
                                    Icon(Icons.Default.DirectionsCar, null, tint = AppColors.Primary, modifier = Modifier.padding(8.dp).size(22.dp))
                                }
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(car.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = when {
                                                status == "REJECTED" -> AppColors.Error.copy(alpha = .12f)
                                                status == "APPROVED" && (activeBlackout != null || scheduledBlackout != null) -> Color(0xFFFEF3C7)
                                                status == "APPROVED" -> AppColors.Success.copy(alpha = .12f)
                                                else -> Color(0xFFFFF7E6)
                                            }
                                        ) {
                                            Text(
                                                when {
                                                    status == "REJECTED" -> "REJECTED"
                                                    displayedOffMarket -> "OFF MARKET"
                                                    scheduledBlackout != null -> "SCHEDULED"
                                                    else -> status.replace("_", " ")
                                                },
                                                color = when {
                                                    status == "REJECTED" -> AppColors.Error
                                                    displayedOffMarket || scheduledBlackout != null -> Color(0xFF9A6408)
                                                    status == "APPROVED" -> AppColors.Success
                                                    else -> Color(0xFF9A6408)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        car.category + " • " + car.seats + " seats • " + car.transmission + " • " + (car.fuelType ?: "Fuel"),
                                        color = AppColors.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                    Text(
                                        "₹" + car.pricePerDay.setScale(0) + " / day • " + car.driverName + " • " + (car.city ?: "Location unavailable"),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }
                            }

                            displayedBlackout?.let {
                                Text(
                                    (if (displayedOffMarket) "Off market: " else "Scheduled off market: ") + formatRentalDate(it.startDate) + " → " + formatRentalDate(it.endDate) + " • " + it.reasonLabel,
                                    color = Color(0xFF9A6408),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        calendarCarId = car.id
                                        calendarMonth = YearMonth.now()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) { Text("Calendar", style = MaterialTheme.typography.labelMedium) }

                                if (displayedBlackout != null) {
                                    OutlinedButton(
                                        onClick = {
                                            onRestoreVehicleToMarket(car.id, displayedBlackout.id) {
                                                onLoadVehicleAvailability(car.id)
                                            }
                                        },
                                        enabled = !state.saving,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text("Restore", color = AppColors.Success, style = MaterialTheme.typography.labelMedium) }
                                } else if (status == "APPROVED") {
                                    Button(
                                        onClick = {
                                            offMarketCar = car
                                            offMarketStart = LocalDate.now().plusDays(1).format(rentalDateFormatter)
                                            offMarketEnd = LocalDate.now().plusDays(1).format(rentalDateFormatter)
                                            offMarketReason = "SERVICE_MAINTENANCE"
                                            offMarketNote = ""
                                        },
                                        enabled = !state.saving,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text("Take off market", style = MaterialTheme.typography.labelMedium) }
                                }

                                if (status == "REJECTED") {
                                    OutlinedButton(
                                        onClick = { onAddVehicleWithCar(car) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text("Correct & resubmit", style = MaterialTheme.typography.labelMedium) }
                                }
                            }

                            car.rejectionReason?.let { Text("Review note: " + it, color = AppColors.Error, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }

            item {
                OutlinedButton(onClick = onAddVehicle, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("Add another vehicle")
                }
            }
            state.error?.let { item { Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodySmall) } }
        }
    } else {
        val vendorStatus = state.vendor?.status?.uppercase() ?: "NOT_ONBOARDED"
        val isPending = vendorStatus == "PENDING"
        val isRejected = vendorStatus == "REJECTED"

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text("Become a rental partner", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (isRejected) "Let’s fix the reviewed details and resubmit." else "A simple profile is all we need to get your fleet reviewed.",
                            color = AppColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (isPending) {
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E6))) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("You’re almost there", style = MaterialTheme.typography.titleMedium, color = Color(0xFF9A6408))
                            Text("Your vendor profile is with admin for verification.", color = AppColors.TextSecondary)
                            Text("Once verified, you can add vehicles and manage their availability.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Start earning from your car", style = MaterialTheme.typography.titleMedium)
                            Text("List chauffeur-driven vehicles, choose when they are available, and track bookings from one place.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Profile", "Admin review", "Add vehicles").forEach { label ->
                                    Surface(shape = RoundedCornerShape(14.dp), color = AppColors.Primary.copy(alpha = .08f)) {
                                        Text(label, color = AppColors.Primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isRejected) {
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Error.copy(alpha = .07f))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Admin note", fontWeight = FontWeight.SemiBold, color = AppColors.Error)
                            Text(state.vendor?.rejectionReason ?: "Please review the submitted details.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (!isPending) {
                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Your profile", style = MaterialTheme.typography.titleMedium)
                            CompactFieldRow("Full name", fullName, { fullName = it }, "Fleet / business (optional)", businessName, { businessName = it })
                            VendorField("Address", address) { address = it }
                            CompactFieldRow("City", city, { city = it }, "State", stateName, { stateName = it })
                            CompactFieldRow("PIN code", pin, { pin = it }, "PAN (optional)", pan, { pan = it })
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Payout details", style = MaterialTheme.typography.titleMedium)
                            Text("Optional for now; you can complete payout details later.", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                            VendorField("Payout UPI (optional)", upi) { upi = it }
                        }
                    }
                }
            }

            state.error?.let { item { Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodySmall) } }
            item {
                Button(
                    onClick = {
                        onSubmit(
                            RentalVendorOnboardingRequest(
                                vendorType = "INDIVIDUAL",
                                fullName = fullName.trim(),
                                businessName = businessName.trim().ifBlank { null },
                                address = address.trim(),
                                city = city.trim(),
                                state = stateName.trim(),
                                pinCode = pin.trim(),
                                panNumber = pan.trim().ifBlank { null },
                                payoutUpiId = upi.trim().ifBlank { null }
                            ), {}
                        )
                    },
                    enabled = !state.saving && !isPending &&
                        fullName.isNotBlank() && address.isNotBlank() &&
                        city.isNotBlank() && stateName.isNotBlank() && pin.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    when {
                        state.saving -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        isRejected -> Text("Resubmit for verification")
                        isPending -> Text("Pending admin verification")
                        else -> Text("Start vendor verification")
                    }
                }
            }
        }
    }
}

private fun rentalCarImageUrls(imageUrl: String?): List<String?> {
    val urls = imageUrl.orEmpty()
        .split("|", "\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(4)
    return List(4) { index -> urls.getOrNull(index) }
}

@Composable
private fun RentalCarImageTile(url: String?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(9.dp)),
        color = AppColors.Primary.copy(alpha = .045f)
    ) {
        if (url.isNullOrBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = "Car photo placeholder",
                    tint = AppColors.TextSecondary.copy(alpha = .28f),
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            AsyncImage(
                model = rentalPhotoDisplayUrl(url),
                contentDescription = "Car photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

private fun listOfNotBlank(vararg values: String?): List<String> =
    values.mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }

@Composable
private fun EarningsMetric(
    label: String,
    amount: BigDecimal,
    alignEnd: Boolean = false,
    emphasize: Boolean = false
) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            "₹" + amount.setScale(2).toPlainString(),
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasize) AppColors.Success else AppColors.TextPrimary,
            maxLines = 1,
            softWrap = false
        )
        Text(label, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun MarketplaceScreen(onBack: () -> Unit, onCarRental: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column {
                    Text("Marketplace", style = MaterialTheme.typography.headlineSmall)
                    Text("Explore mPay services", color = AppColors.TextSecondary)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), onClick = onCarRental) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = AppColors.Primary.copy(alpha = .10f)) {
                        Icon(Icons.Default.DirectionsCar, "Car Rental", tint = AppColors.Primary, modifier = Modifier.padding(12.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Car Rental", style = MaterialTheme.typography.titleLarge)
                        Text("Chauffeur-driven cars for your trip", color = AppColors.TextSecondary)
                    }
                    Text("NEW", style = MaterialTheme.typography.labelMedium, color = AppColors.Primary)
                }
            }
        }
    }
}

@Composable
fun CarRentalMarketplaceScreen(
    state: RentalUiState,
    onBack: () -> Unit,
    onBook: (RentalCarResponse, String, String) -> Unit,
    onSearch: (String, String) -> Unit,
    onClearFilter: () -> Unit
) {
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    val canApplyFilter = runCatching {
        val s = LocalDateTime.parse(start, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val e = LocalDateTime.parse(end, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        e.isAfter(s) && !s.isBefore(LocalDateTime.now())
    }.getOrDefault(false)
    val filterApplied = start.isNotBlank() || end.isNotBlank()

    LaunchedEffect(Unit) {
        start = ""
        end = ""
        onClearFilter()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Car Rental Marketplace", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (filterApplied) "Showing vehicles available for the selected period."
                        else "All approved vehicles are shown. Filter only when needed.",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        RentalDateTimeField("From", start, { start = it }, Modifier.weight(1f))
                        RentalDateTimeField("To", end, { end = it }, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(
                            onClick = { onSearch(start, end) },
                            enabled = canApplyFilter && !state.loading,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (state.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Filter available cars")
                        }
                        if (filterApplied) {
                            OutlinedButton(
                                onClick = {
                                    start = ""
                                    end = ""
                                    onClearFilter()
                                },
                                enabled = !state.loading,
                                modifier = Modifier.weight(.42f),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Clear") }
                        }
                    }
                }
            }
        }
        state.error?.let { item { Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodySmall) } }
        if (state.loading && state.cars.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(26.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
        if (!state.loading && state.cars.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F8FC))) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFE2F2FC)) {
                            Icon(Icons.Default.DirectionsCar, null, tint = Color(0xFF1677B8), modifier = Modifier.padding(12.dp).size(28.dp))
                        }
                        Text(if (filterApplied) "No cars available for this period." else "No cars available right now.", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (filterApplied) "Clear the filter to see all marketplace vehicles again." else "Approved vehicles will appear here when available.",
                            color = AppColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        items(state.cars, key = { it.id }) { car ->
            val images = rentalPhotoSlots(car.imageUrl)
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            RentalCarImageTile(images[0], Modifier.weight(1f).aspectRatio(1.12f))
                            RentalCarImageTile(images[1], Modifier.weight(1f).aspectRatio(1.12f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            RentalCarImageTile(images[2], Modifier.weight(1f).aspectRatio(1.12f))
                            RentalCarImageTile(images[3], Modifier.weight(1f).aspectRatio(1.12f))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(car.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(car.category + " • " + car.seats + " seats • " + car.transmission, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, tint = AppColors.Primary, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(car.driverName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        Text((car.fuelType ?: "Fuel") + " • " + (car.city ?: "Location unavailable"), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("₹" + car.pricePerDay.setScale(0) + "/day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Chauffeur included", color = AppColors.Success, style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { onBook(car, start, end) },
                                enabled = !filterApplied || canApplyFilter,
                                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Book", style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun VehiclePhotoField(
    title: String,
    value: String,
    galleryUri: String?,
    onValueChange: (String) -> Unit,
    onPickGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preview = galleryUri ?: value.takeIf { it.isNotBlank() }
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onPickGallery)
            ) {
                RentalCarImageTile(preview, Modifier.fillMaxSize())
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                    shape = RoundedCornerShape(7.dp),
                    color = Color.Black.copy(alpha = .62f)
                ) {
                    Text(
                        if (galleryUri != null) "Gallery selected" else "Tap for gallery",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
            VendorField(title, value, onValueChange = onValueChange)
        }
    }
}

@Composable
fun RentalVehicleOnboardingScreen(
    state: RentalUiState,
    onSubmit: (RentalVehicleOnboardingRequest, Map<Int, String>, () -> Unit) -> Unit,
    onBack: () -> Unit,
    editingCar: RentalCarResponse? = null,
    onResubmit: ((String, RentalVehicleUpdateRequest, Map<Int, String>, () -> Unit) -> Unit)? = null
) {
    var name by remember(editingCar?.id) { mutableStateOf(editingCar?.name.orEmpty()) }
    var make by remember(editingCar?.id) { mutableStateOf(editingCar?.make.orEmpty()) }
    var model by remember(editingCar?.id) { mutableStateOf(editingCar?.model.orEmpty()) }
    var variant by remember(editingCar?.id) { mutableStateOf(editingCar?.variant.orEmpty()) }
    var category by remember(editingCar?.id) { mutableStateOf(editingCar?.category ?: "Sedan") }
    var seats by remember(editingCar?.id) { mutableStateOf(editingCar?.seats?.toString() ?: "5") }
    var transmission by remember(editingCar?.id) { mutableStateOf(editingCar?.transmission ?: "Automatic") }
    var fuel by remember(editingCar?.id) { mutableStateOf(editingCar?.fuelType ?: "Petrol") }
    var manufacturingYear by remember(editingCar?.id) { mutableStateOf(editingCar?.manufacturingYear?.toString().orEmpty()) }
    var registrationYear by remember(editingCar?.id) { mutableStateOf(editingCar?.registrationYear?.toString().orEmpty()) }
    var registrationNumber by remember(editingCar?.id) { mutableStateOf(editingCar?.registrationNumber.orEmpty()) }
    var pickupAddress by remember(editingCar?.id) { mutableStateOf(editingCar?.pickupAddress.orEmpty()) }
    var city by remember(editingCar?.id) { mutableStateOf(editingCar?.city.orEmpty()) }
    var stateName by remember(editingCar?.id) { mutableStateOf(editingCar?.state.orEmpty()) }
    var pricePerDay by remember(editingCar?.id) { mutableStateOf(editingCar?.pricePerDay?.toPlainString().orEmpty()) }
    val existingPhotos = remember(editingCar?.id) { rentalPhotoSlots(editingCar?.imageUrl) }
    var photoFront by remember(editingCar?.id) { mutableStateOf(existingPhotos.getOrNull(0).orEmpty()) }
    var photoSide by remember(editingCar?.id) { mutableStateOf(existingPhotos.getOrNull(1).orEmpty()) }
    var photoRear by remember(editingCar?.id) { mutableStateOf(existingPhotos.getOrNull(2).orEmpty()) }
    var photoInterior by remember(editingCar?.id) { mutableStateOf(existingPhotos.getOrNull(3).orEmpty()) }
    var galleryFront by remember(editingCar?.id) { mutableStateOf<String?>(null) }
    var gallerySide by remember(editingCar?.id) { mutableStateOf<String?>(null) }
    var galleryRear by remember(editingCar?.id) { mutableStateOf<String?>(null) }
    var galleryInterior by remember(editingCar?.id) { mutableStateOf<String?>(null) }

    var driverName by remember(editingCar?.id) { mutableStateOf(editingCar?.driverName.orEmpty()) }
    var driverMobile by remember(editingCar?.id) { mutableStateOf(editingCar?.driverMobile.orEmpty()) }
    var licenseNumber by remember(editingCar?.id) { mutableStateOf(editingCar?.driverLicenseNumber.orEmpty()) }
    var licenseExpiry by remember(editingCar?.id) { mutableStateOf(editingCar?.driverLicenseExpiry.orEmpty()) }
    var driverAddress by remember(editingCar?.id) { mutableStateOf(editingCar?.driverAddress.orEmpty()) }

    val combinedPhotos = listOf(photoFront, photoSide, photoRear, photoInterior)
        .map { it.trim() }
        .joinToString("|")
        .takeIf { listOf(photoFront, photoSide, photoRear, photoInterior).any { it.isNotBlank() } }

    val galleryPhotos = listOf(galleryFront, gallerySide, galleryRear, galleryInterior)
        .mapIndexedNotNull { index, uri -> uri?.let { index to it } }
        .toMap()

    val frontGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        galleryFront = uri?.toString()
    }
    val sideGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        gallerySide = uri?.toString()
    }
    val rearGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        galleryRear = uri?.toString()
    }
    val interiorGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        galleryInterior = uri?.toString()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (editingCar == null) "Add your vehicle" else "Correct vehicle details",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        if (editingCar == null) "Two quick sections: vehicle details first, then the assigned driver." else "Update the rejected details and resubmit for review.",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(9.dp), color = AppColors.Primary.copy(alpha = .08f)) {
                            Icon(Icons.Default.DirectionsCar, null, tint = AppColors.Primary, modifier = Modifier.padding(7.dp).size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("1. Vehicle details", style = MaterialTheme.typography.titleMedium)
                            Text("Identity, specifications, pickup and daily price", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    CompactFieldRow("Vehicle name", name, { name = it }, "Make", make, { make = it })
                    CompactFieldRow("Model", model, { model = it }, "Variant (optional)", variant, { variant = it })
                    CompactFieldRow("Category", category, { category = it }, "Seats", seats, { seats = it })
                    CompactFieldRow("Transmission", transmission, { transmission = it }, "Fuel type", fuel, { fuel = it })
                    CompactFieldRow("Manufacturing year", manufacturingYear, { manufacturingYear = it }, "Registration year", registrationYear, { registrationYear = it })
                    VendorField("Registration number", registrationNumber) { registrationNumber = it }
                    CompactFieldRow("City", city, { city = it }, "State", stateName, { stateName = it })
                    VendorField("Pickup address", pickupAddress) { pickupAddress = it }
                    VendorField("Price per day (₹)", pricePerDay) { pricePerDay = it }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vehicle photos (optional)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "For each slot, either enter an image URL or tap the preview to choose one from your gallery. Up to 4 photos are supported: front 3/4, side, rear 3/4 and interior.",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        VehiclePhotoField(
                            "Front 3/4 URL", photoFront, galleryFront, { photoFront = it },
                            { frontGalleryLauncher.launch("image/*") }, Modifier.weight(1f)
                        )
                        VehiclePhotoField(
                            "Side URL", photoSide, gallerySide, { photoSide = it },
                            { sideGalleryLauncher.launch("image/*") }, Modifier.weight(1f)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        VehiclePhotoField(
                            "Rear 3/4 URL", photoRear, galleryRear, { photoRear = it },
                            { rearGalleryLauncher.launch("image/*") }, Modifier.weight(1f)
                        )
                        VehiclePhotoField(
                            "Interior URL", photoInterior, galleryInterior, { photoInterior = it },
                            { interiorGalleryLauncher.launch("image/*") }, Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(9.dp), color = AppColors.Success.copy(alpha = .10f)) {
                            Icon(Icons.Default.Person, null, tint = AppColors.Success, modifier = Modifier.padding(7.dp).size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("2. Driver details", style = MaterialTheme.typography.titleMedium)
                            Text("The chauffeur assigned to this vehicle", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    CompactFieldRow("Driver full name", driverName, { driverName = it }, "Driver mobile", driverMobile, { driverMobile = it })
                    VendorField("Driving licence no.", licenseNumber) { licenseNumber = it }
                    RentalDateField("Licence expiry", licenseExpiry, onValueChange = { licenseExpiry = it })
                    VendorField("Driver address (optional)", driverAddress) { driverAddress = it }
                }
            }
        }

        item {
            Text(
                "All vehicle details and the assigned chauffeur are reviewed before the car appears in the customer marketplace.",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        state.error?.let { item { Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodySmall) } }

        item {
            val valid = name.isNotBlank() &&
                make.isNotBlank() &&
                model.isNotBlank() &&
                registrationNumber.isNotBlank() &&
                pickupAddress.isNotBlank() &&
                city.isNotBlank() &&
                stateName.isNotBlank() &&
                driverName.isNotBlank() &&
                driverMobile.isNotBlank() &&
                licenseNumber.isNotBlank() &&
                licenseExpiry.isNotBlank() &&
                pricePerDay.toBigDecimalOrNull() != null &&
                seats.toIntOrNull() != null &&
                manufacturingYear.toIntOrNull() != null &&
                registrationYear.toIntOrNull() != null

            Button(
                onClick = {
                    val driver = RentalDriverRequest(
                        driverName.trim(),
                        driverMobile.trim(),
                        licenseNumber.trim(),
                        licenseExpiry.trim(),
                        driverAddress.trim().ifBlank { null }
                    )
                    if (editingCar != null && onResubmit != null) {
                        onResubmit(
                            editingCar.id,
                            RentalVehicleUpdateRequest(
                                name = name.trim(),
                                category = category.trim(),
                                seats = seats.toInt(),
                                transmission = transmission.trim(),
                                fuelType = fuel.trim(),
                                manufacturingYear = manufacturingYear.toInt(),
                                registrationYear = registrationYear.toInt(),
                                registrationNumber = registrationNumber.trim(),
                                make = make.trim(),
                                model = model.trim(),
                                variant = variant.trim().ifBlank { null },
                                pickupAddress = pickupAddress.trim(),
                                city = city.trim(),
                                state = stateName.trim(),
                                pricePerDay = pricePerDay.toBigDecimal(),
                                imageUrl = combinedPhotos,
                                driver = driver
                            ),
                            galleryPhotos,
                            onBack
                        )
                    } else {
                        onSubmit(
                            RentalVehicleOnboardingRequest(
                                name = name.trim(),
                                category = category.trim(),
                                seats = seats.toInt(),
                                transmission = transmission.trim(),
                                fuelType = fuel.trim(),
                                manufacturingYear = manufacturingYear.toInt(),
                                registrationYear = registrationYear.toInt(),
                                registrationNumber = registrationNumber.trim(),
                                make = make.trim(),
                                model = model.trim(),
                                variant = variant.trim().ifBlank { null },
                                pickupAddress = pickupAddress.trim(),
                                city = city.trim(),
                                state = stateName.trim(),
                                pricePerDay = pricePerDay.toBigDecimal(),
                                imageUrl = combinedPhotos,
                                driver = driver
                            ),
                            galleryPhotos,
                            onBack
                        )
                    }
                },
                enabled = valid && !state.saving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp)
            ) {
                if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (editingCar == null) "Submit vehicle for review" else "Resubmit vehicle for review")
            }
        }
    }
}

@Composable
private fun RentalVehicleDetailsDialog(
    car: RentalCarResponse,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(20.dp)) {
            LazyColumn(
                Modifier.fillMaxWidth().padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(car.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                car.category + " • " + car.seats + " seats • " + car.transmission,
                                color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rentalPhotoSlots(car.imageUrl).forEach { photo ->
                            RentalCarImageTile(photo, Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
                item { Text("Vehicle details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                item { Text("Make / model: " + listOfNotBlank(car.make, car.model, car.variant).joinToString(" ").ifBlank { "—" }) }
                item { Text("Category / seats: " + car.category + " / " + car.seats) }
                item { Text("Transmission / fuel: " + car.transmission + " / " + (car.fuelType ?: "—")) }
                item { Text("Manufacturing year: " + (car.manufacturingYear?.toString() ?: "—")) }
                item { Text("Registration year: " + (car.registrationYear?.toString() ?: "—")) }
                item { Text("Registration number: " + (car.registrationNumber ?: "—")) }
                item { Text("Price per day: ₹" + car.pricePerDay.setScale(2).toPlainString()) }
                item { Text("Pickup address: " + (car.pickupAddress ?: "—")) }
                item { Text("City / state: " + listOfNotBlank(car.city, car.state).joinToString(", ").ifBlank { "—" }) }
                item { Text("Approval status: " + (car.approvalStatus ?: "—")) }
                car.rejectionReason?.takeIf { it.isNotBlank() }?.let { reason ->
                    item { Text("Review note: " + reason, color = AppColors.Error) }
                }
                item { Text("Driver details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                item { Text("Name: " + car.driverName) }
                item { Text("Mobile: " + (car.driverMobile ?: "—")) }
                item { Text("Licence number: " + (car.driverLicenseNumber ?: "—")) }
                item { Text("Licence expiry: " + (car.driverLicenseExpiry ?: "—")) }
                item { Text("Driver address: " + (car.driverAddress ?: "—")) }
            }
        }
    }
}

@Composable
fun RentalBookingScreen(
    car: RentalCarResponse,
    state: RentalUiState,
    onQuote: (RentalBookingQuoteRequest, (RentalBookingQuoteResponse) -> Unit) -> Unit,
    onBack: () -> Unit,
    onConfirm: (RentalBookingRequest, () -> Unit) -> Unit,
    initialStart: String? = null,
    initialEnd: String? = null
) {
    var pickup by remember { mutableStateOf(car.pickupAddress.orEmpty()) }
    var drop by remember { mutableStateOf(car.city.orEmpty()) }
    var start by remember { mutableStateOf(initialStart.orEmpty()) }
    var end by remember { mutableStateOf(initialEnd.orEmpty()) }
    var quote by remember { mutableStateOf<RentalBookingQuoteResponse?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }; Text("Book with driver", style = MaterialTheme.typography.headlineSmall) } }
        item { Text(car.name, style = MaterialTheme.typography.titleLarge) }
        item { Text("Driver: " + car.driverName + (car.driverMobile?.let { " · " + it } ?: ""), color = AppColors.TextSecondary) }
        item { VendorField("Pickup location", pickup) { pickup = it } }
        item { VendorField("Drop location", drop) { drop = it } }
        item { RentalDateTimeField("Start date & time", start, onValueChange = { start = it }) }
        item { RentalDateTimeField("End date & time", end, onValueChange = { end = it }) }
        item {
            Text(
                "Pricing is per day (24 hours). Any partial day is charged as one full day; time is used for availability and the exact rental duration.",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        quote?.let { q ->
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Fare summary", style = MaterialTheme.typography.titleLarge)
                        Text("${q.days} day(s) × ₹${q.pricePerDay}")
                        Text("Total: ₹${q.total}", style = MaterialTheme.typography.titleLarge)
                        Text("Payment: From your wallet", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text("₹${q.total.setScale(2).toPlainString()} will be deducted from your wallet when you confirm.", color = AppColors.TextSecondary)
                        Button(
                            enabled = !state.saving,
                            onClick = { onConfirm(RentalBookingRequest(UUID.randomUUID().toString(), car.id, pickup.trim(), drop.trim(), start, end, "WALLET"), onBack) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Confirm booking") }
                    }
                }
            }
        } ?: item {
            Button(
                enabled = !state.saving && pickup.isNotBlank() && drop.isNotBlank() && runCatching { LocalDateTime.parse(start, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.isSuccess && runCatching { LocalDateTime.parse(end, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.isSuccess,
                onClick = { onQuote(RentalBookingQuoteRequest(car.id, pickup.trim(), drop.trim(), start, end)) { quote = it } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Check fare") }
        }
        state.error?.let { item { Text(it, color = AppColors.Error) } }
    }
}


@Composable
fun RentalMyBookingsScreen(
    state: RentalUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onCancel: (String, () -> Unit) -> Unit,
    onWalletRefresh: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    var copiedBookingId by remember { mutableStateOf<String?>(null) }
    var cancelBookingId by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf("ALL") }
    LaunchedEffect(Unit) { onRefresh() }

    cancelBookingId?.let { bookingId ->
        AlertDialog(
            onDismissRequest = { if (!state.saving) cancelBookingId = null },
            title = { Text("Cancel booking?") },
            text = { Text("This will cancel the rental booking and refund the wallet amount. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        onCancel(bookingId) {
                            cancelBookingId = null
                            onWalletRefresh()
                            onRefresh()
                        }
                    },
                    enabled = !state.saving
                ) { Text(if (state.saving) "Cancelling…" else "Cancel booking") }
            },
            dismissButton = { TextButton(onClick = { cancelBookingId = null }, enabled = !state.saving) { Text("Keep booking") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("My Bookings", style = MaterialTheme.typography.headlineSmall)
                    Text("Your chauffeur-driven rental bookings", color = AppColors.TextSecondary)
                }
                IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Refresh bookings") }
            }
        }
        state.error?.let { item { Text(it, color = AppColors.Error) } }
        item {
            val filters = listOf("ALL") + state.bookings.map { it.status.uppercase() }.distinct()
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = statusFilter.equals(filter, true),
                        onClick = { statusFilter = filter },
                        label = { Text(if (filter == "ALL") "All" else filter.replace("_", " ")) },
                        modifier = Modifier.height(34.dp)
                    )
                }
            }
        }

        if (state.loading && state.bookings.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
        if (!state.loading && state.bookings.isEmpty()) {
            item { Text("No rental bookings yet.", color = AppColors.TextSecondary) }
        }
        val filteredBookings = state.bookings.filter { statusFilter == "ALL" || it.status.equals(statusFilter, true) }
        if (!state.loading && filteredBookings.isEmpty() && state.bookings.isNotEmpty()) {
            item { Text("No bookings match the selected status.", color = AppColors.TextSecondary) }
        }
        items(filteredBookings, key = { it.bookingId }) { booking ->
            val statusColor = rentalStatusColor(booking.status)
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsCar, null, tint = AppColors.Primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(booking.carName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Spacer(Modifier.width(7.dp))
                            Surface(shape = RoundedCornerShape(14.dp), color = statusColor.copy(alpha = .12f)) {
                                Text(
                                    booking.status.uppercase(),
                                    color = statusColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(rentalBookingShareText(booking)))
                                copiedBookingId = booking.bookingId
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                if (copiedBookingId == booking.bookingId) Icons.Default.Check else Icons.Default.ContentCopy,
                                if (copiedBookingId == booking.bookingId) "Copied" else "Copy booking details",
                                tint = if (copiedBookingId == booking.bookingId) AppColors.Success else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        "Booking " + booking.bookingId,
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Driver", color = AppColors.Primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Text(booking.driverName + (booking.driverMobile?.let { " • " + it } ?: ""), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                        Text("Trip", color = AppColors.Primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Text(booking.pickup + " → " + booking.drop, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                        Text("When", color = AppColors.Primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            formatRentalBookingDateTime(booking.startDate) + " to " + formatRentalBookingDateTime(booking.endDate),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Paid from wallet", color = AppColors.Success, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("₹" + booking.total.setScale(2).toPlainString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Booked " + formatRentalBookingDateTime(booking.createdAt),
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (booking.status.equals("CONFIRMED", true) && runCatching { LocalDateTime.parse(booking.startDate) }.getOrNull()?.isAfter(LocalDateTime.now()) == true) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = { cancelBookingId = booking.bookingId },
                                enabled = !state.saving,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text("Cancel", color = AppColors.Error, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
