package com.thedavelopers.eventqr.ui.components

import android.content.Context
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Compose parity for the compact pass head's title-row count: the same slot, the same baseline,
 * and the same "the title keeps the majority of the row" cap as include_pass_head_compact.xml.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PassHeadCompactComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun count_rendersOnTheTitleLine() {
        setHead(count = "28 events")

        composeTestRule.onNodeWithText("Discover").assertIsDisplayed()
        composeTestRule.onNodeWithText("28 events").assertIsDisplayed()
    }

    @Test
    fun noCount_leavesTheTitleRowAlone() {
        setHead()

        composeTestRule.onNodeWithText("Discover").assertIsDisplayed()
        composeTestRule.onNodeWithText("28 events").assertDoesNotExist()
    }

    @Test
    fun count_sharesTheTitleBaseline() {
        setHead(count = "28 events")

        val titleBaseline = baselinePx("Discover")
        val countBaseline = baselinePx("28 events")

        assertTrue("baseline missing - fonts did not measure", countBaseline > 0f)
        assertTrue(
            "title baseline $titleBaseline vs count baseline $countBaseline",
            abs(titleBaseline - countBaseline) <= 2f,
        )
    }

    @Test
    fun longCount_keepsTheTitleAtLeast55PercentOfTheRow() {
        setHead(count = "Manage admin and user accounts") // 30 characters

        val gutterPx = with(composeTestRule.density) { 20.dp.roundToPx() }
        val rowWidth = composeTestRule.onRoot().fetchSemanticsNode().size.width - 2 * gutterPx
        val titleWidth = composeTestRule.onNodeWithText("Discover").fetchSemanticsNode().size.width
        val cap = ApplicationProvider.getApplicationContext<Context>()
            .resources.getDimensionPixelSize(R.dimen.ui_pass_head_count_max)

        assertTrue(
            "count cap is ${cap}px of the ${rowWidth}px row",
            cap <= rowWidth * 0.45f,
        )
        assertTrue(
            "title got ${titleWidth}px of the ${rowWidth}px row",
            titleWidth >= rowWidth * 0.55f,
        )
    }

    /**
     * Parity with the XML: a label that fits stays at the overline size (11sp).
     *
     * The shrink is not asserted through rendering: under Robolectric's legacy graphics the
     * autosize overflow check is inert, so a long label keeps the 11sp maximum here no matter
     * what the range is. [count_autosizesBetweenTheFloorAndTheOverlineSize] asserts the range
     * itself instead.
     */
    @Test
    fun shortCount_keepsTheOverlineSize() {
        setHead(count = "28 EVENTS")

        assertEquals(11f, fontSizeSp("28 EVENTS"), 0.01f)
    }

    /**
     * The autosize range is wired: 11sp (`ui_type_overline`) down to the 9sp ramp floor
     * (`ui_pass_head_count_text_min`) in 0.5sp steps - the same range as `android:autoSize*` on
     * include_pass_head_compact.xml. Asserted structurally because the rendered size cannot show
     * the floor under Robolectric (see [shortCount_keepsTheOverlineSize]).
     */
    @Test
    fun count_autosizesBetweenTheFloorAndTheOverlineSize() {
        var autoSize: TextAutoSize? = null
        composeTestRule.setContent {
            EventQrTheme {
                autoSize = countAutoSize()
            }
        }

        assertEquals(
            TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
            autoSize,
        )
    }

    private fun setHead(count: String? = null) {
        composeTestRule.setContent {
            EventQrTheme {
                PassHeadCompact(
                    title = "Discover",
                    eyebrow = "Attendee",
                    count = count,
                )
            }
        }
    }

    private fun baselinePx(text: String): Float {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = composeTestRule.onNodeWithText(text)
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return node.fetchSemanticsNode().boundsInRoot.top + layouts.first().firstBaseline
    }

    /** The font size the count was actually laid out with, in sp. */
    private fun fontSizeSp(text: String): Float {
        val layouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithText(text)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.first().layoutInput.style.fontSize.value
    }
}
