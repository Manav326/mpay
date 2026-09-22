package com.recharge.client.features.rental

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.recharge.client.core.model.*
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.viewmodel.RentalUiState

@Composable
fun RentalVendorOnboardingScreen(
    state: RentalUiState,
    onSubmit: (RentalVendorOnboardingRequest, () -> Unit) -> Unit,
    onBack: () -> Unit,
    onAddVehicle: () -> Unit,
    onRefreshVehicles: () -> Unit,
    onRefreshPayouts: () -> Unit,
    onAddVehicleWithCar: (RentalCarResponse) -> Unit = {}
) {
    var fullName by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.fullName.orEmpty()) }
    var businessName by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.businessName.orEmpty()) }
    var address by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.address.orEmpty()) }
    var city by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.city.orEmpty()) }
    var stateName by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.state.orEmpty()) }
    var pin by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.pinCode.orEmpty()) }
    var pan by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.panNumber.orEmpty()) }
    var upi by remember(state.vendor?.vendorId) { mutableStateOf(state.vendor?.payoutUpiId.orEmpty()) }

    if (state.vendor?.status?.uppercase() == "VERIFIED") {
        LaunchedEffect(state.vendor?.vendorId) { onRefreshVehicles(); onRefreshPayouts() }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                    Column {
                        Text("Vendor dashboard", style = MaterialTheme.typography.headlineSmall)
                        Text("Your chauffeur-driven rental fleet", color = AppColors.TextSecondary)
                    }
                }
            }
            state.vendor?.let { v ->
                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Vendor profile", style = MaterialTheme.typography.titleLarge)
                            Text("Status: VERIFIED")
                            Text("Name: " + (v.fullName ?: "—"))
                            v.businessName?.let { Text("Business / fleet: " + it) }
                            Text("Location: " + (v.city ?: "—") + ", " + (v.state ?: "—") + " " + (v.pinCode ?: ""))
                            Text("Vehicles: " + v.vehicleCount)
                        }
                    }
                }
            }
            item {
                val gross = state.payouts.fold(BigDecimal.ZERO) { total, payout -> total + payout.grossAmount }
                val fees = state.payouts.fold(BigDecimal.ZERO) { total, payout -> total + payout.platformFeeAmount }
                val net = state.payouts.filter { it.status == "PAID" }.fold(BigDecimal.ZERO) { total, payout -> total + payout.vendorNetAmount }
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Rental earnings", style = MaterialTheme.typography.titleLarge)
                        Text("Gross bookings: ₹" + gross.setScale(2).toPlainString())
                        Text("Platform fee: ₹" + fees.setScale(2).toPlainString(), color = AppColors.TextSecondary)
                        Text("Paid to you: ₹" + net.setScale(2).toPlainString(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("My vehicles", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onRefreshVehicles, enabled = !state.loading) { Text("Refresh") }
                }
            }
            if (state.loading && state.vendorCars.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (state.vendorCars.isEmpty()) {
                item { Text("No vehicles submitted yet.", color = AppColors.TextSecondary) }
            } else {
                items(state.vendorCars, key = { it.id }) { car ->
                    val status = car.approvalStatus?.uppercase() ?: "PENDING_REVIEW"
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DirectionsCar, null, tint = AppColors.Primary)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(car.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        car.category + " • " + car.seats + " seats • " + car.transmission,
                                        color = AppColors.TextSecondary
                                    )
                                }
                                Text(status)
                            }
                            Text("Driver: " + car.driverName)
                            Text("₹" + car.pricePerDay.setScale(0) + " / day • " + (car.city ?: "Location unavailable"), color = AppColors.TextSecondary)
                            car.rejectionReason?.let { Text("Review note: " + it, color = AppColors.Error) }
                            if (status == "APPROVED") {
                                Text("This vehicle is live in the customer marketplace.", color = AppColors.TextSecondary)
                            } else if (status == "PENDING_REVIEW") {
                                Text("Waiting for admin review.", color = AppColors.TextSecondary)
                            } else if (status == "REJECTED") {
                                Text("Vehicle requires correction and resubmission.", color = AppColors.Error)
                                OutlinedButton(onClick = { onAddVehicleWithCar(car) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Correct and resubmit")
                                }
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = onAddVehicle, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("Add another vehicle")
                }
            }
            state.error?.let { item { Text(it, color = AppColors.Error) } }
        }
    } else {
        val vendorStatus = state.vendor?.status?.uppercase() ?: "NOT_ONBOARDED"
        val isPending = vendorStatus == "PENDING"
        val isRejected = vendorStatus == "REJECTED"
        val title = when {
            isPending -> "Vendor application"
            isRejected -> "Vendor application"
            else -> "Become a Vendor"
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                }
            }
            if (isPending) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E6))
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(
                                "Submitted for verification",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF9A6408)
                            )
                            Text(
                                "Status: PENDING",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF9A6408)
                            )
                            Text(
                                "Your vendor application has been saved successfully and is waiting for admin verification. You do not need to submit it again.",
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        if (isRejected)
                            "Your application was returned for correction."
                        else
                            "Rent your car with a professional driver through mPay.",
                        color = AppColors.TextSecondary
                    )
                }
            }
            if (isRejected) {
                item { state.vendor?.rejectionReason?.let { Text("Admin note: " + it, color = AppColors.Error) } }
            }

            val fieldsEnabled = !isPending
            item { VendorField("Full name", fullName, fieldsEnabled) { fullName = it } }
            item { VendorField("Business / fleet name (optional)", businessName, fieldsEnabled) { businessName = it } }
            item { VendorField("Address", address, fieldsEnabled) { address = it } }
            item { VendorField("City", city, fieldsEnabled) { city = it } }
            item { VendorField("State", stateName, fieldsEnabled) { stateName = it } }
            item { VendorField("PIN code", pin, fieldsEnabled) { pin = it } }
            item { VendorField("PAN (optional for now)", pan, fieldsEnabled) { pan = it } }
            item { VendorField("Payout UPI (optional)", upi, fieldsEnabled) { upi = it } }

            state.error?.let { item { Text(it, color = AppColors.Error) } }
            item {
                Button(
                    onClick = {
                        onSubmit(
                            RentalVendorOnboardingRequest(
                                vendorType = "INDIVIDUAL",
                                fullName = fullName,
                                businessName = businessName.ifBlank { null },
                                address = address,
                                city = city,
                                state = stateName,
                                pinCode = pin,
                                panNumber = pan.ifBlank { null },
                                payoutUpiId = upi.ifBlank { null }
                            ), {}
                        )
                    },
                    enabled = !state.saving && !isPending &&
                        fullName.isNotBlank() && address.isNotBlank() &&
                        city.isNotBlank() && stateName.isNotBlank() && pin.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                ) {
                    when {
                        state.saving -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        isPending -> Text("Submitted • Pending verification")
                        isRejected -> Text("Resubmit for verification")
                        else -> Text("Submit for verification")
                    }
                }
            }
        }
    }
}
@Composable
private fun VendorField(
    label: String,
    value: String,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

private val rentalDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

private fun rentalStatusColor(status: String): Color = when (status.uppercase()) {
    "CONFIRMED", "COMPLETED", "REFUNDED", "PAID" -> AppColors.Success
    "CANCELLED", "REJECTED", "FAILED", "EXPIRED" -> AppColors.Error
    "PENDING", "PROCESSING" -> Color(0xFFD97706)
    "IN_PROGRESS", "ACTIVE" -> Color(0xFF2563EB)
    else -> AppColors.TextSecondary
}

private fun rentalBookingShareText(booking: RentalBookingResponse): String = listOf(
    "mPay Car Rental Booking",
    "Booking ID: ${booking.bookingId}",
    "Car: ${booking.carName}",
    "From: ${booking.pickup}",
    "To: ${booking.drop}",
    "Start: ${booking.startDate}",
    "End: ${booking.endDate}",
    "Amount: ₹${booking.total.setScale(2).toPlainString()}",
    "Status: ${booking.status.uppercase()}"
).joinToString(" | ")

private val rentalDateTimeDisplayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

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
private fun RentalDateTimeField(label: String, value: String, onValueChange: (String) -> Unit) {
    val context = LocalContext.current
    val display = runCatching {
        LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).format(rentalDateTimeDisplayFormatter)
    }.getOrElse { "Select date & time" }
    OutlinedButton(
        onClick = { showDateTimePicker(context, value, onValueChange) },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(display, style = MaterialTheme.typography.bodyLarge)
        }
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
    onRefresh: () -> Unit
) {
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    val canSearch = runCatching { LocalDateTime.parse(start, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.isSuccess &&
        runCatching { LocalDateTime.parse(end, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.isSuccess &&
        runCatching {
            val s = LocalDateTime.parse(start, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val e = LocalDateTime.parse(end, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            e.isAfter(s) && !s.isBefore(LocalDateTime.now())
        }.getOrDefault(false)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Car Rental Marketplace", style = MaterialTheme.typography.headlineSmall)
                    Text("Find cars available for your selected trip time.", color = AppColors.TextSecondary)
                }
                IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Refresh") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Trip availability", style = MaterialTheme.typography.titleLarge)
                    RentalDateTimeField("From", start) { start = it }
                    RentalDateTimeField("To", end) { end = it }
                    Text(
                        "Only cars available for the selected time window will be shown. Final availability is checked again before booking.",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { onSearch(start, end) },
                        enabled = canSearch && !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Search available cars")
                    }
                }
            }
        }
        state.error?.let { item { Text(it, color = AppColors.Error) } }
        if (state.loading && state.cars.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
        if (!state.loading && state.cars.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F8FC))
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFE2F2FC)) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = Color(0xFF1677B8),
                                modifier = Modifier.padding(14.dp).size(30.dp)
                            )
                        }
                        Text(
                            if (canSearch) "No cars available for this time window." else "Select your trip time to search.",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "Cars already booked for an overlapping period are removed from these results.",
                            color = AppColors.TextSecondary
                        )
                        OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(12.dp)) {
                            Text("Check again")
                        }
                    }
                }
            }
        }
        items(state.cars, key = { it.id }) { car ->
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp), color = AppColors.Primary.copy(alpha = .10f)) {
                            Icon(Icons.Default.DirectionsCar, null, tint = AppColors.Primary, modifier = Modifier.padding(12.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(car.name, style = MaterialTheme.typography.titleLarge)
                            Text(car.category + " • " + car.seats + " seats • " + car.transmission, color = AppColors.TextSecondary)
                        }
                    }
                    Text("Driver: " + car.driverName, style = MaterialTheme.typography.titleMedium)
                    Text((car.fuelType ?: "Fuel") + " • " + (car.city ?: "Location unavailable"), color = AppColors.TextSecondary)
                    Text("₹" + car.pricePerDay.setScale(0) + " / day", style = MaterialTheme.typography.headlineSmall)
                    Button(
                        onClick = { onBook(car, start, end) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Person, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Book with driver")
                    }
                }
            }
        }
    }
}


@Composable
fun RentalVehicleOnboardingScreen(
    state: RentalUiState,
    onSubmit: (RentalVehicleOnboardingRequest, () -> Unit) -> Unit,
    onBack: () -> Unit,
    editingCar: RentalCarResponse? = null,
    onResubmit: ((String, RentalVehicleUpdateRequest, () -> Unit) -> Unit)? = null
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
    var imageUrl by remember(editingCar?.id) { mutableStateOf(editingCar?.imageUrl.orEmpty()) }
    var driverName by remember(editingCar?.id) { mutableStateOf(editingCar?.driverName.orEmpty()) }
    var driverMobile by remember(editingCar?.id) { mutableStateOf(editingCar?.driverMobile.orEmpty()) }
    var licenseNumber by remember(editingCar?.id) { mutableStateOf(editingCar?.driverLicenseNumber.orEmpty()) }
    var licenseExpiry by remember(editingCar?.id) { mutableStateOf(editingCar?.driverLicenseExpiry.orEmpty()) }
    var driverAddress by remember(editingCar?.id) { mutableStateOf(editingCar?.driverAddress.orEmpty()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }; Text(if (editingCar == null) "Add vehicle" else "Correct vehicle", style = MaterialTheme.typography.headlineSmall) } }
        item { Text(if (editingCar == null) "Submit the vehicle and assigned chauffeur for admin review." else "Correct the rejected details and resubmit this same vehicle for admin review.", color = AppColors.TextSecondary) }
        item { Text("Vehicle details", style = MaterialTheme.typography.titleLarge) }
        item { VendorField("Vehicle name", name) { name = it } }
        item { VendorField("Make", make) { make = it } }
        item { VendorField("Model", model) { model = it } }
        item { VendorField("Variant (optional)", variant) { variant = it } }
        item { VendorField("Category", category) { category = it } }
        item { VendorField("Seats", seats) { seats = it } }
        item { VendorField("Transmission", transmission) { transmission = it } }
        item { VendorField("Fuel type", fuel) { fuel = it } }
        item { VendorField("Manufacturing year", manufacturingYear) { manufacturingYear = it } }
        item { VendorField("Registration year", registrationYear) { registrationYear = it } }
        item { VendorField("Registration number", registrationNumber) { registrationNumber = it } }
        item { VendorField("Pickup address", pickupAddress) { pickupAddress = it } }
        item { VendorField("City", city) { city = it } }
        item { VendorField("State", stateName) { stateName = it } }
        item { VendorField("Price per day (₹)", pricePerDay) { pricePerDay = it } }
        item { VendorField("Vehicle image URL (optional)", imageUrl) { imageUrl = it } }
        item { Text("Driver details", style = MaterialTheme.typography.titleLarge) }
        item { VendorField("Driver full name", driverName) { driverName = it } }
        item { VendorField("Driver mobile", driverMobile) { driverMobile = it } }
        item { VendorField("Driving licence number", licenseNumber) { licenseNumber = it } }
        item { RentalDateTimeField("Licence expiry", licenseExpiry) { licenseExpiry = it } }
        item { VendorField("Driver address (optional)", driverAddress) { driverAddress = it } }
        state.error?.let { item { Text(it, color = AppColors.Error) } }
        item {
            val valid = name.isNotBlank() && make.isNotBlank() && model.isNotBlank() && registrationNumber.isNotBlank() &&
                pickupAddress.isNotBlank() && city.isNotBlank() && stateName.isNotBlank() && driverName.isNotBlank() &&
                driverMobile.isNotBlank() && licenseNumber.isNotBlank() && licenseExpiry.isNotBlank() &&
                pricePerDay.toBigDecimalOrNull() != null && seats.toIntOrNull() != null &&
                manufacturingYear.toIntOrNull() != null && registrationYear.toIntOrNull() != null
            Button(
                onClick = {
                    val driver = RentalDriverRequest(
                        driverName.trim(), driverMobile.trim(), licenseNumber.trim(), licenseExpiry.trim(), driverAddress.ifBlank { null }
                    )
                    if (editingCar != null && onResubmit != null) {
                        onResubmit(
                            editingCar.id,
                            RentalVehicleUpdateRequest(
                                name = name.trim(), category = category.trim(), seats = seats.toInt(), transmission = transmission.trim(),
                                fuelType = fuel.trim(), manufacturingYear = manufacturingYear.toInt(), registrationYear = registrationYear.toInt(),
                                registrationNumber = registrationNumber.trim(), make = make.trim(), model = model.trim(),
                                variant = variant.ifBlank { null }, pickupAddress = pickupAddress.trim(), city = city.trim(), state = stateName.trim(),
                                pricePerDay = pricePerDay.toBigDecimal(), imageUrl = imageUrl.ifBlank { null }, driver = driver
                            ),
                            onBack
                        )
                    } else {
                        onSubmit(
                            RentalVehicleOnboardingRequest(
                                name = name.trim(), category = category.trim(), seats = seats.toInt(), transmission = transmission.trim(),
                                fuelType = fuel.trim(), manufacturingYear = manufacturingYear.toInt(), registrationYear = registrationYear.toInt(),
                                registrationNumber = registrationNumber.trim(), make = make.trim(), model = model.trim(),
                                variant = variant.ifBlank { null }, pickupAddress = pickupAddress.trim(), city = city.trim(), state = stateName.trim(),
                                pricePerDay = pricePerDay.toBigDecimal(), imageUrl = imageUrl.ifBlank { null }, driver = driver
                            ),
                            onBack
                        )
                    }
                },
                enabled = valid && !state.saving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (editingCar == null) "Submit vehicle for review" else "Resubmit vehicle for review") }
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
        item { RentalDateTimeField("Start date & time", start) { start = it } }
        item { RentalDateTimeField("End date & time", end) { end = it } }
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
                Button(onClick = {
                    onCancel(bookingId) {
                        cancelBookingId = null
                        onWalletRefresh()
                        onRefresh()
                    }
                }, enabled = !state.saving) { Text(if (state.saving) "Cancelling…" else "Cancel booking") }
            },
            dismissButton = { TextButton(onClick = { cancelBookingId = null }, enabled = !state.saving) { Text("Keep booking") } }
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
            val filters = listOf("ALL", "CONFIRMED", "CANCELLED", "COMPLETED", "PENDING")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = statusFilter.equals(filter, true),
                        onClick = { statusFilter = filter },
                        label = { Text(if (filter == "ALL") "All" else filter.replace("_", " ")) }
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
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsCar, null, tint = AppColors.Primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(booking.carName, style = MaterialTheme.typography.titleLarge)
                            Text("Booking " + booking.bookingId, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(shape = RoundedCornerShape(20.dp), color = statusColor.copy(alpha = .12f)) {
                            Text(booking.status.uppercase(), style = MaterialTheme.typography.labelLarge, color = statusColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                    Text("Driver: " + booking.driverName + (booking.driverMobile?.let { " • " + it } ?: ""))
                    Text(booking.pickup + " → " + booking.drop, color = AppColors.TextSecondary)
                    Text(booking.startDate + " to " + booking.endDate, color = AppColors.TextSecondary)
                    Text("₹" + booking.total.setScale(2).toPlainString() + " • From your wallet", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            clipboard.setText(AnnotatedString(rentalBookingShareText(booking)))
                            copiedBookingId = booking.bookingId
                        }) {
                            Icon(if (copiedBookingId == booking.bookingId) Icons.Default.Check else Icons.Default.ContentCopy, null)
                            Spacer(Modifier.width(5.dp))
                            Text(if (copiedBookingId == booking.bookingId) "Copied" else "Copy details")
                        }
                        if (booking.status.equals("CONFIRMED", true) && runCatching { LocalDateTime.parse(booking.startDate) }.getOrNull()?.isAfter(LocalDateTime.now()) == true) {
                            OutlinedButton(onClick = { cancelBookingId = booking.bookingId }, enabled = !state.saving) {
                                Text("Cancel", color = AppColors.Error)
                            }
                        }
                    }
                }
            }
        }
    }
}

