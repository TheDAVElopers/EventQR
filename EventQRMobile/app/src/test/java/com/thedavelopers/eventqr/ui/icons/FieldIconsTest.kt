package com.thedavelopers.eventqr.ui.icons

import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The vectors carry a 24dp intrinsic box; auth fields must still render a 20dp glyph. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FieldIconsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val glyphPx = context.resources.getDimensionPixelSize(R.dimen.ui_field_icon)

    @Test
    fun apply_pinsBothFieldGlyphsTo20dp() {
        val field = EditText(context).apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(AppIcons.Lock, 0, AppIcons.Visibility, 0)
        }

        FieldIcons.apply(field)

        assertEquals(glyphPx, field.compoundDrawablesRelative[0].bounds.width())
        assertEquals(glyphPx, field.compoundDrawablesRelative[2].bounds.width())
    }

    @Test
    fun setTrailing_swapsTheEyeAndKeepsTheLeadingGlyph() {
        val field = EditText(context).apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(AppIcons.Lock, 0, AppIcons.Visibility, 0)
        }
        FieldIcons.apply(field)

        FieldIcons.setTrailing(field, AppIcons.VisibilityOff)

        assertEquals(glyphPx, field.compoundDrawablesRelative[0].bounds.width())
        assertEquals(glyphPx, field.compoundDrawablesRelative[2].bounds.width())
    }

    /** A site with a chevron passes `ui_chevron` (16dp); the default stays the 20dp field glyph. */
    @Test
    fun apply_pinsTheCallSiteSizeOnEverySlot() {
        val chevronPx = context.resources.getDimensionPixelSize(R.dimen.ui_chevron)
        val field = EditText(context).apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                AppIcons.Chevron,
                AppIcons.Chevron,
                AppIcons.Chevron,
                AppIcons.Chevron,
            )
        }

        FieldIcons.apply(field, R.dimen.ui_chevron)

        assertEquals(16 * context.resources.displayMetrics.density, chevronPx.toFloat(), 0.001f)
        field.compoundDrawablesRelative.forEach { assertEquals(chevronPx, it.bounds.width()) }

        FieldIcons.apply(field)

        field.compoundDrawablesRelative.forEach { assertEquals(glyphPx, it.bounds.width()) }
    }
}
