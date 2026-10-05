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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thedavelopers.eventqr.ui.theme.StatusActiveGreenBg
import com.thedavelopers.eventqr.ui.theme.StatusActiveGreenText
import com.thedavelopers.eventqr.ui.theme.StatusApprovedGreenBg
import com.thedavelopers.eventqr.ui.theme.StatusApprovedGreenText
import com.thedavelopers.eventqr.ui.theme.StatusCompletedGrayBg
import com.thedavelopers.eventqr.ui.theme.StatusCompletedGrayText
import com.thedavelopers.eventqr.ui.theme.StatusPendingAmberBg
import com.thedavelopers.eventqr.ui.theme.StatusPendingAmberText
import com.thedavelopers.eventqr.ui.theme.StatusRegisteredPurpleBg
import com.thedavelopers.eventqr.ui.theme.StatusRegisteredPurpleText
import com.thedavelopers.eventqr.ui.theme.StatusRejectedRedBg
import com.thedavelopers.eventqr.ui.theme.StatusRejectedRedText
import java.util.Locale

enum class EventBadgeStatus {
    PENDING,
    APPROVED,
    REJECTED,
    ACTIVE,
    COMPLETED,
    REGISTERED,
    UPCOMING,
    CANCELLED,
}

data class BadgeStyle(
    val backgroundColor: Color,
    val textColor: Color,
    val icon: ImageVector?,
    val defaultLabel: String,
)

@Composable
fun StatusBadge(
    status: EventBadgeStatus,
    modifier: Modifier = Modifier,
    customLabel: String? = null,
    showIcon: Boolean = true,
) {
    val style = when (status) {
        EventBadgeStatus.PENDING -> BadgeStyle(
            backgroundColor = StatusPendingAmberBg,
            textColor = StatusPendingAmberText,
            icon = Icons.Default.HourglassTop,
            defaultLabel = "Pending",
        )
        EventBadgeStatus.APPROVED -> BadgeStyle(
            backgroundColor = StatusApprovedGreenBg,
            textColor = StatusApprovedGreenText,
            icon = Icons.Default.CheckCircle,
            defaultLabel = "Approved",
        )
        EventBadgeStatus.REJECTED -> BadgeStyle(
            backgroundColor = StatusRejectedRedBg,
            textColor = StatusRejectedRedText,
            icon = Icons.Default.Cancel,
            defaultLabel = "Rejected",
        )
        EventBadgeStatus.ACTIVE -> BadgeStyle(
            backgroundColor = StatusActiveGreenBg,
            textColor = StatusActiveGreenText,
            icon = Icons.Default.PlayCircle,
            defaultLabel = "Active",
        )
        EventBadgeStatus.COMPLETED -> BadgeStyle(
            backgroundColor = StatusCompletedGrayBg,
            textColor = StatusCompletedGrayText,
            icon = Icons.Default.CheckCircle,
            defaultLabel = "Completed",
        )
        EventBadgeStatus.REGISTERED -> BadgeStyle(
            backgroundColor = StatusRegisteredPurpleBg,
            textColor = StatusRegisteredPurpleText,
            icon = Icons.Default.CheckCircle,
            defaultLabel = "Registered",
        )
        EventBadgeStatus.UPCOMING -> BadgeStyle(
            backgroundColor = StatusPendingAmberBg,
            textColor = StatusPendingAmberText,
            icon = Icons.Default.Schedule,
            defaultLabel = "Upcoming",
        )
        EventBadgeStatus.CANCELLED -> BadgeStyle(
            backgroundColor = StatusRejectedRedBg,
            textColor = StatusRejectedRedText,
            icon = Icons.Default.Cancel,
            defaultLabel = "Cancelled",
        )
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(style.backgroundColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
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
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
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
        norm.contains("PEND") -> EventBadgeStatus.PENDING
        norm.contains("APPROV") -> EventBadgeStatus.APPROVED
        norm.contains("REJECT") -> EventBadgeStatus.REJECTED
        norm.contains("ACTIVE") || norm.contains("LIVE") || norm.contains("ONGOING") -> EventBadgeStatus.ACTIVE
        norm.contains("COMPLET") || norm.contains("ENDED") || norm.contains("PAST") -> EventBadgeStatus.COMPLETED
        norm.contains("REGIST") -> EventBadgeStatus.REGISTERED
        norm.contains("UPCOM") -> EventBadgeStatus.UPCOMING
        norm.contains("CANCEL") -> EventBadgeStatus.CANCELLED
        else -> EventBadgeStatus.PENDING
    }
}
