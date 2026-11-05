package com.thedavelopers.eventqr.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import com.thedavelopers.eventqr.ui.theme.LocalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The 9sp floor, proved on a real device.
 *
 * `PassHeadCompactComposeTest` (Robolectric) can only assert that the autosize range is wired:
 * its legacy graphics report one pixel per character, so nothing overflows the 112dp cap and the
 * shrink never happens. This test runs the same composable on hardware graphics inside a 360dp
 * host and proves the *rendered* size instead: each label is compared against the same label laid
 * out at an explicit 11sp and an explicit 9sp, unbounded. A resize is the only thing that can make
 * the auto-sized width match the 9sp reference, and at this 0.5sp granularity only the 9sp step
 * produces that width.
 *
 * [LONG] is 17 characters, one more than `CountLabel.MAX_LENGTH`: the data-layer cap is what keeps
 * shipped counts short, so the autosize floor is the safety net for a longer localised label -
 * exactly the case this test covers.
 */
@RunWith(AndroidJUnit4::class)
class PassHeadCompactAutosizeDeviceTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shortCount_keepsTheOverlineSize() {
        val rendered = render(SHORT)

        assertEquals("short count must keep the 11sp width", rendered.at11, rendered.auto, 1f)
        assertTrue(
            "a 9sp rendering would have been ${rendered.at9}px, got ${rendered.auto}px",
            rendered.auto - rendered.at9 > 20f,
        )
    }

    @Test
    fun longCount_stepsDownToTheRampFloor() {
        val rendered = render(LONG)

        assertTrue(
            "the label must overflow the ${capPx()}px cap at 11sp, was ${rendered.at11}px",
            rendered.at11 > capPx(),
        )
        assertEquals("long count must be rendered at the 9sp width", rendered.at9, rendered.auto, 1f)
        assertTrue(
            "count measured ${rendered.auto}px of the ${capPx()}px cap",
            rendered.auto <= capPx(),
        )
        assertFalse("long count must fit, not ellipsize", rendered.ellipsized)
    }

    /**
     * Lays out the head plus the two references and reads their widths. All three share the same
     * text, so the node list is ordered by composition: head, 11sp reference, 9sp reference.
     */
    private fun render(count: String): Rendered {
        composeTestRule.setContent {
            EventQrTheme {
                val overline = LocalType.current.overline
                Column {
                    PassHeadCompact(
                        title = "Discover",
                        count = count,
                        modifier = Modifier.width(360.dp),
                    )
                    Text(text = count, style = overline.copy(fontSize = 11.sp), maxLines = 1)
                    Text(text = count, style = overline.copy(fontSize = 9.sp), maxLines = 1)
                }
            }
        }

        val nodes = composeTestRule.onAllNodesWithText(count)
        return Rendered(
            auto = nodes.widthAt(0),
            at11 = nodes.widthAt(1),
            at9 = nodes.widthAt(2),
            ellipsized = nodes.layoutAt(0).multiParagraph.isLineEllipsized(0),
        )
    }

    private fun SemanticsNodeInteractionCollection.widthAt(index: Int): Float =
        get(index).fetchSemanticsNode().size.width.toFloat()

    private fun SemanticsNodeInteractionCollection.layoutAt(index: Int): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        get(index).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.last()
    }

    private fun capPx(): Int = with(composeTestRule.density) { 112.dp.roundToPx() }

    private data class Rendered(val auto: Float, val at11: Float, val at9: Float, val ellipsized: Boolean)

    private companion object {
        const val SHORT = "28 EVENTS"
        const val LONG = "28 EVENTS TONIGHT"
    }
}
