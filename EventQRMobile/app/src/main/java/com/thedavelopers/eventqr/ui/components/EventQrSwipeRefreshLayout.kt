package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R

/*
 * The one pull-to-refresh (§5.11).
 *
 * Resting spinner: ui_primary on the ui_surface disc, 2dp stroke. No shadow, no raised disc, no
 * tinted variant per screen. Placement rule unchanged: it wraps only the content BELOW the
 * header, never the header itself, so the spinner drops in under the pass head and the head stays
 * put.
 *
 * The skeleton that shows while it spins must match the loaded geometry within 4dp (§5.11).
 */
class EventQrSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SwipeRefreshLayout(context, attrs) {

    init {
        setColorSchemeColors(ContextCompat.getColor(context, R.color.ui_primary))
        setProgressBackgroundColorSchemeColor(ContextCompat.getColor(context, R.color.ui_surface))
        setProgressViewOffset(
            false,
            resources.getDimensionPixelOffset(R.dimen.space_sm),
            resources.getDimensionPixelOffset(R.dimen.space_xxxl),
        )
    }
}
