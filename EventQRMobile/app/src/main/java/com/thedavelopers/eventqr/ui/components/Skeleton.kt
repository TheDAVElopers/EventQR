package com.thedavelopers.eventqr.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.LocalGrid
import com.thedavelopers.eventqr.ui.theme.UiSurfaceSunken
import androidx.compose.ui.unit.Dp

/*
 * Skeletons (§5.11). ONE parameterized component: block, row, card.
 *
 *   base ui_surface_sunken, radius 10
 *   shimmer alpha 0.55 -> 1.0, 900ms, reverse repeat (values read from dimens/integers, never
 *   hard-coded here)
 *   reduced motion: if Settings.Global.ANIMATOR_DURATION_SCALE == 0 the block renders static
 *
 * Rule: a skeleton must match its final geometry within 4dp, so there is no layout jump. That is
 * the whole point - if you cannot match it, do not ship the skeleton.
 *
 * XML mirrors: include_skeleton_block / include_skeleton_row / include_skeleton_card.
 */

/** True when the system animator scale is 0 - the one check every animation in the app must make. */
fun animationsEnabled(context: android.content.Context): Boolean {
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return scale != 0f
}

@Composable
private fun shimmerAlpha(): Float {
    val context = LocalContext.current
    val enabled = remember(context) { animationsEnabled(context) }
    if (!enabled) return 1f

    val min = remember(context) {
        androidx.core.content.res.ResourcesCompat.getFloat(context.resources, R.dimen.ui_skeleton_alpha_min)
    }
    val durationMs = remember(context) { context.resources.getInteger(R.integer.motion_skeleton_ms) }
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = min,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    return alpha
}

/** The base block. Width/height/radius come from the call site to match the real content. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp? = null,
) {
    val grid = LocalGrid.current
    val alpha = shimmerAlpha()
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height ?: grid.skeletonBarHeight)
            .alpha(alpha)
            .clip(RoundedCornerShape(grid.radiusCard))
            .background(UiSurfaceSunken),
    )
}

/** Row geometry identical to list_row_icon: 40dp circle + title bar + meta bar. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val grid = LocalGrid.current
    val alpha = shimmerAlpha()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.ui_skeleton_row_height))
            .padding(start = grid.rowStart, end = grid.rowEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dimensionResource(R.dimen.ui_skeleton_avatar))
                .alpha(alpha)
                .clip(RoundedCornerShape(grid.radiusBadge))
                .background(UiSurfaceSunken),
        )
        Spacer(modifier = Modifier.width(grid.s16))
        Column {
            Box(
                modifier = Modifier
                    .width(dimensionResource(R.dimen.ui_skeleton_title_width))
                    .height(grid.skeletonBarHeight)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(grid.radiusCard))
                    .background(UiSurfaceSunken),
            )
            Spacer(modifier = Modifier.height(grid.s8))
            Box(
                modifier = Modifier
                    .width(dimensionResource(R.dimen.ui_skeleton_meta_width))
                    .height(grid.skeletonBarHeight)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(grid.radiusCard))
                    .background(UiSurfaceSunken),
            )
        }
    }
}

/** One hairline card holding three skeleton rows - matches a loaded Card.Grouped. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val grid = LocalGrid.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(grid.radiusCard)),
    ) {
        SkeletonRow()
        SkeletonRow()
        SkeletonRow()
    }
}

/**
 * Wraps a skeleton in the TalkBack contract from §9.5: the CONTAINER announces "Loading", the
 * bars are invisible to the reader.
 */
@Composable
fun SkeletonContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val label = stringResource(R.string.ui_skeleton_loading)
    Box(modifier = modifier.semantics { contentDescription = label }) {
        content()
    }
}
