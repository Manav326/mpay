package com.recharge.client.features.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recharge.client.core.model.CurrentUserResponse
import com.recharge.client.core.model.RentalVendorResponse
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.ui.CopyableValue
import com.recharge.client.core.ui.ProfileAvatar
import com.recharge.client.core.ui.formatExactTimestamp
import com.recharge.client.core.ui.formatMoney
import com.recharge.client.core.ui.MpayStatusPill
import com.recharge.client.core.viewmodel.ProfileUiState
import java.math.BigDecimal

@Composable
fun ProfileScreen(
    state: ProfileUiState, vendor: RentalVendorResponse?, onLoad: () -> Unit, onRefreshVendor: () -> Unit,
    onSave: (String, String, Uri?) -> Unit, onRemovePhoto: () -> Unit,
    onLogout: () -> Unit, onProfileUpdated: () -> Unit, onBecomeVendor: () -> Unit, isVisible: Boolean
) {
    var editing by remember { mutableStateOf(false) }
    var showPolicies by remember { mutableStateOf(false) }
    LaunchedEffect(isVisible) { if (isVisible) { onLoad(); onRefreshVendor() } }
    LaunchedEffect(state.saved) { if (state.saved) { editing = false; onProfileUpdated() } }
    val user = state.user

    LazyColumn(
        Modifier.fillMaxSize().widthIn(max = 760.dp).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF172033))
                Text("Your personal account, identity and mPay preferences", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E7)), elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(user, 82.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(user?.name?.takeIf { it.isNotBlank() } ?: "Your name", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(user?.email?.takeIf { it.isNotBlank() } ?: "Add an email address", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(user?.mobile ?: "—", color = Color(0xFF475569), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                    FilledTonalButton(onClick = { editing = true }, shape = RoundedCornerShape(13.dp)) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Edit", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceWarm.copy(alpha = .55f)), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Account details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.PrimaryDark)
                    Text("Permanent account information and activity", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                    HorizontalDivider()
                    CopyableValue("Account ID", user?.publicUserId.orEmpty())
                    ProfileInfoRow("Account type", roleLabel(user?.role))
                    ProfileInfoRow("Commission rate", formatMoney(user?.commissionRate ?: BigDecimal.ZERO) + "%")
                    ProfileInfoRow("Joined", formatExactTimestamp(user?.createdAt))
                    ProfileInfoRow("Last profile update", formatExactTimestamp(user?.profileUpdatedAt ?: user?.createdAt))
                }
            }
        }
        state.error?.let {
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            val hasVendorProfile = !vendor?.vendorId.isNullOrBlank()
            val verified = hasVendorProfile && vendor?.status.equals("VERIFIED", true)
            val vendorTitle = when {
                verified -> "Rental Vendor Dashboard"
                hasVendorProfile && vendor?.status.equals("REJECTED", true) -> "Rental Vendor Application"
                hasVendorProfile && vendor?.status.equals("PENDING", true) -> "Vendor Application · Pending Verification"
                hasVendorProfile -> "Rental Vendor Application"
                else -> "Become a Vendor"
            }
            val vendorSubtitle = when {
                verified -> "Business workspace: manage cars, availability, payouts and rental operations."
                hasVendorProfile && vendor?.status.equals("REJECTED", true) -> "Review the rejection note and resubmit your vendor details."
                hasVendorProfile && vendor?.status.equals("PENDING", true) -> "Your application is submitted and awaiting admin verification."
                hasVendorProfile -> "Review your vendor application status."
                else -> "List your chauffeur-driven car and manage it through the mPay marketplace."
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                onClick = onBecomeVendor,
                colors = CardDefaults.cardColors(containerColor = if (verified) AppColors.VendorNavy else Color(0xFFFFF7E6)),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(13.dp), color = if (verified) AppColors.VendorGold.copy(alpha = .16f) else AppColors.SurfaceWarm) {
                        Icon(Icons.Default.DirectionsCar, null, tint = if (verified) AppColors.VendorGold else AppColors.PrimaryDark, modifier = Modifier.padding(10.dp).size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(vendorTitle, style = MaterialTheme.typography.titleLarge, color = if (verified) Color.White else AppColors.PrimaryDark, fontWeight = FontWeight.Bold)
                        Text(vendorSubtitle, color = if (verified) Color.White.copy(alpha = .74f) else AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        if (hasVendorProfile) MpayStatusPill(vendor?.status ?: "—")
                    }
                    Icon(Icons.Default.ChevronRight, "Open vendor", tint = if (verified) Color.White else AppColors.PrimaryDark)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("Settings & policies", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    ProfileActionRow(Icons.Default.Settings, "Account settings", "Update your name, email and profile photo") { editing = true }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ProfileActionRow(Icons.Default.Description, "Terms & Privacy", "Review the basic rules for using mPay services") { showPolicies = true }
                }
            }
        }
        item {
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Error), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Logout", maxLines = 1, softWrap = false, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (editing) EditProfileDialog(user, state.saving, { if (!state.saving) editing = false }, onSave, onRemovePhoto, state.deletingImage)

    if (showPolicies) {
        AlertDialog(
            onDismissRequest = { showPolicies = false },
            title = { Text("Terms & Privacy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Use mPay only for lawful personal and business activity. Keep your account details accurate and protect your login credentials.", style = MaterialTheme.typography.bodySmall)
                    Text("Rental marketplace", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Vehicle availability, pricing, booking and vendor information should be kept accurate. Booking and wallet actions are subject to the applicable service rules.", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                    Text("Privacy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Profile and transaction information is used to provide account, payment, recharge and rental services. Do not share sensitive account credentials with others.", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                }
            },
            confirmButton = { TextButton(onClick = { showPolicies = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, color = Color(0xFF1F2937), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1.2f))
    }
}

@Composable
private fun ProfileActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF1F5F9)) {
            Icon(icon, null, tint = Color(0xFF475569), modifier = Modifier.padding(8.dp).size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
        }
        Icon(Icons.Default.ChevronRight, null, tint = AppColors.TextSecondary)
    }
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
