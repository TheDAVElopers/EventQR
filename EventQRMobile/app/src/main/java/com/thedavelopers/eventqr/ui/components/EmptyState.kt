package com.thedavelopers.eventqr.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.LocalGrid
import com.thedavelopers.eventqr.ui.theme.LocalType
import com.thedavelopers.eventqr.ui.theme.UiOnPrimary
import com.thedavelopers.eventqr.ui.theme.UiPrimary
import com.thedavelopers.eventqr.ui.theme.UiSurfaceSunken
import com.thedavelopers.eventqr.ui.theme.UiTextPrimary
import com.thedavelopers.eventqr.ui.theme.UiTextSecondary

/*
 * Empty state (§5.10). Left aligned, one left edge, and it names the next action.
 *
 *   48dp sunken icon plate (10dp radius, 24dp icon in ui_text_secondary)
 *   16dp gap, title 18/600 max 2 lines
 *   4dp gap, body 14/20 secondary max 2 lines
 *   16dp gap, exactly ONE action (ghost for low priority, primary for the main task)
 *
 * Banned: illustration blobs, a 96dp glyph, centring, exclamation marks.
 *
 * The View twin is EventQrEmptyState; both render the same anatomy.
 */

/** The single empty-state composable. [primaryAction] picks the one action's weight. */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    primaryAction: Boolean = false,
) {
    val grid = LocalGrid.current
    val type = LocalType.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = grid.emptyMaxWidth)
            .padding(start = grid.gutter, end = grid.gutter, top = grid.s40),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .size(grid.emptyPlate)
                .clip(RoundedCornerShape(grid.radiusCard))
                .background(UiSurfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = UiTextSecondary,
                modifier = Modifier.size(dimensionResource(R.dimen.ui_empty_glyph)),
            )
        }

        Spacer(modifier = Modifier.height(grid.s16))

        Text(
            text = title,
            style = type.title,
            color = UiTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(grid.s4))

        Text(
            text = description,
            style = type.bodyS,
            color = UiTextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (!actionLabel.isNullOrBlank() && onActionClick != null) {
            Spacer(modifier = Modifier.height(grid.s16))
            if (primaryAction) {
                PrimaryEmptyAction(label = actionLabel, onClick = onActionClick)
            } else {
                GhostEmptyAction(label = actionLabel, onClick = onActionClick)
            }
        }
    }
}

@Composable
private fun PrimaryEmptyAction(label: String, onClick: () -> Unit) {
    val grid = LocalGrid.current
    val type = LocalType.current
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(grid.radiusButton),
        colors = ButtonDefaults.buttonColors(containerColor = UiPrimary, contentColor = UiOnPrimary),
        elevation = null,
        modifier = Modifier.height(grid.buttonHeight),
    ) {
        Text(text = label, style = type.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
private fun GhostEmptyAction(label: String, onClick: () -> Unit) {
    val grid = LocalGrid.current
    val type = LocalType.current
    Box(
        modifier = Modifier
            .heightIn(min = grid.touchMin)
            .clip(RoundedCornerShape(grid.radiusButton))
            .clickable(onClick = onClick)
            .padding(horizontal = grid.s16),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = type.label, color = UiPrimary, maxLines = 1)
    }
}
