package com.recharge.client.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
fun LoginScreen(
    authState: AuthUiState,
    onLogin: (String, String) -> Unit,
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit
) {
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MpayBrandHeader()
        Spacer(Modifier.height(28.dp))
        Text("Welcome Back", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text("Sign in to your mPay account", color = AppColors.TextSecondary)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = mobile,
            onValueChange = { value -> if (value.length <= 10 && value.all(Char::isDigit)) mobile = value },
            modifier = Modifier.fillMaxWidth(), label = { Text("Mobile number") },
            supportingText = { Text("10-digit Indian mobile number") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") }, singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }
            }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.End)) { Text("Forgot password?") }
        (authState as? AuthUiState.Error)?.message?.let {
            Text(it, color = AppColors.Error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = { onLogin(mobile, password) },
            enabled = authState !is AuthUiState.Loading && mobile.length == 10 && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            if (authState is AuthUiState.Loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(22.dp)) else Text("Login")
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onSignUp) { Text("New here? Create an account") }
    }
}
