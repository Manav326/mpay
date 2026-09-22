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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                    Text("Become a Vendor", style = MaterialTheme.typography.headlineSmall)
                }
            }
            item {
                Text(
                    if (state.vendor?.status?.uppercase() == "REJECTED")
                        "Your application was returned for correction."
                    else
                        "Rent your car with a professional driver through mPay.",
                    color = AppColors.TextSecondary
                )
            }
            if (state.vendor?.status?.uppercase() == "REJECTED") {
                item { state.vendor?.rejectionReason?.let { Text("Admin note: " + it, color = AppColors.Error) } }
            }
            item { VendorField("Full name", fullName) { fullName = it } }
            item { VendorField("Business / fleet name (optional)", businessName) { businessName = it } }
            item { VendorField("Address", address) { address = it } }
            item { VendorField("City", city) { city = it } }
            item { VendorField("State", stateName) { stateName = it } }
            item { VendorField("PIN code", pin) { pin = it } }
            item { VendorField("PAN (optional for now)", pan) { pan = it } }
            item { VendorField("Payout UPI (optional)", upi) { upi = it } }
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
                    enabled = !state.saving && fullName.isNotBlank() && address.isNotBlank() &&
                        city.isNotBlank() && stateName.isNotBlank() && pin.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                ) {
                    if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(if (state.vendor?.status?.uppercase() == "REJECTED") "Resubmit for verification" else "Submit for verification")
                }
            }
        }
    }
}
@Composable
private fun VendorField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
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
fun CarRentalMarketplaceScreen(state: RentalUiState, onBack: () -> Unit, onBook: (RentalCarResponse) -> Unit, onRefresh: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }; Text("Car Rental Marketplace", style = MaterialTheme.typography.headlineSmall) } }
        item { Text("Chauffeur-driven cars available for your trip.", color = AppColors.TextSecondary) }
        state.error?.let { item { Text(it, color = AppColors.Error) } }
        if (state.loading && state.cars.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
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
                        Text("No cars available right now.", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "There are no approved chauffeur-driven cars available for your account at the moment. New vehicles will appear here as soon as they are approved.",
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
                        Surface(shape = RoundedCornerShape(14.dp), color = AppColors.Primary.copy(alpha = .10f)) { Icon(Icons.Default.DirectionsCar, null, tint = AppColors.Primary, modifier = Modifier.padding(12.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(car.name, style = MaterialTheme.typography.titleLarge); Text(car.category + " • " + car.seats + " seats • " + car.transmission, color = AppColors.TextSecondary) }
                    }
                    Text("Driver: " + car.driverName, style = MaterialTheme.typography.titleMedium)
                    Text((car.fuelType ?: "Fuel") + " • " + (car.city ?: "Location unavailable"), color = AppColors.TextSecondary)
                    Text("₹" + car.pricePerDay.setScale(0) + " / day", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = { onBook(car) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Person, null); Spacer(Modifier.width(6.dp)); Text("Book with driver") }
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
    onConfirm: (RentalBookingRequest, () -> Unit) -> Unit
) {
    var pickup by remember { mutableStateOf(car.pickupAddress.orEmpty()) }
    var drop by remember { mutableStateOf(car.city.orEmpty()) }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
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
                        Text("Payment: Wallet")
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