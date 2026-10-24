package com.thedavelopers.eventqr.ui.theme

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

/** Rounding contract of the dimension helpers, at a density where dp values are fractional (420dpi = 2.625). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-420dpi")
class TokensTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val density get() = context.resources.displayMetrics.density

    @Test
    fun density_isFractionalForTheseTests() {
        assertEquals(2.625f, density, 0.0001f)
    }

    @Test
    fun dimenPx_roundsLikeRoundToInt() {
        // 3dp = 7.875px, 7dp = 18.375px, 5dp = 13.125px, 9dp = 23.625px
        for ((id, dp) in listOf(R.dimen.space_3 to 3, R.dimen.space_7 to 7, R.dimen.space_5 to 5, R.dimen.space_9 to 9, R.dimen.space_lg to 16)) {
            assertEquals("dimenPx($dp dp)", (dp * density).roundToInt(), context.dimenPx(id))
        }
        assertEquals(8, context.dimenPx(R.dimen.space_3))
    }

    @Test
    fun dimenOffset_truncatesLikeToInt() {
        for ((id, dp) in listOf(R.dimen.space_3 to 3, R.dimen.space_7 to 7, R.dimen.space_5 to 5, R.dimen.space_9 to 9, R.dimen.space_lg to 16)) {
            assertEquals("dimenOffset($dp dp)", (dp * density).toInt(), context.dimenOffset(id))
        }
        assertEquals(7, context.dimenOffset(R.dimen.space_3))
        assertNotEquals(context.dimenPx(R.dimen.space_3), context.dimenOffset(R.dimen.space_3))
    }

    @Test
    fun dimen_isUnroundedFloat() {
        assertEquals(3 * density, context.dimen(R.dimen.space_3), 0.0001f)
        assertEquals(14 * density, context.dimen(R.dimen.radius_14), 0.0001f)
    }

    @Test
    fun dimenFloatAndInteger_readUnitlessTokens() {
        assertEquals(0.5f, context.dimenFloat(R.dimen.alpha_disabled), 0f)
        assertEquals(0.0892857f, context.dimenFloat(R.dimen.tracking_button), 0f)
        assertEquals(-0.5f, context.dimenFloat(R.dimen.tracking_compose_tight), 0f)
        assertEquals(280, context.integer(R.integer.motion_pulse_ms))
        assertEquals(400, context.integer(R.integer.motion_restore_ms))
        assertEquals(80, context.integer(R.integer.motion_settle_ms))
    }

    @Test
    fun viewForwards_matchContextHelpers() {
        val v = TextView(context)
        assertEquals(context.dimen(R.dimen.space_7), v.dimen(R.dimen.space_7), 0f)
        assertEquals(context.dimenPx(R.dimen.space_7), v.dimenPx(R.dimen.space_7))
        assertEquals(context.dimenOffset(R.dimen.space_7), v.dimenOffset(R.dimen.space_7))
        assertEquals(context.dimenFloat(R.dimen.alpha_busy), v.dimenFloat(R.dimen.alpha_busy), 0f)
        assertEquals(context.integer(R.integer.motion_settle_ms), v.integer(R.integer.motion_settle_ms))
    }

    @Test
    fun setTextSizeRes_matchesLooseSpTextSize() {
        val viaToken = TextView(context).apply { setTextSizeRes(R.dimen.type_body_medium) }
        val viaLiteral = TextView(context).apply { textSize = 14f }
        assertEquals(viaLiteral.textSize, viaToken.textSize, 0.001f)
        assertTrue(viaToken.textSize > 0f)
    }

    @Test
    fun strong_makesTypefaceBold() {
        val tv = TextView(context)
        assertEquals(false, tv.typeface?.isBold ?: false)
        tv.strong()
        assertEquals(Typeface.BOLD, tv.typeface.style)
    }

    @Test
    fun mediumWeight_isSansSerifMedium() {
        val tv = TextView(context)
        tv.mediumWeight()
        val expected = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        assertEquals(expected.style, tv.typeface.style)
        assertEquals(expected.weight, tv.typeface.weight)
        assertEquals(Typeface.NORMAL, tv.typeface.style)
    }

    @Test
    fun regular_appliesTheBaseTypefaceUnchangedAndClearsFakeBold() {
        // Same as the old setTypeface(base, Typeface.NORMAL): style 0 means "use base as is", only fake-bold is reset.
        val tv = TextView(context)
        val base = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        tv.paint.isFakeBoldText = true
        tv.regular(base)
        assertEquals(base, tv.typeface)
        assertEquals(false, tv.paint.isFakeBoldText)
        val current = tv.typeface
        tv.regular()
        assertEquals(current, tv.typeface)
    }

    @Test
    fun defaultBold_isThePlatformDefaultBold() {
        val tv = TextView(context)
        tv.defaultBold()
        assertEquals(Typeface.DEFAULT_BOLD, tv.typeface)
    }

    @Test
    fun boldSpan_isABoldStyleSpan() {
        val span = boldSpan()
        assertTrue(span is StyleSpan)
        assertEquals(Typeface.BOLD, span.style)
    }

    @Test
    fun rippleAndChipHeightTokens_haveTheOriginalValues() {
        assertEquals(0.12f, context.dimenFloat(R.dimen.alpha_ripple), 0f)
        assertEquals(32 * density, context.dimen(R.dimen.control_height_32), 0.001f)
    }

    @Test
    fun roundedRect_usesRadiusAndRoundedStroke() {
        val withStroke = context.roundedRect(0xFF112233.toInt(), R.dimen.radius_14, 0xFF445566.toInt()) as GradientDrawable
        assertEquals(14 * density, withStroke.cornerRadius, 0.001f)
        val noStroke = context.roundedRect(0xFF112233.toInt(), R.dimen.radius_md)
        assertEquals(12 * density, noStroke.cornerRadius, 0.001f)
        // stroke width is private on the drawable; the helper must not throw for either overload
        context.roundedRect(0xFF112233.toInt(), R.dimen.radius_md, 0xFF445566.toInt(), R.dimen.stroke_focus)
    }
}
