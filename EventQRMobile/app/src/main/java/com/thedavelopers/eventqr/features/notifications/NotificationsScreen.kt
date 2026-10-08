package com.thedavelopers.eventqr.features.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.core.api.dto.NotificationType
import com.thedavelopers.eventqr.core.util.RelativeTimeUtils
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.ui.components.EmptyStateView
import com.thedavelopers.eventqr.ui.theme.LocalSpacing
import com.thedavelopers.eventqr.ui.theme.StatusActiveGreen
import com.thedavelopers.eventqr.ui.theme.StatusActiveGreenBg
import androidx.compose.ui.res.stringResource
import com.thedavelopers.eventqr.R

private val UNREAD_TINT_ALPHA = 0.08f

@Composable
fun NotificationsScreen(
    notifications: List<NotificationResponse>,
    isLoading: Boolean,
    errorMessage: String?,
    onNotificationClick: (NotificationResponse) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        // The header (title, back, Mark all read) is the shared EventQrDetailHeader, hosted by the activity.
        contentWindowInsets = WindowInsets.navigationBars,
        containerColor = colorScheme.background,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                isLoading && notifications.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colorScheme.primary)
                    }
                }
                errorMessage != null && notifications.isEmpty() -> {
                    EmptyStateView(
                        icon = Icons.Default.Error,
                        title = "Couldn't Load Notifications",
                        description = errorMessage,
                        actionLabel = "Retry",
                        onActionClick = onRetryClick,
                        iconTint = colorScheme.error,
                        iconBackgroundColor = colorScheme.errorContainer,
                    )
                }
                notifications.isEmpty() -> {
                    EmptyStateView(
                        icon = Icons.Default.NotificationsOff,
                        title = "No Notifications",
                        description = "You're all caught up! When you receive updates about your events or registrations, they will appear here.",
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(spacing.screenHorizontalPadding),
                    ) {
                        items(notifications, key = { it.notificationId }) { item ->
                            NotificationItemCard(
                                item = item,
                                onClick = { onNotificationClick(item) },
                            )
                            Spacer(modifier = Modifier.height(spacing.small + spacing.nano))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItemCard(
    item: NotificationResponse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val colorScheme = MaterialTheme.colorScheme
    val isRead = item.status == NotificationStatus.READ || item.readAt != null
    val (icon, iconTint, iconBg) = resolveNotificationVisuals(item.notificationType)
    val timestamp = RelativeTimeUtils.formatRelativeOrDash(item.createdAt)
    val containerColor = if (isRead) {
        colorScheme.surface
    } else {
        colorScheme.primaryContainer.copy(alpha = UNREAD_TINT_ALPHA)
    }
    val stateLabel = if (isRead) stringResource(R.string.notifications_read_notification) else stringResource(R.string.notifications_unread_notification)
    val itemDescription = stringResource(R.string.notifications_item_1_s_2_s, stateLabel, item.title)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(spacing.cardContentGap))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(spacing.cardContentGap)
            .semantics { contentDescription = itemDescription },
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(spacing.mediumLarge + spacing.mediumLarge)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(spacing.iconSizeMedium),
            )
        }

        Spacer(modifier = Modifier.width(spacing.mediumSmall))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isRead) FontWeight.SemiBold else FontWeight.Bold,
                        color = colorScheme.onSurface,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!isRead) {
                    Spacer(modifier = Modifier.width(spacing.micro))
                    Box(
                        modifier = Modifier
                            .size(spacing.small)
                            .clip(CircleShape)
                            .background(colorScheme.primary),
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.extraSmall))

            Text(
                text = item.message,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(spacing.micro))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(spacing.extraSmall + spacing.small),
                )
                Spacer(modifier = Modifier.width(spacing.extraSmall))
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun resolveNotificationVisuals(type: NotificationType?): Triple<ImageVector, Color, Color> {
    val colorScheme = MaterialTheme.colorScheme
    val successIcon = StatusActiveGreen
    val successBg = StatusActiveGreenBg
    val warningIcon = colorScheme.onSecondaryContainer
    val warningBg = colorScheme.secondaryContainer
    val errorIcon = colorScheme.onErrorContainer
    val errorBg = colorScheme.errorContainer
    val infoIcon = colorScheme.onPrimaryContainer
    val infoBg = colorScheme.primaryContainer

    return when (type) {
        NotificationType.STAFF_ASSIGNMENT -> Triple(Icons.Default.Group, infoIcon, infoBg)
        NotificationType.REGISTRATION_NEW -> Triple(Icons.Default.CheckCircle, successIcon, successBg)
        NotificationType.CAPACITY_WARNING, NotificationType.CAPACITY_FULL ->
            Triple(Icons.Default.Warning, warningIcon, warningBg)
        NotificationType.REWARD_EXHAUSTED -> Triple(Icons.Default.CardGiftcard, errorIcon, errorBg)
        NotificationType.REWARD_REDEEMED -> Triple(Icons.Default.CardGiftcard, successIcon, successBg)
        NotificationType.POINTS_ADJUSTED -> Triple(Icons.Default.CheckCircle, infoIcon, infoBg)
        NotificationType.EVENT_APPROVED -> Triple(Icons.Default.CheckCircle, successIcon, successBg)
        NotificationType.EVENT_REJECTED -> Triple(Icons.Default.Error, errorIcon, errorBg)
        NotificationType.EVENT_STARTING_SOON -> Triple(Icons.Default.Schedule, infoIcon, infoBg)
        NotificationType.EVENT_COMPLETED -> Triple(Icons.Default.CheckCircle, successIcon, successBg)
        NotificationType.SCAN_REJECTED -> Triple(Icons.Default.Error, errorIcon, errorBg)
        NotificationType.SCAN_APPROVED -> Triple(Icons.Default.CheckCircle, successIcon, successBg)
        else -> Triple(Icons.Default.Notifications, infoIcon, infoBg)
    }
}
