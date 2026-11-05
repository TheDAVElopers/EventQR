package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The compact pass head's count must step down instead of losing its end: a 112dp cap (the title
 * keeps the majority of the title row) and uniform autosize from the overline size (11sp) to the
 * ramp floor (9sp) inside that cap.
 *
 * The shrink itself is not measured here. Robolectric's legacy text measurement reports one pixel
 * per character, so no label can ever overflow the cap without native graphics - and native
 * graphics costs a runtime download on a cold CI cache. What is asserted instead is the
 * configuration that produces the shrink. The label length that makes the shrink unnecessary is
 * guaranteed at the data layer by [CountLabel.format], covered by [CountLabelTest] on the JVM.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class PassHeadCountAutosizeTest {

    @Test
    fun count_autosizesFromTheOverlineSizeDownToTheFloorInsideTheCap() {
        val count = inflate().count

        assertEquals("uniform autosize", TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, count.autoSizeTextType)
        assertEquals("max 11sp", spToPx(11f).toDouble(), count.autoSizeMaxTextSize.toDouble(), 0.5)
        assertEquals("min 9sp", spToPx(9f).toDouble(), count.autoSizeMinTextSize.toDouble(), 0.5)
        assertEquals("cap 112dp", dpToPx(112f).toDouble(), count.maxWidth.toDouble(), 0.5)
        assertEquals("never more than one line", 1, count.maxLines)
    }

    /** The cap is a cap, not a width: the count is wrap_content at rest and the title keeps 55%. */
    @Test
    fun count_staysInsideTheCapAndLeavesTheTitle55PercentOfTheRow() {
        val (root, cap) = inflate().let { it.root to it.cap }
        val context = themedContext()
        val density = context.resources.displayMetrics.density
        val gutter = context.resources.getDimensionPixelSize(R.dimen.ui_gutter)
        val rowWidth = 320f * density - 2 * gutter // narrowest supported width

        val title = root.findViewById<TextView>(R.id.passHeadTitle)
        val count = root.findViewById<TextView>(R.id.passHeadTitleCount)

        assertTrue("the cap holds the title's majority: $cap of $rowWidth", cap <= rowWidth * 0.45f)
        assertTrue("count measured ${count.measuredWidth}px", count.measuredWidth <= cap)
        assertTrue("title measured ${title.measuredWidth}px", title.measuredWidth >= rowWidth * 0.55f)
    }

    private class Head(val root: View, val count: TextView, val cap: Int)

    private fun inflate(): Head {
        val context = themedContext()
        val density = context.resources.displayMetrics.density
        val root = LayoutInflater.from(context)
            .inflate(R.layout.include_pass_head_compact, LinearLayout(context), false)

        root.findViewById<TextView>(R.id.passHeadTitle).text = "Discover"
        val count = root.findViewById<TextView>(R.id.passHeadTitleCount).apply {
            text = "28 EVENTS"
            visibility = View.VISIBLE
        }

        root.measure(
            View.MeasureSpec.makeMeasureSpec((320f * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        return Head(root, count, count.maxWidth)
    }

    private fun themedContext(): Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_EventQR)

    private fun spToPx(sp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        sp,
        ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics,
    )

    private fun dpToPx(dp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics,
    )
}
