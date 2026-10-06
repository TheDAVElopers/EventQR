package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.thedavelopers.eventqr.ui.theme.LocalSpacing

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    errorMessage: String? = null,
    helperMessage: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    val spacing = LocalSpacing.current
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val visualTransformation = when {
        !isPassword -> VisualTransformation.None
        passwordVisible -> VisualTransformation.None
        else -> PasswordVisualTransformation()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(spacing.inputHeightMin),
            enabled = enabled,
            label = { Text(text = label) },
            placeholder = placeholder?.let { { Text(text = it) } },
            isError = errorMessage != null,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyLarge,
            visualTransformation = visualTransformation,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            trailingIcon = {
                if (isPassword) {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                }
            },
            supportingText = {
                val support = errorMessage ?: helperMessage
                if (support != null) {
                    Text(text = support, textAlign = TextAlign.Start)
                }
            },
        )
        Spacer(modifier = Modifier.height(spacing.nano))
    }
}
