package com.recharge.client.features.rental

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
    onRefreshVehicles: () -> Unit
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
        LaunchedEffect(state.vendor?.vendorId) { onRefreshVehicles() }
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

@Composable
fun CarRentalMarketplaceScreen(state: RentalUiState, onBack: () -> Unit, onBook: (RentalCarResponse) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }; Text("Car Rental", style = MaterialTheme.typography.headlineSmall) } }
        item { Text("Chauffeur-driven cars available for your trip.", color = AppColors.TextSecondary) }
        state.error?.let { item { Text(it, color = AppColors.Error) } }
        if (state.loading && state.cars.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        if (!state.loading && state.cars.isEmpty()) item { Text("No approved vehicles are available yet.", color = AppColors.TextSecondary) }
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
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var variant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Sedan") }
    var seats by remember { mutableStateOf("5") }
    var transmission by remember { mutableStateOf("Automatic") }
    var fuel by remember { mutableStateOf("Petrol") }
    var manufacturingYear by remember { mutableStateOf("") }
    var registrationYear by remember { mutableStateOf("") }
    var registrationNumber by remember { mutableStateOf("") }
    var pickupAddress by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var stateName by remember { mutableStateOf("") }
    var pricePerDay by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var driverName by remember { mutableStateOf("") }
    var driverMobile by remember { mutableStateOf("") }
    var licenseNumber by remember { mutableStateOf("") }
    var licenseExpiry by remember { mutableStateOf("") }
    var driverAddress by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }; Text("Add vehicle", style = MaterialTheme.typography.headlineSmall) } }
        item { Text("Submit the vehicle and assigned chauffeur for admin review.", color = AppColors.TextSecondary) }
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
        item { VendorField("Licence expiry (YYYY-MM-DD)", licenseExpiry) { licenseExpiry = it } }
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
                    onSubmit(
                        RentalVehicleOnboardingRequest(
                            name = name.trim(), category = category.trim(), seats = seats.toInt(), transmission = transmission.trim(),
                            fuelType = fuel.trim(), manufacturingYear = manufacturingYear.toInt(), registrationYear = registrationYear.toInt(),
                            registrationNumber = registrationNumber.trim(), make = make.trim(), model = model.trim(),
                            variant = variant.ifBlank { null }, pickupAddress = pickupAddress.trim(), city = city.trim(), state = stateName.trim(),
                            pricePerDay = pricePerDay.toBigDecimal(), imageUrl = imageUrl.ifBlank { null },
                            driver = RentalDriverRequest(driverName.trim(), driverMobile.trim(), licenseNumber.trim(), licenseExpiry.trim(), driverAddress.ifBlank { null })
                        ),
                        onBack
                    )
                },
                enabled = valid && !state.saving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Submit vehicle for review") }
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
        item { VendorField("Start date (YYYY-MM-DD)", start) { start = it } }
        item { VendorField("End date (YYYY-MM-DD)", end) { end = it } }
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
                            onClick = { onConfirm(RentalBookingRequest(car.id, pickup.trim(), drop.trim(), start, end, "WALLET"), onBack) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Confirm booking") }
                    }
                }
            }
        } ?: item {
            Button(
                enabled = !state.saving && pickup.isNotBlank() && drop.isNotBlank() && start.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && end.matches(Regex("\\d{4}-\\d{2}-\\d{2}")),
                onClick = { onQuote(RentalBookingQuoteRequest(car.id, pickup.trim(), drop.trim(), start, end)) { quote = it } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Check fare") }
        }
        state.error?.let { item { Text(it, color = AppColors.Error) } }
    }
}
