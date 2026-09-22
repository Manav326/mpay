package com.recharge.client.features.rental

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
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
    onRefresh: () -> Unit,
    onSearch: (String, String) -> Unit
) {
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var searchApplied by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Text("Car Rental Marketplace", style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            Text(
                "Choose when you need the car. We will only show cars available for that exact time window.",
                color = AppColors.TextSecondary
            )
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F4FF))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Trip timing", style = MaterialTheme.typography.titleLarge)
                    RentalDateTimeField("From", start) { start = it; searchApplied = false }
                    RentalDateTimeField("To", end) { end = it; searchApplied = false }
                    Text(
                        "Place-based filtering will be added later. For now, availability is based on the selected date and time.",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        enabled = !state.loading &&
                            runCatching { LocalDateTime.parse(start, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.isSuccess &&
                            runCatching { LocalDateTime.parse(end, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.isSuccess,
                        onClick = {
                            onSearch(start, end)
                            searchApplied = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Find available cars")
                    }
                }
            }
        }

        state.error?.let { item { Text(it, color = AppColors.Error) } }

        if (!searchApplied) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F8FC))) {
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
                        Text("Select your trip time", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Choose the From and To date and time above to see cars that are actually available for your trip.",
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }
        } else if (state.loading && state.cars.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (!state.loading && state.cars.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F8FC))) {
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
                        Text("No cars available for this time.", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "There are no approved chauffeur-driven cars available for your selected date and time. Try a different time window.",
                            color = AppColors.TextSecondary
                        )
                        OutlinedButton(
                            onClick = { searchApplied = false; start = ""; end = ""; onRefresh() },
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Change time") }
                    }
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Available cars", style = MaterialTheme.typography.titleLarge)
                        Text("Matched to your selected time window", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { searchApplied = false }) { Text("Change time") }
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
}
@Composable
fun RentalBookingScreen(
    car: RentalCarResponse,
    state: RentalUiState,
    initialStart: String = "",
    initialEnd: String = "",
    onQuote: (RentalBookingQuoteRequest, (RentalBookingQuoteResponse) -> Unit) -> Unit,
    onBack: () -> Unit,
    onConfirm: (RentalBookingRequest, () -> Unit) -> Unit
) {
    var pickup by remember(car.id) { mutableStateOf(car.pickupAddress.orEmpty()) }
    var drop by remember(car.id) { mutableStateOf(car.city.orEmpty()) }
    var start by remember(car.id, initialStart) { mutableStateOf(initialStart) }
    var end by remember(car.id, initialEnd) { mutableStateOf(initialEnd) }
    var quote by remember(car.id, initialStart, initialEnd) { mutableStateOf<RentalBookingQuoteResponse?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }; Text("Book with driver", style = MaterialTheme.typography.headlineSmall) } }
        item { Text(car.name, style = MaterialTheme.typography.titleLarge) }
        item { Text("Driver: " + car.driverName + (car.driverMobile?.let { " · " + it } ?: ""), color = AppColors.TextSecondary) }
        item { VendorField("Pickup location", pickup) { pickup = it; quote = null } }
        item { VendorField("Drop location", drop) { drop = it; quote = null } }
        item { RentalDateTimeField("Start date & time", start) { start = it; quote = null } }
        item { RentalDateTimeField("End date & time", end) { end = it; quote = null } }
        item {
            Text(
                "Pricing is per day (24 hours). Any partial day is charged as one full day; time is used for availability and the exact rental duration.",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        quote?.let { q ->
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F4FF))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Fare summary", style = MaterialTheme.typography.titleLarge)
                        Text("${q.days} day(s) × ₹${q.pricePerDay}")
                        Text("Total: ₹${q.total}", style = MaterialTheme.typography.titleLarge)
                        Text("Payment source: From your wallet", color = Color(0xFF6D4AC4), style = MaterialTheme.typography.titleMedium)
                        Text("The rental amount will be debited from your available wallet balance.")
                        Button(
                            enabled = !state.saving,
                            onClick = {
                                onConfirm(
                                    RentalBookingRequest(UUID.randomUUID().toString(), car.id, pickup.trim(), drop.trim(), start, end, "WALLET"),
                                    onBack
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Confirm booking")
                        }
                    }
                }
            }
        } ?: item {
            Button(
                enabled = !state.saving &&
                    pickup.isNotBlank() && drop.isNotBlank() &&
                    runCatching { LocalDateTime.parse(start, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.isSuccess &&
                    runCatching { LocalDateTime.parse(end, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.isSuccess,
                onClick = { onQuote(RentalBookingQuoteRequest(car.id, pickup.trim(), drop.trim(), start, end)) { quote = it } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Check fare") }
        }
        state.error?.let { item { Text(it, color = AppColors.Error) } }
    }
}
@Composable
fun RentalMyBookingsScreen(state: RentalUiState, onRefresh: () -> Unit, onBack: () -> Unit) {
    LaunchedEffect(Unit) { onRefresh() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column {
                    Text("My Bookings", style = MaterialTheme.typography.headlineSmall)
                    Text("Your chauffeur-driven rental bookings", color = AppColors.TextSecondary)
                }
            }
        }
        state.error?.let { item { Text(it, color = AppColors.Error) } }
        if (state.loading && state.bookings.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
        if (!state.loading && state.bookings.isEmpty()) {
            item { Text("No rental bookings yet.", color = AppColors.TextSecondary) }
        }
        items(state.bookings, key = { it.bookingId }) { booking ->
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsCar, null, tint = AppColors.Primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(booking.carName, style = MaterialTheme.typography.titleLarge)
                            Text("Booking " + booking.bookingId, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(booking.status, style = MaterialTheme.typography.labelLarge, color = AppColors.Primary)
                    }
                    Text("Driver: " + booking.driverName + (booking.driverMobile?.let { " • " + it } ?: ""))
                    Text(booking.pickup + " → " + booking.drop, color = AppColors.TextSecondary)
                    Text(booking.startDate.toString() + " to " + booking.endDate.toString(), color = AppColors.TextSecondary)
                    Text("₹" + booking.total.setScale(2).toPlainString() + " • " + booking.paymentMethod, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}