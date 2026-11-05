package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextOverflow
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.LocalGrid
import com.thedavelopers.eventqr.ui.theme.LocalType
import com.thedavelopers.eventqr.ui.theme.UiHairline
import com.thedavelopers.eventqr.ui.theme.UiTextPrimary
import com.thedavelopers.eventqr.ui.theme.UiTextSecondary

/*
 * Metric row (§5.9, form 2): the ONLY way to show a secondary number.
 *
 *   56dp, overline label left, numeric_m value right, 1dp hairline under
 *   no card, no fill, no icon, no coloured number
 *
 * The primary three metrics belong in the pass-head stub, never here. A 2x2 / 2x3 grid of equal
 * cards is banned.
 *
 * XML mirror: widget_metric_row.xml
 */

@Composable
fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val grid = LocalGrid.current
    val type = LocalType.current

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.ui_metric_row_height))
                .padding(start = grid.gutter, end = grid.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = type.overline,
                color = UiTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(grid.s16))
            Text(
                text = value,
                style = type.numericM,
                color = UiTextPrimary,
                maxLines = 1,
            )
        }
        if (showDivider) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = grid.gutter)
                    .height(dimensionResource(R.dimen.stroke_hairline))
                    .background(UiHairline),
            )
        }
    }
}
