package com.thedavelopers.eventqr.features.notifications

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.core.api.dto.NotificationType
import com.thedavelopers.eventqr.core.util.RelativeTimeUtils
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.ui.components.EmptyState
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.LocalGrid
import com.thedavelopers.eventqr.ui.theme.LocalType
import com.thedavelopers.eventqr.ui.theme.UiBackground
import com.thedavelopers.eventqr.ui.theme.UiHairline
import com.thedavelopers.eventqr.ui.theme.UiPrimary
import com.thedavelopers.eventqr.ui.theme.UiPrimaryContainer
import com.thedavelopers.eventqr.ui.theme.UiTextPrimary
import com.thedavelopers.eventqr.ui.theme.UiTextSecondary

/**
 * Notifications (§6.5) - retires Gen B.
 *
 * The activity owns the top bar (shared `EventQrDetailHeader`), so this screen draws no header and
 * no Scaffold. Rows use the `list_row_icon` geometry: 88dp, 40dp tonal tile, title, two-line body,
 * relative time in `micro` on the right, and a 3dp `primary` dot at x = 20dp for unread. Hairline
 * dividers inset to the text edge. No per-row card, no circular icon backgrounds, no purple glow.
 */
@Composable
fun NotificationsScreen(
    notifications: List<NotificationResponse>,
    isLoading: Boolean,
    errorMessage: String?,
    onNotificationClick: (NotificationResponse) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = LocalGrid.current

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        containerColor = UiBackground,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(UiBackground),
        ) {
            when {
                isLoading && notifications.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = UiPrimary, strokeWidth = grid.s2)
                }

                errorMessage != null && notifications.isEmpty() -> EmptyState(
                    icon = AppIcons.Error,
                    title = stringResource(R.string.ui_notifications_error_title),
                    description = errorMessage,
                    actionLabel = stringResource(R.string.common_retry),
                    onActionClick = onRetryClick,
                    primaryAction = true,
                )

                notifications.isEmpty() -> EmptyState(
                    icon = AppIcons.NoNotifications,
                    title = stringResource(R.string.ui_notifications_empty_title),
                    description = stringResource(R.string.ui_notifications_empty_body),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = grid.s24),
                ) {
                    items(notifications, key = { it.notificationId }) { item ->
                        NotificationRow(
                            item = item,
                            onClick = { onNotificationClick(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationRow(
    item: NotificationResponse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = LocalGrid.current
    val type = LocalType.current
    val isRead = item.status == NotificationStatus.READ || item.readAt != null
    val timestamp = RelativeTimeUtils.formatRelativeOrDash(item.createdAt)
    val stateLabel = stringResource(
        if (isRead) R.string.ui_notifications_read else R.string.ui_notifications_unread,
    )
    val description = stringResource(
        R.string.ui_notifications_item,
        stateLabel,
        item.title,
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimensionResource(R.dimen.ui_row_height_tall))
                .clickable(onClick = onClick)
                .padding(
                    start = grid.s20,
                    end = grid.s16,
                    top = grid.s12,
                    bottom = grid.s12,
                )
                .semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Unread marker occupies its own 3dp column so the tile never shifts between states.
            Box(
                modifier = Modifier
                    .width(grid.s4)
                    .height(grid.s20),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (!isRead) {
                    Box(
                        modifier = Modifier
                            .size(grid.s4)
                            .clip(RoundedCornerShape(grid.radiusBadge))
                            .background(UiPrimary),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(grid.rowTile)
                    .clip(RoundedCornerShape(grid.radiusCard))
                    .background(UiPrimaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(resolveNotificationIcon(item.notificationType)),
                    contentDescription = null,
                    tint = UiPrimary,
                    modifier = Modifier.size(dimensionResource(R.dimen.icon_md)),
                )
            }

            Spacer(modifier = Modifier.width(grid.s16))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = type.title,
                        color = UiTextPrimary,
                        fontWeight = if (isRead) FontWeight.SemiBold else FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(grid.s8))
                    Text(
                        text = timestamp,
                        style = type.micro,
                        color = UiTextSecondary,
                        maxLines = 1,
                    )
                }

                Spacer(modifier = Modifier.height(grid.s2))

                Text(
                    text = item.message,
                    style = type.bodyS,
                    color = UiTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.stroke_hairline))
                .background(UiBackground),
        ) {
            Spacer(modifier = Modifier.width(grid.dividerInset))
            Box(modifier = Modifier.fillMaxWidth().height(dimensionResource(R.dimen.stroke_hairline)).background(UiHairline))
        }
    }
}

/** One icon per type; colour is not meaning here, so every icon is `primary` on the tonal tile. */
@DrawableRes
internal fun resolveNotificationIcon(type: NotificationType?): Int = when (type) {
    NotificationType.STAFF_ASSIGNMENT -> AppIcons.People
    NotificationType.REGISTRATION_NEW -> AppIcons.Success
    NotificationType.CAPACITY_WARNING, NotificationType.CAPACITY_FULL -> AppIcons.Warning
    NotificationType.REWARD_EXHAUSTED -> AppIcons.Reward
    NotificationType.REWARD_REDEEMED -> AppIcons.Reward
    NotificationType.POINTS_ADJUSTED -> AppIcons.Success
    NotificationType.EVENT_APPROVED -> AppIcons.Success
    NotificationType.EVENT_REJECTED -> AppIcons.Error
    NotificationType.EVENT_STARTING_SOON -> AppIcons.Time
    NotificationType.EVENT_COMPLETED -> AppIcons.Success
    NotificationType.SCAN_REJECTED -> AppIcons.Error
    NotificationType.SCAN_APPROVED -> AppIcons.Success
    else -> AppIcons.Notification
}
