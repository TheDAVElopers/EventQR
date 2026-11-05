package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.LocalGrid
import com.thedavelopers.eventqr.ui.theme.LocalType
import com.thedavelopers.eventqr.ui.theme.UiHairline
import com.thedavelopers.eventqr.ui.theme.UiOnPrimary
import com.thedavelopers.eventqr.ui.theme.UiOutlineInteractive
import com.thedavelopers.eventqr.ui.theme.UiPrimary
import com.thedavelopers.eventqr.ui.theme.UiSurface
import com.thedavelopers.eventqr.ui.theme.UiSurfaceSunken
import com.thedavelopers.eventqr.ui.theme.UiTextDisabled
import com.thedavelopers.eventqr.ui.theme.UiTextPrimary

/*
 * Filter chips (§5.6).
 *
 *   36dp tall, radius 4 (NOT a pill), 1dp ui_outline_interactive at rest (3.12:1 - a control
 *   boundary must be perceivable), 12dp side padding
 *   label 13/500 ui_text_primary
 *   selected: ui_primary fill, ui_on_primary label, NO check icon - the fill is the state
 *   disabled: sunken fill, ui_text_disabled
 *   row: horizontal scroll, 8dp gaps, 20dp start pad, 8dp bottom pad
 *
 * Public signature unchanged; the Material FilterChip (pill + check glyph + tinted container) is
 * replaced with a plain tonal box so the shape and the state are ours.
 */

@Composable
fun <T> FilterChipRow(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: (T) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    val grid = LocalGrid.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = grid.gutter, end = grid.gutter, top = grid.s8, bottom = grid.s8),
        horizontalArrangement = Arrangement.spacedBy(grid.chipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            FilterChipItem(
                label = labelProvider(item),
                selected = item == selectedItem,
                enabled = enabled,
                onClick = { onItemSelected(item) },
            )
        }
    }
}

@Composable
fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val grid = LocalGrid.current
    val type = LocalType.current
    val borderWidth = dimensionResource(R.dimen.stroke_hairline)
    val shape = RoundedCornerShape(grid.radiusButton)

    val fill: Color = when {
        !enabled -> UiSurfaceSunken
        selected -> UiPrimary
        else -> UiSurface
    }
    val labelColor: Color = when {
        !enabled -> UiTextDisabled
        selected -> UiOnPrimary
        else -> UiTextPrimary
    }
    val border: Color? = when {
        !enabled -> null
        selected -> null
        else -> UiOutlineInteractive
    }

    Box(
        modifier = modifier
            .heightIn(min = grid.touchMin)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(dimensionResource(R.dimen.ui_chip_height))
                .clip(shape)
                .background(fill)
                .then(if (border != null) Modifier.border(borderWidth, border, shape) else Modifier)
                .padding(horizontal = grid.chipPadH),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = type.label,
                color = labelColor,
                maxLines = 1,
            )
        }
    }
}
