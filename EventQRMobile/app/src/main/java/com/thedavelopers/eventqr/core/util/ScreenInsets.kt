package com.thedavelopers.eventqr.core.util

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * System-bar insets for the screens that own their own shell.
 *
 * The redesign draws edge to edge (see `EventQrApplication`): a bar's own fill has to run behind the
 * system bar while its *content* stays inside the safe area. Headers apply their own inset - the pass
 * heads, `EventQrDetailHeader` and `EventQrTopBar` all call `applyEventQrTopInsetPadding()` - so what is
 * left here is the navigation-bar side plus the auth shell, which is padded by hand.
 */
object ScreenInsets {

    /** Adds the status-bar inset as top padding, keeping the view's horizontal padding. */
    fun padForStatusBar(view: View) {
        val start = view.paddingLeft
        val end = view.paddingRight
        val bottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            target.setPadding(
                start,
                insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
                end,
                bottom
            )
            insets
        }
        // A caller that installs the listener after the first insets pass (a screen that sets its
        // content from a coroutine) would otherwise never receive them.
        ViewCompat.requestApplyInsets(view)
    }

    /** Adds the navigation-bar inset as bottom padding, keeping the view's other padding. */
    fun padForNavigationBar(view: View) {
        val start = view.paddingLeft
        val end = view.paddingRight
        val top = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            target.setPadding(
                start,
                top,
                end,
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /**
     * Keeps a screen's scrolling content off the gesture bar. The content root is padded instead of the
     * list so the bar's own fill still reaches the bottom edge; screens that host the Compose bottom nav
     * must not call this (the nav already applies the same inset).
     */
    fun padContentForNavigationBar(activity: Activity) {
        activity.findViewById<View>(android.R.id.content)?.let(::padForNavigationBar)
    }
}
