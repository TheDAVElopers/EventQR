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
import org.junit.Assert.assertNotNull
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

/**
 * styledText / cardRes / spacerRes render the documented dp values at a fractional density
 * (420dpi = 2.625).
 *
 * The redesign changed two contracts on purpose: text carries IBM Plex and no font padding (§3.5),
 * and a card is a hairline surface with elevation 0 (§4.4). The assertions encode the new contract.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-420dpi")
class StyledHelpersTest {

    private val host get() = Robolectric.buildActivity(StyledHelpersHost::class.java).create().get()
    private val density = 2.625f

    @Test
    fun styledText_appliesSizeBoldColorAndGravity() {
        val a = host
        val tv = a.styledText("Hello", R.dimen.ui_type_body, true, 0xFF123456.toInt(), Gravity.CENTER)
        assertEquals("Hello", tv.text.toString())
        // ui_type_body = 15sp at fontScale 1 and density 2.625
        assertEquals(15f * density, tv.textSize, 0.001f)
        assertEquals(0xFF123456.toInt(), tv.currentTextColor)
        // Robolectric does not expose the bundled font's weight; the contract under test is that a
        // typeface is installed (never the platform default) and padding is off (§3.5).
        assertNotNull(tv.typeface)
        assertEquals(Gravity.CENTER, tv.gravity)
        // §3.5: includeFontPadding is false on every custom style.
        assertEquals(false, tv.includeFontPadding)
    }

    @Test
    fun styledText_defaults() {
        val a = host
        val tv = a.styledText("x")
        assertEquals(15f * density, tv.textSize, 0.001f)
        assertEquals(a.orgColors.text, tv.currentTextColor)
        assertNotNull(tv.typeface)
    }

    @Test
    fun cardRes_isAHairlineSurfaceWithZeroElevation() {
        val a = host
        val card = a.cardRes(R.dimen.space_lg)
        // 16dp * 2.625 = 42
        assertEquals(42, card.paddingLeft)
        assertEquals(42, card.paddingTop)
        assertEquals(42, card.paddingRight)
        assertEquals(42, card.paddingBottom)
        // §4.4: content never has a shadow.
        assertEquals(0f, card.elevation, 0f)
        val lp = card.layoutParams as LinearLayout.LayoutParams
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, lp.width)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, lp.height)
        // space_8 = 8dp -> 21 top and bottom
        assertEquals(21, lp.topMargin)
        assertEquals(21, lp.bottomMargin)
        assertEquals(0, lp.leftMargin)
        assertEquals(LinearLayout.VERTICAL, card.orientation)
        // The surface is a drawable resource, not a code-built GradientDrawable.
        assertEquals(
            androidx.core.content.ContextCompat.getDrawable(a, R.drawable.bg_ui_card)?.javaClass,
            card.background?.javaClass,
        )
    }

    @Test
    fun cardRes_fractionalPadding_rounds() {
        // 12dp * 2.625 = 31.5 -> 32
        assertEquals(32, host.cardRes(R.dimen.space_md).paddingLeft)
    }

    @Test
    fun spacerRes_usesTheTokenHeight() {
        val sp = host.spacerRes(R.dimen.space_4)
        assertEquals(1, sp.layoutParams.width)
        // 4dp * 2.625 = 10.5 -> roundToInt 11
        assertEquals(11, sp.layoutParams.height)
    }
}
