package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding

/**
 * The shared §5.8 top bar (`include_top_bar.xml`) - back arrow, title, one trailing action.
 *
 * It is a view rather than a plain `<LinearLayout>` only so it can pad itself for the status bar, the
 * way every other header in the app does ([EventQrDetailHeader], the pass heads). The window is edge
 * to edge, so without the inset the 56dp row starts at y = 0 and the back arrow and title render
 * inside the status-bar band - on a 34dp status bar the title overlaps the clock.
 *
 * Doing it here rather than in each activity also covers the screens that inflate content late, from
 * a coroutine, after `onCreate` has returned.
 */
class EventQrTopBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        applyEventQrTopInsetPadding()
    }
}
