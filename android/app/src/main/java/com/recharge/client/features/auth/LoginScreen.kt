package com.recharge.client.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
fun LoginScreen(
    authState: AuthUiState,
    onLogin: (String, String) -> Unit,
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit
) {
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AuthScreen {
        MpayBrandHeader()
        Spacer(Modifier.height(24.dp))
        AuthSectionTitle(
            title = "Welcome back",
            subtitle = "Sign in to your mPay account and keep your money moving."
        )
        Spacer(Modifier.height(20.dp))

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
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
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

        Spacer(Modifier.height(2.dp))
        TextButton(
            onClick = onForgotPassword,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Forgot password?")
        }

        (authState as? AuthUiState.Error)?.message?.let {
            Text(
                text = it,
                color = AppColors.Error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onLogin(mobile, password) },
            enabled = authState !is AuthUiState.Loading && mobile.length == 10 && password.isNotBlank(),
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
                Text("Sign in", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "New to mPay?",
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        TextButton(
            onClick = onSignUp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Create an account")
        }
    }
}
