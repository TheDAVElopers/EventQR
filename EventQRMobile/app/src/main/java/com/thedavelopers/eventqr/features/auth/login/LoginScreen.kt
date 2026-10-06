package com.thedavelopers.eventqr.features.auth.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thedavelopers.eventqr.ui.components.AuthHeader
import com.thedavelopers.eventqr.ui.components.AuthTextField
import com.thedavelopers.eventqr.ui.theme.LocalSpacing

@Composable
fun LoginScreen(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    emailError: String?,
    passwordError: String?,
    isLoading: Boolean,
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        AuthHeader(
            title = "Sign In",
            subtitle = "Sign in to start scanning and streamline event entry.",
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = spacing.extraLarge,
                    vertical = spacing.extraLarge,
                ),
        ) {
            AuthTextField(
                value = email,
                onValueChange = onEmailChange,
                label = "Email Address",
                placeholder = "Enter email address",
                errorMessage = emailError,
                keyboardType = KeyboardType.Email,
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            AuthTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = "Password",
                placeholder = "Enter password",
                errorMessage = passwordError,
                isPassword = true,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            )

            Spacer(modifier = Modifier.height(spacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onForgotPassword,
                    enabled = !isLoading,
                    modifier = Modifier.height(spacing.minTouchTarget),
                ) {
                    Text(
                        text = "Forgot Password?",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            Button(
                onClick = onSignIn,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(spacing.buttonHeightMin),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(spacing.iconSizeLarge),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text(text = "Signing in...")
                } else {
                    Text(
                        text = "Sign In",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(spacing.minTouchTarget),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Don't have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onRegister,
                    enabled = !isLoading,
                    modifier = Modifier.height(spacing.minTouchTarget),
                ) {
                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
