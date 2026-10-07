package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.thedavelopers.eventqr.ui.theme.EventAccentActive
import com.thedavelopers.eventqr.ui.theme.EventAccentActiveFill
import com.thedavelopers.eventqr.ui.theme.EventAccentApproved
import com.thedavelopers.eventqr.ui.theme.EventAccentApprovedFill
import com.thedavelopers.eventqr.ui.theme.EventAccentCompleted
import com.thedavelopers.eventqr.ui.theme.EventAccentCompletedFill
import com.thedavelopers.eventqr.ui.theme.EventAccentPending
import com.thedavelopers.eventqr.ui.theme.EventAccentPendingFill
import com.thedavelopers.eventqr.ui.theme.EventAccentRejected
import com.thedavelopers.eventqr.ui.theme.EventAccentRejectedFill
import com.thedavelopers.eventqr.ui.theme.EventAccentRegistered
import com.thedavelopers.eventqr.ui.theme.EventAccentRegisteredFill
import com.thedavelopers.eventqr.ui.theme.EventAccentUpcoming
import com.thedavelopers.eventqr.ui.theme.EventAccentUpcomingFill
import com.thedavelopers.eventqr.ui.theme.LocalSpacing
import java.util.Locale

data class EventCardAccent(
    val container: Color,
    val fill: Color,
)

fun eventCardAccent(status: EventBadgeStatus): EventCardAccent = when (status) {
    EventBadgeStatus.ACTIVE -> EventCardAccent(EventAccentActive, EventAccentActiveFill)
    EventBadgeStatus.COMPLETED -> EventCardAccent(EventAccentCompleted, EventAccentCompletedFill)
    EventBadgeStatus.UPCOMING -> EventCardAccent(EventAccentUpcoming, EventAccentUpcomingFill)
    EventBadgeStatus.PENDING -> EventCardAccent(EventAccentPending, EventAccentPendingFill)
    EventBadgeStatus.APPROVED -> EventCardAccent(EventAccentApproved, EventAccentApprovedFill)
    EventBadgeStatus.REGISTERED -> EventCardAccent(EventAccentRegistered, EventAccentRegisteredFill)
    EventBadgeStatus.REJECTED -> EventCardAccent(EventAccentRejected, EventAccentRejectedFill)
    EventBadgeStatus.CANCELLED -> EventCardAccent(EventAccentRejected, EventAccentRejectedFill)
    EventBadgeStatus.DRAFT -> EventCardAccent(EventAccentCompleted, EventAccentCompletedFill)
    EventBadgeStatus.UNKNOWN -> EventCardAccent(EventAccentCompleted, EventAccentCompletedFill)
}

@Composable
fun EventCard(
    title: String,
    status: EventBadgeStatus,
    day: String,
    month: String,
    time: String,
    location: String,
    modifier: Modifier = Modifier,
    statusLabel: String? = null,
    registeredCount: Int? = null,
    capacity: Int? = null,
    accentColor: Color = eventCardAccent(status).container,
    progressColor: Color = eventCardAccent(status).fill,
    onClick: (() -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
    val progressTrackColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.cardMinHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(spacing.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = spacing.cardElevation),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.cardContentPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(spacing.dateBadgeSize)
                        .clip(RoundedCornerShape(spacing.cardCornerRadiusSmall))
                        .background(accentColor)
                        .padding(spacing.badgeContentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = day.ifBlank { "--" },
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = month.uppercase(Locale.ENGLISH).ifBlank { "---" },
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(spacing.cardContentGap))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title.ifBlank { "Untitled Event" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(spacing.small))
                        StatusBadge(
                            status = status,
                            customLabel = statusLabel,
                        )
                    }

                    Spacer(modifier = Modifier.height(spacing.micro))

                    if (time.isNotBlank() && time != "-") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = metaColor,
                                modifier = Modifier.size(spacing.iconSizeSmall),
                            )
                            Spacer(modifier = Modifier.width(spacing.extraSmall))
                            Text(
                                text = time,
                                style = MaterialTheme.typography.bodySmall,
                                color = metaColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.nano))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = metaColor,
                            modifier = Modifier.size(spacing.iconSizeSmall),
                        )
                        Spacer(modifier = Modifier.width(spacing.extraSmall))
                        Text(
                            text = location.ifBlank { "Venue TBD" },
                            style = MaterialTheme.typography.bodySmall,
                            color = metaColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (trailingAction != null) {
                    Spacer(modifier = Modifier.width(spacing.small))
                    trailingAction()
                }
            }

            if (registeredCount != null && capacity != null && capacity > 0) {
                Spacer(modifier = Modifier.height(spacing.mediumSmall))
                val fraction = (registeredCount.toFloat() / capacity.toFloat()).coerceIn(0f, 1f)
                val percent = (fraction * 100).toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$registeredCount / $capacity Registered",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = metaColor,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(spacing.micro))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(spacing.progressBarHeight)
                        .clip(RoundedCornerShape(spacing.progressBarCornerRadius)),
                    color = progressColor,
                    trackColor = progressTrackColor,
                )
            }

            if (footer != null) {
                Spacer(modifier = Modifier.height(spacing.mediumSmall))
                footer()
            }
        }
    }
}
