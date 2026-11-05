package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.appcompat.view.ContextThemeWrapper
import kotlin.math.abs

/**
 * Phase 1 component foundation: every new layout must inflate under the real app theme, bind from
 * its binder, and keep the geometry contract (a hidden stat hides its divider too).
 *
 * This is the cheap substitute for the emulator pass: a layout that references a missing style,
 * a style that forgets to supply layout params, or a binder that touches a null id fails here
 * instead of on a device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ComponentInflationTest {

    private fun themedContext(): Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_EventQR)

    private fun inflate(layoutRes: Int, parent: ViewGroup): View {
        val context = themedContext()
        val container = when (parent) {
            is LinearLayout -> parent
            else -> LinearLayout(context)
        }
        return LayoutInflater.from(context).inflate(layoutRes, container, false)
    }

    @Test
    fun passHead_inflatesAndBindsTwoStats() {
        val root = inflate(R.layout.include_pass_head, LinearLayout(themedContext()))

        PassHeadBinder.bind(
            root,
            PassHeadModel(
                eyebrow = "ATTENDEE",
                title = "Juan",
                stats = listOf(PassStat("1 240", "Points"), PassStat("6", "Events")),
                bellCount = 3,
            ),
        )

        assertEquals("Juan", (root.findViewById<View>(R.id.passHeadTitle) as android.widget.TextView).text.toString())
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.passHeadStub).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.passHeadCell2).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.passHeadCell3).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.passHeadDivider3).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.passHeadBellBadge).visibility)
    }

    @Test
    fun passHead_eventStat_isSingularOnlyAtOne() {
        val context = themedContext()
        for ((value, expected) in listOf("0" to "Events", "1" to "Event", "2" to "Events")) {
            val root = inflate(R.layout.include_pass_head, LinearLayout(context))
            PassHeadBinder.bind(
                root,
                PassHeadModel(title = "Juan", stats = listOf(PassStat(value, "Events"))),
            )

            assertEquals(
                expected,
                root.findViewById<android.widget.TextView>(R.id.passHeadStat1Label).text.toString(),
            )
        }
    }

    @Test
    fun passHead_zeroStats_hidesTheStubButKeepsTheTear() {
        val root = inflate(R.layout.include_pass_head, LinearLayout(themedContext()))
        PassHeadBinder.bind(root, PassHeadModel(title = "Rewards"))

        assertEquals(View.GONE, root.findViewById<View>(R.id.passHeadStub).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.passHeadTear).visibility)
    }

    @Test
    fun passHead_tabsReplaceTheEyebrow() {
        val root = inflate(R.layout.include_pass_head, LinearLayout(themedContext()))
        PassHeadBinder.bind(
            root,
            PassHeadModel(
                title = "Today",
                eyebrow = "STAFF",
                tabs = listOf("Attendee", "Staff"),
                selectedTabIndex = 1,
            ),
        )

        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.passHeadTabs).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.passHeadEyebrow).visibility)
        assertTrue(root.findViewById<View>(R.id.passHeadTab2).isSelected)
    }

    @Test
    fun compactPassHead_inflatesAndTrails() {
        val root = inflate(R.layout.include_pass_head_compact, LinearLayout(themedContext()))
        val badge = android.widget.TextView(themedContext()).apply { text = "4" }
        PassHeadBinder.bindCompact(root, title = "Discover", eyebrow = "NEXT UP", trailing = badge)

        assertEquals("Discover", (root.findViewById<View>(R.id.passHeadTitle) as android.widget.TextView).text.toString())
        assertEquals(1, (root.findViewById<View>(R.id.passHeadTrailing) as LinearLayout).childCount)
    }

    /** The result count is a second line of the title, so it must share the title's baseline. */
    @Test
    fun compactPassHead_countSitsOnTheTitleBaseline() {
        val root = inflate(R.layout.include_pass_head_compact, LinearLayout(themedContext()))
        val title = root.findViewById<android.widget.TextView>(R.id.passHeadTitle)
        val count = root.findViewById<android.widget.TextView>(R.id.passHeadTitleCount)
        title.text = "Discover"
        count.text = "28 EVENTS"
        count.visibility = View.VISIBLE

        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        assertTrue("title baseline missing - fonts did not measure", title.baseline > 0)
        assertEquals(title.top + title.baseline, count.top + count.baseline)
    }

    /**
     * A long count must never squeeze the title: the count is capped and steps down inside that cap
     * instead of truncating. Checked at the narrowest supported width (320dp screen, 20dp gutter per
     * side) so it holds everywhere.
     */
    @Test
    fun compactPassHead_longCount_keepsTheTitleAtLeast55PercentOfTheRow() {
        val context = themedContext()
        val root = inflate(R.layout.include_pass_head_compact, LinearLayout(context))
        val density = context.resources.displayMetrics.density
        val gutter = context.resources.getDimensionPixelSize(R.dimen.ui_gutter)
        val rowWidth = 320f * density - 2 * gutter
        val cap = context.resources.getDimensionPixelSize(R.dimen.ui_pass_head_count_max)

        val title = root.findViewById<android.widget.TextView>(R.id.passHeadTitle)
        val count = root.findViewById<android.widget.TextView>(R.id.passHeadTitleCount)
        title.text = "Discover"
        count.text = "Manage admin and user accounts" // 30 characters
        count.visibility = View.VISIBLE

        root.measure(
            View.MeasureSpec.makeMeasureSpec((320f * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        // Robolectric measures text without the real font metrics, so assert the cap itself plus the
        // width both views actually got: the count can never claim more than 45% of the row.
        assertEquals("count cap", cap, count.maxWidth)
        assertTrue("count was ${count.measuredWidth}px, cap is ${cap}px", count.measuredWidth <= cap)
        assertTrue(
            "title got ${title.measuredWidth}px of the ${rowWidth.toInt()}px row",
            title.measuredWidth >= rowWidth * 0.55f,
        )
        assertEquals(title.top + title.baseline, count.top + count.baseline)
    }

    @Test
    fun listRows_inflate() {
        val context = themedContext()
        val rows = listOf(R.layout.list_row_icon, R.layout.list_row_value, R.layout.list_row_date)
        rows.forEach { layout ->
            val row = inflate(layout, LinearLayout(context))
            assertNotNull(row.findViewById<View>(R.id.listRowTitle))
            ListRowBinder.bind(
                row,
                title = "DevFest Manila 2026",
                meta = "12 Oct 2026",
                trailing = ListRowBinder.Trailing.VALUE,
                trailingText = "10 pts",
            )
            assertEquals(View.GONE, row.findViewById<View>(R.id.listRowChevron)?.visibility ?: View.GONE)
        }
    }

    @Test
    fun topBar_inflates() {
        val bar = inflate(R.layout.include_top_bar, LinearLayout(themedContext()))
        assertNotNull(bar.findViewById<View>(R.id.topBarBack))
        assertNotNull(bar.findViewById<View>(R.id.topBarTitle))
    }

    @Test
    fun metricRow_inflates() {
        val row = inflate(R.layout.widget_metric_row, LinearLayout(themedContext()))
        assertNotNull(row.findViewById<View>(R.id.metricLabel))
        assertNotNull(row.findViewById<View>(R.id.metricValue))
    }

    @Test
    fun dialogsAndSheets_inflate() {
        val context = themedContext()
        assertNotNull(LayoutInflater.from(context).inflate(R.layout.dialog_base, FrameLayout(context), false))
        assertNotNull(LayoutInflater.from(context).inflate(R.layout.sheet_bottom_base, FrameLayout(context), false))
    }

    /**
     * Quick actions (§6.2): two equal-width chips fill the gutter, so the row ends flush with
     * NEXT UP / DISCOVER and never needs a horizontal drag. `Discover` is gone - it is the Events
     * destination in the always-visible bottom nav, so a chip for it would be a duplicate entry.
     */
    @Test
    fun dashboardQuickActions_areEqualWidthAcrossTheGutter() {
        val context = themedContext()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.activity_user_dashboard, LinearLayout(context), false)
        val chips = listOf(R.id.btnTransactionHistory, R.id.btnRequestEventCard)
            .map { root.findViewById<View>(it) }
        val row = chips.first().parent as LinearLayout
        val density = context.resources.displayMetrics.density
        val gutter = context.resources.getDimensionPixelSize(R.dimen.ui_gutter)
        val gap = context.resources.getDimensionPixelSize(R.dimen.ui_chip_gap)
        val screenWidth = (360f * density).toInt()

        row.measure(
            View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)

        assertEquals("row starts on the left gutter", gutter, chips[0].left)
        assertEquals("row ends on the right gutter", screenWidth - gutter, chips[1].right)
        assertEquals("two chips only", 2, row.childCount)
        assertTrue(
            "equal widths within 1px: ${chips.map { it.measuredWidth }}",
            abs(chips[0].measuredWidth - chips[1].measuredWidth) <= 1,
        )
        assertEquals(
            "chips plus gap fill the content width",
            screenWidth - 2 * gutter,
            chips.sumOf { it.measuredWidth } + gap,
        )
        assertEquals(
            "the Discover chip id is no longer resolvable",
            0,
            context.resources.getIdentifier("btnAttendeeHub", "id", context.packageName),
        )
    }

    @Test
    fun skeletons_inflate() {
        val context = themedContext()
        assertNotNull(LayoutInflater.from(context).inflate(R.layout.include_skeleton_block, LinearLayout(context), false))
        assertNotNull(LayoutInflater.from(context).inflate(R.layout.include_skeleton_row, LinearLayout(context), false))
        assertNotNull(LayoutInflater.from(context).inflate(R.layout.include_skeleton_row_audit, LinearLayout(context), false))
        assertNotNull(LayoutInflater.from(context).inflate(R.layout.include_skeleton_card, LinearLayout(context), false))
    }

    @Test
    fun scannerViewport_inflates() {
        val root = inflate(R.layout.include_scan_viewport, LinearLayout(themedContext()))
        assertNotNull(root.findViewById<View>(R.id.scanPanel))
        assertNotNull(root.findViewById<View>(R.id.scanBracketTl))
        assertNotNull(root.findViewById<View>(R.id.scanTorch))
    }

    @Test
    fun qrCredential_inflates() {
        val root = inflate(R.layout.include_qr_credential, FrameLayout(themedContext()))
        assertNotNull(root.findViewById<View>(R.id.qrCredentialRoot))
        assertNotNull(root.findViewById<View>(R.id.qrPlate))
        assertNotNull(root.findViewById<View>(R.id.qrSerial))
    }
    /** Phase 6 screens: every layout the cleanup touched must still inflate under the app theme. */
    @Test
    fun redesignedScreens_inflate() {
        val screens = listOf(
            R.layout.activity_admin_dashboard,
            R.layout.activity_admin_event_requests,
            R.layout.activity_admin_account_management,
            R.layout.activity_create_admin_account,
            R.layout.activity_admin_audit_logs,
            R.layout.activity_forgot_password,
            R.layout.activity_reset_password,
            R.layout.activity_new_password,
            R.layout.activity_change_password,
            R.layout.activity_terms,
            R.layout.activity_splash_modern,
            R.layout.activity_report_preview,
            R.layout.activity_manage_rewards,
            R.layout.item_admin_account,
            R.layout.item_admin_audit_log,
            R.layout.item_terms_section,
            R.layout.item_manage_reward,
            R.layout.item_report_header,
            R.layout.item_report_section,
            R.layout.item_report_chart,
            R.layout.item_report_table,
            R.layout.dialog_event_request_submitted,
        )
        for (layout in screens) {
            assertNotNull(
                "layout $layout did not inflate",
                inflate(layout, LinearLayout(themedContext())),
            )
        }
    }

}

