package com.thedavelopers.eventqr.core.util

import android.app.Activity
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.thedavelopers.eventqr.R

/**
 * Small-size behaviour for the wordmark bar (`MyFiles/design/04-logo-direction.md` §3.3).
 *
 * The wordmark never shortens and the mark never drops. What gives way, in order, is the ACCESS PASS
 * eyebrow and then the wordmark cap (24/21sp -> the compact 19sp), so a 320dp screen, or a user at
 * fontScale 1.3 or above, cannot squeeze the lockup until it clips. Above both thresholds nothing is
 * touched: the sizes declared in the layout win.
 */
object Wordmark {

    /** A bar narrower than this (the screen minus its 20dp gutters) drops to the compact lockup. */
    private const val NARROW_SCREEN_DP = 340

    private const val COMPACT_FONT_SCALE = 1.3f

    /** §3.3 drop order: eyebrow first, then the wordmark cap. The mark never drops. */
    fun isCompact(screenWidthDp: Int, fontScale: Float): Boolean =
        screenWidthDp < NARROW_SCREEN_DP || fontScale >= COMPACT_FONT_SCALE

    fun applyBarRules(activity: Activity) {
        val wordmark = activity.findViewById<TextView>(R.id.txtWordmark) ?: return
        val config = activity.resources.configuration
        if (!isCompact(config.screenWidthDp, config.fontScale)) return

        wordmark.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            activity.resources.getDimension(R.dimen.ui_wordmark_size_compact)
        )
        wordmark.letterSpacing = ResourcesCompat.getFloat(activity.resources, R.dimen.ui_track_wordmark_compact)
        activity.findViewById<View>(R.id.txtBarEyebrow)?.visibility = View.GONE
    }
}
