package com.thedavelopers.eventqr.ui.icons

import android.graphics.drawable.Drawable
import android.widget.TextView
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.thedavelopers.eventqr.R

/**
 * Compound glyphs render at `@dimen/ui_field_icon` (20dp, `EventQR-Redesign-Plan.md` §5.2/§5.6), but
 * every `ic_ui_*` vector carries a 24dp intrinsic size and a compound drawable is laid out at its
 * intrinsic size. Re-bound the four slots explicitly, in place, so the icon family keeps one
 * address per glyph.
 */
object FieldIcons {

    /** Pins a view's current compound drawables to [size]. Call once per screen. */
    fun apply(view: TextView, @DimenRes size: Int = R.dimen.ui_field_icon) {
        val drawables = view.compoundDrawablesRelative
        place(view, drawables[0], drawables[1], drawables[2], drawables[3], size)
    }

    /** Swaps the trailing glyph (the eye, or the 16dp caret) and keeps the leading glyph and both bounds. */
    fun setTrailing(view: TextView, @DrawableRes glyph: Int, @DimenRes size: Int = R.dimen.ui_field_icon) {
        val drawables = view.compoundDrawablesRelative
        place(
            view,
            drawables[0],
            drawables[1],
            ContextCompat.getDrawable(view.context, glyph),
            drawables[3],
            size,
        )
    }

    private fun place(
        view: TextView,
        start: Drawable?,
        top: Drawable?,
        end: Drawable?,
        bottom: Drawable?,
        @DimenRes size: Int,
    ) {
        val px = view.resources.getDimensionPixelSize(size)
        listOf(start, top, end, bottom).forEach { it?.setBounds(0, 0, px, px) }
        view.setCompoundDrawablesRelative(start, top, end, bottom)
    }
}
