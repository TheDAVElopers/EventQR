package com.thedavelopers.eventqr.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.thedavelopers.eventqr.R

/**
 * The four directional icons only (`back`, `forward`, `chevron`, `expand`).
 *
 * `painterResource` ignores `android:autoMirrored`, so RTL mirroring has to be explicit: a
 * `scaleX` flip under [LayoutDirection.Rtl] restores what the same vector does in a View.
 */
@Composable
fun MirroredIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = dimensionResource(R.dimen.icon_lg),
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size).then(if (rtl) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
    )
}
