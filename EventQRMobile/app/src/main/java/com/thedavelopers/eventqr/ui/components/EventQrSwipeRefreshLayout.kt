package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R

/*
 * The one pull-to-refresh used app-wide: same purple spinner on a white disc on every page.
 * Restyle it here and every page follows.
 *
 * Placement rule: it wraps only the content below the header, never the header itself, so the spinner
 * always drops in under the header and the header stays put. Pages with a hero header (dashboards,
 * profile) keep that header above this layout, not inside it.
 */
class EventQrSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SwipeRefreshLayout(context, attrs) {

    init {
        setColorSchemeColors(ContextCompat.getColor(context, R.color.eventqr_purple))
        setProgressBackgroundColorSchemeColor(ContextCompat.getColor(context, R.color.surface))
    }
}
