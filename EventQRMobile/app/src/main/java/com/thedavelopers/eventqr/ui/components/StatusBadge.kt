package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import com.thedavelopers.eventqr.ui.theme.LocalGrid
import com.thedavelopers.eventqr.ui.theme.LocalType
import com.thedavelopers.eventqr.ui.theme.UiAccent
import com.thedavelopers.eventqr.ui.theme.UiErrorContainer
import com.thedavelopers.eventqr.ui.theme.UiInfoContainer
import com.thedavelopers.eventqr.ui.theme.UiOnAccent
import com.thedavelopers.eventqr.ui.theme.UiOnErrorContainer
import com.thedavelopers.eventqr.ui.theme.UiOnInfoContainer
import com.thedavelopers.eventqr.ui.theme.UiOnSuccessContainer
import com.thedavelopers.eventqr.ui.theme.UiOnWarningContainer
import com.thedavelopers.eventqr.ui.theme.UiSuccessContainer
import com.thedavelopers.eventqr.ui.theme.UiSurfaceSunken
import com.thedavelopers.eventqr.ui.theme.UiTextSecondary
import com.thedavelopers.eventqr.ui.theme.UiWarningContainer
import java.util.Locale

/*
 * THE status badge (§5.4). One component, six tones, one shape, one size - it replaces forty
 * badge/pill drawables.
 *
 *   height 24dp, full radius, 8dp side padding, optional 6dp dot in the label colour
 *   label 11sp / 600 / 0.06em, sentence case (a badge reads as a word: "Checked in")
 *
 * The tone mapping is the ONLY permitted status vocabulary. If a screen needs a new status, it
 * maps onto one of these six, it does not get a new drawable (§10.6).
 */

enum class BadgeTone {
    NEUTRAL,
    SUCCESS,
    WARNING,
    ERROR,
    INFO,

    /** The only lime badge: "this is confirmed" (valid pass, checked in, entered, selected). */
    VALID,
}

/**
 * Legacy status enum, kept because ~15 screens and two adapters call `StatusBadge(status = ...)`.
 * New code should pass a [BadgeTone] to [UiStatusBadge] instead.
 */
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

/** Source-compatible shape for the old API. [icon] is no longer used; the badge draws a dot. */
data class BadgeStyle(
    val backgroundColor: Color,
    val textColor: Color,
    val icon: ImageVector?,
    val defaultLabel: String,
    val tone: BadgeTone = BadgeTone.NEUTRAL,
)

private data class ToneColors(val container: Color, val content: Color)

@Composable
@ReadOnlyComposable
private fun toneColors(tone: BadgeTone): ToneColors = when (tone) {
    BadgeTone.NEUTRAL -> ToneColors(UiSurfaceSunken, UiTextSecondary)
    BadgeTone.SUCCESS -> ToneColors(UiSuccessContainer, UiOnSuccessContainer)
    BadgeTone.WARNING -> ToneColors(UiWarningContainer, UiOnWarningContainer)
    BadgeTone.ERROR -> ToneColors(UiErrorContainer, UiOnErrorContainer)
    BadgeTone.INFO -> ToneColors(UiInfoContainer, UiOnInfoContainer)
    BadgeTone.VALID -> ToneColors(UiAccent, UiOnAccent)
}

/** §5.4 mapping table. This is the whole permitted status vocabulary. */
fun toneFor(status: EventBadgeStatus): BadgeTone = when (status) {
    EventBadgeStatus.PENDING -> BadgeTone.WARNING
    EventBadgeStatus.APPROVED -> BadgeTone.SUCCESS
    EventBadgeStatus.REJECTED -> BadgeTone.ERROR
    EventBadgeStatus.ACTIVE -> BadgeTone.VALID
    EventBadgeStatus.COMPLETED -> BadgeTone.NEUTRAL
    EventBadgeStatus.REGISTERED -> BadgeTone.NEUTRAL
    EventBadgeStatus.UPCOMING -> BadgeTone.INFO
    EventBadgeStatus.CANCELLED -> BadgeTone.NEUTRAL
    EventBadgeStatus.DRAFT -> BadgeTone.NEUTRAL
    EventBadgeStatus.UNKNOWN -> BadgeTone.NEUTRAL
}

fun defaultLabelFor(status: EventBadgeStatus): String = when (status) {
    EventBadgeStatus.PENDING -> "Pending review"
    EventBadgeStatus.APPROVED -> "Approved"
    EventBadgeStatus.REJECTED -> "Rejected"
    EventBadgeStatus.ACTIVE -> "Active"
    EventBadgeStatus.COMPLETED -> "Completed"
    EventBadgeStatus.REGISTERED -> "Registered"
    EventBadgeStatus.UPCOMING -> "Upcoming"
    EventBadgeStatus.CANCELLED -> "Cancelled"
    EventBadgeStatus.DRAFT -> "Draft"
    EventBadgeStatus.UNKNOWN -> "Unknown"
}

@Composable
internal fun badgeStyle(status: EventBadgeStatus): BadgeStyle {
    val tone = toneFor(status)
    val colors = toneColors(tone)
    return BadgeStyle(
        backgroundColor = colors.container,
        textColor = colors.content,
        icon = null,
        defaultLabel = defaultLabelFor(status),
        tone = tone,
    )
}

/**
 * The badge. [showIcon] is kept for call-site compatibility and now means "show the 6dp status
 * dot"; the tone, not the dot, is the decoration.
 */
@Composable
fun StatusBadge(
    status: EventBadgeStatus,
    modifier: Modifier = Modifier,
    customLabel: String? = null,
    showIcon: Boolean = true,
) {
    val style = badgeStyle(status)
    UiStatusBadge(
        text = customLabel ?: style.defaultLabel,
        tone = style.tone,
        modifier = modifier,
        showDot = showIcon,
    )
}

/** Tone-first entry point. Prefer this in new code. */
@Composable
fun UiStatusBadge(
    text: String,
    tone: BadgeTone,
    modifier: Modifier = Modifier,
    showDot: Boolean = false,
) {
    val grid = LocalGrid.current
    val type: TextStyle = LocalType.current.badge
    val colors = toneColors(tone)

    Box(
        modifier = modifier
            .heightIn(min = grid.badgeHeight)
            .clip(RoundedCornerShape(grid.radiusBadge))
            .background(colors.container)
            .padding(horizontal = grid.badgePadH),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(grid.badgeDot)
                        .clip(RoundedCornerShape(grid.radiusBadge))
                        .background(colors.content),
                )
                Spacer(modifier = Modifier.width(grid.s4))
            }
            Text(text = text, color = colors.content, style = type, maxLines = 1)
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
