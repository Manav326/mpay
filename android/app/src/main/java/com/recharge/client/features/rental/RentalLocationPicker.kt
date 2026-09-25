package com.recharge.client.features.rental

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.widget.PlaceAutocomplete
import com.recharge.client.BuildConfig
import com.recharge.client.core.model.RentalLocationInput
import com.recharge.client.core.theme.AppColors

@Composable
fun RentalLocationPickerField(
    label: String,
    value: RentalLocationInput?,
    onSelected: (RentalLocationInput) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = true,
    helper: String? = null,
    error: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val placesClient = remember(context) { if (BuildConfig.MAPS_API_KEY.isNotBlank()) Places.createClient(context) else null }
    val launcher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null || placesClient == null) return@rememberLauncherForActivityResult
        val prediction = runCatching { PlaceAutocomplete.getPredictionFromIntent(result.data!!) }.getOrNull() ?: return@rememberLauncherForActivityResult
        val request = FetchPlaceRequest.newInstance(
            prediction.placeId,
            listOf(
                Place.Field.ID,
                Place.Field.DISPLAY_NAME,
                Place.Field.FORMATTED_ADDRESS,
                Place.Field.LOCATION
            )
        )
        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place = response.place
                val location = place.location ?: return@addOnSuccessListener
                onSelected(
                    RentalLocationInput(
                        address = place.formattedAddress ?: prediction.getFullText(null).toString(),
                        latitude = location.latitude,
                        longitude = location.longitude,
                        placeId = place.id
                    )
                )
            }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        OutlinedButton(
            onClick = {
                if (BuildConfig.MAPS_API_KEY.isBlank()) return@OutlinedButton
                val intent = PlaceAutocomplete.createIntent(context) {
                    setCountries(listOf("IN"))
                    setRegionCode("IN")
                    setInitialQuery(value?.address.orEmpty())
                }
                launcher.launch(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            enabled = BuildConfig.MAPS_API_KEY.isNotBlank()
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                Text(
                    value?.address?.takeIf { it.isNotBlank() } ?: "Search and choose a location",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Icon(Icons.Default.Map, contentDescription = null, tint = AppColors.Primary)
        }

        if (BuildConfig.MAPS_API_KEY.isBlank()) {
            Text(
                "Map service is not configured for this build. Add MAPS_API_KEY to enable location selection.",
                color = AppColors.Warning,
                style = MaterialTheme.typography.labelSmall
            )
        } else if (value != null) {
            RentalSelectedMapPreview(value, onSelected)
            Text(
                "Selected location is stored as address + latitude/longitude + Place ID for reliable downstream use.",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            Text(
                helper ?: "Required: choose the exact pickup/drop point from the map.",
                color = error?.let { AppColors.Error } ?: AppColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }

        error?.let {
            Text(it, color = AppColors.Error, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RentalSelectedMapPreview(
    location: RentalLocationInput,
    onSelected: (RentalLocationInput) -> Unit
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(location.latitude, location.longitude), 15f)
    }
    LaunchedEffect(location.latitude, location.longitude) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(
            LatLng(location.latitude, location.longitude),
            15f
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxWidth().height(190.dp),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false
            ),
            onMapClick = { point ->
                onSelected(
                    location.copy(
                        latitude = point.latitude,
                        longitude = point.longitude,
                        placeId = location.placeId
                    )
                )
            }
        ) {
            Marker(
                state = MarkerState(LatLng(location.latitude, location.longitude)),
                title = location.address
            )
        }
    }
    Text(
        "Tap the map to fine-tune the pin.",
        color = AppColors.TextSecondary,
        style = MaterialTheme.typography.labelSmall
    )
}
