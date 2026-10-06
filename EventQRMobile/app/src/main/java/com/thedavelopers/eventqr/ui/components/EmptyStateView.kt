package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.thedavelopers.eventqr.ui.theme.LocalSpacing

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
) {
    val spacing = LocalSpacing.current
    val resolvedIconTint = iconTint ?: MaterialTheme.colorScheme.primary
    val resolvedIconBackground = iconBackgroundColor ?: MaterialTheme.colorScheme.surfaceVariant
    val actionText = actionLabel.takeIf { onActionClick != null }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.extraLarge, vertical = spacing.emptyStateVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(spacing.iconContainerLarge)
                .clip(CircleShape)
                .background(resolvedIconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = resolvedIconTint,
                modifier = Modifier.size(spacing.iconSizeXLarge),
            )
        }

        Spacer(modifier = Modifier.height(spacing.mediumLarge))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(spacing.small))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (!actionText.isNullOrBlank() && onActionClick != null) {
            Spacer(modifier = Modifier.height(spacing.large))
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.heightIn(min = spacing.buttonHeightMin),
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
