package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.LocalGrid
import com.thedavelopers.eventqr.ui.theme.LocalType
import com.thedavelopers.eventqr.ui.theme.UiHairline
import com.thedavelopers.eventqr.ui.theme.UiPrimary
import com.thedavelopers.eventqr.ui.theme.UiSurface
import com.thedavelopers.eventqr.ui.theme.UiSurfaceSunken
import com.thedavelopers.eventqr.ui.theme.UiTextPrimary
import com.thedavelopers.eventqr.ui.theme.UiTextSecondary
import java.util.Locale

/*
 * Event card (§5.3 Card, §5.5 date block).
 *
 * A hairline card with zero elevation: surface fill, 1dp ui_hairline, radius 10, 16dp padding.
 * The leading block is a NEUTRAL 44dp date tile (sunken fill, day in numeric_m, month in
 * overline) - a date has no semantic state, so it never gets a coloured badge (§P6).
 *
 * Public signature is unchanged, so the screens that already call EventCard inherit this look.
 */

data class EventCardAccent(
    val container: Color,
    val fill: Color,
)

/**
 * Kept for call-site compatibility. The redesign carries status in the ONE badge, not in a card
 * accent, so both slots now resolve to the neutral / primary roles.
 */
@Composable
fun eventCardAccent(status: EventBadgeStatus): EventCardAccent = EventCardAccent(UiTextSecondary, UiPrimary)

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
    val grid = LocalGrid.current
    val type = LocalType.current
    val shape = RoundedCornerShape(grid.radiusCard)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = grid.rowHeightDate)
            .clip(shape)
            .background(UiSurface)
            .border(dimensionResource(R.dimen.stroke_hairline), UiHairline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(grid.cardPadding),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // Neutral date block: 44dp, sunken, 10dp radius.
            Column(
                modifier = Modifier
                    .size(grid.rowDateBlock)
                    .clip(RoundedCornerShape(grid.radiusCard))
                    .background(UiSurfaceSunken),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    text = day.ifBlank { "--" },
                    style = type.numericM,
                    color = UiTextPrimary,
                    maxLines = 1,
                )
                Text(
                    text = month.uppercase(Locale.ENGLISH).ifBlank { "---" },
                    style = type.overline,
                    color = UiTextSecondary,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.width(grid.s16))

            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title.ifBlank { stringResource(R.string.common_untitled_event) },
                        style = type.title,
                        color = UiTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(grid.s8))
                    StatusBadge(status = status, customLabel = statusLabel, showIcon = false)
                }

                if (time.isNotBlank() && time != "-") {
                    Spacer(modifier = Modifier.height(grid.s4))
                    MetaLine(text = time)
                }

                Spacer(modifier = Modifier.height(grid.s2))
                MetaLine(text = location.ifBlank { stringResource(R.string.common_venue_tbd) })
            }

            if (trailingAction != null) {
                Spacer(modifier = Modifier.width(grid.s8))
                trailingAction()
            }
        }

        if (registeredCount != null && capacity != null) {
            Spacer(modifier = Modifier.height(grid.s12))
            if (capacity <= 0) {
                // Capacity 0 means unlimited: count only, no "/ cap", no bar.
                Text(
                    text = stringResource(R.string.ui_card_registered_count, registeredCount),
                    style = type.bodyS,
                    color = UiTextSecondary,
                )
            } else {
                val fraction = (registeredCount.toFloat() / capacity.toFloat()).coerceIn(0f, 1f)
                val percent = (fraction * 100).toInt()
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.ui_card_registered_of, registeredCount, capacity),
                        style = type.bodyS,
                        color = UiTextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.ui_card_percent, percent),
                        style = type.numericS,
                        color = UiTextPrimary,
                    )
                }
                Spacer(modifier = Modifier.height(grid.s8))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensionResource(R.dimen.ui_strength_bar_height))
                        .clip(RoundedCornerShape(grid.s2)),
                    color = progressColor,
                    trackColor = UiSurfaceSunken,
                )
            }
        }

        if (footer != null) {
            Spacer(modifier = Modifier.height(grid.s12))
            footer()
        }
    }
}

@Composable
private fun MetaLine(text: String) {
    val type = LocalType.current
    Text(
        text = text,
        style = type.bodyS,
        color = UiTextSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
