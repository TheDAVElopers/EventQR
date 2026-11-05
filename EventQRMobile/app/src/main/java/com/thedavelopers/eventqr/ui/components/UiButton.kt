package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.thedavelopers.eventqr.ui.theme.LocalGrid
import com.thedavelopers.eventqr.ui.theme.LocalType
import com.thedavelopers.eventqr.ui.theme.UiDisabledFill
import com.thedavelopers.eventqr.ui.theme.UiError
import com.thedavelopers.eventqr.ui.theme.UiOnPrimary
import com.thedavelopers.eventqr.ui.theme.UiPrimary
import com.thedavelopers.eventqr.ui.theme.UiPrimaryContainer
import com.thedavelopers.eventqr.ui.theme.UiSurface
import com.thedavelopers.eventqr.ui.theme.UiTextDisabled

/**
 * The one button family (§5.1): flat, radius 4, elevation 0, sentence case.
 *
 * Tones: [UiButtonTone.PRIMARY] (`ui_primary` fill / white label) and
 * [UiButtonTone.SECONDARY] (`ui_primary_container` fill / `ui_primary` label). Ghost actions are
 * text-only and belong to [UiTextAction] below, not to a filled control.
 *
 * Heights: 52dp for a screen-level action, 44dp when [inline] - the touch target stays 48dp in both
 * cases. No pill radius, no shadow, no all caps.
 */
enum class UiButtonTone { PRIMARY, SECONDARY, DESTRUCTIVE }

@Composable
fun UiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: UiButtonTone = UiButtonTone.PRIMARY,
    inline: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val grid = LocalGrid.current
    val type = LocalType.current
    val shape = RoundedCornerShape(grid.radiusButton)
    val height = if (inline) grid.buttonHeightInline else grid.buttonHeight
    val fill = when {
        !enabled -> UiDisabledFill
        tone == UiButtonTone.SECONDARY -> UiPrimaryContainer
        tone == UiButtonTone.DESTRUCTIVE -> UiSurface
        else -> UiPrimary
    }
    val labelColor = when {
        !enabled -> UiTextDisabled
        tone == UiButtonTone.SECONDARY -> UiPrimary
        tone == UiButtonTone.DESTRUCTIVE -> UiError
        else -> UiOnPrimary
    }
    val border = if (tone == UiButtonTone.DESTRUCTIVE) {
        Modifier.border(androidx.compose.ui.res.dimensionResource(com.thedavelopers.eventqr.R.dimen.stroke_hairline), UiError, shape)
    } else {
        Modifier
    }
    val label = if (inline) type.label else type.body.copy(fontWeight = FontWeight.SemiBold)

    Box(
        modifier = modifier
            .height(height)
            .heightIn(min = grid.touchMin)
            .clip(shape)
            .background(fill)
            .then(border)
            .selectable(
                selected = false,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(grid.s20),
                color = labelColor,
                strokeWidth = grid.s2,
            )
        } else {
            // Label padding is horizontal only; the height is the control's own contract.
            Text(
                text = text,
                style = label,
                color = labelColor,
                modifier = Modifier.padding(horizontal = grid.s16),
                maxLines = 1,
            )
        }
    }
}

/** Ghost text action: no fill, `ui_primary` label, 44dp minimum target (§5.1). */
@Composable
fun UiTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val grid = LocalGrid.current
    val type = LocalType.current

    Box(
        modifier = modifier
            .heightIn(min = grid.touchMin)
            .clip(RoundedCornerShape(grid.radiusButton))
            .selectable(
                selected = false,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = type.label,
            color = if (enabled) UiPrimary else UiTextDisabled,
            modifier = Modifier.padding(PaddingValues(horizontal = grid.s8)),
            maxLines = 1,
        )
    }
}

// Kept so a caller can build a full-width footer button without repeating the modifier chain.
@Composable
fun UiPrimaryButtonFullWidth(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UiButton(text = text, onClick = onClick, modifier = modifier.fillMaxWidth())
}
