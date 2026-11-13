package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.graphics.drawable.InsetDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.constraintlayout.helper.widget.Flow
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.attendee.AttendeeRewardsActivity
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpScanPurpose
import com.thedavelopers.eventqr.features.organizer.chip
import com.thedavelopers.eventqr.features.organizer.chipFlow
import com.thedavelopers.eventqr.features.organizer.events.ManageEventsActivity
import com.thedavelopers.eventqr.features.organizer.reports.EventReportsActivity
import com.thedavelopers.eventqr.features.organizer.testOrganizerEvent
import com.thedavelopers.eventqr.features.organizer.transactions.TransactionRulesActivity
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * §5.6 chip-row recipe, enforced where it is declared. Every wrapping chip row is a `Flow` on
 * `Widget.EventQR.Ui.ChipFlow` (chain, packed, bias 0, `ui_chip_gap` gaps) with no
 * `flow_maxElementsWrap`; a chip's state is `View.isSelected` and nothing else swaps its background.
 * An exclusive single choice is a select field (§5.2) instead, so it declares no Flow at all.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class FilterChipRowTest {

    /** `flow_wrapMode="chain"` and `flow_horizontalStyle="packed"` in the library's attr enums. */
    private companion object {
        const val WRAP_CHAIN = 1
        const val CHAIN_PACKED = 2
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val chipRowLayouts = mapOf(
        "activity_user_events" to R.layout.activity_user_events,
        "activity_organizer_events" to R.layout.activity_organizer_events,
        "activity_user_registered_events" to R.layout.activity_user_registered_events,
        "activity_admin_event_requests" to R.layout.activity_admin_event_requests,
        "activity_staff_assigned_events" to R.layout.activity_staff_assigned_events,
        "activity_search_attendees" to R.layout.activity_search_attendees,
        "activity_user_rewards" to R.layout.activity_user_rewards,
    )

    private data class FlowDecl(val styleRes: Int, val attributeNames: Set<String>)

    /**
     * §5.6 v1.26 top gap: a chip row directly under the pass head takes `space_lg` itself, because
     * there is no search field above it to supply that gap.
     */
    private val chipRowsUnderThePassHead = setOf("activity_staff_assigned_events")

    /**
     * Every other chip-row layout with the gap it actually ships. The value travels with the name so
     * changing a screen's spacing fails here instead of silently outgrowing its comment.
     */
    private val chipRowSpacingExceptions = mapOf(
        "activity_user_events" to R.dimen.space_lg, // search row above
        "activity_organizer_events" to R.dimen.space_8, // search row above
        "activity_user_registered_events" to R.dimen.space_lg, // search row above
        "activity_search_attendees" to R.dimen.space_12, // search field above
        "activity_admin_event_requests" to R.dimen.space_12, // search row above, as paddingTop
        "activity_user_rewards" to R.dimen.space_24, // scrolled content, under a section heading
    )

    /**
     * Layouts that declare a `Flow` but are not filter chip rows. Named so a new Flow cannot appear
     * undeclared; the code-built host is covered by the reports/transaction tests instead.
     */
    private val nonFilterChipFlows = mapOf(
        "view_chip_flow" to "the code-built chip-row host that chipFlow() inflates",
        "activity_user_dashboard" to "the attendee quick-action strip: Chip.Action + flow_wrapMode=aligned",
    )

    /** Every layout in `res/layout` that declares a Flow, read from source so a new one is named. */
    private fun layoutsDeclaringAFlow(): Set<String> {
        val dir = listOf(File("src/main/res/layout"), File("app/src/main/res/layout"))
            .firstOrNull { it.isDirectory }
            ?: error("res/layout not found from ${File(".").absolutePath}")
        return dir.listFiles { f -> f.extension == "xml" }
            .orEmpty()
            .filter { it.readText().contains("<androidx.constraintlayout.helper.widget.Flow") }
            .map { it.nameWithoutExtension }
            .toSet()
    }

    /** Reads the compiled layout's Flow element, so the check covers the XML as shipped. */
    private fun flowDeclaration(layoutRes: Int): FlowDecl {
        val parser = context.resources.getLayout(layoutRes)
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG &&
                    parser.name == "androidx.constraintlayout.helper.widget.Flow"
                ) {
                    val names = (0 until parser.attributeCount).map { parser.getAttributeName(it) }.toSet()
                    return FlowDecl(parser.styleAttribute, names)
                }
            }
        } finally {
            parser.close()
        }
        error("no Flow in layout $layoutRes")
    }

    @Test
    fun everyLayoutDeclaringAFlowIsRegisteredAsAChipRowOrANamedNonFilterFlow() {
        // Read from source, not from this map: a new Flow fails here with its own file name.
        assertEquals(
            "a layout that declares a Flow must be a chip row or a named non-filter Flow",
            chipRowLayouts.keys + nonFilterChipFlows.keys,
            layoutsDeclaringAFlow(),
        )
        // Every chip row picks a side of the gap rule, so neither list can silently miss a screen.
        assertEquals(
            "chip rows are either head-adjacent or a documented exception",
            chipRowLayouts.keys,
            chipRowsUnderThePassHead + chipRowSpacingExceptions.keys,
        )
    }

    @Test
    fun everyChipRowFlowUsesTheSharedStyleAndNeverCapsTheLineCount() {
        chipRowLayouts.forEach { (name, layout) ->
            val flow = flowDeclaration(layout)
            assertEquals("$name Flow style", R.style.Widget_EventQR_Ui_ChipFlow, flow.styleRes)
            assertFalse(
                "$name must not cap the chips per line (flow_maxElementsWrap)",
                "flow_maxElementsWrap" in flow.attributeNames,
            )
            // Layout attributes do not resolve from a style, so the Flow keeps its own geometry.
            assertTrue("$name Flow keeps layout_width", "layout_width" in flow.attributeNames)
        }
    }

    @Test
    fun chipRowDirectlyUnderThePassHeadTakesSpaceLgFromTheHead() {
        val expected = context.resources.getDimensionPixelSize(R.dimen.space_lg)
        chipRowsUnderThePassHead.forEach { name ->
            val root = LayoutInflater.from(themed()).inflate(chipRowLayouts.getValue(name), null)
            val container = root.findFlow()?.parent as? View ?: error("$name has no Flow")
            val margin = (container.layoutParams as ViewGroup.MarginLayoutParams).topMargin
            // The docs name layout_marginTop, so the margin itself carries the gap ...
            assertEquals("$name chip row layout_marginTop", expected, margin)
            // ... and the row owns no padding: a padded row would double the inset, not move it.
            assertEquals("$name chip row paddingTop", 0, container.paddingTop)
            assertEquals("$name chip row paddingBottom", 0, container.paddingBottom)
            assertEquals("$name chip row top gap", expected, margin + container.paddingTop)
        }
    }

    @Test
    fun chipRowSpacingExceptionsKeepTheGapTheyShipToday() {
        chipRowSpacingExceptions.forEach { (name, dimen) ->
            val root = LayoutInflater.from(themed()).inflate(chipRowLayouts.getValue(name), null)
            val container = root.findFlow()?.parent as? View ?: error("$name has no Flow")
            val margin = (container.layoutParams as ViewGroup.MarginLayoutParams).topMargin
            assertEquals(
                "$name gap changed: update its comment in chipRowSpacingExceptions",
                context.resources.getDimensionPixelSize(dimen),
                margin + container.paddingTop,
            )
        }
    }

    @Test
    fun staffAssignedEventsRowGapIsSymmetricAndTheSkeletonSharesIt() {
        val root = LayoutInflater.from(themed()).inflate(R.layout.activity_staff_assigned_events, null)
        val list = root.findViewById<View>(R.id.recyclerAssignedEvents)
        val host = root.findViewById<View>(R.id.swipeRefreshAssignedEvents)
        val skeleton = root.findViewById<View>(R.id.skeletonLoading)
        val container = root.findViewById<View>(R.id.chipFlow).parent as View
        val rowParams = container.layoutParams as ViewGroup.MarginLayoutParams
        val hostParams = host.layoutParams as ViewGroup.MarginLayoutParams
        val inset = context.resources.getDimensionPixelSize(R.dimen.space_6)

        // The 16dp above the first row is the host's margin, never the list's padding, so the gap
        // under the chips is one number and the skeleton needs no margin of its own to match it.
        assertEquals(
            "space_16 above the first row",
            context.resources.getDimensionPixelSize(R.dimen.space_16),
            hostParams.topMargin,
        )
        assertEquals("the list carries no top padding", 0, list.paddingTop)
        assertEquals(
            "the skeleton starts on the first row's y",
            0,
            (skeleton.layoutParams as ViewGroup.MarginLayoutParams).topMargin,
        )

        // Symmetry, in painted gaps: tear -> 36dp fill must equal 36dp fill -> first row.
        val headGap = rowParams.topMargin + inset
        val listGap = inset + hostParams.topMargin
        assertEquals("head gap equals list gap", listGap, headGap)
        assertEquals(
            "head gap is space_lg plus the chip inset",
            context.resources.getDimensionPixelSize(R.dimen.space_lg) + inset,
            headGap,
        )

        // Pull-to-refresh geometry is untouched by the spacing move.
        assertEquals(
            "the list still pads its bottom",
            context.resources.getDimensionPixelSize(R.dimen.space_24),
            list.paddingBottom,
        )
        assertFalse("clipToPadding stays off", (list as ViewGroup).clipToPadding)
        assertEquals("no over-scroll glow", View.OVER_SCROLL_NEVER, list.overScrollMode)
    }

    @Test
    fun theChipFlowStyleIsChainPackedStartAlignedWithTheChipGap() {
        val attrs = intArrayOf(
            androidx.constraintlayout.widget.R.attr.flow_wrapMode,
            androidx.constraintlayout.widget.R.attr.flow_horizontalStyle,
            androidx.constraintlayout.widget.R.attr.flow_horizontalBias,
            androidx.constraintlayout.widget.R.attr.flow_horizontalGap,
            androidx.constraintlayout.widget.R.attr.flow_verticalGap,
        )
        val a = ContextThemeWrapper(context, R.style.Theme_EventQR).theme
            .obtainStyledAttributes(R.style.Widget_EventQR_Ui_ChipFlow, attrs)
        try {
            assertEquals("wrapMode chain", WRAP_CHAIN, a.getInt(0, -1))
            assertEquals("horizontalStyle packed", CHAIN_PACKED, a.getInt(1, -1))
            assertEquals("horizontalBias 0", 0f, a.getFloat(2, -1f), 0f)
            val gap = context.resources.getDimensionPixelSize(R.dimen.ui_chip_gap)
            assertEquals("horizontalGap", gap, a.getDimensionPixelSize(3, -1))
            assertEquals("verticalGap", gap, a.getDimensionPixelSize(4, -1))
        } finally {
            a.recycle()
        }
    }

    @Test
    fun discoverChipFlowHoldsOnlyStatusChipsAndTheDateChipSharesTheSearchRow() {
        val themed = ContextThemeWrapper(context, R.style.Theme_EventQR)
        val root = LayoutInflater.from(themed).inflate(R.layout.activity_user_events, null)
        val flow = root.findViewById<Flow>(R.id.chipFlow)

        assertEquals(
            listOf(R.id.chipAll, R.id.chipThisWeek, R.id.chipRegistered),
            flow.referencedIds.toList(),
        )
        // The date chip is a selector, not a status filter: it rides the search row and the field
        // keeps the rest of the width.
        val row = root.findViewById<View>(R.id.searchRow)
        val search = root.findViewById<View>(R.id.inputEventSearch)
        assertSame(row, search.parent)
        assertSame(row, root.findViewById<View>(R.id.chipDateFilter).parent)
        val searchParams = search.layoutParams as LinearLayout.LayoutParams
        assertEquals(0, searchParams.width)
        assertEquals(1f, searchParams.weight, 0f)
        // The Date chip is a 48dp target like its siblings and paints through the inset drawable.
        val touch = context.resources.getDimensionPixelSize(R.dimen.ui_touch_min)
        val date = root.findViewById<TextView>(R.id.chipDateFilter)
        assertEquals(touch, date.minHeight)
        assertTrue(date.background is InsetDrawable)
    }

    @Test
    fun organizerEventsKeepsTheInsetFillWhenTheSelectionMoves() {
        val activity = Robolectric.buildActivity(ManageEventsActivity::class.java).create().start().resume().get()
        val ids = listOf(R.id.chipAll, R.id.chipUpcoming, R.id.chipActive, R.id.chipCompleted)
        val chips = ids.map { activity.findViewById<TextView>(it) }
        val flow = activity.findViewById<Flow>(R.id.chipFlow)
        assertEquals(ids, flow.referencedIds.toList())

        ids.forEachIndexed { index, id ->
            activity.findViewById<View>(id).performClick()
            chips.forEachIndexed { other, chip ->
                assertEquals("only chip $index is selected", other == index, chip.isSelected)
                // D3: a swapped bg_ui_chip_selected painted the fill at the full 48dp target.
                assertTrue("chip $other keeps bg_ui_chip_inset", chip.background is InsetDrawable)
            }
        }
    }

    @Test
    fun filterChipsExposeAButtonRoleAndTheirSelectedState() {
        val themed = ContextThemeWrapper(context, R.style.Theme_EventQR)
        val chip = TextView(themed).apply { asFilterChip() }

        val rest = AccessibilityNodeInfo.obtain().also { chip.onInitializeAccessibilityNodeInfo(it) }
        assertEquals(Button::class.java.name, rest.className.toString())
        assertFalse(rest.isSelected)

        chip.isSelected = true
        val picked = AccessibilityNodeInfo.obtain().also { chip.onInitializeAccessibilityNodeInfo(it) }
        assertEquals(Button::class.java.name, picked.className.toString())
        assertTrue(picked.isSelected)
        assertNotEquals(rest.isSelected, picked.isSelected)
    }

    // ---- label colours: the redundant setTextColor calls are gone, so the selector must carry them ----

    private fun themed() = ContextThemeWrapper(context, R.style.Theme_EventQR)

    private fun role(view: View): String {
        val info = AccessibilityNodeInfo.obtain()
        view.onInitializeAccessibilityNodeInfo(info)
        return info.className.toString()
    }

    private fun nodeSelected(view: View): Boolean {
        val info = AccessibilityNodeInfo.obtain()
        view.onInitializeAccessibilityNodeInfo(info)
        return info.isSelected
    }

    /** Selected = `ui_on_primary`, unselected = `ui_text_primary`, both as the selector says and as painted. */
    private fun assertLabelColours(chip: TextView) {
        val onPrimary = themed().getColor(R.color.ui_on_primary)
        val textPrimary = themed().getColor(R.color.ui_text_primary)
        val enabled = android.R.attr.state_enabled
        val selected = android.R.attr.state_selected
        assertEquals(textPrimary, chip.textColors.getColorForState(intArrayOf(enabled), 0))
        assertEquals(onPrimary, chip.textColors.getColorForState(intArrayOf(enabled, selected), 0))

        chip.isSelected = false
        assertEquals("unselected label", textPrimary, chip.currentTextColor)
        chip.isSelected = true
        assertEquals("selected label", onPrimary, chip.currentTextColor)
        chip.isSelected = false
    }

    @Test
    fun xmlChipLabelsFollowTheSelectedState() {
        val inflater = LayoutInflater.from(themed())
        val organizer = inflater.inflate(R.layout.activity_organizer_events, null)
        assertLabelColours(organizer.findViewById(R.id.chipCompleted))
        val search = inflater.inflate(R.layout.activity_search_attendees, null)
        assertLabelColours(search.findViewById(R.id.chipCancelled))
        val discover = inflater.inflate(R.layout.activity_user_events, null)
        assertLabelColours(discover.findViewById(R.id.chipDateFilter))
    }

    @Test
    fun searchAttendeesChipsPaintThroughTheInsetFillAndTakeSelection() {
        val root = LayoutInflater.from(themed()).inflate(R.layout.activity_search_attendees, null)
        val ids = listOf(R.id.chipAll, R.id.chipRegistered, R.id.chipCheckedIn, R.id.chipExited, R.id.chipCancelled)
        ids.forEach { id ->
            val chip = root.findViewById<TextView>(id)
            assertTrue(chip.background is InsetDrawable)
            chip.isSelected = true
            assertTrue(chip.isSelected)
            assertTrue("selection keeps the inset fill", chip.background is InsetDrawable)
        }
    }

    // ---- code-built chips: chip() and chipFlow() ----

    @Test
    fun programmaticChipPaintsThroughTheInsetFillAndStateIsIsSelectedOnly() {
        val activity = Robolectric.buildActivity(ManageEventsActivity::class.java).create().get()
        val chip = activity.chip("Approved", active = false)

        assertTrue(chip.background is InsetDrawable)
        assertTrue(chip.isClickable)
        assertEquals(context.resources.getDimensionPixelSize(R.dimen.ui_touch_min), chip.minHeight)
        assertEquals(Button::class.java.name, role(chip))
        assertFalse(chip.isSelected)
        assertFalse(nodeSelected(chip))

        chip.isSelected = true
        assertTrue(chip.isSelected)
        assertTrue(nodeSelected(chip))
        assertTrue("selection must not swap the background", chip.background is InsetDrawable)
        assertTrue(activity.chip("On", active = true).isSelected)
        assertLabelColours(chip)
    }

    @Test
    fun chipFlowHostsTheChipsOnTheSharedFlowAndNeverScrolls() {
        val activity = Robolectric.buildActivity(ManageEventsActivity::class.java).create().get()
        val chips = listOf("A", "B", "C").map { activity.chip(it) }
        val host = activity.chipFlow(chips)
        val flow = host.findViewById<Flow>(R.id.chipFlow)

        assertEquals(chips.map { it.id }, flow.referencedIds.toList())
        chips.forEach { assertSame(host, it.parent) }
        assertFalse("a chip row never scrolls", host.containsScroller())
    }

    private fun View.containsScroller(): Boolean =
        this is HorizontalScrollView ||
            (this is ViewGroup && (0 until childCount).any { getChildAt(it).containsScroller() })

    private fun View.findFlow(): Flow? {
        if (this is Flow) return this
        if (this is ViewGroup) {
            for (i in 0 until childCount) getChildAt(i).findFlow()?.let { return it }
        }
        return null
    }

    @Test
    fun reportsStatusRowIsAWrappingFlowWhoseSelectionIsIsSelectedOnly() {
        val activity = Robolectric.buildActivity(EventReportsActivity::class.java).create().get()
        val picked = mutableListOf<Any>()
        val selector = activity.statusSelector { picked += it }

        val flow = selector.findFlow()!!
        assertFalse("the status row never scrolls", selector.containsScroller())
        val host = flow.parent as ViewGroup
        val chips = (0 until host.childCount).map { host.getChildAt(it) }.filterIsInstance<TextView>()
        assertEquals(3, chips.size)
        assertEquals(chips.map { it.id }, flow.referencedIds.toList())
        assertEquals(listOf(true, false, false), chips.map { it.isSelected })

        chips[2].performClick()
        assertEquals(listOf(false, false, true), chips.map { it.isSelected })
        assertEquals(1, picked.size)
        chips.forEach { assertTrue("click keeps the inset fill", it.background is InsetDrawable) }
        assertLabelColours(chips[1])
    }

    @Test
    fun transactionRulesPurposeIsASelectFieldNotAChipRow() {
        val activity = Robolectric.buildActivity(TransactionRulesActivity::class.java).create().get()
        activity.buildScreen(
            event = testOrganizerEvent(),
            loadedPurposes = listOf(
                OrganizerMvpScanPurpose(
                    label = "Entry", description = "", enabled = true, duplicateRule = "",
                    trackingOnly = false, pointsEnabled = false, pointsValue = 0,
                    requiredSelectionLabel = "", id = UUID.randomUUID().toString(),
                ),
            ),
            loadedRules = emptyList(),
        )

        // One rule per scan purpose is an exclusive single choice: a select field, never a chip Flow.
        assertNull(activity.window.decorView.findFlow())
        assertNotNull(activity.findViewById<View>(R.id.txr_purpose_field))
    }

    @Test
    fun rewardsSectionSwitcherChipsAreButtonsAndAvailableIsSelected() {
        val activity = Robolectric.buildActivity(AttendeeRewardsActivity::class.java).create().get()
        val available = activity.findViewById<TextView>(R.id.chipAvailable)
        val claimed = activity.findViewById<TextView>(R.id.txtMyClaims)
        val history = activity.findViewById<TextView>(R.id.txtRewardsHistory)

        listOf(available, claimed, history).forEach { assertEquals(Button::class.java.name, role(it)) }
        assertTrue(available.isSelected)
        assertFalse(claimed.isSelected)
        // setTextColor is gone: the label colour comes from the selector alone.
        assertEquals(themed().getColor(R.color.ui_on_primary), available.currentTextColor)
        assertEquals(themed().getColor(R.color.ui_text_primary), claimed.currentTextColor)
    }
}
