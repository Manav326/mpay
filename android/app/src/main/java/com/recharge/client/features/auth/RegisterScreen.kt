package com.recharge.client.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.recharge.client.core.viewmodel.AuthUiState

@Composable
fun RegisterScreen(authState: AuthUiState, onRegister: (String, String, String, String) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 24.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
        Spacer(Modifier.height(8.dp))
        MpayBrandHeader(compact = true)
        Spacer(Modifier.height(18.dp))
        Text("Create your account", style = MaterialTheme.typography.headlineMedium)
        Text("Your wallet and earnings will be tied to this account.", color = AppColors.TextSecondary)
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email address") },
            supportingText = { Text("Optional") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = mobile,
            onValueChange = { value -> if (value.length <= 10 && value.all(Char::isDigit)) mobile = value },
            modifier = Modifier.fillMaxWidth(), label = { Text("Mobile number") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") }, supportingText = { Text("Minimum 8 characters") }, singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = confirm, onValueChange = { confirm = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Confirm password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(Modifier.height(8.dp))
        val localError = when {
            email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Enter a valid email address"
            confirm.isNotEmpty() && confirm != password -> "Passwords do not match"
            password.isNotEmpty() && password.length < 8 -> "Password must be at least 8 characters"
            else -> null
        }
        val error = localError ?: (authState as? AuthUiState.Error)?.message
        if (error != null) {
            Text(error, color = AppColors.Error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = { onRegister(name.trim(), email.trim(), mobile, password) },
            enabled = authState !is AuthUiState.Loading && name.isNotBlank() && (email.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) && mobile.length == 10 && password.length >= 8 && password == confirm,
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            if (authState is AuthUiState.Loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(22.dp)) else Text("Create account")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Already have an account? Login") }
    }
}
