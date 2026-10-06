package com.thedavelopers.eventqr.features.auth.forgotpassword

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.thedavelopers.eventqr.ui.components.AuthHeader
import com.thedavelopers.eventqr.ui.components.AuthTextField
import com.thedavelopers.eventqr.ui.theme.LocalSpacing

@Composable
fun ForgotPasswordScreen(
    email: String,
    onEmailChange: (String) -> Unit,
    emailError: String?,
    isLoading: Boolean,
    showConfirmation: Boolean,
    onSendResetLink: () -> Unit,
    onBackToSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Box {
                AuthHeader(
                    title = "Forgot Password",
                    subtitle = "Reset your password and regain access to your account.",
                )
                IconButton(
                    onClick = onBackToSignIn,
                    modifier = Modifier
                        .padding(start = spacing.medium, top = spacing.large)
                        .size(spacing.minTouchTarget),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Sign In",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = spacing.large,
                        vertical = spacing.extraLarge,
                    ),
            ) {
                if (showConfirmation) {
                    ConfirmationState(onBackToSignIn = onBackToSignIn)
                } else {
                    FormState(
                        email = email,
                        onEmailChange = onEmailChange,
                        emailError = emailError,
                        isLoading = isLoading,
                        onSendResetLink = onSendResetLink,
                        onBackToSignIn = onBackToSignIn,
                    )
                }
            }
        }
    }
}

@Composable
private fun FormState(
    email: String,
    onEmailChange: (String) -> Unit,
    emailError: String?,
    isLoading: Boolean,
    onSendResetLink: () -> Unit,
    onBackToSignIn: () -> Unit,
) {
    val spacing = LocalSpacing.current

    AuthTextField(
        value = email,
        onValueChange = onEmailChange,
        label = "Email Address",
        placeholder = "Enter email address",
        errorMessage = emailError,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Done,
        enabled = !isLoading,
    )

    Spacer(modifier = Modifier.height(spacing.mediumLarge))

    Button(
        onClick = onSendResetLink,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(spacing.buttonHeightMin),
    ) {
        Text(
            text = if (isLoading) "Sending..." else "Send Reset Link",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
    }

    Spacer(modifier = Modifier.height(spacing.medium))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(spacing.minTouchTarget),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Remembered your password?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onBackToSignIn,
            modifier = Modifier.height(spacing.minTouchTarget),
        ) {
            Text(
                text = "Sign in",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun ConfirmationState(onBackToSignIn: () -> Unit) {
    val spacing = LocalSpacing.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(spacing.iconContainerLarge),
        )

        Spacer(modifier = Modifier.height(spacing.medium))

        Text(
            text = "Check your email",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(spacing.small))

        Text(
            text = "If that email exists in our system, a reset link has been sent. Please check your inbox and follow the instructions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(spacing.extraLarge))

        Button(
            onClick = onBackToSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(spacing.buttonHeightMin),
        ) {
            Text(
                text = "Back to Sign In",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}
