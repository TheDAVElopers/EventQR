package com.thedavelopers.eventqr.features.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.core.api.dto.NotificationType
import com.thedavelopers.eventqr.core.util.RelativeTimeUtils
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.ui.components.EmptyStateView
import com.thedavelopers.eventqr.ui.components.EventQrTopAppBar
import com.thedavelopers.eventqr.ui.theme.BackgroundLight
import com.thedavelopers.eventqr.ui.theme.BorderLight
import com.thedavelopers.eventqr.ui.theme.BrandPrimary
import com.thedavelopers.eventqr.ui.theme.PaperWhite
import com.thedavelopers.eventqr.ui.theme.StatCardAmberBg
import com.thedavelopers.eventqr.ui.theme.StatCardAmberIcon
import com.thedavelopers.eventqr.ui.theme.StatCardGreenBg
import com.thedavelopers.eventqr.ui.theme.StatCardGreenIcon
import com.thedavelopers.eventqr.ui.theme.StatCardPurpleBg
import com.thedavelopers.eventqr.ui.theme.StatCardPurpleIcon
import com.thedavelopers.eventqr.ui.theme.StatusRejectedRedBg
import com.thedavelopers.eventqr.ui.theme.StatusRejectedRedText
import com.thedavelopers.eventqr.ui.theme.SurfaceAlt
import com.thedavelopers.eventqr.ui.theme.TextMuted
import com.thedavelopers.eventqr.ui.theme.TextPrimary
import com.thedavelopers.eventqr.ui.theme.TextSecondary

@Composable
fun NotificationsScreen(
    notifications: List<NotificationResponse>,
    isLoading: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onNotificationClick: (NotificationResponse) -> Unit,
    onMarkAllReadClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            EventQrTopAppBar(
                title = "Notifications",
                onBackClick = onBackClick,
                actions = {
                    val hasUnread = notifications.any { it.status != NotificationStatus.READ && it.readAt == null }
                    if (notifications.isNotEmpty()) {
                        TextButton(
                            onClick = onMarkAllReadClick,
                            enabled = hasUnread,
                        ) {
                            Text(
                                text = "Mark all read",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (hasUnread) BrandPrimary else TextMuted,
                            )
                        }
                    }
                },
            )
        },
        containerColor = BackgroundLight,
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
                        CircularProgressIndicator(color = BrandPrimary)
                    }
                }
                errorMessage != null && notifications.isEmpty() -> {
                    EmptyStateView(
                        icon = Icons.Default.Error,
                        title = "Couldn't Load Notifications",
                        description = errorMessage,
                        actionLabel = "Retry",
                        onActionClick = onRetryClick,
                        iconTint = StatusRejectedRedText,
                        iconBackgroundColor = StatusRejectedRedBg,
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
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        items(notifications, key = { it.notificationId }) { item ->
                            NotificationItemCard(
                                item = item,
                                onClick = { onNotificationClick(item) },
                            )
                            Spacer(modifier = Modifier.height(10.dp))
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
    val isRead = item.status == NotificationStatus.READ || item.readAt != null
    val (icon, iconTint, iconBg) = resolveNotificationVisuals(item.notificationType)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isRead) PaperWhite else SurfaceAlt)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Icon Container
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content
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
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!isRead) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(BrandPrimary),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.message,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = RelativeTimeUtils.formatRelative(item.createdAt ?: item.readAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }
    }
}

private fun resolveNotificationVisuals(type: NotificationType?): Triple<ImageVector, Color, Color> {
    return when (type) {
        NotificationType.STAFF_ASSIGNMENT -> Triple(Icons.Default.Group, StatCardPurpleIcon, StatCardPurpleBg)
        NotificationType.REGISTRATION_NEW -> Triple(Icons.Default.CheckCircle, StatCardGreenIcon, StatCardGreenBg)
        NotificationType.CAPACITY_WARNING, NotificationType.CAPACITY_FULL -> Triple(Icons.Default.Notifications, StatCardAmberIcon, StatCardAmberBg)
        NotificationType.REWARD_EXHAUSTED -> Triple(Icons.Default.CardGiftcard, StatusRejectedRedText, StatusRejectedRedBg)
        NotificationType.REWARD_REDEEMED -> Triple(Icons.Default.CardGiftcard, StatCardGreenIcon, StatCardGreenBg)
        NotificationType.POINTS_ADJUSTED -> Triple(Icons.Default.CheckCircle, StatCardPurpleIcon, StatCardPurpleBg)
        NotificationType.EVENT_APPROVED -> Triple(Icons.Default.CheckCircle, StatCardGreenIcon, StatCardGreenBg)
        NotificationType.EVENT_REJECTED -> Triple(Icons.Default.Error, StatusRejectedRedText, StatusRejectedRedBg)
        NotificationType.EVENT_STARTING_SOON -> Triple(Icons.Default.Schedule, StatCardPurpleIcon, StatCardPurpleBg)
        NotificationType.EVENT_COMPLETED -> Triple(Icons.Default.CheckCircle, StatCardGreenIcon, StatCardGreenBg)
        NotificationType.SCAN_REJECTED -> Triple(Icons.Default.Error, StatusRejectedRedText, StatusRejectedRedBg)
        NotificationType.SCAN_APPROVED -> Triple(Icons.Default.CheckCircle, StatCardGreenIcon, StatCardGreenBg)
        else -> Triple(Icons.Default.Notifications, StatCardGreenIcon, StatCardGreenBg)
    }
}
