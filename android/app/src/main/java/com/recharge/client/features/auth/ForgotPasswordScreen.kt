package com.recharge.client.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.viewmodel.PasswordResetUiState

@Composable
fun ForgotPasswordScreen(
    state: PasswordResetUiState,
    onRequestOtp: (String) -> Unit,
    onReset: (String, String, String) -> Unit,
    onBack: () -> Unit
) {
    var mobile by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val otpSent = state is PasswordResetUiState.OtpSent || state is PasswordResetUiState.Resetting || state is PasswordResetUiState.Success
    val errorMessage = (state as? PasswordResetUiState.Error)?.message

    AuthScreen {

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
        }
        Spacer(Modifier.height(8.dp))
        MpayBrandHeader(compact = true)
        Spacer(Modifier.height(18.dp))

        Text("Forgot password?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            if (otpSent) "Enter the OTP and choose a new password." else "We’ll send a one-time code to your mobile number.",
            color = AppColors.TextSecondary
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = mobile,
            onValueChange = { value -> if (value.length <= 10 && value.all(Char::isDigit)) mobile = value },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mobile number") },
            supportingText = { Text("10-digit Indian mobile number") },
            singleLine = true,
            enabled = !otpSent,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = AuthFieldColors(),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(10.dp))

        if (!otpSent) {
            Button(
                onClick = { onRequestOtp(mobile) },
                enabled = mobile.length == 10 && state !is PasswordResetUiState.Sending,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (state is PasswordResetUiState.Sending) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(22.dp), color = Color.White)
                else Text("Send OTP", fontWeight = FontWeight.SemiBold)
            }
        } else {
            val otpState = state as? PasswordResetUiState.OtpSent
            if (otpState?.deliveryMode.equals("mock", ignoreCase = true) && !otpState?.demoOtp.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Success.copy(alpha = 0.08f))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Development / Mock OTP", style = MaterialTheme.typography.labelLarge)
                        Text(otpState?.demoOtp.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                        Text("Use this OTP while Twilio SMS is not configured.", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            OutlinedTextField(
                value = otp,
                onValueChange = { value -> if (value.length <= 6 && value.all(Char::isDigit)) otp = value },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("OTP") },
                supportingText = { Text("Enter the 6-digit OTP sent by SMS to your mobile number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = AuthFieldColors(),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("New password") },
                supportingText = { Text("Minimum 8 characters") },
                singleLine = true,
                colors = AuthFieldColors(),
                shape = RoundedCornerShape(16.dp),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Confirm new password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = AuthFieldColors(),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(8.dp))
            val localError = when {
                otp.isNotEmpty() && otp.length != 6 -> "OTP must be 6 digits"
                newPassword.isNotEmpty() && newPassword.length < 8 -> "Password must be at least 8 characters"
                confirmPassword.isNotEmpty() && confirmPassword != newPassword -> "Passwords do not match"
                else -> null
            }
            (localError ?: errorMessage)?.let {
                Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = { onReset(mobile, otp, newPassword) },
                enabled = state !is PasswordResetUiState.Resetting && otp.length == 6 && newPassword.length >= 8 && newPassword == confirmPassword,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (state is PasswordResetUiState.Resetting) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(22.dp), color = Color.White)
                else Text("Reset password", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { otp = ""; newPassword = ""; confirmPassword = ""; onRequestOtp(mobile) }) {
                Text("Send OTP again")
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Back to login", color = AppColors.PrimaryDark, fontWeight = FontWeight.SemiBold)
        }
    }
}
