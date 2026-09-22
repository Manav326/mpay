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
    onBack: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var stateName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pan by remember { mutableStateOf("") }
    var upi by remember { mutableStateOf("") }

    if (state.vendor?.status?.uppercase() in setOf("PENDING", "VERIFIED", "REJECTED")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }; Text("Vendor application", style = MaterialTheme.typography.headlineSmall) } }
            item { Text(
                when (state.vendor?.status?.uppercase()) {
                    "VERIFIED" -> "Your vendor account has been verified."
                    "REJECTED" -> "Your application needs changes before it can be reviewed again."
                    else -> "Your vendor information has been submitted and is awaiting review."
                }, color = AppColors.TextSecondary
            ) }
            state.vendor?.let { v ->
                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("Submitted information", style = MaterialTheme.typography.titleLarge)
                            Text("Status: " + v.status)
                            Text("Name: " + (v.fullName ?: "—"))
                            v.businessName?.let { Text("Business / fleet: " + it) }
                            Text("Address: " + (v.address ?: "—"))
                            Text("Location: " + (v.city ?: "—") + ", " + (v.state ?: "—") + " " + (v.pinCode ?: ""))
                            v.panNumber?.let { Text("PAN: " + it) }
                            v.payoutUpiId?.let { Text("Payout UPI: " + it) }
                            v.rejectionReason?.let { Text("Review note: " + it, color = AppColors.Error) }
                            Text("Vehicles submitted: " + v.vehicleCount)
                        }
                    }
                }
            }
            item { if (state.vendor?.status?.uppercase() == "VERIFIED") { Button(onClick = { }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Add a vehicle") } } else { Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Back to Profile") } } }
        }
    } else     LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }; Text("Become a Vendor", style = MaterialTheme.typography.headlineSmall) } }
        item { Text("Rent your car with a professional driver through mPay.", color = AppColors.TextSecondary) }
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
                enabled = !state.saving && fullName.isNotBlank() && address.isNotBlank() && city.isNotBlank() && stateName.isNotBlank() && pin.isNotBlank(),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            ) { if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Submit for verification") }
        }
    }
}

@Composable
private fun VendorField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
fun CarRentalMarketplaceScreen(state: RentalUiState, onBack: () -> Unit) {
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
                    Button(onClick = { }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Person, null); Spacer(Modifier.width(6.dp)); Text("Book with driver") }
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
