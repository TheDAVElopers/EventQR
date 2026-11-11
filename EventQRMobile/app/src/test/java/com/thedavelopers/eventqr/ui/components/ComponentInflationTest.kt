package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.organizer.events.DiscardChangesDialog
import com.thedavelopers.eventqr.features.organizer.events.OrganizerEventHeadBinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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

    /**
     * Regression for the notched bell badge: the 18dp count sits with a -2dp top/end margin (§4.6),
     * so it overhangs the 44dp bell frame by 2dp. Every ancestor that could clip it has to opt out,
     * otherwise the badge renders as a circle with its top-end corner bitten off.
     */
    @Test
    fun passHead_bellBadge_isNotClippedByItsAncestors() {
        val root = inflate(R.layout.include_pass_head, LinearLayout(themedContext()))
        PassHeadBinder.bind(root, PassHeadModel(title = "Juan", bellCount = 8))

        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        val bell = root.findViewById<ViewGroup>(R.id.passHeadBell)
        val badge = root.findViewById<View>(R.id.passHeadBellBadge)
        val row0 = bell.parent as ViewGroup
        val identity = row0.parent as ViewGroup

        // The spec's -2dp offset makes the badge overhang the bell frame on the top and end edges.
        assertTrue("badge does not overhang the bell frame", badge.right > bell.width && badge.top < 0)
        // So no ancestor may clip it. clipChildren has to be off all the way up: in hardware
        // rendering a child is clipped to its own bounds whenever its PARENT keeps clipChildren,
        // so leaving it on Block A clipped row0 (and the badge inside it) at the gutter edge.
        assertFalse("passHeadBell clips the badge", bell.clipChildren)
        assertFalse("row0 clips the badge", row0.clipChildren)
        assertFalse("passHeadIdentity clips the badge", identity.clipChildren)
        // The software path clips to the padded rect instead, hence the padding opt-out too.
        assertFalse("passHeadIdentity clips the badge to its padding", identity.clipToPadding)
    }

    /**
     * The same rule against the real host layouts. Inflating the four dashboards catches a host
     * that wraps the pass head in a clipping container - the include owns the clip chain, and a
     * host that keeps clipChildren on any box narrower than the badge re-notches it.
     */
    @Test
    fun passHead_bellBadge_isNotClippedInAnyDashboardHost() {
        val hosts = intArrayOf(
            R.layout.activity_user_dashboard,
            R.layout.activity_staff_dashboard,
            R.layout.activity_organizer_dashboard,
            R.layout.activity_admin_dashboard,
        )
        hosts.forEach { layoutRes ->
            val host = themedContext().resources.getResourceEntryName(layoutRes)
            val root = inflate(layoutRes, LinearLayout(themedContext()))
            // A ComposeView needs a window recomposer, which a detached inflate does not have.
            hideComposeViews(root)
            PassHeadBinder.bind(
                root.findViewById(R.id.passHead),
                PassHeadModel(title = "Juan", bellCount = 8),
            )
            root.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)

            val badge = root.findViewById<View>(R.id.passHeadBellBadge)
            val badgeRect = boundsIn(root as ViewGroup, badge)
            assertTrue(
                "$host: badge does not overhang the bell frame",
                badgeRect.right > boundsIn(root as ViewGroup, badge.parent as View).right,
            )

            // A child is cropped to its own bounds whenever its PARENT keeps clipChildren, and the
            // crop takes the whole subtree with it. So for every box the badge's ink escapes, the
            // box's parent has to opt out - the regression here was Block A keeping clipChildren,
            // which cropped row0 (and the badge) at the gutter edge with nothing else clipping.
            var node: View = badge
            while (true) {
                val box = boundsIn(root as ViewGroup, node)
                val escapes = badgeRect.left < box.left || badgeRect.top < box.top ||
                    badgeRect.right > box.right || badgeRect.bottom > box.bottom
                if (escapes) {
                    val holder = node.parent as ViewGroup
                    assertFalse(
                        "$host: ${holder.javaClass.simpleName} clips ${node.javaClass.simpleName}, " +
                            "which carries the badge (badge=$badgeRect box=$box)",
                        holder.clipChildren,
                    )
                }
                val parent = node.parent as View
                if (parent === root) break
                node = parent
            }
        }
    }

    /**
     * Item 2: the create/request entry point belongs to the screen, not to the empty state. Both
     * organizer screens carry the same section action outside `EventQrEmptyState`, so it survives
     * a populated, empty and failed list.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun organizerScreens_carryTheRequestActionOutsideTheEmptyState() {
        val context = themedContext()
        val touchMin = context.resources.getDimensionPixelSize(R.dimen.ui_touch_min)
        val glyphSlots = arrayOf("start", "top", "end", "bottom")
        val layouts = intArrayOf(
            R.layout.activity_organizer_dashboard,
            R.layout.activity_organizer_events,
        )
        for (layoutRes in layouts) {
            val host = context.resources.getResourceEntryName(layoutRes)
            val root = LayoutInflater.from(context).inflate(layoutRes, null, false)
            val action = root.findViewById<android.widget.TextView>(R.id.btnRequestEvent)
            assertNotNull("$host has no request action", action)
            assertEquals(
                "$host must use the one Request event label",
                context.getString(R.string.ui_organizer_request_event),
                action.text.toString(),
            )
            assertEquals("$host starts with the action visible", View.VISIBLE, action.visibility)

            var node: View? = action
            while (node != null) {
                assertFalse(
                    "$host nests the request action inside the empty state",
                    node is EventQrEmptyState,
                )
                node = node.parent as? View
            }

            // Section action, not a chip: text only, no glyph in any compound slot, zero horizontal
            // padding (a chip pads its box by 8-12dp; the section action's zero padding is what puts
            // the label on the gutter), and the 48dp floor that makes the label a touch target.
            val actions = listOfNotNull(action, root.findViewById<android.widget.TextView>(R.id.btnSeeAllEvents))
            for (view in actions) {
                val name = context.resources.getResourceEntryName(view.id)
                assertEquals(
                    "$host/$name must stay a 48dp touch target",
                    touchMin,
                    view.minimumHeight,
                )
                assertEquals("$host/$name must not pad like a chip", 0, view.paddingStart)
                assertEquals("$host/$name must not pad like a chip", 0, view.paddingEnd)
                view.compoundDrawablesRelative.forEachIndexed { index, drawable ->
                    assertNull(
                        "$host/$name carries a glyph in the ${glyphSlots[index]} slot",
                        drawable,
                    )
                }
                // Painted boundary: at rest the section action paints nothing, while a chip fills
                // its box with ui_surface and strokes a 1dp outline. Draw the real background at
                // rest and read its centre pixel, so a chip drawable cannot hide here.
                val background = requireNotNull(view.background) { "$host/$name has no press wash" }
                val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
                background.setState(view.drawableState)
                background.setBounds(0, 0, 8, 8)
                background.draw(Canvas(bitmap))
                assertEquals(
                    "$host/$name must paint no chip boundary",
                    0,
                    Color.alpha(bitmap.getPixel(4, 4)),
                )
            }
        }
    }

    /**
     * §6.4: the per-event strip is a 48dp row, so its action chip carries `wrap_content` height
     * over the 48dp floor and grows at fontScale 2.0 instead of clipping. The chip style's own
     * 36dp height is a visual size, never a box the label has to fit inside.
     */
    @Test
    fun eventStrip_chipGrowsWithItsLabel() {
        val context = themedContext()
        val root = inflate(R.layout.include_organizer_event_strip, LinearLayout(context))
        val chip = root.findViewById<android.widget.TextView>(R.id.eventStripAction)
        assertNotNull("the strip lost its action chip", chip)
        assertEquals(
            "the strip chip must not pin a fixed height",
            ViewGroup.LayoutParams.WRAP_CONTENT,
            chip.layoutParams.height,
        )
        assertEquals(
            "the strip chip must clear the 48dp touch floor",
            context.resources.getDimensionPixelSize(R.dimen.ui_touch_min),
            chip.minimumHeight,
        )
    }

    private fun boundsIn(root: ViewGroup, view: View): Rect =
        Rect(0, 0, view.width, view.height)
            .also { root.offsetDescendantRectToMyCoords(view, it) }

    /**
     * Regression for the clipped stat label: Block B is a 56dp MINIMUM band, not a fixed 56dp box.
     * The value (numeric_m) over the label (overline) scale with the font size, and on the fixed
     * band the cell ran out of height: the label was squeezed to what was left and the stub's own
     * clipChildren cut the glyphs mid-cap. Measured on device at fontScale 1.5 the label got a 30px
     * box for a 43px line and its ink ended on the band's last row (0px clear); at 2.0 it got 0px.
     */
    @Test
    fun passHead_stub_isAMinimumSoTheStatTextCanGrow() {
        val context = themedContext()
        val minHeight = context.resources.getDimensionPixelSize(R.dimen.ui_pass_head_stub_height)
        val root = inflate(R.layout.include_pass_head, LinearLayout(context))
        PassHeadBinder.bind(
            root,
            PassHeadModel(title = "Juan", stats = listOf(PassStat("1 240", "Points"))),
        )

        val stub = root.findViewById<View>(R.id.passHeadStub)
        val value = root.findViewById<android.widget.TextView>(R.id.passHeadStat1Value)
        val label = root.findViewById<View>(R.id.passHeadStat1Label)

        // The boundary itself: a floor, never a fixed height. Same shape as Block A (116dp min)
        // and row1 (44dp min), and as the per-event stub in activity_organizer_event_details.
        assertEquals(
            "the stub must be wrap_content, not a fixed band",
            ViewGroup.LayoutParams.WRAP_CONTENT,
            stub.layoutParams.height,
        )
        assertEquals("stub min height", minHeight, stub.minimumHeight)

        measureAndLayout(root, 1080)
        // Robolectric measures text with stub metrics, so the band is never asserted against the
        // real 56dp-at-1.0 result (verified on device: 371..525px at fontScale 1.0, unchanged by
        // this fix); only the floor holds in a unit test.
        assertTrue(
            "stub is ${stub.measuredHeight}px, below its ${minHeight}px floor",
            stub.measuredHeight >= minHeight,
        )

        // Grow the type the way a large font scale does. The band has to take the extra height and
        // both cells have to keep a full line box - a fixed band squeezes the label to 0 instead.
        value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 240f)
        measureAndLayout(root, 1080)

        assertTrue(
            "stub did not grow with its text: ${stub.measuredHeight}px against a ${minHeight}px floor",
            stub.measuredHeight > minHeight,
        )
        assertTrue("label was squeezed to ${label.measuredHeight}px", label.measuredHeight > 0)
        val cell = label.parent as View
        assertTrue(
            "cell ${cell.height}px overflows the ${stub.measuredHeight}px band",
            cell.height <= stub.measuredHeight,
        )
    }

    /**
     * The value and label must sit INSIDE the band with a clear margin, at any text size, and no
     * box between the glyphs and the window may crop them. The cells carry a vertical pad
     * (ui_pass_head_stub_pad_v) so a grown band never lands a glyph on its edge, and the stub and
     * its cells opt out of clipChildren so a squeezed or oversized line is never chopped mid-cap.
     */
    @Test
    fun passHead_stub_keepsValueAndLabelInsideTheBandAtAnyTextSize() {
        val context = themedContext()
        val pad = context.resources.getDimensionPixelSize(R.dimen.ui_pass_head_stub_pad_v)
        val root = inflate(R.layout.include_pass_head, LinearLayout(context))
        PassHeadBinder.bind(
            root,
            PassHeadModel(
                title = "Juan",
                stats = listOf(PassStat("1 240", "Points"), PassStat("6", "Registered"), PassStat("12", "Check-ins")),
            ),
        )
        val stub = root.findViewById<ViewGroup>(R.id.passHeadStub)
        assertFalse("passHeadStub crops its cells", stub.clipChildren)

        // 1.0 -> 2.0 of the 20sp/11sp ramp, in px at the unit-test density.
        for (valuePx in listOf(40f, 60f, 120f, 240f)) {
            val labelPx = valuePx * 11f / 20f
            val ids = listOf(
                R.id.passHeadStat1Value to R.id.passHeadStat1Label,
                R.id.passHeadStat2Value to R.id.passHeadStat2Label,
                R.id.passHeadStat3Value to R.id.passHeadStat3Label,
            )
            ids.forEach { (valueId, labelId) ->
                root.findViewById<android.widget.TextView>(valueId)
                    .setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, valuePx)
                root.findViewById<android.widget.TextView>(labelId)
                    .setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, labelPx)
            }
            measureAndLayout(root, 1080)

            ids.forEach { (valueId, labelId) ->
                val value = root.findViewById<View>(valueId)
                val label = root.findViewById<View>(labelId)
                val cell = value.parent as ViewGroup
                val where = "text ${valuePx}px"
                assertFalse("$where: a stat cell crops its text", cell.clipChildren)
                assertTrue("$where: label has no height", label.height > 0)
                assertTrue("$where: cell has no top pad", cell.paddingTop >= pad && pad > 0)
                assertTrue("$where: cell has no bottom pad", cell.paddingBottom >= pad)
                // Value above label, both inside the band, label bottom a pad clear of the edge.
                assertTrue("$where: value starts above the band", cell.top + value.top >= pad)
                assertTrue("$where: label overlaps the value", label.top >= value.bottom)
                assertTrue(
                    "$where: label bottom ${cell.top + label.bottom}px is within $pad px of the " +
                        "${stub.height}px band edge",
                    cell.top + label.bottom <= stub.height - pad,
                )
            }
        }
    }

    private fun measureAndLayout(root: View, width: Int) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun hideComposeViews(view: View) {
        if (view is ComposeView) {
            view.visibility = View.GONE
            return
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) hideComposeViews(view.getChildAt(index))
        }
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
        assertNotNull(
            LayoutInflater.from(context).inflate(R.layout.view_date_filter_content, FrameLayout(context), false),
        )
    }

    /** Sheet actions are floors, not fixed heights: a two-line label at fontScale 2.0 must not clip. */
    @Test
    fun sheetActions_areMinimumHeightsNotFixedHeights() {
        val context = themedContext()
        val sheet = LayoutInflater.from(context).inflate(R.layout.sheet_bottom_base, FrameLayout(context), false)
        val primary = sheet.findViewById<View>(R.id.sheetPrimaryAction)
        val secondary = sheet.findViewById<View>(R.id.sheetSecondaryAction)

        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, primary.layoutParams.height)
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_button_height),
            primary.minimumHeight,
        )
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, secondary.layoutParams.height)
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_button_height_inline),
            secondary.minimumHeight,
        )
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
        assertNotNull(root.findViewById<View>(R.id.qrAttendeeName))
        assertNotNull(root.findViewById<View>(R.id.qrPlate))
        assertNotNull(root.findViewById<View>(R.id.qrSerial))
    }

    /**
     * §5.14 one object, one anatomy: the QR display is the shared pass plus the save/brightness
     * controls. The duplicate details card and the never-visible registration-id load plumbing are
     * deleted, not hidden.
     */
    @Test
    fun qrDisplay_inflatesTheSharedPassWithoutTheDuplicateCardOrPlumbing() {
        val context = themedContext()
        val root = inflate(R.layout.activity_qr_display, LinearLayout(context))

        assertNotNull(root.findViewById<View>(R.id.qrCredential))
        assertNotNull(root.findViewById<View>(R.id.btnMarkQrDownloaded))
        assertNotNull(root.findViewById<View>(R.id.switchKeepBright))
        listOf(
            "txtQrAttendeeName",
            "txtQrAttendeeEmail",
            "txtQrCredentialValue",
        ).forEach { gone ->
            assertEquals(
                "$gone must be deleted from the QR display",
                0,
                context.resources.getIdentifier(gone, "id", context.packageName),
            )
        }
        // The hidden registration-id load plumbing is deleted with the views it served: neither the
        // QR display nor the post-registration screen carries it any more.
        listOf("edtQrRegistrationId", "btnLoadQr", "txtQrValue").forEach { gone ->
            assertEquals(
                "$gone must be deleted from the layout and from R",
                0,
                context.resources.getIdentifier(gone, "id", context.packageName),
            )
        }
    }

    /** The off-scale 10dp step is retired; the closed 4dp scale covers every former use. */
    @Test
    fun theOffScaleTenDpTokenIsGone() {
        val context = themedContext()

        assertEquals(0, context.resources.getIdentifier("space_10", "dimen", context.packageName))
        assertTrue(context.resources.getIdentifier("space_md", "dimen", context.packageName) != 0)
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
            R.layout.activity_event_management_hub,
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
            R.layout.activity_organizer_event_details,
        )
        for (layout in screens) {
            assertNotNull(
                "layout $layout did not inflate",
                inflate(layout, LinearLayout(themedContext())),
            )
        }
    }

    /**
     * §4.6 per-event identity head: the name with the badge as the title row's trailing value, the
     * two optional meta lines, and the tear as the dark block's LAST child (as a sibling it painted
     * on `ui_background` at ~1.01:1 and was invisible).
     */
    @Test
    fun organizerEventHead_bindsNameStatusWhenWhere() {
        val context = themedContext()
        val root = inflate(R.layout.include_organizer_event_head, LinearLayout(context))
        OrganizerEventHeadBinder.bind(
            root,
            title = "DevFest Manila 2026",
            status = EventStatus.APPROVED,
            whenText = "12 Jun 2026, 9:00 AM - 5:00 PM",
            whereText = "SMX Convention Center",
        )

        assertEquals(
            "DevFest Manila 2026",
            (root.findViewById<View>(R.id.oeHeadTitle) as android.widget.TextView).text.toString(),
        )
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.oeHeadStatus).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.oeHeadWhen).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.oeHeadWhere).visibility)

        val head = root as ViewGroup
        assertEquals(
            "the tear must be the head's last child",
            R.id.oeHeadTear,
            head.getChildAt(head.childCount - 1).id,
        )

        // §9.5: one node for the name plus status, and the badge's tone is never read.
        assertEquals(
            "DevFest Manila 2026, Upcoming",
            head.findViewById<View>(R.id.oeHeadTitleRow).contentDescription,
        )
        assertEquals(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO,
            head.findViewById<View>(R.id.oeHeadStatus).importantForAccessibility,
        )
    }

    /** Blank meta shrinks the head to the title row; every text container stays wrap_content (§9.3). */
    @Test
    fun organizerEventHead_hidesBlankMetaAndKeepsEveryTextContainerWrapContent() {
        val context = themedContext()
        val root = inflate(R.layout.include_organizer_event_head, LinearLayout(context))
        OrganizerEventHeadBinder.bind(
            root,
            title = "DevFest Manila 2026",
            status = null,
            whenText = " ",
            whereText = null,
        )

        assertEquals(View.GONE, root.findViewById<View>(R.id.oeHeadWhen).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.oeHeadWhere).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.oeHeadStatus).visibility)

        listOf(R.id.oeHeadTitle, R.id.oeHeadWhen, R.id.oeHeadWhere).forEach { id ->
            assertEquals(
                "view $id must be wrap_content, not a fixed text container",
                ViewGroup.LayoutParams.WRAP_CONTENT,
                root.findViewById<View>(id).layoutParams.height,
            )
        }
        assertEquals(3, (root.findViewById<View>(R.id.oeHeadTitle) as android.widget.TextView).maxLines)
    }

    /** A 3-line 28sp title at 2.0 needs the row: the badge reflows under it, `space_8` above. */
    @Test
    @Config(
        sdk = [35],
        shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class],
        fontScale = 2.0f,
    )
    fun organizerEventHead_badgeReflowsBelowTheTitleAtLargeFont() {
        val context = themedContext()
        val root = inflate(R.layout.include_organizer_event_head, LinearLayout(context))
        OrganizerEventHeadBinder.bind(
            root,
            title = "DevFest Manila 2026",
            status = EventStatus.REJECTED,
            whenText = "12 Jun 2026",
            whereText = "SMX Convention Center",
        )

        val badge = root.findViewById<View>(R.id.oeHeadStatus)
        val meta = root.findViewById<ViewGroup>(R.id.oeHeadMeta)
        val titleRow = root.findViewById<ViewGroup>(R.id.oeHeadTitleRow)

        assertSame("the badge must leave the title row", meta, badge.parent)
        assertNotSame(titleRow, badge.parent)
        assertSame(
            "the badge is the meta block's last line",
            badge,
            meta.getChildAt(meta.childCount - 1),
        )
        val params = badge.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(
            "space_8 above the reflowed badge",
            context.resources.getDimensionPixelSize(R.dimen.space_8),
            params.topMargin,
        )
        assertEquals("no start margin once reflowed", 0, params.marginStart)
    }

    /** At the default scale the badge stays the title row's trailing value (§5.4 placement). */
    @Test
    fun organizerEventHead_badgeTrailsTheTitleAtDefaultFont() {
        val context = themedContext()
        val root = inflate(R.layout.include_organizer_event_head, LinearLayout(context))
        OrganizerEventHeadBinder.bind(
            root,
            title = "DevFest Manila 2026",
            status = EventStatus.ACTIVE,
            whenText = null,
            whereText = null,
        )

        val badge = root.findViewById<View>(R.id.oeHeadStatus)
        val titleRow = root.findViewById<ViewGroup>(R.id.oeHeadTitleRow)
        assertSame(titleRow, badge.parent)
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.space_12),
            (badge.layoutParams as ViewGroup.MarginLayoutParams).marginStart,
        )
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_badge_height),
            badge.minimumHeight,
        )
        assertEquals(
            "the head overrides the shared badge's fixed 24dp height",
            ViewGroup.LayoutParams.WRAP_CONTENT,
            badge.layoutParams.height,
        )
    }

    /**
     * The view page's seven blocks in order (§6.4 v1.7): top bar, scroll holding head -> stub ->
     * three-row table -> review note -> About -> actions, plus the skeleton that replaces the shipped
     * `visibility="invisible"` blank. Date / Time / Venue are ids that must no longer exist: they are
     * the head's `when` and `where`.
     */
    @Test
    fun organizerEventDetails_hasTheSevenBlockOrderAndNoDateVenueRows() {
        val context = themedContext()
        val root = inflate(R.layout.activity_organizer_event_details, LinearLayout(context)) as ViewGroup
        val density = context.resources.displayMetrics.density
        val screenWidth = (360f * density).toInt()

        // First load: the skeleton is on screen and the content is not (no invisible blank).
        val scroll = root.getChildAt(1) as ScrollView
        val skeleton = root.getChildAt(2) as ViewGroup
        assertEquals(View.VISIBLE, skeleton.visibility)
        assertEquals(View.GONE, scroll.visibility)

        // Layout the loaded page: a GONE subtree is never measured, so the geometry checks below
        // need the content visible, exactly as bind() leaves it.
        scroll.visibility = View.VISIBLE
        skeleton.visibility = View.GONE

        root.measure(
            View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((800f * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        val column = scroll.getChildAt(0) as LinearLayout

        assertEquals("block 2 is the shared head", R.id.oeHead, column.getChildAt(0).id)
        assertEquals("block 3 is the stub", R.id.oedStub, column.getChildAt(1).id)
        assertEquals("block 5 is the review note", R.id.oedReviewNote, column.getChildAt(3).id)
        assertEquals("block 6 is About", R.id.oedAboutBlock, column.getChildAt(4).id)

        // Stub: a floor, never a fixed band - value over label grows at large font scale.
        val stub = column.getChildAt(1)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, stub.layoutParams.height)
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_pass_head_stub_height),
            stub.minimumHeight,
        )

        // Table: exactly three rows, each followed by its hairline.
        val table = column.getChildAt(2) as ViewGroup
        assertEquals("three rows plus three hairlines", 6, table.childCount)
        assertEquals(R.id.oedRowRegistration, table.getChildAt(0).id)
        assertEquals(R.id.oedRowOrganizer, table.getChildAt(2).id)
        assertEquals(R.id.oedRowCoOrganizers, table.getChildAt(4).id)
        listOf("oedRowDate", "oedRowTime", "oedRowVenue").forEach { gone ->
            assertEquals(
                "$gone must be deleted: date, time and venue live in the head",
                0,
                context.resources.getIdentifier(gone, "id", context.packageName),
            )
        }

        // Actions: primary first, ghost beneath, 8dp apart, both full width - not an equal pair.
        val actions = column.getChildAt(5) as ViewGroup
        assertEquals(2, actions.childCount)
        val primary = actions.getChildAt(0)
        val ghost = actions.getChildAt(1)
        assertEquals(R.id.oedActionAttendees, primary.id)
        assertEquals(R.id.oedActionReports, ghost.id)
        assertTrue(
            "the primary sits above the ghost",
            primary.top + primary.height <= ghost.top,
        )
        assertEquals(
            "8dp between the two actions",
            context.resources.getDimensionPixelSize(R.dimen.space_8),
            ghost.top - (primary.top + primary.height),
        )
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_button_height),
            primary.minimumHeight,
        )
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_button_height_inline),
            ghost.minimumHeight,
        )
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, primary.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, ghost.layoutParams.width)
        // The floors hold at 1.0 (measured 52 / 48) and the controls may only grow with the label.
        assertTrue("primary is ${primary.height}px", primary.height >= primary.minimumHeight)
        assertTrue("ghost is ${ghost.height}px", ghost.height >= ghost.minimumHeight)

        // Skeleton: head + stub + three rows in their own geometry, never an invisible blank.
        assertEquals(
            context.getString(R.string.ui_skeleton_loading),
            skeleton.contentDescription.toString(),
        )
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_organizer_head_skeleton_height),
            skeleton.getChildAt(0).layoutParams.height,
        )
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_pass_head_stub_height),
            skeleton.getChildAt(1).layoutParams.height,
        )
        val skeletonRows = skeleton.getChildAt(2) as ViewGroup
        assertEquals(3, skeletonRows.childCount)
        for (index in 0 until skeletonRows.childCount) {
            assertEquals(
                context.resources.getDimensionPixelSize(R.dimen.ui_metric_row_height),
                skeletonRows.getChildAt(index).minimumHeight,
            )
        }
    }

    /**
     * The editor (§6.4 v1.7): full-bleed head first, five eyebrowed sections in the view page's
     * block order, four perforations between them, and §5.2's field anatomy - labelFor at its own
     * control, a wrap_content box on the 56dp floor.
     */
    @Test
    fun organizerEventEdit_hasTheSectionOrderAndTheFieldAnatomy() {
        val context = themedContext()
        val root = inflate(R.layout.activity_organizer_event_edit, LinearLayout(context)) as ViewGroup

        val scroll = root.getChildAt(1) as ScrollView
        val column = scroll.getChildAt(0) as LinearLayout
        assertEquals("the head is full-bleed: no padding on the scroll column", 0, column.paddingStart)
        assertEquals("block 1 of the column is the shared head", R.id.oeHead, column.getChildAt(0).id)
        assertEquals(
            "the editor hides both meta lines: the venue is a field and the schedule is a row",
            View.GONE,
            root.findViewById<View>(R.id.oeHeadWhen).visibility,
        )
        assertEquals(View.GONE, root.findViewById<View>(R.id.oeHeadWhere).visibility)

        val form = column.getChildAt(1) as ViewGroup
        assertEquals("locked note + 5 sections + 4 perforations", 10, form.childCount)
        assertEquals(R.id.eeSectionEvent, form.getChildAt(1).id)
        assertEquals(R.id.eeSectionSchedule, form.getChildAt(3).id)
        assertEquals(R.id.eeSectionRegistration, form.getChildAt(5).id)
        assertEquals(R.id.eeSectionAbout, form.getChildAt(7).id)
        assertEquals(R.id.eeSectionBanner, form.getChildAt(9).id)

        val controlHeight = context.resources.getDimensionPixelSize(R.dimen.ui_control_height)
        listOf(
            R.id.eeTitleLabel to R.id.eeTitleInput,
            R.id.eeVenueLabel to R.id.eeVenueInput,
            R.id.eeCapacityLabel to R.id.eeCapacityInput,
        ).forEach { (labelId, inputId) ->
            val label = root.findViewById<android.widget.TextView>(labelId)
            val input = root.findViewById<View>(inputId)
            assertEquals("label $labelId must point at its control", inputId, label.labelFor)
            assertEquals(
                "field $inputId must be wrap_content, not a fixed box",
                ViewGroup.LayoutParams.WRAP_CONTENT,
                input.layoutParams.height,
            )
            assertEquals("field $inputId floor", controlHeight, input.minimumHeight)
        }

        // The description is the multiline control: 96dp minimum, not a one-line box.
        assertEquals(
            R.id.eeDescriptionInput,
            root.findViewById<android.widget.TextView>(R.id.eeDescriptionLabel).labelFor,
        )
        assertEquals(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            root.findViewById<View>(R.id.eeDescriptionInput).layoutParams.height,
        )
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_field_multiline_height),
            root.findViewById<View>(R.id.eeDescriptionInput).minimumHeight,
        )
        // Banner: 16:9 at the 320dp content width.
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_organizer_event_banner_height),
            root.findViewById<View>(R.id.eeBannerPreview).layoutParams.height,
        )

        // No text container in the whole layout is a fixed height (§9.3).
        assertTrue(
            "the layout must actually hold type",
            assertNoFixedTextContainers(root, "activity_organizer_event_edit") >= 10,
        )
    }

    /** §9.3: a view that holds type grows with the font scale; only media may carry a fixed size. */
    private fun assertNoFixedTextContainers(view: View, layout: String): Int {
        var checked = 0
        if (view is android.widget.TextView) {
            val name = if (view.id == View.NO_ID) {
                "${view.javaClass.simpleName} \"${view.text}\""
            } else {
                view.resources.getResourceEntryName(view.id)
            }
            if (view.layoutParams.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                throw AssertionError("$layout: $name is ${view.layoutParams.height}px high")
            }
            checked++
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                checked += assertNoFixedTextContainers(view.getChildAt(index), layout)
            }
        }
        return checked
    }

    /**
     * The save bar is the screen's last block, its height is a floor, and the primary starts
     * disabled because nothing is dirty until the first edit.
     */
    @Test
    fun organizerEventEdit_saveBarIsTheLastBlockAndStartsDisabled() {
        val context = themedContext()
        val root = inflate(R.layout.activity_organizer_event_edit, LinearLayout(context)) as ViewGroup

        val last = root.getChildAt(root.childCount - 1)
        assertEquals("the save bar is the last block", R.id.eeSaveBar, last.id)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, last.layoutParams.height)
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_footer_height),
            last.minimumHeight,
        )
        assertFalse("clean form: save must start disabled", root.findViewById<View>(R.id.eeSaveButton).isEnabled)
        assertEquals(View.GONE, root.findViewById<View>(R.id.eeUnsaved).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.eeSaveError).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.eeSaveProgress).visibility)

        // 52dp primary on its floor, never a fixed height.
        val save = root.findViewById<View>(R.id.eeSaveButton)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, save.layoutParams.height)
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_button_height),
            save.minimumHeight,
        )

        // §6.6: the bar is bottom-anchored, so the dirty line grows it upward and the primary keeps
        // its place on screen.
        val density = context.resources.displayMetrics.density
        val screenWidth = (360f * density).toInt()
        val screenHeight = (800f * density).toInt()
        fun layoutScreen() {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(screenHeight, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }
        layoutScreen()
        assertEquals("the bar sits on the bottom edge", screenHeight, last.bottom)
        val cleanButtonTop = boundsIn(root, save).top
        val cleanBarHeight = last.height

        root.findViewById<View>(R.id.eeUnsaved).visibility = View.VISIBLE
        layoutScreen()
        assertEquals("dirty state must not move the primary", cleanButtonTop, boundsIn(root, save).top)
        assertTrue("the bar grows upward", last.height > cleanBarHeight)
        assertEquals("the bar keeps the bottom edge", screenHeight, last.bottom)
    }

    /** The locked rows are values, not controls: no editable field, no tap, a 56dp floor. */
    @Test
    fun organizerEventEdit_lockedRowsAreReadOnly() {
        val context = themedContext()
        val root = inflate(R.layout.activity_organizer_event_edit, LinearLayout(context))

        val rowFloor = context.resources.getDimensionPixelSize(R.dimen.ui_metric_row_height)
        listOf(R.id.eeEventDatesRow to R.id.eeEventDatesValue, R.id.eeWindowRow to R.id.eeWindowValue)
            .forEach { (rowId, valueId) ->
                val row = root.findViewById<View>(rowId)
                val value = root.findViewById<View>(valueId)
                assertFalse("row $rowId must be read-only", value is android.widget.EditText)
                assertFalse("value $valueId must not be tappable", row.isClickable)
                assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, row.layoutParams.height)
                assertEquals("row $rowId floor", rowFloor, row.minimumHeight)
            }
        assertEquals(View.GONE, root.findViewById<View>(R.id.eeLockedNote).visibility)
    }

    /** Dirty back: the discard confirm says what will be lost and only `Discard` commits. */
    @Test
    fun discardChangesDialog_wiresKeepEditingAndDestructiveDiscard() {
        val context = themedContext()
        var discarded = false
        val dialog = DiscardChangesDialog.create(context) { discarded = true }
        dialog.show()

        assertEquals(
            context.getString(R.string.ui_organizer_edit_discard_title),
            dialog.findViewById<android.widget.TextView>(R.id.dialogTitle)?.text.toString(),
        )
        assertEquals(
            context.getString(R.string.ui_organizer_edit_discard_body),
            dialog.findViewById<android.widget.TextView>(R.id.dialogBody)?.text.toString(),
        )
        val keep = dialog.findViewById<Button>(R.id.dialogNegative)!!
        val commit = dialog.findViewById<Button>(R.id.dialogPositive)!!
        assertEquals(context.getString(R.string.ui_organizer_edit_keep_editing), keep.text.toString())
        assertEquals(context.getString(R.string.ui_organizer_edit_discard), commit.text.toString())
        assertEquals(
            "the commit carries the filled red label colour (`DestructiveCommit`), the ghost does not",
            ContextCompat.getColor(context, R.color.ui_on_primary),
            commit.currentTextColor,
        )

        keep.performClick()
        assertFalse("Keep editing must not discard", discarded)
        assertFalse(dialog.isShowing)

        val second = DiscardChangesDialog.create(context) { discarded = true }
        second.show()
        second.findViewById<Button>(R.id.dialogPositive)?.performClick()
        assertTrue("Discard must point at the discard callback", discarded)
        assertFalse(second.isShowing)
    }
}
