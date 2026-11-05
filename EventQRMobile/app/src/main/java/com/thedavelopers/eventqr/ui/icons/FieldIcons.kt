package com.thedavelopers.eventqr.ui.icons

import android.graphics.drawable.Drawable
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.thedavelopers.eventqr.R

/**
 * Field glyphs render at `@dimen/ui_field_icon` (20dp, `EventQR-Redesign-Plan.md` §5.2), but every
 * `ic_ui_*` vector carries a 24dp intrinsic size and a compound drawable is laid out at its
 * intrinsic size. Re-bound the four slots explicitly, in place, so the icon family keeps one
 * address per glyph.
 */
object FieldIcons {

    /** Pins a field's current compound drawables to the field-icon size. Call once per screen. */
    fun apply(view: TextView) {
        val drawables = view.compoundDrawablesRelative
        place(view, drawables[0], drawables[1], drawables[2], drawables[3])
    }

    /** Swaps the trailing glyph (the eye) and keeps the leading glyph and both bounds. */
    fun setTrailing(view: TextView, @DrawableRes glyph: Int) {
        val drawables = view.compoundDrawablesRelative
        place(view, drawables[0], drawables[1], ContextCompat.getDrawable(view.context, glyph), drawables[3])
    }

    private fun place(view: TextView, start: Drawable?, top: Drawable?, end: Drawable?, bottom: Drawable?) {
        val px = view.resources.getDimensionPixelSize(R.dimen.ui_field_icon)
        listOf(start, top, end, bottom).forEach { it?.setBounds(0, 0, px, px) }
        view.setCompoundDrawablesRelative(start, top, end, bottom)
    }
}
