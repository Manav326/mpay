package com.recharge.client.features.auth


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.viewmodel.AuthUiState
import com.recharge.client.core.viewmodel.RegistrationOtpUiState
import kotlinx.coroutines.delay

@Composable
fun RegisterScreen(
    authState: AuthUiState,
    registrationOtpState: RegistrationOtpUiState,
    onRegister: (String, String, String, String, String?) -> Unit,
    onSendOtp: (String) -> Unit,
    onVerifyOtp: (String, String) -> Unit,
    onClearOtp: () -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var otpMode by remember { mutableStateOf(false) }
    var resendRemaining by remember { mutableIntStateOf(0) }

    LaunchedEffect(registrationOtpState) {
        val sent = registrationOtpState as? RegistrationOtpUiState.Sent
        if (sent != null) resendRemaining = sent.resendAfterSeconds.toInt()
    }

    LaunchedEffect(resendRemaining) {
        if (resendRemaining > 0) {
            delay(1000)
            resendRemaining -= 1
        }
    }

    val verificationToken = (registrationOtpState as? RegistrationOtpUiState.Verified)?.verificationToken
    val otpError = (registrationOtpState as? RegistrationOtpUiState.Error)?.message

    val localError = when {
        email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Enter a valid email address"
        confirm.isNotEmpty() && confirm != password -> "Passwords do not match"
        password.isNotEmpty() && password.length < 8 -> "Password must be at least 8 characters"
        else -> null
    }
    val error = localError ?: (authState as? AuthUiState.Error)?.message

    AuthScreen {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(42.dp)
                    .shadow(2.dp, RoundedCornerShape(14.dp))
                    .background(AppColors.Background, RoundedCornerShape(14.dp))
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = AppColors.TextPrimary
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        MpayBrandHeader(compact = true)
        Spacer(Modifier.height(16.dp))

        Text(
            "Create your account",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = AppColors.TextPrimary
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "Set up your wallet once, then recharge and pay with ease.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary
        )
        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Full name") },
            placeholder = { Text("Your name") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = AuthFieldColors()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email address") },
            supportingText = { Text("Optional") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = AuthFieldColors()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = mobile,
            onValueChange = { value ->
                if (value.length <= 10 && value.all(Char::isDigit)) {
                    if (value != mobile && otpMode) {
                        otpMode = false
                        otp = ""
                        onClearOtp()
                    }
                    mobile = value
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mobile number") },
            placeholder = { Text("10-digit mobile number") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            singleLine = true,
            enabled = !otpMode || verificationToken == null,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = AuthFieldColors()
        )

        Spacer(Modifier.height(8.dp))

        if (verificationToken != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Success.copy(alpha = 0.09f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = AppColors.Success)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Mobile number verified", fontWeight = FontWeight.SemiBold)
                        Text("This number will be saved as verified.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        otpMode = false
                        otp = ""
                        onClearOtp()
                    }) { Text("Change") }
                }
            }
        } else if (!otpMode) {
            OutlinedButton(
                onClick = {
                    otpMode = true
                    otp = ""
                    onSendOtp(mobile)
                },
                enabled = mobile.length == 10 && registrationOtpState !is RegistrationOtpUiState.Sending,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (registrationOtpState is RegistrationOtpUiState.Sending) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text("Verify mobile number")
                }
            }
            Text(
                "Optional. You can also create your account without verifying your mobile now.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            Text("Verify your mobile", fontWeight = FontWeight.SemiBold)
            Text("Enter the 6-digit OTP sent to your mobile number.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            val sent = registrationOtpState as? RegistrationOtpUiState.Sent
            if (sent?.deliveryMode.equals("mock", ignoreCase = true) && !sent?.demoOtp.isNullOrBlank()) {
                Text("Development OTP: " + sent?.demoOtp.orEmpty(), color = AppColors.Success, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
            }
            OutlinedTextField(
                value = otp,
                onValueChange = { value -> if (value.length <= 6 && value.all(Char::isDigit)) otp = value },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("OTP") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = AuthFieldColors(),
                shape = RoundedCornerShape(16.dp)
            )
            otpError?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onVerifyOtp(mobile, otp) },
                    enabled = otp.length == 6 && registrationOtpState !is RegistrationOtpUiState.Verifying,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    if (registrationOtpState is RegistrationOtpUiState.Verifying) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp), color = Color.White)
                    } else Text("Verify OTP")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { otp = ""; onSendOtp(mobile) },
                    enabled = resendRemaining == 0 && registrationOtpState !is RegistrationOtpUiState.Sending
                ) {
                    Text(if (resendRemaining > 0) "Resend " + resendRemaining + "s" else "Resend")
                }
            }
            TextButton(
                onClick = {
                    otpMode = false
                    otp = ""
                    onClearOtp()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("Skip verification") }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            supportingText = { Text("Minimum 8 characters") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = AuthFieldColors()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Confirm password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = AuthFieldColors()
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                color = AppColors.Error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = { onRegister(name.trim(), email.trim(), mobile, password, verificationToken) },
            enabled = authState !is AuthUiState.Loading &&
                name.isNotBlank() &&
                (email.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) &&
                mobile.length == 10 &&
                password.length >= 8 &&
                password == confirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(17.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 5.dp,
                pressedElevation = 2.dp,
                disabledElevation = 0.dp
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Primary,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFE8E0CF),
                disabledContentColor = Color(0xFF8A867F)
            )
        ) {
            if (authState is AuthUiState.Loading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.height(22.dp),
                    color = Color.White
                )
            } else {
                Text(
                    "Create account",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Already have an account?",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onBack) {
                Text(
                    "Sign in",
                    color = AppColors.PrimaryDark,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
