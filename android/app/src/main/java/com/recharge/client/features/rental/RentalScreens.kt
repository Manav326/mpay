package com.recharge.client.features.rental

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
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
import com.recharge.client.core.ui.MpayEmptyState
import com.recharge.client.core.ui.MpayFinancialAmount
import com.recharge.client.core.ui.MpayStatusPill
import com.recharge.client.core.ui.statusColor
import com.recharge.client.core.viewmodel.RentalUiState


private val rentalDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
private val rentalDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val rentalDateDisplayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
private val rentalBookingDisplayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mma", Locale.ENGLISH)
private val rentalDateTimeDisplayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

private val indianStatesAndUt = listOf(
    "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh",
    "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka",
    "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram",
    "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu",
    "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
    "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu",
    "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry"
)

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

private fun rentalBookingDisplayStatus(booking: RentalBookingResponse): Pair<String, Boolean> {
    val raw = booking.status.uppercase()
    val completed = raw == "CONFIRMED" &&
        runCatching {
            LocalDateTime.parse(booking.endDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }.getOrNull()?.isBefore(LocalDateTime.now()) == true
    return if (completed) {
        "Ride completed" to true
    } else {
        raw.replace("_", " ").lowercase(Locale.ENGLISH)
            .replaceFirstChar { it.uppercase() } to false
    }
}

private fun normalizeIndianMobile(value: String): String =
    value.filter(Char::isDigit).let { digits -> if (digits.length > 10) digits.takeLast(10) else digits }

private fun normalizeLicenseExpiry(value: String): String =
    runCatching {
        LocalDate.parse(value, rentalDateFormatter).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }.getOrElse { value }

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
private fun VendorSelectField(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    var expanded by remember(value) { mutableStateOf(false) }
    val safeOptions = (listOf(value).filter { it.isNotBlank() } + options).distinct()
    Box(modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                Text(value.ifBlank { "Select" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            safeOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onValueChange(option); expanded = false }
                )
            }
        }
    }
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
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null
) {
    val context = LocalContext.current
    val display = if (value.isBlank()) {
        "Select date"
    } else {
        runCatching { LocalDate.parse(value, rentalDateFormatter).format(rentalDateDisplayFormatter) }
            .getOrElse {
                runCatching { LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate().format(rentalDateDisplayFormatter) }
                    .getOrElse { value }
            }
    }
    OutlinedButton(
        onClick = {
            val initial = runCatching { LocalDate.parse(value, rentalDateFormatter) }.getOrElse { minDate ?: LocalDate.now() }
            DatePickerDialog(
                context,
                { _, year, month, day -> onValueChange(LocalDate.of(year, month + 1, day).format(rentalDateFormatter)) },
                initial.year,
                initial.monthValue - 1,
                initial.dayOfMonth
            ).apply {
                minDate?.let { datePicker.minDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            }.show()
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
                    RentalDateField("From", startDate, onStartDate, Modifier.weight(1f), minDate = LocalDate.now())
                    RentalDateField("To", endDate, onEndDate, Modifier.weight(1f), minDate = LocalDate.now())
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
                val validPeriod = runCatching {
                    val from = LocalDate.parse(startDate, rentalDateFormatter)
                    val to = LocalDate.parse(endDate, rentalDateFormatter)
                    !from.isBefore(LocalDate.now()) && !to.isBefore(from)
                }.getOrDefault(false)
                if (startDate.isNotBlank() && endDate.isNotBlank() && !validPeriod) {
                    Text("Choose a current/future period with the end date on or after the start date.", color = AppColors.Error, style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = !saving && startDate.isNotBlank() && endDate.isNotBlank() && runCatching { val from = LocalDate.parse(startDate, rentalDateFormatter); val to = LocalDate.parse(endDate, rentalDateFormatter); !from.isBefore(LocalDate.now()) && !to.isBefore(from) }.getOrDefault(false)) {
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
                    IconButton(onClick = onPrevious) { Icon(Icons.Default.ChevronLeft, "Previous month") }
                    IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "Next month") }
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
    onLoadVehicleCalendar: (String, Int, Int) -> Unit = { _, _, _ -> },
    onUpdateVendorProfile: (RentalVendorUpdateRequest, () -> Unit) -> Unit = { _, done -> done() }
) {
    var vendorType by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.vendorType ?: "INDIVIDUAL") }
    var fullName by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.fullName.orEmpty()) }
    var businessName by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.businessName.orEmpty()) }
    var address by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.address.orEmpty()) }
    var city by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.city.orEmpty()) }
    var stateName by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.state.orEmpty()) }
    var pin by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.pinCode.orEmpty()) }
    var pan by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.panNumber.orEmpty()) }
    var upi by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.payoutUpiId.orEmpty()) }
    var bankName by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.bankName.orEmpty()) }
    var bankAccount by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.bankAccountNumber.orEmpty()) }
    var bankIfsc by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.bankIfsc.orEmpty()) }
    var primaryPayout by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.payoutPrimaryMethod ?: "") }
    var editingVendorProfile by remember { mutableStateOf(false) }

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

        if (editingVendorProfile) {
            RentalVendorProfileDialog(
                fullName = fullName,
                businessName = businessName,
                address = address,
                city = city,
                stateName = stateName,
                pin = pin,
                pan = pan,
                bankName = bankName,
                bankAccount = bankAccount,
                bankIfsc = bankIfsc,
                upi = upi,
                primaryPayout = primaryPayout,
                saving = state.saving,
                error = state.error,
                onFullName = { fullName = it },
                onBusinessName = { businessName = it },
                onAddress = { address = it },
                onCity = { city = it },
                onState = { stateName = it },
                onPin = { pin = it },
                onPan = { pan = it },
                onBankName = { bankName = it },
                onBankAccount = { bankAccount = it },
                onBankIfsc = { bankIfsc = it },
                onUpi = { upi = it },
                onPrimaryPayout = { primaryPayout = it },
                onDismiss = { if (!state.saving) editingVendorProfile = false },
                onSave = {
                    onUpdateVendorProfile(
                        RentalVendorUpdateRequest(
                            vendorType = state.vendor?.vendorType ?: "INDIVIDUAL",
                            fullName = fullName.trim(),
                            businessName = businessName.trim().ifBlank { null },
                            address = address.trim(),
                            city = city.trim(),
                            state = stateName.trim(),
                            pinCode = pin.trim(),
                            panNumber = pan.trim().ifBlank { null },
                            payoutUpiId = upi.trim().ifBlank { null },
                            bankAccountNumber = bankAccount.trim().ifBlank { null },
                            bankIfsc = bankIfsc.trim().ifBlank { null },
                            bankName = bankName.trim().ifBlank { null },
                            payoutPrimaryMethod = primaryPayout.trim().ifBlank { null }
                        )
                    ) { editingVendorProfile = false }
                }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.VendorNavy),
                    elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                        Column(Modifier.weight(1f)) {
                            Text("Vendor Studio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Fleet, payouts, earnings and availability", color = Color.White.copy(alpha = .76f), style = MaterialTheme.typography.bodySmall)
                        }

                    }
                }
            }

            state.vendor?.let { v ->
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7FF)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFDBEAFE)) {
                                    Icon(Icons.Default.Person, null, tint = Color(0xFF1D4ED8), modifier = Modifier.padding(9.dp).size(22.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Business profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF163B63))
                                    Text("Business identity & marketplace status", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(16.dp), color = AppColors.Success.copy(alpha = .12f)) {
                                    Text("VERIFIED", color = AppColors.Success, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                                }
                                TextButton(
                                        onClick = {
                                            vendorType = v.vendorType ?: "INDIVIDUAL"
                                            fullName = v.fullName.orEmpty()
                                            businessName = v.businessName.orEmpty()
                                            address = v.address.orEmpty()
                                            city = v.city.orEmpty()
                                            stateName = v.state.orEmpty()
                                            pin = v.pinCode.orEmpty()
                                            pan = v.panNumber.orEmpty()
                                            upi = v.payoutUpiId.orEmpty()
                                            bankName = v.bankName.orEmpty()
                                            bankAccount = v.bankAccountNumber.orEmpty()
                                            bankIfsc = v.bankIfsc.orEmpty()
                                            primaryPayout = v.payoutPrimaryMethod.orEmpty()
                                            editingVendorProfile = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)
                                    ) { Text("Edit") }
                                }
                            }
                            HorizontalDivider(color = Color(0xFFD7E4F2))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("Owner", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                                    Text(v.fullName ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    v.businessName?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, color = Color(0xFF2563EB), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                                    Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(v.vehicleCount.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                        Text("Vehicles", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null, tint = Color(0xFF2563EB), modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(listOfNotNull(v.city?.takeIf { it.isNotBlank() }, v.state?.takeIf { it.isNotBlank() }).joinToString(", ").ifBlank { "Location unavailable" }, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF8FAFC)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text("Payout details", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Bank", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                        Text(v.bankName ?: "—", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Account", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                        Text(v.bankAccountNumber ?: "—", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("IFSC", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                        Text(v.bankIfsc ?: "—", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("UPI", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                        Text(v.payoutUpiId ?: "—", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Primary", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                        Text(v.payoutPrimaryMethod ?: "—", color = AppColors.PrimaryDark, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Rental earnings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Rental payout overview — separate from your recharge commission earnings.", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                }
            }
            item { RentalEarningsSummaryCard(state.earnings) }

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
                items(state.vendorCars.chunked(2), key = { row -> row.firstOrNull()?.id ?: row.hashCode() }) { rowCars ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        rowCars.forEach { car ->
                            val status = car.approvalStatus?.uppercase() ?: "PENDING_REVIEW"
                            val blackouts = state.vehicleUnavailabilityByCar[car.id].orEmpty()
                            val today = LocalDate.now()
                            val activeBlackout = blackouts.firstOrNull {
                                val startDate = runCatching { LocalDate.parse(it.startDate, rentalDateFormatter) }.getOrNull()
                                val endDate = runCatching { LocalDate.parse(it.endDate, rentalDateFormatter) }.getOrNull()
                                startDate != null && endDate != null && !startDate.isAfter(today) && !endDate.isBefore(today)
                            }
                            val scheduledBlackout = blackouts.firstOrNull {
                                runCatching { LocalDate.parse(it.startDate, rentalDateFormatter) }.getOrNull()?.isAfter(today) == true
                            }
                            val displayedBlackout = activeBlackout ?: scheduledBlackout
                            val displayedOffMarket = activeBlackout != null
                            val statusColor = when (status) {
                                "APPROVED" -> AppColors.Success
                                "REJECTED" -> AppColors.Error
                                else -> Color(0xFF9A6408)
                            }
                            val secondaryLabel = when {
                                displayedOffMarket -> "OFF MARKET"
                                scheduledBlackout != null -> "SCHEDULED"
                                else -> null
                            }
                            val images = rentalPhotoSlots(car.imageUrl)

                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(17.dp),
                                onClick = { detailsCar = car },
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(9.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    RentalVehicleGallery(car.imageUrl)
                                    Text(car.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(
                                        car.category + " • " + car.seats + " seats",
                                        color = AppColors.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Badge, null, tint = AppColors.Primary, modifier = Modifier.size(15.dp))
                                        Text(
                                            car.driverName + (car.driverMobile?.let { " • " + it } ?: ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = RoundedCornerShape(9.dp), color = statusColor.copy(alpha = .12f)) {
                                            Text(
                                                status.replace("_", " "),
                                                color = statusColor,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                maxLines = 1
                                            )
                                        }
                                        secondaryLabel?.let { label ->
                                            Surface(shape = RoundedCornerShape(9.dp), color = Color(0xFFFFF3CD)) {
                                                Text(
                                                    label,
                                                    color = Color(0xFF9A6408),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                    Text("₹" + car.pricePerDay.setScale(0) + "/day", fontWeight = FontWeight.Bold, color = Color(0xFF176B4D))
                                     Text(
                                         "Fuel expense paid by client",
                                         color = AppColors.TextSecondary,
                                         style = MaterialTheme.typography.labelSmall
                                     )
                                    Text(
                                        car.transmission + " • " + (car.fuelType ?: "Fuel"),
                                        color = AppColors.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                    displayedBlackout?.let {
                                        Surface(shape = RoundedCornerShape(9.dp), color = Color(0xFFFFF8E1)) {
                                            Text(
                                                formatRentalDate(it.startDate) + " → " + formatRentalDate(it.endDate),
                                                color = Color(0xFF8A5A00),
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        OutlinedButton(
                                            onClick = { calendarCarId = car.id; calendarMonth = YearMonth.now() },
                                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 5.dp),
                                            shape = RoundedCornerShape(9.dp),
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Calendar", style = MaterialTheme.typography.labelSmall) }
                                        if (displayedBlackout != null) {
                                            OutlinedButton(
                                                onClick = { onRestoreVehicleToMarket(car.id, displayedBlackout.id) { onLoadVehicleAvailability(car.id) } },
                                                enabled = !state.saving,
                                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 5.dp),
                                                shape = RoundedCornerShape(9.dp),
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Restore", color = AppColors.Success, style = MaterialTheme.typography.labelSmall) }
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
                                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 5.dp),
                                                shape = RoundedCornerShape(9.dp),
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Off market", style = MaterialTheme.typography.labelSmall) }
                                        }
                                    }
                                    if (status == "REJECTED") {
                                        OutlinedButton(
                                            onClick = { onAddVehicleWithCar(car) },
                                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 5.dp),
                                            shape = RoundedCornerShape(9.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Correct & resubmit", style = MaterialTheme.typography.labelSmall) }
                                    }
                                    car.rejectionReason?.takeIf { it.isNotBlank() }?.let {
                                        Text("Review: " + it, color = AppColors.Error, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                    }
                                }
                            }
                        }
                        if (rowCars.size == 1) Spacer(Modifier.weight(1f))
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
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.VendorNavy)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Partner type", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Choose the identity used for your vendor application.", color = Color.White.copy(alpha = .76f), style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = vendorType.equals("INDIVIDUAL", true),
                                    onClick = { vendorType = "INDIVIDUAL" },
                                    label = { Text("Individual") },
                                    enabled = !isPending,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppColors.VendorGold,
                                        selectedLabelColor = Color(0xFF3B2500)
                                    )
                                )
                                FilterChip(
                                    selected = vendorType.equals("BUSINESS", true),
                                    onClick = { vendorType = "BUSINESS" },
                                    label = { Text("Business") },
                                    enabled = !isPending,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppColors.VendorGold,
                                        selectedLabelColor = Color(0xFF3B2500)
                                    )
                                )
                            }
                        }
                    }
                }
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
                            Text("Add UPI or bank details and choose which one is primary for payouts.", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                            VendorField("Payout UPI (optional)", upi) { upi = it }
                            CompactFieldRow("Bank name", bankName, { bankName = it }, "Bank account", bankAccount, { bankAccount = it })
                            VendorField("Bank IFSC", bankIfsc) { bankIfsc = it }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Primary:", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
                                FilterChip(selected = primaryPayout == "UPI", onClick = { primaryPayout = "UPI" }, label = { Text("UPI") }, enabled = upi.isNotBlank())
                                FilterChip(selected = primaryPayout == "BANK", onClick = { primaryPayout = "BANK" }, label = { Text("Bank") }, enabled = bankAccount.isNotBlank() && bankIfsc.isNotBlank())
                            }
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
                                vendorType = vendorType,
                                fullName = fullName.trim(),
                                businessName = businessName.trim().ifBlank { null },
                                address = address.trim(),
                                city = city.trim(),
                                state = stateName.trim(),
                                pinCode = pin.trim(),
                                panNumber = pan.trim().ifBlank { null },
                                payoutUpiId = upi.trim().ifBlank { null },
                                bankAccountNumber = bankAccount.trim().ifBlank { null },
                                bankIfsc = bankIfsc.trim().ifBlank { null },
                                bankName = bankName.trim().ifBlank { null },
                                payoutPrimaryMethod = primaryPayout.trim().ifBlank { null }
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

@Composable
private fun RentalVehicleGallery(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    val urls = rentalPhotoSlots(imageUrl)
    var focusedIndex by remember(urls.joinToString("|")) { mutableIntStateOf(0) }
    val orderedSmall = (0..3).filter { it != focusedIndex }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        RentalCarImageTile(
            urls[focusedIndex],
            Modifier.fillMaxWidth().aspectRatio(1.75f)
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            orderedSmall.take(3).forEach { index ->
                RentalCarImageTile(
                    urls[index],
                    Modifier
                        .weight(1f)
                        .aspectRatio(1.55f)
                        .clickable { focusedIndex = index }
                )
            }
        }
    }
}

@Composable
private fun RentalPublicCarDetailsDialog(
    car: RentalCarResponse,
    bookEnabled: Boolean,
    onDismiss: () -> Unit,
    onBook: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            Modifier.fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            LazyColumn(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Box {
                        RentalCarImageTile(rentalPhotoSlots(car.imageUrl)[0], Modifier.fillMaxWidth().aspectRatio(1.65f))
                        Surface(
                            Modifier.align(Alignment.TopEnd).padding(8.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = .60f)
                        ) {
                            Text(car.category, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(car.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(listOfNotBlank(car.make, car.model, car.variant).joinToString(" ").ifBlank { car.category }, color = AppColors.TextSecondary)
                        }
                        Text("₹" + car.pricePerDay.setScale(0) + "/day", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.Success)
                    }
                }
                item {
                    RentalDetailSection("Vehicle", AppColors.Rental, listOf(
                        "Seats" to car.seats.toString(),
                        "Transmission" to car.transmission,
                        "Fuel" to (car.fuelType ?: "—"),
                        "Location" to listOfNotBlank(car.city, car.state).joinToString(", ").ifBlank { "—" }
                    ))
                }
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceWarm.copy(alpha = .70f))) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RentalCarImageTile(car.driverPhotoUrl, Modifier.size(68.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Chauffeur", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
                                Text(car.driverName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                car.driverRating?.let {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, null, tint = AppColors.PrimaryDark, modifier = Modifier.size(15.dp))
                                        Text(" " + it.setScale(1).toPlainString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    if (!bookEnabled) {
                        Text(
                            "Complete a valid future availability window before booking.",
                            color = AppColors.Error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Button(
                        onClick = onBook,
                        enabled = bookEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Text("Book with driver", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp))
                    }
                }
                item {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Text("Close") }
                }
            }
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
    onSearch: (String, String, String) -> Unit,
    onClearFilter: () -> Unit
) {
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var detailsCar by remember { mutableStateOf<RentalCarResponse?>(null) }

    val parsedStart = runCatching { LocalDateTime.parse(start, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull()
    val parsedEnd = runCatching { LocalDateTime.parse(end, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull()
    val hasDateInput = start.isNotBlank() || end.isNotBlank()
    val validWindow = parsedStart != null && parsedEnd != null && parsedEnd.isAfter(parsedStart) && !parsedStart.isBefore(LocalDateTime.now())
    val dateInputValid = !hasDateInput || validWindow
    val locationInput = location.trim()
    val canApplyFilter = locationInput.isNotBlank() || validWindow
    val filterApplied = locationInput.isNotBlank() || hasDateInput

    detailsCar?.let { car ->
        RentalPublicCarDetailsDialog(
            car = car,
            bookEnabled = !hasDateInput || validWindow,
            onDismiss = { detailsCar = null },
            onBook = {
                detailsCar = null
                onBook(car, start, end)
            }
        )
    }

    LaunchedEffect(Unit) {
        start = ""
        end = ""
        location = ""
        detailsCar = null
        onClearFilter()
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
                    Text("Car Rental Marketplace", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Find chauffeur-driven cars by place and availability.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceWarm.copy(alpha = .62f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        singleLine = true,
                        label = { Text("City / pickup area") },
                        placeholder = { Text("Patna, Airport Road…") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        RentalDateTimeField("From", start, { start = it }, Modifier.weight(1f))
                        RentalDateTimeField("To", end, { end = it }, Modifier.weight(1f))
                    }
                    if (!dateInputValid) {
                        Text("Choose both dates and times, with an end later than the start and a future start.", color = AppColors.Error, style = MaterialTheme.typography.labelSmall)
                    } else if (locationInput.isBlank() && !hasDateInput) {
                        Text("Use a place, a time window, or both.", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(
                            onClick = { onSearch(start, end, locationInput) },
                            enabled = canApplyFilter && dateInputValid && !state.loading,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            shape = RoundedCornerShape(11.dp)
                        ) {
                            if (state.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            else Text("Apply filters", fontWeight = FontWeight.Bold)
                        }
                        if (filterApplied) {
                            OutlinedButton(
                                onClick = {
                                    start = ""
                                    end = ""
                                    location = ""
                                    detailsCar = null
                                    onClearFilter()
                                },
                                enabled = !state.loading,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(11.dp)
                            ) { Text("Clear") }
                        }
                    }
                }
            }
        }
        state.error?.let { item { Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodySmall) } }

        if (state.loading && state.cars.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(26.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) } }
        }
        if (!state.loading && state.cars.isEmpty()) {
            item {
                MpayEmptyState(
                    title = if (filterApplied) "No cars match these filters" else "No cars available right now",
                    message = if (filterApplied) "Try a broader pickup area or availability window." else "Approved chauffeur-driven vehicles will appear here.",
                    icon = { Icon(Icons.Default.DirectionsCar, null, tint = AppColors.Rental, modifier = Modifier.size(30.dp)) },
                    actionLabel = if (filterApplied) "Clear filters" else null,
                    onAction = if (filterApplied) {
                        {
                            start = ""; end = ""; location = ""; detailsCar = null; onClearFilter()
                        }
                    } else null
                )
            }
        }

        items(state.cars, key = { it.id }) { car ->
            val image = rentalPhotoSlots(car.imageUrl).firstOrNull().orEmpty()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        RentalCarImageTile(image, Modifier.fillMaxWidth().aspectRatio(1.72f))
                        Surface(
                            Modifier.align(Alignment.TopEnd).padding(8.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = .58f)
                        ) {
                            Text(car.category, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(car.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(listOfNotBlank(car.make, car.model, car.variant).joinToString(" ").ifBlank { car.category }, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        Text("₹" + car.pricePerDay.setScale(2) + "/day", color = AppColors.Success, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RentalCarImageTile(car.driverPhotoUrl, Modifier.size(46.dp))
                        Column(Modifier.weight(1f)) {
                            Text(car.driverName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(car.seats.toString() + " seats • " + car.transmission + " • " + (car.fuelType ?: "Fuel"), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                car.driverRating?.let {
                                    Text("  •  ★ " + it.setScale(1), color = AppColors.PrimaryDark, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Text(listOfNotBlank(car.city, car.state).joinToString(", ").ifBlank { "Location unavailable" }, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.widthIn(max = 110.dp))
                    }
                    Text("Chauffeur included", color = AppColors.Success, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { detailsCar = car }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("View details") }
                        Button(
                            onClick = { onBook(car, start, end) },
                            enabled = !hasDateInput || validWindow,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Book", fontWeight = FontWeight.Bold) }
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
    onClearGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gallerySelected = galleryUri != null
    val preview = galleryUri ?: value.takeIf { it.isNotBlank() }

    Card(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(enabled = !gallerySelected, onClick = onPickGallery)
            ) {
                RentalCarImageTile(preview, Modifier.fillMaxSize())
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                    shape = RoundedCornerShape(7.dp),
                    color = Color.Black.copy(alpha = .62f)
                ) {
                    Text(
                        if (gallerySelected) "Device photo" else "Choose photo",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
            if (gallerySelected) {
                Text(
                    "Photo selected from device",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary
                )
                OutlinedButton(
                    onClick = onClearGallery,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 7.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Use image URL instead", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                VendorField(
                    "$title image URL",
                    value,
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = onValueChange
                )
                Text(
                    "Or choose a photo from your device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary
                )
                OutlinedButton(
                    onClick = onPickGallery,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 7.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Choose from device", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun RentalVehicleOnboardingScreen(
    state: RentalUiState,
    onSubmit: (RentalVehicleOnboardingRequest, Map<Int, String>, String?, () -> Unit) -> Unit,
    onBack: () -> Unit,
    editingCar: RentalCarResponse? = null,
    onResubmit: ((String, RentalVehicleUpdateRequest, Map<Int, String>, String?, () -> Unit) -> Unit)? = null
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
    var driverPhotoUri by remember(editingCar?.id) { mutableStateOf<String?>(null) }

    val currentVehicleYear = LocalDate.now().year
    val earliestVehicleYear = currentVehicleYear - 20
    val manufacturingYearOptions = (earliestVehicleYear..currentVehicleYear).map(Int::toString)
    val selectedManufacturingYear = manufacturingYear.toIntOrNull()
    val registrationYearOptions = (
        maxOf(
            earliestVehicleYear,
            selectedManufacturingYear ?: earliestVehicleYear
        )..currentVehicleYear
    ).map(Int::toString)

    LaunchedEffect(manufacturingYear) {
        val manufacturing = manufacturingYear.toIntOrNull()
        val registration = registrationYear.toIntOrNull()
        if (manufacturing != null && registration != null && registration < manufacturing) {
            registrationYear = manufacturing.toString()
        }
    }

    val combinedPhotos = listOf(photoFront, photoSide, photoRear, photoInterior)
        .map { it.trim() }
        .joinToString("|")
        .takeIf { listOf(photoFront, photoSide, photoRear, photoInterior).any { it.isNotBlank() } }

    val galleryPhotos = listOf(galleryFront, gallerySide, galleryRear, galleryInterior)
        .mapIndexedNotNull { index, uri -> uri?.let { index to it } }
        .toMap()

    val frontGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        galleryFront = uri?.toString()
        if (uri != null) photoFront = ""
    }
    val sideGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        gallerySide = uri?.toString()
        if (uri != null) photoSide = ""
    }
    val rearGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        galleryRear = uri?.toString()
        if (uri != null) photoRear = ""
    }
    val interiorGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        galleryInterior = uri?.toString()
        if (uri != null) photoInterior = ""
    }
    val driverPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        driverPhotoUri = uri?.toString()
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
                    CompactFieldRow("Model", model, { model = it }, "Variant", variant, { variant = it })
                    Text(
                        "Model = vehicle series; variant = the specific trim or version.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VendorSelectField("Category", category, listOf("Sedan", "SUV", "Hatchback", "MUV", "Luxury", "Other"), modifier = Modifier.weight(1f), onValueChange = { category = it })
                        VendorSelectField("Seats", seats, (2..8).map { it.toString() }, modifier = Modifier.weight(1f), onValueChange = { seats = it })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VendorSelectField("Transmission", transmission, listOf("Automatic", "Manual"), modifier = Modifier.weight(1f), onValueChange = { transmission = it })
                        VendorSelectField("Fuel type", fuel, listOf("Petrol", "Diesel", "CNG", "Electric", "Hybrid", "Other"), modifier = Modifier.weight(1f), onValueChange = { fuel = it })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VendorSelectField(
                            "Manufacturing year",
                            manufacturingYear,
                            manufacturingYearOptions,
                            modifier = Modifier.weight(1f),
                            onValueChange = { manufacturingYear = it }
                        )
                        VendorSelectField(
                            "Registration year",
                            registrationYear,
                            registrationYearOptions,
                            modifier = Modifier.weight(1f),
                            onValueChange = { registrationYear = it }
                        )
                    }
                    Text(
                        "Vehicle age is limited to 20 years; registration year cannot be before manufacture year.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary
                    )
                    VendorField("Registration number", registrationNumber) { registrationNumber = it }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VendorField("City", city, modifier = Modifier.weight(1f)) { city = it }
                        VendorSelectField(
                            "State",
                            stateName,
                            indianStatesAndUt,
                            modifier = Modifier.weight(1f),
                            onValueChange = { stateName = it }
                        )
                    }
                    VendorField("Pickup address", pickupAddress) { pickupAddress = it }
                    VendorField("Price per day (₹)", pricePerDay) { pricePerDay = it }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Vehicle photos", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Use either an image URL or a photo from your device for each slot.",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        VehiclePhotoField(
                            "Front photo", photoFront, galleryFront,
                            { photoFront = it },
                            {
                                frontGalleryLauncher.launch("image/*")
                            },
                            { galleryFront = null },
                            Modifier.weight(1f)
                        )
                        VehiclePhotoField(
                            "Side photo", photoSide, gallerySide,
                            { photoSide = it },
                            {
                                sideGalleryLauncher.launch("image/*")
                            },
                            { gallerySide = null },
                            Modifier.weight(1f)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        VehiclePhotoField(
                            "Rear photo", photoRear, galleryRear,
                            { photoRear = it },
                            {
                                rearGalleryLauncher.launch("image/*")
                            },
                            { galleryRear = null },
                            Modifier.weight(1f)
                        )
                        VehiclePhotoField(
                            "Interior photo", photoInterior, galleryInterior,
                            { photoInterior = it },
                            {
                                interiorGalleryLauncher.launch("image/*")
                            },
                            { galleryInterior = null },
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(9.dp), color = AppColors.Success.copy(alpha = .10f)) {
                            Icon(Icons.Default.Badge, null, tint = AppColors.Success, modifier = Modifier.padding(7.dp).size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("2. Driver details", style = MaterialTheme.typography.titleMedium)
                            Text("The chauffeur assigned to this vehicle", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Card(
                        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 18.dp, bottomEnd = 10.dp, bottomStart = 10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RentalCarImageTile(
                                driverPhotoUri ?: editingCar?.driverPhotoUrl,
                                Modifier
                                    .size(82.dp)
                                    .clip(RoundedCornerShape(topEnd = 17.dp, topStart = 7.dp, bottomEnd = 7.dp, bottomStart = 7.dp))
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Driver photo", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                Text("Passport-style square photo", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                OutlinedButton(
                                    onClick = { driverPhotoLauncher.launch("image/*") },
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(if (driverPhotoUri != null) "Change" else "Add", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    CompactFieldRow("Driver full name", driverName, { driverName = it }, "Driver mobile", driverMobile, { driverMobile = it })
                    VendorField("Driving licence no.", licenseNumber) { licenseNumber = it }
                    RentalDateField("Licence expiry", licenseExpiry, onValueChange = { licenseExpiry = it }, minDate = LocalDate.now())
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
            val manufacturing = manufacturingYear.toIntOrNull()
            val registration = registrationYear.toIntOrNull()
            val valid = name.isNotBlank() &&
                make.isNotBlank() &&
                model.isNotBlank() &&
                registrationNumber.isNotBlank() &&
                pickupAddress.isNotBlank() &&
                city.isNotBlank() &&
                stateName.isNotBlank() &&
                driverName.isNotBlank() &&
                driverMobile.matches(Regex("[6-9][0-9]{9}")) &&
                licenseNumber.isNotBlank() &&
                licenseExpiry.isNotBlank() &&
                pricePerDay.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true &&
                seats.toIntOrNull()?.let { it in 2..8 } == true &&
                manufacturing != null &&
                registration != null &&
                manufacturing in earliestVehicleYear..currentVehicleYear &&
                registration in manufacturing..currentVehicleYear

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
                            driverPhotoUri,
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
                            driverPhotoUri,
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
    val status = car.approvalStatus?.replace("_", " ")?.uppercase() ?: "—"
    val statusColor = when (car.approvalStatus?.uppercase()) {
        "APPROVED" -> AppColors.Success
        "REJECTED" -> AppColors.Error
        else -> Color(0xFFB45309)
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            Modifier.fillMaxWidth().padding(10.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            LazyColumn(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(car.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF102A43))
                            Text(
                                listOfNotBlank(car.make, car.model, car.variant).joinToString(" ").ifBlank { car.category },
                                color = Color(0xFF2563EB),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(shape = RoundedCornerShape(14.dp), color = statusColor.copy(alpha = .12f)) {
                            Text(status, color = statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Vehicle photos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                rentalPhotoSlots(car.imageUrl).forEach { photo ->
                                    RentalCarImageTile(photo, Modifier.weight(1f).aspectRatio(1.05f))
                                }
                            }
                        }
                    }
                }
                item {
                    RentalDetailSection(
                        title = "Vehicle overview",
                        tint = Color(0xFF1D4ED8),
                        rows = listOf(
                            "Make / model" to listOfNotBlank(car.make, car.model, car.variant).joinToString(" ").ifBlank { "—" },
                            "Category / seats" to (car.category + " / " + car.seats),
                            "Transmission / fuel" to (car.transmission + " / " + (car.fuelType ?: "—")),
                            "Manufacturing year" to (car.manufacturingYear?.toString() ?: "—"),
                            "Registration year" to (car.registrationYear?.toString() ?: "—")
                        )
                    )
                }
                item {
                    RentalDetailSection(
                        title = "Pricing & location",
                        tint = Color(0xFF0F766E),
                        rows = listOf(
                            "Price per day" to ("₹" + car.pricePerDay.setScale(2).toPlainString()),
                            "Registration number" to (car.registrationNumber ?: "—"),
                            "Pickup address" to (car.pickupAddress ?: "—"),
                            "City / state" to listOfNotBlank(car.city, car.state).joinToString(", ").ifBlank { "—" }
                        )
                    )
                }
                car.rejectionReason?.takeIf { it.isNotBlank() }?.let { reason ->
                    item {
                        Surface(shape = RoundedCornerShape(14.dp), color = AppColors.Error.copy(alpha = .08f)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Review note", style = MaterialTheme.typography.labelLarge, color = AppColors.Error, fontWeight = FontWeight.Bold)
                                Text(reason, color = Color(0xFF7F1D1D), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (!car.driverPhotoUrl.isNullOrBlank()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Driver photo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                RentalCarImageTile(car.driverPhotoUrl, Modifier.fillMaxWidth().height(180.dp))
                            }
                        }
                    }
                }
                item {
                    RentalDetailSection(
                        title = "Driver details",
                        tint = AppColors.VendorNavy,
                        rows = listOf(
                            "Name" to car.driverName,
                            "Mobile" to (car.driverMobile ?: "—"),
                            "Licence number" to (car.driverLicenseNumber ?: "—"),
                            "Licence expiry" to (car.driverLicenseExpiry?.takeIf { it.isNotBlank() }?.let { formatRentalDate(it) } ?: "—"),
                            "Driver address" to (car.driverAddress ?: "—")
                        )
                    )
                }
                item {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("Close details")
                    }
                }
            }
        }
    }
}

@Composable
private fun RentalDetailSection(
    title: String,
    tint: Color,
    rows: List<Pair<String, String>>
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tint)
            HorizontalDivider()
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Text(label, modifier = Modifier.weight(.85f), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(value, modifier = Modifier.weight(1.15f), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = Color(0xFF1F2937), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun VendorEarningsCard(
    label: String,
    value: BigDecimal,
    background: Color,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary, maxLines = 1)
            Text("₹" + value.setScale(2).toPlainString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
        }
    }
}

@Composable
private fun RentalEarningsSummaryCard(
    earnings: RentalVendorEarningsResponse?
) {
    if (earnings == null) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Rental earnings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Loading payout summary…", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        return
    }

    fun money(v: BigDecimal): String = "₹" + v.setScale(2).toPlainString()

    @Composable
    fun Metric(label: String, value: BigDecimal, tint: Color, modifier: Modifier) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = tint.copy(alpha = .09f),
            modifier = modifier
        ) {
            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Bold)
                Text(money(value), style = MaterialTheme.typography.titleMedium, color = tint, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    fun Period(title: String, period: RentalVendorEarningsPeriodResponse, monthly: Boolean = false) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Metric("Gross", period.grossAmount, Color(0xFF334155), Modifier.weight(1f))
                    Metric("Platform fee", period.platformFeeAmount, Color(0xFFD97706), Modifier.weight(1f))
                    Metric("Net earning", period.vendorNetAmount, AppColors.Success, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (monthly) "Bookings this month" else "Completed today",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (monthly) period.bookingCount.toString() else period.completedBookingCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!monthly) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Upcoming confirmed bookings", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(earnings.upcomingBookingCount.toString(), color = AppColors.PrimaryDark, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Period("Today", earnings.today)
    Spacer(Modifier.height(8.dp))
    Period("This month", earnings.monthly, monthly = true)
}

@Composable
private fun RentalEarningsPeriodCard(
    payouts: List<RentalVendorPayoutResponse>,
    isToday: Boolean
) {
    val zone = ZoneId.of("Asia/Kolkata")
    val now = java.time.ZonedDateTime.now(zone)
    val from = if (isToday) now.toLocalDate().atStartOfDay(zone) else now.toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
    val paid = payouts.filter { payout ->
        payout.status.equals("PAID", true) &&
            runCatching {
                val created = java.time.Instant.parse(payout.createdAt).atZone(zone)
                !created.isBefore(from) && !created.isAfter(now)
            }.getOrDefault(false)
    }
    val gross = paid.fold(BigDecimal.ZERO) { total, payout -> total + payout.grossAmount }
    val net = paid.fold(BigDecimal.ZERO) { total, payout -> total + payout.vendorNetAmount }
    val countLabel = paid.size.toString() + " completed rental" + if (paid.size == 1) "" else "s"

    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (isToday) "Today • " + now.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
                else now.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Earnings received", color = AppColors.TextSecondary)
                    Text("₹" + net.setScale(2).toPlainString(), style = MaterialTheme.typography.headlineSmall, color = AppColors.Success, fontWeight = FontWeight.Bold)
                    Text(countLabel, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Rental volume", color = AppColors.TextSecondary)
                    Text("₹" + gross.setScale(2).toPlainString(), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun RentalVendorProfileDialog(
    fullName: String,
    businessName: String,
    address: String,
    city: String,
    stateName: String,
    pin: String,
    pan: String,
    bankName: String,
    bankAccount: String,
    bankIfsc: String,
    upi: String,
    primaryPayout: String,
    saving: Boolean,
    error: String?,
    onFullName: (String) -> Unit,
    onBusinessName: (String) -> Unit,
    onAddress: (String) -> Unit,
    onCity: (String) -> Unit,
    onState: (String) -> Unit,
    onPin: (String) -> Unit,
    onPan: (String) -> Unit,
    onBankName: (String) -> Unit,
    onBankAccount: (String) -> Unit,
    onBankIfsc: (String) -> Unit,
    onUpi: (String) -> Unit,
    onPrimaryPayout: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit vendor profile") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { CompactFieldRow("Full name", fullName, onFullName, "Business name", businessName, onBusinessName) }
                item { VendorField("Address", address, onValueChange = onAddress) }
                item { CompactFieldRow("City", city, onCity, "State", stateName, onState) }
                item { CompactFieldRow("PIN", pin, onPin, "PAN", pan, onPan) }
                item { Text("Payout details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                item { VendorField("Bank name", bankName, onValueChange = onBankName) }
                item { CompactFieldRow("Bank account", bankAccount, onBankAccount, "IFSC", bankIfsc, onBankIfsc) }
                item { VendorField("UPI ID", upi, onValueChange = onUpi) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Primary payout method", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            FilterChip(selected = primaryPayout == "UPI", onClick = { onPrimaryPayout("UPI") }, label = { Text("UPI") }, enabled = upi.isNotBlank())
                            FilterChip(selected = primaryPayout == "BANK", onClick = { onPrimaryPayout("BANK") }, label = { Text("Bank") }, enabled = bankAccount.isNotBlank() && bankIfsc.isNotBlank())
                        }
                    }
                }
                error?.let { message -> item { Text(message, color = AppColors.Error, style = MaterialTheme.typography.bodySmall) } }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !saving &&
                    fullName.isNotBlank() && address.isNotBlank() && city.isNotBlank() && stateName.isNotBlank() && pin.isNotBlank() &&
                    (primaryPayout.isBlank() ||
                        (primaryPayout == "UPI" && upi.isNotBlank()) ||
                        (primaryPayout == "BANK" && bankAccount.isNotBlank() && bankIfsc.isNotBlank()))
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save changes")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } }
    )
}

@Composable
fun RentalBookingScreen(
    car: RentalCarResponse,
    state: RentalUiState,
    wallet: WalletResponse?,
    onQuote: (RentalBookingQuoteRequest, (RentalBookingQuoteResponse) -> Unit) -> Unit,
    onBack: () -> Unit,
    onAddMoney: () -> Unit,
    onRefreshWallet: () -> Unit,
    onConfirm: (RentalBookingRequest, () -> Unit) -> Unit,
    initialStart: String? = null,
    initialEnd: String? = null
) {
    var pickup by remember(car.id, initialStart, initialEnd) { mutableStateOf(car.pickupAddress.orEmpty()) }
    var drop by remember(car.id, initialStart, initialEnd) { mutableStateOf(car.city.orEmpty()) }
    var start by remember(car.id, initialStart, initialEnd) { mutableStateOf(initialStart.orEmpty()) }
    var end by remember(car.id, initialStart, initialEnd) { mutableStateOf(initialEnd.orEmpty()) }
    var quote by remember(car.id, initialStart, initialEnd) { mutableStateOf<RentalBookingQuoteResponse?>(null) }

    val parsedStart = runCatching { LocalDateTime.parse(start, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull()
    val parsedEnd = runCatching { LocalDateTime.parse(end, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull()
    val validWindow = parsedStart != null && parsedEnd != null && parsedEnd.isAfter(parsedStart) && !parsedStart.isBefore(LocalDateTime.now())
    val walletKnown = wallet != null
    val available = wallet?.availableBalance ?: BigDecimal.ZERO
    val insufficient = walletKnown && quote != null && available < quote!!.total

    fun clearQuote() { quote = null }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Book with driver", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Your payment will come from the available wallet balance.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RentalCarImageTile(car.imageUrl?.split("|")?.firstOrNull(), Modifier.size(76.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(car.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(listOfNotBlank(car.make, car.model, car.variant).joinToString(" ").ifBlank { car.category }, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text("Chauffeur: " + car.driverName, color = AppColors.PrimaryDark, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item { VendorField("Pickup location", pickup, onValueChange = { pickup = it; clearQuote() }) }
        item { VendorField("Drop location", drop, onValueChange = { drop = it; clearQuote() }) }
        item { RentalDateTimeField("Start date & time", start, { start = it; clearQuote() }) }
        item { RentalDateTimeField("End date & time", end, { end = it; clearQuote() }) }
        item {
            if (!validWindow) {
                Text(
                    "Choose a future start and an end date/time later than the start.",
                    color = if (start.isNotBlank() || end.isNotBlank()) AppColors.Error else AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    "Pricing is per day (24 hours). Any partial day is charged as one full day; time also controls availability.",
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item {
            if (quote == null) {
                Button(
                    enabled = !state.saving && pickup.isNotBlank() && drop.isNotBlank() && validWindow,
                    onClick = {
                        val request = RentalBookingQuoteRequest(
                            car.id,
                            pickup.trim(),
                            drop.trim(),
                            start,
                            end
                        )
                        onQuote(request) { response ->
                            if (
                                car.id == request.carId &&
                                pickup.trim() == request.pickupLocation &&
                                drop.trim() == request.dropLocation &&
                                start == request.startDate &&
                                end == request.endDate
                            ) {
                                quote = response
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("Check fare", fontWeight = FontWeight.Bold)
                }
            }
        }
        quote?.let { q ->
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceWarm.copy(alpha = .65f))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Fare summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(q.days.toString() + " day(s) × ₹" + q.pricePerDay.setScale(2), color = AppColors.TextSecondary)
                            }
                            Text("₹" + q.total.setScale(2), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AppColors.Debit)
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Available balance", color = AppColors.TextSecondary)
                            Text(
                                if (walletKnown) "₹" + available.setScale(2) else "Unavailable",
                                fontWeight = FontWeight.Bold,
                                color = if (walletKnown) MaterialTheme.colorScheme.onSurface else AppColors.Error
                            )
                        }
                        Text("Payment method: Wallet", color = AppColors.PrimaryDark, fontWeight = FontWeight.SemiBold)
                        when {
                            !walletKnown -> {
                                Card(
                                    shape = RoundedCornerShape(13.dp),
                                    colors = CardDefaults.cardColors(containerColor = AppColors.Warning.copy(alpha = .08f))
                                ) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text("Wallet balance unavailable", color = AppColors.Warning, fontWeight = FontWeight.Bold)
                                        Text(
                                            "We cannot safely confirm this booking until the latest available wallet balance is loaded.",
                                            color = AppColors.TextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        OutlinedButton(
                                            onClick = onRefreshWallet,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(11.dp)
                                        ) { Text("Refresh wallet") }
                                    }
                                }
                            }
                            insufficient -> {
                                Card(shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Error.copy(alpha = .07f))) {
                                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text("Not enough available balance", color = AppColors.Error, fontWeight = FontWeight.Bold)
                                        Text("Add ₹" + q.total.subtract(available).max(BigDecimal.ZERO).setScale(2) + " to complete this booking.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                        OutlinedButton(onClick = onAddMoney, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) { Text("Add money") }
                                    }
                                }
                            }
                            else -> {
                                Text("₹" + q.total.setScale(2) + " will be deducted from your available wallet balance when you confirm.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                Button(
                                    enabled = !state.saving && walletKnown,
                                    onClick = {
                                        onConfirm(
                                            RentalBookingRequest(
                                                UUID.randomUUID().toString(),
                                                car.id,
                                                pickup.trim(),
                                                drop.trim(),
                                                start,
                                                end,
                                                "WALLET"
                                            ),
                                            onBack
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                    else Text("Confirm booking", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        TextButton(onClick = { clearQuote() }, enabled = !state.saving, modifier = Modifier.align(Alignment.End)) { Text("Recheck fare") }
                    }
                }
            }
        }
        state.error?.let { item { Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodySmall) } }
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
                    enabled = !state.saving,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)
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
                    Text("My Bookings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Your chauffeur-driven rental bookings", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Refresh bookings") }
            }
        }
        state.error?.let { item { Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodySmall) } }
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
            item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) } }
        }
        if (!state.loading && state.bookings.isEmpty()) {
            item {
                MpayEmptyState(
                    title = "No rental bookings yet",
                    message = "Confirmed chauffeur-driven rentals will appear here.",
                    icon = { Icon(Icons.Default.DirectionsCar, null, tint = AppColors.Rental, modifier = Modifier.size(30.dp)) }
                )
            }
        }
        val filteredBookings = state.bookings.filter { statusFilter == "ALL" || it.status.equals(statusFilter, true) }
        if (!state.loading && filteredBookings.isEmpty() && state.bookings.isNotEmpty()) {
            item { MpayEmptyState(title = "No matching bookings", message = "Try another status filter.") }
        }

        items(filteredBookings, key = { it.bookingId }) { booking ->
            val status = booking.status.uppercase()
            val (statusLabel, rideCompleted) = rentalBookingDisplayStatus(booking)
            val displayStatusCode = if (rideCompleted) "COMPLETED" else status
            val credit = status == "CANCELLED" || status == "REFUNDED"
            Card(shape = RoundedCornerShape(17.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RentalVehicleGallery(
                        booking.carImageUrl,
                        Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsCar, null, tint = AppColors.Rental, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(booking.carName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = rentalStatusColor(displayStatusCode).copy(alpha = .12f)
                        ) {
                            Text(
                                statusLabel,
                                color = rentalStatusColor(displayStatusCode),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
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
                    Text("Booking " + booking.bookingId, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("Driver", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                            Text(booking.driverName + (booking.driverMobile?.let { " • " + it } ?: ""), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Payment", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                            Text(booking.paymentMethod, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("Trip", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                            Text(booking.pickup + " → " + booking.drop, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Total", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                            MpayFinancialAmount(booking.total, credit = credit)
                        }
                    }
                    Text(
                        formatRentalBookingDateTime(booking.startDate) + " → " + formatRentalBookingDateTime(booking.endDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                    Text("Booked " + formatRentalBookingDateTime(booking.createdAt), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)

                    if (status == "CONFIRMED" && runCatching { LocalDateTime.parse(booking.startDate) }.getOrNull()?.isAfter(LocalDateTime.now()) == true) {
                        OutlinedButton(
                            onClick = { cancelBookingId = booking.bookingId },
                            enabled = !state.saving,
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(11.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Error)
                        ) {
                            Text("Cancel booking", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
