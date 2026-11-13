package com.thedavelopers.eventqr.features.organizer.reports

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpLoad
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import com.thedavelopers.eventqr.features.organizer.allTexts
import com.thedavelopers.eventqr.features.organizer.testOrganizerEvent
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFiltersDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportSummaryDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportType
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The bulk action is a trailing `Widget.EventQR.Ui.SectionAction` on the heading row (§6.4), not the
 * lilac `Button.Secondary.Inline` pill: text only, no fill, 48dp floor, one progress line in the flow.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class EventReportsGenerateAllTest {

    private var controller: ActivityController<EventReportsActivity>? = null
    private val fake = FakeReportsRepository(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        controller = null
        RuntimeEnvironment.setFontScale(1.0f)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun dp(value: Int): Int =
        (value * ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density).roundToInt()

    /**
     * Both repositories are installed before `create()`, so the screen's own load resolves from the
     * fakes and nothing under the assertions is rebuilt by a network call.
     */
    private fun build(fontScale: Float = 1.0f): EventReportsActivity {
        RuntimeEnvironment.setFontScale(fontScale)
        val controller = Robolectric.buildActivity(EventReportsActivity::class.java)
        this.controller = controller
        val activity = controller.get()
        activity.installRepositoryFactory { FakeEventsRepository(it) }
        activity.reportsRepositoryFactory = { fake }
        controller.create()
        idle()
        return activity
    }

    private fun EventReportsActivity.action(): TextView =
        requireNotNull(findViewById<TextView>(R.id.eventReportsGenerateAll)) { "the bulk action must exist" }

    /** Only the visible tree: the summary strip carries a Retry of its own while hidden. */
    private fun View.findVisibleText(label: String): TextView? {
        if (visibility != View.VISIBLE) return null
        if (this is TextView && text.toString() == label) return this
        if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findVisibleText(label)?.let { return it }
        return null
    }

    @Test
    fun action_isATrailingSectionActionOnTheHeadingRow() {
        val activity = build()
        val action = activity.action()

        assertEquals("Generate all", action.text.toString())

        val row = action.parent as LinearLayout
        assertEquals("the heading row is horizontal", LinearLayout.HORIZONTAL, row.orientation)
        val title = row.getChildAt(0) as TextView
        assertEquals("Generate reports", title.text.toString())
        assertEquals("the action is the row's trailing child", row.childCount - 1, row.indexOfChild(action))
        assertEquals("the title takes the leftover width", 1f, (title.layoutParams as LinearLayout.LayoutParams).weight, 0f)
        assertEquals(0, (title.layoutParams as LinearLayout.LayoutParams).width)
        assertEquals("the title wraps instead of ellipsizing", Int.MAX_VALUE, title.maxLines)
        assertNull(title.ellipsize)

        val params = action.layoutParams as LinearLayout.LayoutParams
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, params.width)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, params.height)
        assertEquals(dp(8), params.marginStart)
        assertTrue("a 48dp touch floor", action.minimumHeight >= dp(48))
        assertEquals("no glyph", 0, action.compoundDrawablesRelative.count { it != null })
    }

    @Test
    fun action_usesTheGhostBackground_notTheSecondaryFill() {
        val activity = build()
        val action = activity.action()

        // Ghost at rest is a transparent wash; the secondary pill is the ui_primary_container fill.
        val rest = action.background.current as android.graphics.drawable.GradientDrawable
        val restFill = rest.color?.defaultColor
        assertEquals(android.graphics.Color.TRANSPARENT, restFill)
        assertNotEquals(
            ContextCompat.getColor(activity, R.color.ui_primary_container),
            restFill,
        )
        assertEquals("ui_primary label", ContextCompat.getColor(activity, R.color.ui_primary), action.currentTextColor)
    }

    @Test
    fun start_disablesTheActionAndShowsTheProgressLine() {
        val activity = build()
        fake.gate = CompletableDeferred()
        val action = activity.action()

        action.performClick()
        idle()

        assertEquals("Generating…", action.text.toString())
        assertFalse("no second run while one is live", action.isEnabled)
        assertEquals(
            "the disabled label is the token, not an alpha",
            ContextCompat.getColor(activity, R.color.ui_text_disabled),
            action.currentTextColor,
        )
        assertTrue(
            "one progress line in the flow",
            activity.window.decorView.allTexts().contains("Generating report 1 of 8…"),
        )
    }

    @Test
    fun success_resetsTheActionAndOpensTheCombinedPreview() {
        val activity = build()
        val action = activity.action()

        action.performClick()
        idle()

        assertEquals("the whole catalogue is generated", 8, fake.generated.size)
        assertEquals(
            ReportPreviewActivity::class.java.name,
            shadowOf(activity).nextStartedActivity?.component?.className,
        )
        assertEquals("Generate all", action.text.toString())
        assertTrue(action.isEnabled)
    }

    @Test
    fun noResolvedEvent_rendersNoAction() {
        val activity = build()

        activity.onEventsLoaded(OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.BACKEND))
        idle()

        assertNull(
            "the bulk action needs an event to act on",
            activity.findViewById<TextView>(R.id.eventReportsGenerateAll),
        )
    }

    @Test
    fun failure_showsTheStripAndRetryRunsTheWholeSetAgain() {
        val activity = build()
        fake.result = NetworkResult.Error("Couldn't generate the roster.")
        val action = activity.action()

        action.performClick()
        idle()

        val texts = activity.window.decorView.allTexts()
        assertTrue("the strip carries the sentence", texts.contains("Couldn't generate the roster."))
        assertFalse("no progress line left behind", texts.any { it.startsWith("Generating report") })
        assertTrue("the action is usable again", action.isEnabled)
        assertEquals("Generate all", action.text.toString())
        assertEquals("the first failure ends the run", 1, fake.generated.size)

        val retry = requireNotNull(activity.window.decorView.findVisibleText(activity.getString(R.string.common_retry)))
        retry.performClick()
        idle()

        assertEquals("Retry runs the whole set again", 2, fake.generated.size)
    }

    @Test
    fun switchingEvent_cancelsTheRunAndOpensNoPreview() {
        val activity = build()
        fake.gate = CompletableDeferred()
        activity.action().performClick()
        idle()

        // The selector resolves the new list and re-renders: the old run must not survive it.
        activity.onEventsLoaded(
            OrganizerMvpLoad(listOf(testOrganizerEvent("Day Fair")), OrganizerMvpDataSource.BACKEND),
        )
        idle()
        fake.gate?.complete(Unit)
        idle()

        assertNull(
            "a cancelled run must not open a preview for the previous event",
            shadowOf(activity).nextStartedActivity,
        )
        val action = activity.action()
        assertEquals("Generate all", action.text.toString())
        assertTrue(action.isEnabled)
    }

    @Test
    fun action_readsAsAButtonAndStatesGeneratingWhileBusy() {
        val activity = build()
        val action = activity.action()

        val atRest = AccessibilityNodeInfoCompat.obtain()
        ViewCompat.onInitializeAccessibilityNodeInfo(action, atRest)
        assertEquals(Button::class.java.name, atRest.className?.toString())
        assertNull("the label is the name, not a contentDescription", atRest.contentDescription)
        atRest.recycle()

        fake.gate = CompletableDeferred()
        action.performClick()
        idle()

        val busy = AccessibilityNodeInfoCompat.obtain()
        ViewCompat.onInitializeAccessibilityNodeInfo(action, busy)
        assertEquals("Generating", busy.stateDescription?.toString())
        busy.recycle()
    }

    @Test
    fun headingRow_atFontScaleTwo_keepsTheActionAtTheTouchFloorAndInsideTheRow() {
        val activity = build(fontScale = 2.0f)
        val action = activity.action()
        val row = action.parent as LinearLayout

        val width = dp(320)
        row.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        row.layout(0, 0, width, row.measuredHeight)

        assertTrue("the label grows instead of clipping", action.height >= dp(48))
        assertNull("the title wraps instead of ellipsizing", (row.getChildAt(0) as TextView).ellipsize)
        assertTrue("the action stays inside the row", action.right <= row.width - row.paddingEnd)
        assertTrue("the title keeps room to wrap", (row.getChildAt(0) as TextView).width > 0)
    }

    private class FakeEventsRepository(context: Context) : OrganizerRepository(context) {
        override suspend fun loadApprovedOrganizerEvents(): OrganizerMvpLoad<List<OrganizerMvpEvent>> =
            OrganizerMvpLoad(listOf(testOrganizerEvent("Night Market")), OrganizerMvpDataSource.BACKEND)
    }

    private class FakeReportsRepository(context: Context) : OrganizerReportsRepository(context) {
        val generated = mutableListOf<EventReportType>()
        var gate: CompletableDeferred<Unit>? = null
        var result: NetworkResult<EventReportDto> = NetworkResult.Success(EventReportDto())

        override suspend fun fetchSummary(eventId: String): NetworkResult<EventReportSummaryDto> =
            NetworkResult.Success(EventReportSummaryDto())

        override suspend fun generateReport(
            eventId: String,
            reportType: EventReportType,
            filters: EventReportFiltersDto,
        ): NetworkResult<EventReportDto> {
            generated += reportType
            gate?.await()
            return result
        }
    }
}
