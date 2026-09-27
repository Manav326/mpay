package com.recharge.client.features.rental

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.recharge.client.core.model.RentalPhotoCandidate
import com.recharge.client.core.model.RentalPhotoCandidateSource
import kotlinx.coroutines.delay

private enum class RentalPhotoPickerSource {
    DEVICE,
    URL
}

private enum class RentalUrlState {
    IDLE,
    CHECKING,
    LOADING,
    READY,
    ERROR
}

private fun validPhotoUrl(value: String): Boolean =
    value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true)

@Composable
fun RentalPhotoPickerDialog(
    title: String,
    currentPreview: String?,
    onDismiss: () -> Unit,
    onUse: (RentalPhotoCandidate) -> Unit
) {
    var source by remember { mutableStateOf<RentalPhotoPickerSource?>(null) }
    var deviceUri by remember { mutableStateOf<String?>(null) }
    var urlInput by remember { mutableStateOf("") }
    var checkedUrl by remember { mutableStateOf<String?>(null) }
    var urlState by remember { mutableStateOf(RentalUrlState.IDLE) }

    val deviceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        deviceUri = uri?.toString()
    }

    LaunchedEffect(source, urlInput) {
        if (source != RentalPhotoPickerSource.URL) {
            checkedUrl = null
            urlState = RentalUrlState.IDLE
            return@LaunchedEffect
        }

        val normalized = urlInput.trim()
        checkedUrl = null
        urlState = when {
            normalized.isBlank() -> RentalUrlState.IDLE
            !validPhotoUrl(normalized) -> RentalUrlState.ERROR
            else -> {
                delay(400)
                checkedUrl = normalized
                RentalUrlState.CHECKING
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            color = Color.White
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Choose how you want to replace this photo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF746C63)
                    )
                }

                currentPreview?.takeIf { it.isNotBlank() }?.let { preview ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Current photo",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF746C63)
                        )
                        AsyncImage(
                            model = preview,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(138.dp)
                                .clip(RoundedCornerShape(15.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    SourceChoice(
                        modifier = Modifier.weight(1f),
                        selected = source == RentalPhotoPickerSource.DEVICE,
                        icon = { Icon(Icons.Default.PhotoCamera, null) },
                        title = "From device",
                        subtitle = "Choose a photo",
                        onClick = {
                            source = RentalPhotoPickerSource.DEVICE
                            deviceLauncher.launch("image/*")
                        }
                    )
                    SourceChoice(
                        modifier = Modifier.weight(1f),
                        selected = source == RentalPhotoPickerSource.URL,
                        icon = { Icon(Icons.Default.Link, null) },
                        title = "Image URL",
                        subtitle = "Paste a direct image link",
                        onClick = {
                            source = RentalPhotoPickerSource.URL
                            deviceUri = null
                        }
                    )
                }

                when (source) {
                    RentalPhotoPickerSource.DEVICE -> {
                        deviceUri?.let { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(15.dp)),
                                contentScale = ContentScale.Crop
                            )
                            PickerStatus(
                                icon = Icons.Default.CheckCircle,
                                text = "Photo selected from your device.",
                                color = Color(0xFF16733C)
                            )
                        } ?: InfoBlock("Choose a photo from your device to preview it here.")
                    }

                    RentalPhotoPickerSource.URL -> {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = {
                                urlInput = it.take(2048)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Image URL") },
                            placeholder = { Text("https://example.com/photo.jpg") }
                        )

                        if (checkedUrl != null) {
                            val requestUrl = checkedUrl
                            AsyncImage(
                                model = requestUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(15.dp)),
                                contentScale = ContentScale.Crop,
                                onLoading = {
                                    if (urlInput.trim() == requestUrl) {
                                        urlState = RentalUrlState.LOADING
                                    }
                                },
                                onSuccess = {
                                    if (urlInput.trim() == requestUrl) {
                                        urlState = RentalUrlState.READY
                                    }
                                },
                                onError = {
                                    if (urlInput.trim() == requestUrl) {
                                        urlState = RentalUrlState.ERROR
                                    }
                                }
                            )
                        }

                        when (urlState) {
                            RentalUrlState.CHECKING,
                            RentalUrlState.LOADING -> InfoBlock("Checking that image…")
                            RentalUrlState.READY -> PickerStatus(
                                icon = Icons.Default.CheckCircle,
                                text = "Image loaded and ready to use.",
                                color = Color(0xFF16733C)
                            )
                            RentalUrlState.ERROR -> Text(
                                if (urlInput.trim().isBlank()) {
                                    "Enter a direct image URL."
                                } else if (!validPhotoUrl(urlInput.trim())) {
                                    "Use an HTTP or HTTPS image URL."
                                } else {
                                    "Unable to load this image. Use a direct public image URL or choose a photo from your device."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            RentalUrlState.IDLE -> InfoBlock("Paste a direct public image URL. The image must load successfully before it can be used.")
                        }
                    }

                    null -> InfoBlock("Choose one option above to replace the photo.")
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            when (source) {
                                RentalPhotoPickerSource.DEVICE ->
                                    deviceUri?.let {
                                        onUse(
                                            RentalPhotoCandidate(
                                                RentalPhotoCandidateSource.DEVICE,
                                                it
                                            )
                                        )
                                    }
                                RentalPhotoPickerSource.URL ->
                                    checkedUrl?.takeIf { urlState == RentalUrlState.READY }?.let {
                                        onUse(
                                            RentalPhotoCandidate(
                                                RentalPhotoCandidateSource.URL,
                                                it
                                            )
                                        )
                                    }
                                null -> Unit
                            }
                        },
                        enabled = when (source) {
                            RentalPhotoPickerSource.DEVICE -> deviceUri != null
                            RentalPhotoPickerSource.URL -> urlState == RentalUrlState.READY && checkedUrl != null
                            null -> false
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Use this photo")
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChoice(
    modifier: Modifier,
    selected: Boolean,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else Color(0xFFE7DED3)
    val background = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .07f) else Color(0xFFFCFAF7)
    Column(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = background,
            border = androidx.compose.foundation.BorderStroke(1.dp, border)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                icon()
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF746C63)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoBlock(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF7F3EE)
    ) {
        Text(
            text,
            Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF746C63)
        )
    }
}

@Composable
private fun PickerStatus(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
