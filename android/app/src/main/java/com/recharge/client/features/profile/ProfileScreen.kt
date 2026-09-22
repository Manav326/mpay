package com.recharge.client.features.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.recharge.client.core.model.CurrentUserResponse
import com.recharge.client.core.model.RentalVendorResponse
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.ui.CopyableValue
import com.recharge.client.core.ui.ProfileAvatar
import com.recharge.client.core.ui.formatExactTimestamp
import com.recharge.client.core.ui.formatMoney
import com.recharge.client.core.viewmodel.ProfileUiState
import java.math.BigDecimal

@Composable
fun ProfileScreen(
    state: ProfileUiState, vendor: RentalVendorResponse?, onLoad: () -> Unit, onRefreshVendor: () -> Unit,
    onSave: (String, String, Uri?) -> Unit, onRemovePhoto: () -> Unit,
    onLogout: () -> Unit, onProfileUpdated: () -> Unit, onBecomeVendor: () -> Unit, isVisible: Boolean
) {
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(isVisible) { if (isVisible) { onLoad(); onRefreshVendor() } }
    LaunchedEffect(state.saved) { if (state.saved) { editing = false; onProfileUpdated() } }
    val user = state.user

    LazyColumn(Modifier.fillMaxSize().widthIn(max = 760.dp).padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Profile", style = MaterialTheme.typography.headlineSmall)
            Text("Manage your account and identity", color = AppColors.TextSecondary)
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E7))) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileAvatar(user, 92.dp)
                    Text(user?.name?.takeIf { it.isNotBlank() } ?: "Your name", style = MaterialTheme.typography.headlineSmall)
                    Text(user?.email?.takeIf { it.isNotBlank() } ?: "Add an email address", color = AppColors.TextSecondary)
                    Text(user?.mobile ?: "—", color = AppColors.TextSecondary)
                    Button(onClick = { editing = true }, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit profile", maxLines = 1, softWrap = false) }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F0FF))) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Account", style = MaterialTheme.typography.titleLarge)
                    CopyableValue("Account ID", user?.publicUserId.orEmpty())
                    MetaRow("Account type", roleLabel(user?.role))
                    MetaRow("Commission rate", "${formatMoney(user?.commissionRate ?: BigDecimal.ZERO)}%")
                    MetaRow("Joined", formatExactTimestamp(user?.createdAt))
                    MetaRow("Last profile update", formatExactTimestamp(user?.profileUpdatedAt ?: user?.createdAt))
                }
            }
        }
        state.error?.let { item { Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodySmall) } }
        item {
            val hasVendorProfile = !vendor?.vendorId.isNullOrBlank()
            val vendorTitle = when {
                hasVendorProfile && vendor?.status.equals("VERIFIED", true) -> "Rental Vendor Dashboard"
                hasVendorProfile && vendor?.status.equals("REJECTED", true) -> "Rental Vendor Application"
                hasVendorProfile -> "Rental Vendor Dashboard"
                else -> "Become a Vendor"
            }
            val vendorSubtitle = when {
                hasVendorProfile && vendor?.status.equals("VERIFIED", true) -> "Manage your chauffeur-driven fleet, bookings and earnings."
                hasVendorProfile && vendor?.status.equals("REJECTED", true) -> "Review the rejection note and resubmit your vendor details."
                hasVendorProfile -> "Your vendor application is under review. Open it to see the latest status."
                else -> "Rent your car with a professional driver through mPay."
            }
            Card(
                shape = RoundedCornerShape(20.dp),
                onClick = onBecomeVendor,
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEDF8F3))
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(vendorTitle, style = MaterialTheme.typography.titleLarge, color = Color(0xFF176B4D))
                    Text(vendorSubtitle, color = AppColors.TextSecondary)
                    if (hasVendorProfile) {
                        Text("Status: " + (vendor?.status ?: "—"), style = MaterialTheme.typography.labelMedium, color = Color(0xFF176B4D))
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Error), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Logout", maxLines = 1, softWrap = false)
            }
        }
    }

    if (editing) EditProfileDialog(user, state.saving, { if (!state.saving) editing = false }, onSave, onRemovePhoto, state.deletingImage)
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

private fun roleLabel(role: String?): String = when (role?.uppercase()) {
    "CLIENT" -> "Client"
    "MANAGER" -> "Manager"
    "ADMIN" -> "Admin"
    else -> role?.replaceFirstChar { it.uppercase() } ?: "—"
}

@Composable
private fun EditProfileDialog(user: CurrentUserResponse?, saving: Boolean, onDismiss: () -> Unit, onSave: (String, String, Uri?) -> Unit, onRemovePhoto: () -> Unit, deletingImage: Boolean) {
    var name by remember(user?.name) { mutableStateOf(user?.name.orEmpty()) }
    var email by remember(user?.email) { mutableStateOf(user?.email.orEmpty()) }
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedImage = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (selectedImage != null) coil.compose.AsyncImage(model = selectedImage, contentDescription = "Selected photo", modifier = Modifier.size(88.dp).clip(CircleShape)) else ProfileAvatar(user, 88.dp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { launcher.launch("image/*") }, enabled = !saving && !deletingImage) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(4.dp)); Text("Change photo", maxLines = 1, softWrap = false) }
                    if (!user?.profileImageUrl.isNullOrBlank()) TextButton(onClick = onRemovePhoto, enabled = !saving && !deletingImage) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("Remove", maxLines = 1, softWrap = false) }
                }
                OutlinedTextField(value = name, onValueChange = { name = it.take(120) }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it.take(254) }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(name, email, selectedImage) }, enabled = !saving && !deletingImage, shape = RoundedCornerShape(12.dp)) { if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving && !deletingImage) { Text("Cancel") } }
    )
}
