package com.recharge.client.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.viewmodel.AuthUiState

@Composable
fun RegisterScreen(
    authState: AuthUiState,
    onRegister: (String, String, String, String) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

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
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }
        Spacer(Modifier.height(2.dp))
        MpayBrandHeader(compact = true)
        Spacer(Modifier.height(18.dp))

        AuthSectionTitle(
            title = "Create your account",
            subtitle = "Set up your wallet once, then recharge and pay with ease."
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = AuthFieldColors()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = mobile,
            onValueChange = { value ->
                if (value.length <= 10 && value.all(Char::isDigit)) mobile = value
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Mobile number") },
            placeholder = { Text("10-digit mobile number") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = AuthFieldColors()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            supportingText = { Text("Minimum 8 characters") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            singleLine = true,
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
            onClick = { onRegister(name.trim(), email.trim(), mobile, password) },
            enabled = authState !is AuthUiState.Loading &&
                name.isNotBlank() &&
                (email.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) &&
                mobile.length == 10 &&
                password.length >= 8 &&
                password == confirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.TextPrimary,
                contentColor = androidx.compose.ui.graphics.Color.White
            )
        ) {
            if (authState is AuthUiState.Loading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.height(22.dp),
                    color = androidx.compose.ui.graphics.Color.White
                )
            } else {
                Text("Create account", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(12.dp))
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
                Text("Sign in")
            }
        }
    }
}
