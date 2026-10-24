package com.thedavelopers.eventqr.features.organizer

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

class StyledHelpersHost : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_EventQR)
        super.onCreate(savedInstanceState)
    }
}

/** styledText / cardRes / spacerRes render the documented dp values at a fractional density (420dpi = 2.625). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-420dpi")
class StyledHelpersTest {

    private val host get() = Robolectric.buildActivity(StyledHelpersHost::class.java).create().get()
    private val density = 2.625f

    @Test
    fun styledText_appliesSizeBoldColorAndGravity() {
        val a = host
        val tv = a.styledText("Hello", R.dimen.type_body_medium, true, 0xFF123456.toInt(), Gravity.CENTER)
        assertEquals("Hello", tv.text.toString())
        // 14sp at fontScale 1 and density 2.625
        assertEquals(14f * density, tv.textSize, 0.001f)
        assertEquals(0xFF123456.toInt(), tv.currentTextColor)
        assertEquals(Typeface.BOLD, tv.typeface.style)
        assertEquals(Gravity.CENTER, tv.gravity)
        assertEquals(true, tv.includeFontPadding)
    }

    @Test
    fun styledText_defaults() {
        val a = host
        val tv = a.styledText("x")
        assertEquals(14f * density, tv.textSize, 0.001f)
        assertEquals(a.orgColors.text, tv.currentTextColor)
        assertEquals(Typeface.NORMAL, tv.typeface?.style ?: Typeface.NORMAL)
    }

    @Test
    fun cardRes_paddingMarginsElevationAndBackground() {
        val a = host
        val card = a.cardRes(R.dimen.space_lg)
        // 16dp * 2.625 = 42
        assertEquals(42, card.paddingLeft)
        assertEquals(42, card.paddingTop)
        assertEquals(42, card.paddingRight)
        assertEquals(42, card.paddingBottom)
        // elevation_raised = 2dp -> 5.25 -> dimenPx rounds to 5
        assertEquals(5f, card.elevation, 0f)
        val lp = card.layoutParams as LinearLayout.LayoutParams
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, lp.width)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, lp.height)
        // space_sm = 8dp -> 21, space_10 = 10dp -> 26 (26.25)
        assertEquals(21, lp.topMargin)
        assertEquals(26, lp.bottomMargin)
        assertEquals(0, lp.leftMargin)
        assertEquals(LinearLayout.VERTICAL, card.orientation)
        val bg = card.background as android.graphics.drawable.GradientDrawable
        // radius_14 = 14dp
        assertEquals(14f * density, bg.cornerRadius, 0.001f)
        assertEquals(a.orgColors.card, bg.color?.defaultColor)
    }

    @Test
    fun cardRes_fractionalPadding_rounds() {
        // 12dp * 2.625 = 31.5 -> 32
        assertEquals(32, host.cardRes(R.dimen.space_md).paddingLeft)
    }

    @Test
    fun spacerRes_usesTheTokenHeight() {
        val sp = host.spacerRes(R.dimen.space_7)
        assertEquals(1, sp.layoutParams.width)
        // 7dp * 2.625 = 18.375 -> 18
        assertEquals(18, sp.layoutParams.height)
    }
}
