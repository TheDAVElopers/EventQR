package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.LocalGrid
import com.thedavelopers.eventqr.ui.theme.LocalType
import com.thedavelopers.eventqr.ui.theme.UiBackground
import com.thedavelopers.eventqr.ui.theme.UiOnDarkHairline
import com.thedavelopers.eventqr.ui.theme.UiTextOnDark
import com.thedavelopers.eventqr.ui.theme.UiTextOnDarkFaint
import com.thedavelopers.eventqr.ui.theme.UiTextOnDarkMuted

/*
 * QR credential surface (§5.14) - the only beautiful object in the app, and the only gradient.
 *
 *   bg_credential_surface: 315deg, #2D2A7C -> #5B25C9, 16dp radius, 1dp 12% white inner hairline
 *   320dp wide, 440dp MIN (grows for a 2-line event name - never clip the title)
 *   20dp padding; the perforation is full bleed with two 24dp notches punched out of the edges
 *   216dp white QR plate, 4dp radius, 184dp module, 16dp quiet zone
 *   serial numeric_s white left, VALID badge (accent / on_accent) right
 *
 * Same anatomy as include_qr_credential.xml, which the two QR activities inflate, and the same
 * treatment the PDF/print exporter must mirror. The gradient colours are the deco_* layer-3
 * stops; no other Compose file may reference them.
 *
 * TalkBack: set [qrContentDescription] from ui_credential_description; the plate is decorative so
 * the pass is read once (§9.5).
 */

/** The holder line: trimmed, inner whitespace collapsed, null when blank (§5.14). */
internal fun String?.asCredentialHolderName(): String? =
    this?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotBlank() }

@Composable
fun QrCredentialCard(
    eventName: String,
    meta: String,
    serial: String,
    roleLabel: String,
    attendeeName: String? = null,
    modifier: Modifier = Modifier,
    valid: Boolean = true,
    qrContentDescription: String? = null,
    qr: @Composable () -> Unit = {},
) {
    val grid = LocalGrid.current
    val type = LocalType.current
    val shape = RoundedCornerShape(grid.radiusSheet)
    val gradient = Brush.linearGradient(
        colors = listOf(
            colorResource(R.color.deco_credential_gradient_start),
            colorResource(R.color.deco_credential_gradient_end),
        ),
    )
    val description = qrContentDescription

    Column(
        modifier = modifier
            .width(grid.credentialWidth)
            .heightIn(min = grid.credentialMinHeight)
            .clip(shape)
            .background(gradient)
            .then(
                if (description != null) {
                    Modifier.semantics { contentDescription = description }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(modifier = Modifier.padding(start = grid.s20, end = grid.s20, top = grid.s20)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ui_credential_brand),
                    style = type.overline,
                    color = UiTextOnDarkFaint,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = roleLabel,
                    style = type.overline,
                    color = UiTextOnDarkFaint,
                    maxLines = 1,
                )
            }

            Text(
                text = eventName,
                style = type.displayM,
                color = UiTextOnDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = grid.s20),
            )

            val holder = attendeeName.asCredentialHolderName()
            if (holder != null) {
                Text(
                    text = holder,
                    style = type.title,
                    color = UiTextOnDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = grid.s8),
                )
            }

            Text(
                text = meta,
                style = type.bodyS,
                color = UiTextOnDarkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = grid.s4),
            )
        }

        // Perforation: full bleed, notches punched out of both edges.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = grid.s20, bottom = grid.s20),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = -grid.s12)
                    .size(grid.notch)
                    .clip(RoundedCornerShape(grid.radiusBadge))
                    .background(UiBackground),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(dimensionResource(R.dimen.stroke_hairline))
                    .background(UiOnDarkHairline),
            )
            Box(
                modifier = Modifier
                    .offset(x = grid.s12)
                    .size(grid.notch)
                    .clip(RoundedCornerShape(grid.radiusBadge))
                    .background(UiBackground),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = grid.s20, end = grid.s20, bottom = grid.s20),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(grid.qrPlate)
                    .clip(RoundedCornerShape(grid.radiusButton))
                    .background(UiTextOnDark),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(grid.qrModule)) {
                    qr()
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = grid.s20),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = serial,
                    style = type.numericS,
                    color = UiTextOnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (valid) {
                    UiStatusBadge(
                        text = stringResource(R.string.ui_credential_valid),
                        tone = BadgeTone.VALID,
                    )
                }
            }
        }
    }
}
