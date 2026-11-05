package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.LocalGrid
import com.thedavelopers.eventqr.ui.theme.LocalType
import com.thedavelopers.eventqr.ui.theme.UiAccent
import com.thedavelopers.eventqr.ui.theme.UiScanBracket
import com.thedavelopers.eventqr.ui.theme.UiScanFrame
import com.thedavelopers.eventqr.ui.theme.UiScanPanel
import com.thedavelopers.eventqr.ui.theme.UiTextPrimary
import com.thedavelopers.eventqr.ui.theme.UiTextSecondary

/*
 * Scanner viewport (§5.13) - Compose mirror of include_scan_viewport.xml.
 *
 * The camera is the only large dark surface in the app and it is FLAT: no gradient, no glow, no
 * translucent cards, no purple. The 1px frame is a hairline; the four 24dp brackets (one vector,
 * mirrored for the other three) are white at 80% and turn ui_accent ONLY while a decode is
 * confirmed. The torch is a 44dp target with a 20dp icon in the panel corner.
 *
 * This composable draws the panel; the screen supplies the camera preview through [preview]. The
 * panel's contentDescription must be @string/ui_scan_panel_description (§9.5); brackets are
 * decorative and stay out of the accessibility tree.
 */

@Composable
fun ScanViewport(
    modifier: Modifier = Modifier,
    decodeConfirmed: Boolean = false,
    onTorchClick: (() -> Unit)? = null,
    preview: @Composable () -> Unit = {},
) {
    val grid = LocalGrid.current
    val bracketTint = if (decodeConfirmed) UiAccent else UiScanBracket
    val bracketPainter = painterResource(AppIcons.QrFrameCorner)

    Box(
        modifier = modifier
            .size(width = grid.scanPanelWidth, height = grid.scanPanelHeight)
            .clip(RoundedCornerShape(grid.radiusSheet))
            .background(UiScanPanel),
    ) {
        Box(
            modifier = Modifier
                .size(grid.scanPreview)
                .align(Alignment.Center)
                .border(
                    width = dimensionResource(R.dimen.ui_scan_frame_stroke),
                    color = UiScanFrame,
                    shape = RoundedCornerShape(grid.radiusButton),
                ),
        ) {
            preview()

            ScanBracket(bracketPainter, bracketTint, Alignment.TopStart)
            ScanBracket(bracketPainter, bracketTint, Alignment.TopEnd, mirrorX = true)
            ScanBracket(bracketPainter, bracketTint, Alignment.BottomStart, mirrorY = true)
            ScanBracket(bracketPainter, bracketTint, Alignment.BottomEnd, mirrorX = true, mirrorY = true)
        }

        if (onTorchClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(dimensionResource(R.dimen.ui_scan_torch_inset))
                    .size(grid.scanTorch)
                    .clip(RoundedCornerShape(grid.radiusButton))
                    .clickable(onClick = onTorchClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(AppIcons.Flashlight),
                    contentDescription = stringResource(R.string.ui_scan_torch),
                    tint = UiTextPrimary,
                    modifier = Modifier.size(dimensionResource(R.dimen.icon_md)),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ScanBracket(
    painter: Painter,
    tint: Color,
    alignment: Alignment,
    mirrorX: Boolean = false,
    mirrorY: Boolean = false,
) {
    val arm = LocalGrid.current.scanBracketArm
    Image(
        painter = painter,
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier
            .align(alignment)
            .size(arm)
            .scale(scaleX = if (mirrorX) -1f else 1f, scaleY = if (mirrorY) -1f else 1f),
    )
}

/**
 * The 48dp context bar above the panel (§5.13): event name in title on the left, scan purpose in
 * label on the right. This replaces the two separate white cards ("SCANNING FOR" / "SCAN PURPOSE").
 */
@Composable
fun ScanContextBar(
    eventName: String,
    purpose: String,
    modifier: Modifier = Modifier,
) {
    val grid = LocalGrid.current
    val type = LocalType.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.ui_scan_context_height))
            .padding(horizontal = grid.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = eventName,
            style = type.title,
            color = UiTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(grid.s16))
        Text(
            text = purpose,
            style = type.label,
            color = UiTextSecondary,
            maxLines = 1,
        )
    }
}
