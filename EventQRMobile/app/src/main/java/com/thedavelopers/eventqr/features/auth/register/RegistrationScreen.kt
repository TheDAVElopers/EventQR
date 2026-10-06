package com.thedavelopers.eventqr.features.auth.register

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.thedavelopers.eventqr.core.util.Validators
import com.thedavelopers.eventqr.ui.components.AuthHeader
import com.thedavelopers.eventqr.ui.components.AuthTextField
import com.thedavelopers.eventqr.ui.theme.LocalSpacing

internal const val TERMS_LABEL = "I agree to the Terms of Service and Privacy Policy"
internal const val TERMS_CHECKBOX_TAG = "eventqr.registration.termsCheckbox"

data class PasswordRequirementRow(
    val label: String,
    val isMet: Boolean,
)

@Composable
fun RegistrationScreen(
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    lastName: String,
    onLastNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    phoneDigits: String,
    onPhoneDigitsChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    termsAccepted: Boolean,
    onTermsAcceptedChange: (Boolean) -> Unit,
    fieldErrors: Map<String, String>,
    isLoading: Boolean,
    onRegister: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val passwordValue = password
    val showRequirements = passwordValue.isNotEmpty()
    val requirements = if (showRequirements) Validators.passwordRequirements(passwordValue) else null
    val metCount = requirements?.let {
        listOf(it.hasMinLength, it.hasCapital, it.hasNumber, it.hasSpecial).count { met -> met }
    } ?: 0
    val rows = if (showRequirements) {
        listOf(
            PasswordRequirementRow("At least 8 characters", requirements!!.hasMinLength),
            PasswordRequirementRow("One uppercase letter", requirements.hasCapital),
            PasswordRequirementRow("One number", requirements.hasNumber),
            PasswordRequirementRow("One special character", requirements.hasSpecial),
        )
    } else {
        emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        AuthHeader(
            title = "Create Account",
            subtitle = "Join EventQR to scan tickets and manage your events.",
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = spacing.large,
                    vertical = spacing.extraLarge,
                ),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                AuthTextField(
                    value = firstName,
                    onValueChange = onFirstNameChange,
                    label = "First Name",
                    errorMessage = fieldErrors["firstName"],
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                )
                AuthTextField(
                    value = lastName,
                    onValueChange = onLastNameChange,
                    label = "Last Name",
                    errorMessage = fieldErrors["lastName"],
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(spacing.mediumSmall))

            AuthTextField(
                value = email,
                onValueChange = onEmailChange,
                label = "Email Address",
                placeholder = "Enter email address",
                errorMessage = fieldErrors["email"],
                keyboardType = KeyboardType.Email,
                enabled = !isLoading,
            )

            Spacer(modifier = Modifier.height(spacing.mediumSmall))

            AuthTextField(
                value = phoneDigits,
                onValueChange = onPhoneDigitsChange,
                label = "Mobile Number",
                placeholder = "9171234567",
                errorMessage = fieldErrors["phone"],
                helperMessage = "Enter the 10-digit number after +63",
                keyboardType = KeyboardType.Phone,
                enabled = !isLoading,
            )

            Text(
                text = "${phoneDigits.length}/10",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.nano),
                textAlign = TextAlign.End,
            )

            Spacer(modifier = Modifier.height(spacing.mediumSmall))

            AuthTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = "Password",
                placeholder = "Enter password",
                errorMessage = fieldErrors["password"],
                isPassword = true,
                keyboardType = KeyboardType.Password,
                enabled = !isLoading,
            )

            if (showRequirements) {
                Spacer(modifier = Modifier.height(spacing.small))
                PasswordStrengthPanel(
                    rows = rows,
                    metCount = metCount,
                )
            }

            Spacer(modifier = Modifier.height(spacing.mediumSmall))

            AuthTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = "Confirm Password",
                placeholder = "Re-enter password",
                errorMessage = fieldErrors["confirmPassword"],
                isPassword = true,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                enabled = !isLoading,
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouchTarget)
                    .toggleable(
                        value = termsAccepted,
                        enabled = !isLoading,
                        role = Role.Checkbox,
                        onValueChange = onTermsAcceptedChange,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = termsAccepted,
                    onCheckedChange = null,
                    enabled = !isLoading,
                    modifier = Modifier
                        .size(spacing.minTouchTarget)
                        .testTag(TERMS_CHECKBOX_TAG),
                )
                Spacer(modifier = Modifier.width(spacing.nano))
                Text(
                    text = TERMS_LABEL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(
                        enabled = !isLoading,
                        role = Role.Checkbox,
                        onClick = { onTermsAcceptedChange(!termsAccepted) },
                    ),
                )
            }

            Spacer(modifier = Modifier.height(spacing.mediumSmall))

            Button(
                onClick = onRegister,
                enabled = !isLoading && Validators.isValidSignUpPassword(password) && termsAccepted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(spacing.buttonHeightMin),
            ) {
                Text(
                    text = if (isLoading) "Creating account..." else "Create Account",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouchTarget),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Already have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onSignIn,
                    modifier = Modifier.height(spacing.minTouchTarget),
                ) {
                    Text(
                        text = "Sign In",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordStrengthPanel(
    rows: List<PasswordRequirementRow>,
    metCount: Int,
) {
    val spacing = LocalSpacing.current
    val accentColor = when (metCount) {
        0 -> MaterialTheme.colorScheme.outline
        1 -> MaterialTheme.colorScheme.error
        2 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val strengthLabel = when (metCount) {
        0 -> ""
        1 -> "Weak"
        2 -> "Fair"
        3 -> "Good"
        else -> "Strong"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Password strength",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (strengthLabel.isNotEmpty()) {
                Text(
                    text = strengthLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = accentColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.extraSmall))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(spacing.progressBarHeight)
                        .clip(RoundedCornerShape(spacing.progressBarCornerRadius))
                        .background(if (index < metCount) accentColor else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.small))

        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.minTouchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (row.isMet) "✓" else "○",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (row.isMet) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(spacing.extraSmall))
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.isMet) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
