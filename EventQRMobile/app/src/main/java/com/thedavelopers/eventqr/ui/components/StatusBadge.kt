package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.thedavelopers.eventqr.ui.theme.LocalSpacing
import com.thedavelopers.eventqr.ui.theme.StatusActiveGreenBg
import com.thedavelopers.eventqr.ui.theme.StatusActiveGreenBgDark
import com.thedavelopers.eventqr.ui.theme.StatusActiveGreenText
import com.thedavelopers.eventqr.ui.theme.StatusActiveGreenTextDark
import com.thedavelopers.eventqr.ui.theme.StatusApprovedGreenBg
import com.thedavelopers.eventqr.ui.theme.StatusApprovedGreenBgDark
import com.thedavelopers.eventqr.ui.theme.StatusApprovedGreenText
import com.thedavelopers.eventqr.ui.theme.StatusApprovedGreenTextDark
import com.thedavelopers.eventqr.ui.theme.StatusCompletedGrayBg
import com.thedavelopers.eventqr.ui.theme.StatusCompletedGrayBgDark
import com.thedavelopers.eventqr.ui.theme.StatusCompletedGrayText
import com.thedavelopers.eventqr.ui.theme.StatusCompletedGrayTextDark
import com.thedavelopers.eventqr.ui.theme.StatusPendingAmberBg
import com.thedavelopers.eventqr.ui.theme.StatusPendingAmberBgDark
import com.thedavelopers.eventqr.ui.theme.StatusPendingAmberText
import com.thedavelopers.eventqr.ui.theme.StatusPendingAmberTextDark
import com.thedavelopers.eventqr.ui.theme.StatusRegisteredPurpleBg
import com.thedavelopers.eventqr.ui.theme.StatusRegisteredPurpleBgDark
import com.thedavelopers.eventqr.ui.theme.StatusRegisteredPurpleText
import com.thedavelopers.eventqr.ui.theme.StatusRegisteredPurpleTextDark
import com.thedavelopers.eventqr.ui.theme.StatusRejectedRedBg
import com.thedavelopers.eventqr.ui.theme.StatusRejectedRedBgDark
import com.thedavelopers.eventqr.ui.theme.StatusRejectedRedText
import com.thedavelopers.eventqr.ui.theme.StatusRejectedRedTextDark
import java.util.Locale

private const val DARK_SURFACE_LUMINANCE_THRESHOLD = 0.5f

enum class EventBadgeStatus {
    PENDING,
    APPROVED,
    REJECTED,
    ACTIVE,
    COMPLETED,
    REGISTERED,
    UPCOMING,
    CANCELLED,
    DRAFT,
    UNKNOWN,
}

data class BadgeStyle(
    val backgroundColor: Color,
    val textColor: Color,
    val icon: ImageVector?,
    val defaultLabel: String,
)

private data class BadgePalette(
    val backgroundColor: Color,
    val textColor: Color,
)

private fun lightBadgePalette(status: EventBadgeStatus): BadgePalette = when (status) {
    EventBadgeStatus.PENDING, EventBadgeStatus.UPCOMING ->
        BadgePalette(StatusPendingAmberBg, StatusPendingAmberText)
    EventBadgeStatus.APPROVED -> BadgePalette(StatusApprovedGreenBg, StatusApprovedGreenText)
    EventBadgeStatus.REJECTED, EventBadgeStatus.CANCELLED ->
        BadgePalette(StatusRejectedRedBg, StatusRejectedRedText)
    EventBadgeStatus.ACTIVE -> BadgePalette(StatusActiveGreenBg, StatusActiveGreenText)
    EventBadgeStatus.COMPLETED -> BadgePalette(StatusCompletedGrayBg, StatusCompletedGrayText)
    EventBadgeStatus.REGISTERED -> BadgePalette(StatusRegisteredPurpleBg, StatusRegisteredPurpleText)
    EventBadgeStatus.DRAFT, EventBadgeStatus.UNKNOWN ->
        BadgePalette(StatusCompletedGrayBg, StatusCompletedGrayText)
}

private fun darkBadgePalette(status: EventBadgeStatus): BadgePalette = when (status) {
    EventBadgeStatus.PENDING, EventBadgeStatus.UPCOMING ->
        BadgePalette(StatusPendingAmberBgDark, StatusPendingAmberTextDark)
    EventBadgeStatus.APPROVED -> BadgePalette(StatusApprovedGreenBgDark, StatusApprovedGreenTextDark)
    EventBadgeStatus.REJECTED, EventBadgeStatus.CANCELLED ->
        BadgePalette(StatusRejectedRedBgDark, StatusRejectedRedTextDark)
    EventBadgeStatus.ACTIVE -> BadgePalette(StatusActiveGreenBgDark, StatusActiveGreenTextDark)
    EventBadgeStatus.COMPLETED -> BadgePalette(StatusCompletedGrayBgDark, StatusCompletedGrayTextDark)
    EventBadgeStatus.REGISTERED ->
        BadgePalette(StatusRegisteredPurpleBgDark, StatusRegisteredPurpleTextDark)
    EventBadgeStatus.DRAFT, EventBadgeStatus.UNKNOWN ->
        BadgePalette(StatusCompletedGrayBgDark, StatusCompletedGrayTextDark)
}

@Composable
internal fun badgeStyle(status: EventBadgeStatus): BadgeStyle {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE_THRESHOLD
    val palette = if (isDark) darkBadgePalette(status) else lightBadgePalette(status)

    return when (status) {
        EventBadgeStatus.PENDING -> BadgeStyle(
            backgroundColor = palette.backgroundColor,
            textColor = palette.textColor,
            icon = Icons.Default.HourglassTop,
            defaultLabel = "Pending",
        )
        EventBadgeStatus.APPROVED -> BadgeStyle(
            backgroundColor = palette.backgroundColor,
            textColor = palette.textColor,
            icon = Icons.Default.CheckCircle,
            defaultLabel = "Approved",
        )
        EventBadgeStatus.REJECTED -> BadgeStyle(
            backgroundColor = palette.backgroundColor,
            textColor = palette.textColor,
            icon = Icons.Default.Cancel,
            defaultLabel = "Rejected",
        )
        EventBadgeStatus.ACTIVE -> BadgeStyle(
            backgroundColor = palette.backgroundColor,
            textColor = palette.textColor,
            icon = Icons.Default.PlayCircle,
            defaultLabel = "Active",
        )
        EventBadgeStatus.COMPLETED -> BadgeStyle(
            backgroundColor = palette.backgroundColor,
            textColor = palette.textColor,
            icon = Icons.Default.CheckCircle,
            defaultLabel = "Completed",
        )
        EventBadgeStatus.REGISTERED -> BadgeStyle(
            backgroundColor = palette.backgroundColor,
            textColor = palette.textColor,
            icon = Icons.Default.CheckCircle,
            defaultLabel = "Registered",
        )
        EventBadgeStatus.UPCOMING -> BadgeStyle(
            backgroundColor = palette.backgroundColor,
            textColor = palette.textColor,
            icon = Icons.Default.Schedule,
            defaultLabel = "Upcoming",
        )
        EventBadgeStatus.CANCELLED -> BadgeStyle(
            backgroundColor = palette.backgroundColor,
            textColor = palette.textColor,
            icon = Icons.Default.Cancel,
            defaultLabel = "Cancelled",
        )
        EventBadgeStatus.DRAFT -> BadgeStyle(
            backgroundColor = palette.backgroundColor,
            textColor = palette.textColor,
            icon = Icons.Default.Description,
            defaultLabel = "Draft",
        )
        EventBadgeStatus.UNKNOWN -> BadgeStyle(
            backgroundColor = palette.backgroundColor,
            textColor = palette.textColor,
            icon = Icons.Default.HelpOutline,
            defaultLabel = "Unknown",
        )
    }
}

@Composable
fun StatusBadge(
    status: EventBadgeStatus,
    modifier: Modifier = Modifier,
    customLabel: String? = null,
    showIcon: Boolean = true,
) {
    val spacing = LocalSpacing.current
    val style = badgeStyle(status)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(style.backgroundColor)
            .padding(horizontal = spacing.badgeHorizontalPadding, vertical = spacing.badgeContentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIcon && style.icon != null) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.textColor,
                    modifier = Modifier.size(spacing.iconSizeSmall),
                )
                Spacer(modifier = Modifier.width(spacing.extraSmall))
            }
            Text(
                text = customLabel ?: style.defaultLabel,
                color = style.textColor,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

fun parseBadgeStatus(raw: String?): EventBadgeStatus {
    val norm = raw?.uppercase(Locale.ENGLISH)?.trim().orEmpty()
    return when {
        norm.contains("DRAFT") -> EventBadgeStatus.DRAFT
        norm.contains("PEND") -> EventBadgeStatus.PENDING
        norm.contains("APPROV") -> EventBadgeStatus.APPROVED
        norm.contains("REJECT") -> EventBadgeStatus.REJECTED
        norm.contains("ACTIVE") || norm.contains("LIVE") || norm.contains("ONGOING") -> EventBadgeStatus.ACTIVE
        norm.contains("COMPLET") || norm.contains("ENDED") || norm.contains("PAST") -> EventBadgeStatus.COMPLETED
        norm.contains("REGIST") -> EventBadgeStatus.REGISTERED
        norm.contains("UPCOM") -> EventBadgeStatus.UPCOMING
        norm.contains("CANCEL") -> EventBadgeStatus.CANCELLED
        else -> EventBadgeStatus.UNKNOWN
    }
}
