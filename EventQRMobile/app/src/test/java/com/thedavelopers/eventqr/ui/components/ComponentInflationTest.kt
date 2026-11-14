package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import java.io.File
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.organizer.events.DiscardChangesDialog
import com.thedavelopers.eventqr.features.organizer.events.OrganizerEventHeadBinder
import com.thedavelopers.eventqr.features.organizer.events.organizerEventDetailStackedRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.ui.icons.FieldIcons
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import android.os.Looper
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

    /**
     * The real screens inflate under an [AppCompatActivity], whose factory is what applies the
     * compat drawable attributes (`app:drawableStartCompat`).
     */
    private fun appCompatInflate(layoutRes: Int): View =
        Robolectric.buildActivity(AppCompatActivity::class.java).create().get()
            .layoutInflater.inflate(layoutRes, null, false)

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
     * organizer screens carry the same request control outside `EventQrEmptyState`, so it survives
     * a populated, empty and failed list.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun organizerScreens_carryTheRequestActionOutsideTheEmptyState() {
        val context = themedContext()
        val touchMin = context.resources.getDimensionPixelSize(R.dimen.ui_touch_min)
        val inlineHeight = context.resources.getDimensionPixelSize(R.dimen.ui_button_height_inline)
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

            // v1.39: Request event is a real control, not a section action. It must read as one: a
            // Button, the inline 48dp floor, no glyph in any compound slot, and a filled secondary
            // paint, so it can never pass for the plain text sitting next to it.
            assertTrue(
                "$host/btnRequestEvent must be a Button, not a text-only section action",
                action is Button,
            )
            assertEquals(
                "$host/btnRequestEvent must sit on the inline 48dp floor",
                inlineHeight,
                action.minimumHeight,
            )
            action.compoundDrawablesRelative.forEachIndexed { index, drawable ->
                assertNull(
                    "$host/btnRequestEvent carries a glyph in the ${glyphSlots[index]} slot",
                    drawable,
                )
            }
            val requestBackground = requireNotNull(action.background) {
                "$host/btnRequestEvent has no secondary fill"
            }
            val requestBitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            requestBackground.setState(action.drawableState)
            requestBackground.setBounds(0, 0, 8, 8)
            requestBackground.draw(Canvas(requestBitmap))
            assertNotEquals(
                "$host/btnRequestEvent must paint a filled control boundary",
                0,
                Color.alpha(requestBitmap.getPixel(4, 4)),
            )

            // See all stays the section action (dashboard only): text only, no glyph in any compound
            // slot, zero horizontal padding (a chip pads its box by 8-12dp; the section action's zero
            // padding is what puts the label on the gutter), and the 48dp floor.
            val actionViews = listOfNotNull(root.findViewById<android.widget.TextView>(R.id.btnSeeAllEvents))
            for (view in actionViews) {
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
     * §6.4/§6.7: Add staff is the pinned footer action, not a chip in an event strip. One primary
     * button, wrap_content height over the 52dp floor so it grows at fontScale 2.0, and the strip
     * ids are gone for good.
     */
    @Test
    fun staffAssignment_addStaffIsAPinnedPrimaryFooter() {
        val context = themedContext()
        val root = inflate(R.layout.activity_staff_assignment, LinearLayout(context)) as ViewGroup

        // Pinned: the footer is the root's last child, full bleed, hidden until a load lands.
        val footer = root.getChildAt(root.childCount - 1)
        assertEquals("the footer must be the last child of the screen", R.id.staffAddFooter, footer.id)
        assertEquals("the footer starts hidden until a load lands", View.GONE, footer.visibility)
        assertEquals(
            "the footer must span the screen",
            ViewGroup.LayoutParams.MATCH_PARENT,
            footer.layoutParams.width,
        )

        val stack = footer as ViewGroup
        assertEquals("the footer is a hairline over one button", 2, stack.childCount)
        assertEquals(
            "the footer must sit on ui_surface",
            androidx.core.content.ContextCompat.getColor(context, R.color.ui_surface),
            (footer.background as android.graphics.drawable.ColorDrawable).color,
        )
        val hairline = stack.getChildAt(0)
        assertEquals(
            "the footer's top rule must be the 1dp hairline",
            context.resources.getDimensionPixelSize(R.dimen.stroke_hairline),
            hairline.layoutParams.height,
        )
        assertEquals(
            "the footer's top rule must be ui_hairline",
            androidx.core.content.ContextCompat.getColor(context, R.color.ui_hairline),
            (hairline.background as android.graphics.drawable.ColorDrawable).color,
        )

        val button = stack.getChildAt(1) as Button
        assertEquals(R.id.btnAddStaff, button.id)
        assertEquals(
            "the footer button must not pin a fixed height",
            ViewGroup.LayoutParams.WRAP_CONTENT,
            button.layoutParams.height,
        )
        assertTrue(
            "the footer button must clear the 52dp primary floor",
            button.minimumHeight >= context.resources.getDimensionPixelSize(R.dimen.ui_button_height),
        )
        assertEquals(context.getString(R.string.ui_organizer_add_staff), button.text.toString())
        assertEquals(
            "the footer button must wear the primary style",
            R.drawable.bg_ui_button_primary,
            org.robolectric.Shadows.shadowOf(button.background).createdFromResId,
        )
        val gutter = context.resources.getDimensionPixelSize(R.dimen.ui_gutter)
        val margins = button.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals("the footer button must sit on the gutter", gutter, margins.marginStart)
        assertEquals("the footer button must sit on the gutter", gutter, margins.marginEnd)
        assertEquals(
            "the footer button must clear the hairline by space_12",
            context.resources.getDimensionPixelSize(R.dimen.space_12),
            margins.topMargin,
        )

        // The list above the footer is the only flexible child; the footer never scrolls away.
        val list = root.findViewById<View>(R.id.swipeRefreshStaffAssignment)
        assertEquals(
            "the list must take the leftover height",
            1f,
            (list.layoutParams as LinearLayout.LayoutParams).weight,
        )
        assertTrue(
            "the footer must sit below the list",
            root.indexOfChild(list) < root.indexOfChild(footer),
        )

        // The production inflater is AppCompat's, so the compat drawable attribute lands on the button.
        val compatButton = appCompatInflate(R.layout.activity_staff_assignment)
            .findViewById<Button>(R.id.btnAddStaff)
        FieldIcons.apply(compatButton)
        val glyph = compatButton.compoundDrawablesRelative[0]
        assertNotNull("the footer button lost its leading glyph", glyph)
        assertEquals(
            "the footer button's glyph must be ic_ui_add",
            R.drawable.ic_ui_add,
            org.robolectric.Shadows.shadowOf(glyph).createdFromResId,
        )
        assertEquals(
            "the glyph must be pinned to the 20dp field ink",
            context.resources.getDimensionPixelSize(R.dimen.ui_field_icon),
            glyph.bounds.width(),
        )
        assertEquals(
            "the glyph must take ui_on_primary",
            androidx.core.content.ContextCompat.getColor(context, R.color.ui_on_primary),
            androidx.core.widget.TextViewCompat.getCompoundDrawableTintList(compatButton)?.defaultColor,
        )

        val ids = root.descendantIdNames(context)
        val stripIds = listOf("eventStrip", "eventStripTitle", "eventStripAction")
        assertTrue(
            "the event strip is still in the roster: ${ids.filter { it in stripIds }}",
            stripIds.none { it in ids },
        )
    }

    /** §6.7: the pinned footer takes the larger type instead of clipping its label. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(fontScale = 2.0f)
    fun staffAssignment_footerGrowsAtFontScale2() {
        val context = themedContext()
        val root = inflate(R.layout.activity_staff_assignment, LinearLayout(context))
        val footer = root.findViewById<View>(R.id.staffAddFooter)
        footer.visibility = View.VISIBLE
        val button = root.findViewById<Button>(R.id.btnAddStaff)
        // A label that needs two lines at 2.0: a fixed 52dp box would clip it instead of growing.
        button.text = "Add staff to this event now"

        measureAndLayout(root, (360 * context.resources.displayMetrics.density).toInt())

        val floor = context.resources.getDimensionPixelSize(R.dimen.ui_button_height)
        assertTrue(
            "button measured ${button.measuredHeight}px: a fixed 52dp box would stay at ${floor}px",
            button.measuredHeight > floor,
        )
        assertTrue(
            "footer ${footer.measuredHeight}px clipped its ${button.measuredHeight}px button",
            footer.measuredHeight >= button.measuredHeight,
        )
    }

    /** §6.4: Add staff is its own screen now; it carries no event strip either. */
    @Test
    fun searchUserAccount_hasNoEventStrip() {
        val context = themedContext()
        val root = inflate(R.layout.activity_search_user_account, LinearLayout(context))

        val ids = root.descendantIdNames(context)
        val stripIds = listOf("eventStrip", "eventStripTitle", "eventStripAction")
        assertTrue(
            "the add-staff screen still carries an event strip: ${ids.filter { it in stripIds }}",
            stripIds.none { it in ids },
        )
        assertNotNull("the add-staff screen lost its search field", root.findViewById<View>(R.id.edtSearchUser))
        assertNotNull("the add-staff screen lost its footer action", root.findViewById<View>(R.id.btnAssignStaff))
    }

    private fun View.descendantIdNames(context: Context): List<String> {
        val names = mutableListOf<String>()
        if (id != View.NO_ID) names += context.resources.getResourceEntryName(id)
        if (this is ViewGroup) {
            for (i in 0 until childCount) names += getChildAt(i).descendantIdNames(context)
        }
        return names
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
        // and row1 (44dp min), and as the Manage event hub's stub (emh_stats_row).
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

    /**
     * §5.16: the one in-flow error strip. Fixed ids are the host contract; the geometry is the
     * spec's (48dp floor, ghost Retry at the inline floor). The host fills the sentence through
     * [showInlineError], which also announces it - a live region would stay silent on a repeat.
     */
    @Test
    fun errorStrip_inflatesAndShowsTheHostSentence() {
        val context = themedContext()
        val root = inflate(R.layout.include_error_strip, LinearLayout(context))
        val message = root.findViewById<android.widget.TextView>(R.id.txtErrorStrip)
        val retry = root.findViewById<android.widget.TextView>(R.id.btnErrorStripRetry)

        assertEquals("the strip starts hidden", View.GONE, root.visibility)
        assertEquals(
            "the strip clears the 48dp touch floor",
            context.resources.getDimensionPixelSize(R.dimen.ui_touch_min),
            root.minimumHeight,
        )
        assertEquals(context.getString(R.string.ui_attendee_retry), retry.text.toString())
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_button_height_inline),
            retry.minimumHeight,
        )

        root.showInlineError("Couldn't load.")
        assertEquals(View.VISIBLE, root.visibility)
        assertEquals("Couldn't load.", message.text.toString())
        // TalkBack must hear a Button, matching the Compose mirror's Role.Button.
        val info = android.view.accessibility.AccessibilityNodeInfo.obtain()
        retry.onInitializeAccessibilityNodeInfo(info)
        assertEquals(android.widget.Button::class.java.name, info.className.toString())
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
     * Quick actions (§6.2): three hug-width chips in a wrapping Flow, 8dp apart, starting on the
     * gutter. `My passes` is the only entry point to the retired Registered tab; `Discover` stays
     * the Events destination in the always-visible bottom nav. No chip may run past the gutter.
     */
    @Test
    fun dashboardQuickActions_areHugWidthChipsInsideTheGutter() {
        val context = themedContext()
        val root = LayoutInflater.from(context)
            .inflate(R.layout.activity_user_dashboard, LinearLayout(context), false)
        val chips = listOf(R.id.btnMyPasses, R.id.btnTransactionHistory, R.id.btnRequestEventCard)
            .map { root.findViewById<View>(it) }
        val row = chips.first().parent as ViewGroup
        val density = context.resources.displayMetrics.density
        val gutter = context.resources.getDimensionPixelSize(R.dimen.ui_gutter)
        val screenWidth = (360f * density).toInt()

        row.measure(
            View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)

        assertEquals("three chips only", 3, chips.size)
        assertEquals("the strip starts on the left gutter", gutter, chips[0].left)
        assertTrue(
            "chips hug their labels instead of filling equal columns: ${chips.map { it.measuredWidth }}",
            chips.all { it.measuredWidth < screenWidth / 3 },
        )
        assertTrue(
            "no chip runs past the right gutter: ${chips.map { it.right }}",
            chips.all { it.right <= screenWidth - gutter },
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
     * §5.14 one object, one anatomy: `activity_qr_display` is the one canonical pass layout, used
     * by both entry points; the shared pass plus the save/brightness controls. The duplicate
     * details card, the never-visible registration-id load plumbing and the second layout are
     * deleted, not hidden.
     */
    @Test
    fun qrDisplay_inflatesTheSharedPassWithoutTheDuplicateCardOrPlumbing() {
        val context = themedContext()
        val root = inflate(R.layout.activity_qr_display, LinearLayout(context))

        assertNotNull(root.findViewById<View>(R.id.layoutQrPassContent))
        assertNotNull(root.findViewById<View>(R.id.qrCredential))
        assertNotNull(root.findViewById<View>(R.id.btnMarkQrDownloaded))
        assertNotNull(root.findViewById<View>(R.id.switchKeepBright))
        assertEquals(
            "the second QR layout must not exist: activity_qr_display is canonical",
            0,
            context.resources.getIdentifier("activity_qr_credential", "layout", context.packageName),
        )
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
     * §4.6 per-event identity head: the name with the badge as the title row's trailing value, and
     * the tear as the dark block's LAST child (as a sibling it painted on `ui_background` at
     * ~1.01:1 and was invisible). The head is identity only; venue and schedule are table rows.
     */
    @Test
    fun organizerEventHead_bindsNameAndStatus() {
        val context = themedContext()
        val root = inflate(R.layout.include_organizer_event_head, LinearLayout(context))
        OrganizerEventHeadBinder.bind(
            root,
            title = "DevFest Manila 2026",
            status = EventStatus.APPROVED,
        )

        assertEquals(
            "DevFest Manila 2026",
            (root.findViewById<View>(R.id.oeHeadTitle) as android.widget.TextView).text.toString(),
        )
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.oeHeadStatus).visibility)

        val head = root as ViewGroup
        assertEquals(
            "the tear must be the head's last child",
            R.id.oeHeadTear,
            head.getChildAt(head.childCount - 1).id,
        )
        listOf("oeHeadWhen", "oeHeadWhere").forEach { gone ->
            assertEquals(
                "$gone must be deleted: venue and schedule are spec-table rows",
                0,
                context.resources.getIdentifier(gone, "id", context.packageName),
            )
        }

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

    /** No status hides the badge; the title still wraps rather than taking a fixed container (§9.3). */
    @Test
    fun organizerEventHead_hidesAbsentStatusAndKeepsTitleWrapContent() {
        val context = themedContext()
        val root = inflate(R.layout.include_organizer_event_head, LinearLayout(context))
        OrganizerEventHeadBinder.bind(
            root,
            title = "DevFest Manila 2026",
            status = null,
        )

        assertEquals(View.GONE, root.findViewById<View>(R.id.oeHeadStatus).visibility)
        assertEquals(
            "the title must be wrap_content, not a fixed text container",
            ViewGroup.LayoutParams.WRAP_CONTENT,
            root.findViewById<View>(R.id.oeHeadTitle).layoutParams.height,
        )
        assertEquals(
            3,
            (root.findViewById<View>(R.id.oeHeadTitle) as android.widget.TextView).maxLines,
        )
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
     * One head, two hosts (§6.4): the read-only details page and the editor both bind
     * `showStatus = false`, because each renders the badge in its own read-only STATUS row. Both
     * inflate the same include, so there is no second header layout to drift.
     */
    @Test
    fun organizerEventHead_bothHostsHideTheBadgeAndKeepTheTitleAlone() {
        val context = themedContext()

        val details = inflate(R.layout.activity_organizer_event_details, LinearLayout(context))
        OrganizerEventHeadBinder.bind(
            details.findViewById(R.id.oeHead),
            title = "DevFest Manila 2026",
            status = EventStatus.ACTIVE,
            showStatus = false,
        )
        assertEquals(View.GONE, details.findViewById<View>(R.id.oeHeadStatus).visibility)
        assertEquals(
            "the details title row reads as the name alone",
            "DevFest Manila 2026",
            details.findViewById<View>(R.id.oeHeadTitleRow).contentDescription.toString(),
        )
        val titleParams = details.findViewById<View>(R.id.oeHeadTitle).layoutParams as LinearLayout.LayoutParams
        assertEquals("the title takes the row", 0, titleParams.width)
        assertEquals(1f, titleParams.weight, 0f)

        val editor = inflate(R.layout.activity_organizer_event_edit, LinearLayout(context))
        OrganizerEventHeadBinder.bind(
            editor.findViewById(R.id.oeHead),
            title = "DevFest Manila 2026",
            status = EventStatus.ACTIVE,
            showStatus = false,
        )
        assertEquals(
            "the editor header is the title alone: the badge is the STATUS row",
            View.GONE,
            editor.findViewById<View>(R.id.oeHeadStatus).visibility,
        )
        assertEquals(
            "DevFest Manila 2026",
            editor.findViewById<View>(R.id.oeHeadTitleRow).contentDescription.toString(),
        )
        val editorTitleParams =
            editor.findViewById<View>(R.id.oeHeadTitle).layoutParams as LinearLayout.LayoutParams
        assertEquals("the editor title takes the full row", 0, editorTitleParams.width)
        assertEquals(1f, editorTitleParams.weight, 0f)
    }

    /**
     * The view page's five blocks in order (§6.4 v1.20): top bar, scroll holding head -> spec table
     * -> review note -> actions, plus the skeleton that replaces the shipped
     * `visibility="invisible"` blank. Venue and schedule are LOCATION / EVENT TIME rows in the table,
     * so the old Date / Time / Venue ids stay deleted. The stats band is Manage event's stub only,
     * so its ids are gone here too.
     */
    @Test
    fun organizerEventDetails_hasTheFiveBlockOrderAndLocationAndTimeRows() {
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

        assertEquals("four blocks: head, table, review note, actions", 4, column.childCount)
        assertEquals("block 2 is the shared head", R.id.oeHead, column.getChildAt(0).id)
        assertEquals("block 4 is the review note", R.id.oedReviewNote, column.getChildAt(2).id)

        // No stats band: Registered / Capacity / Available are the Manage event hub's stub, and
        // repeating them here said the same three numbers twice (§6.4 v1.20).
        listOf("oedStub", "oedStatRegistered", "oedStatCapacity", "oedStatAvailable").forEach { gone ->
            assertEquals(
                "$gone must stay deleted: the stats band lives on Manage event",
                0,
                context.resources.getIdentifier(gone, "id", context.packageName),
            )
        }

        // Table: seven row blocks, each followed by its hairline. STATUS leads (the badge the header
        // no longer carries), location and event time are rows, and About takes the same treatment
        // with the body under its label.
        val table = column.getChildAt(1) as ViewGroup
        assertEquals("seven rows plus seven hairlines", 14, table.childCount)
        assertEquals(R.id.oedRowStatus, table.getChildAt(0).id)
        assertEquals(R.id.oedRowLocation, table.getChildAt(2).id)
        assertEquals(R.id.oedRowEventTime, table.getChildAt(4).id)
        assertEquals(R.id.oedRowRegistration, table.getChildAt(6).id)
        assertEquals(R.id.oedAboutBlock, table.getChildAt(8).id)
        assertEquals(R.id.oedRowOrganizer, table.getChildAt(10).id)
        assertEquals(R.id.oedRowCoOrganizers, table.getChildAt(12).id)
        assertEquals(R.id.oedDividerCoOrganizers, table.getChildAt(13).id)
        // Both directions: the table's order is STATUS, LOCATION, EVENT TIME, REGISTRATION WINDOW,
        // About, ORGANIZER, CO-ORGANIZERS (the one row order both event details use), every row is
        // followed by its own hairline, and the (row, value) pairs are exactly the production
        // stacking list, in order, so a dropped divider, row or value id fails here with its name.
        val expectedTable = listOf(
            R.id.oedRowStatus to R.id.oedDividerStatus,
            R.id.oedRowLocation to R.id.oedDividerLocation,
            R.id.oedRowEventTime to R.id.oedDividerEventTime,
            R.id.oedRowRegistration to R.id.oedDividerRegistration,
            R.id.oedAboutBlock to R.id.oedDividerAbout,
            R.id.oedRowOrganizer to R.id.oedDividerOrganizer,
            R.id.oedRowCoOrganizers to R.id.oedDividerCoOrganizers,
        )
        expectedTable.forEachIndexed { index, (rowId, dividerId) ->
            val name = context.resources.getResourceEntryName(rowId)
            assertEquals("$name row", rowId, table.getChildAt(index * 2).id)
            assertEquals("$name hairline", dividerId, table.getChildAt(index * 2 + 1).id)
        }
        assertEquals("every table child is a row or its hairline", 14, expectedTable.size * 2)
        val tablePairs = (0 until table.childCount)
            .filter { it % 2 == 0 }
            .map { table.getChildAt(it) as ViewGroup }
            .filter { it.id != R.id.oedAboutBlock }
            .map { row -> row.id to row.getChildAt(1).id }
        assertEquals(
            "the inflated table's (row, value) pairs are the production stacking list, in order",
            organizerEventDetailStackedRows,
            tablePairs,
        )
        // Positive control: the lookup resolves, so the absence checks below cannot pass vacuously.
        assertNotEquals(
            0,
            context.resources.getIdentifier("oeHeadTitle", "id", context.packageName),
        )
        listOf("oedRowDate", "oedRowTime", "oedRowVenue").forEach { gone ->
            assertEquals(
                "$gone must stay deleted: venue and schedule are LOCATION and EVENT TIME rows",
                0,
                context.resources.getIdentifier(gone, "id", context.packageName),
            )
        }

        // Actions: primary first, ghost beneath, 8dp apart, both full width - not an equal pair.
        val actions = column.getChildAt(3) as ViewGroup
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

        // Skeleton: head + seven rows in their own geometry, never an invisible blank and never a
        // band for a block the loaded page does not have.
        assertEquals(
            context.getString(R.string.ui_skeleton_loading),
            skeleton.contentDescription.toString(),
        )
        assertEquals("head plate + row stack", 2, skeleton.childCount)
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_organizer_head_skeleton_height),
            skeleton.getChildAt(0).layoutParams.height,
        )
        val skeletonRows = skeleton.getChildAt(1) as ViewGroup
        assertEquals("one skeleton row per loaded table row", 7, skeletonRows.childCount)
        for (index in 0 until skeletonRows.childCount) {
            assertEquals(
                context.resources.getDimensionPixelSize(R.dimen.ui_metric_row_height),
                skeletonRows.getChildAt(index).minimumHeight,
            )
        }
    }

    /**
     * The skeleton head plate is the height the loaded head lands on at the typical 2-line title
     * (§5.11): 16 top + 2 x 34 + 16 + the 1dp tear = 101dp against a 100dp token, inside the 4dp
     * tolerance. The real head is measured too, and its line count decides whether that comparison
     * applies: host font metrics may wrap the sample differently, and the arithmetic assertion below
     * still pins the token.
     */
    @Test
    fun organizerEventDetails_headSkeletonPlateMatchesTheTwoLineHead() {
        val context = themedContext()
        val token = context.resources.getDimensionPixelSize(R.dimen.ui_organizer_head_skeleton_height)
        val density = context.resources.displayMetrics.density
        val screenWidth = (360f * density).toInt()
        val tolerance = (4 * density).toInt()

        val twoLineArithmetic = context.resources.getDimensionPixelSize(R.dimen.space_16) +
            2 * (34f * density).toInt() +
            context.resources.getDimensionPixelSize(R.dimen.space_16) +
            context.resources.getDimensionPixelSize(R.dimen.stroke_hairline)
        assertTrue(
            "token is ${token}px, 2-line head arithmetic is ${twoLineArithmetic}px (101dp)",
            abs(token - twoLineArithmetic) <= tolerance,
        )

        val head = LayoutInflater.from(context)
            .inflate(R.layout.include_organizer_event_head, LinearLayout(context), false) as ViewGroup
        val title = head.findViewById<View>(R.id.oeHeadTitle) as android.widget.TextView
        title.text = "DevFest Manila 2026 for Students and Professionals"
        head.measure(
            View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        if (title.lineCount == 2) {
            assertTrue(
                "the 2-line head measures ${head.measuredHeight}px against a ${token}px token",
                abs(head.measuredHeight - token) <= tolerance,
            )
        }
    }

    /**
     * The band is suppressed on Event details, not deleted from the app: Manage event (the hub) still
     * carries Registered / Capacity / Available on the pass-stub step, so the two screens no longer
     * print the same three numbers and the hub stays the place that owns them (§6.4 v1.20).
     */
    @Test
    fun manageEvent_stillCarriesTheStatsBandThatDetailsDropped() {
        val context = themedContext()

        inflate(R.layout.activity_organizer_event_details, LinearLayout(context))
        assertEquals(
            "Event details must have no stats band",
            0,
            context.resources.getIdentifier("oedStub", "id", context.packageName),
        )

        val hub = inflate(R.layout.activity_event_management_hub, LinearLayout(context)) as ViewGroup
        val stats = hub.findViewById<View>(R.id.emh_stats_row)
        assertNotNull("Manage event must keep the stats band", stats)
        assertEquals(View.VISIBLE, stats.visibility)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, stats.layoutParams.height)
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_pass_head_stub_height),
            stats.minimumHeight,
        )

        // Three slots, two hairlines between them; the labels are Registered / Capacity / Available.
        val statsRow = stats as ViewGroup
        val labels = (0 until statsRow.childCount).mapNotNull { index ->
            val cell = statsRow.getChildAt(index) as? ViewGroup ?: return@mapNotNull null
            val label = cell.getChildAt(1)
            assertTrue(
                "cell $index second child must be the label TextView, not ${label?.javaClass}",
                label is android.widget.TextView,
            )
            (label as android.widget.TextView).text.toString()
        }
        assertEquals(
            listOf(
                context.getString(R.string.event_management_hub_stat_registered),
                context.getString(R.string.event_management_hub_stat_capacity),
                context.getString(R.string.event_management_hub_stat_available),
            ),
            labels,
        )
        listOf(R.id.emh_stat_registered, R.id.emh_stat_capacity, R.id.emh_stat_available)
            .forEach { assertNotNull(hub.findViewById<View>(it)) }
    }

    /**
     * §6.4/§9.3 stacking, the same path the attendee REGISTRATION WINDOW row uses: above
     * `fontScale` 1.3 the label sits over a value beneath it, `space_4` under the label. The rows
     * come from the production list (`organizerEventDetailStackedRows`), so the screen and this test
     * cannot drift; STATUS, ORGANIZER and CO-ORGANIZERS must be on it. Each row is measured: a
     * weighted `0dp` label would measure to 0 in a vertical row, so params alone are not enough.
     */
    @Test
    @Config(
        sdk = [35],
        shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class],
        fontScale = 2.0f,
    )
    fun organizerMetricRows_aboveTheFontScaleThreshold_stackWithAVisibleLabel() {
        val context = themedContext()
        val root = inflate(R.layout.activity_organizer_event_details, LinearLayout(context)) as ViewGroup
        val density = context.resources.displayMetrics.density
        val gutter = context.resources.getDimensionPixelSize(R.dimen.ui_gutter)
        // The page gutter is not available to the row, so measure at the width the row really gets.
        val rowWidth = (360f * density).toInt() - 2 * gutter

        listOf(
            R.id.oedRowOrganizer to R.id.oedOrganizerValue,
            R.id.oedRowCoOrganizers to R.id.oedCoOrganizersValue,
        ).forEach { row ->
            assertTrue(
                "the production stacking list must carry the ${context.resources.getResourceEntryName(row.first)} row",
                organizerEventDetailStackedRows.contains(row),
            )
        }

        organizerEventDetailStackedRows.forEach { (rowId, valueId) ->
            val row = root.findViewById<LinearLayout>(rowId)
            val value = root.findViewById<View>(valueId) as android.widget.TextView
            row.visibility = View.VISIBLE

            MetricRowStacking.apply(context, row, value)

            assertEquals(LinearLayout.VERTICAL, row.orientation)
            assertEquals(Gravity.START, value.gravity and Gravity.START)
            val params = value.layoutParams as LinearLayout.LayoutParams
            // The STATUS badge stacks under its label but keeps hugging; a text value fills the row.
            val fills = rowId != R.id.oedRowStatus
            assertEquals(
                if (fills) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
                params.width,
            )
            assertEquals(0f, params.weight, 0f)
            assertEquals(
                "space_4 between the label and the stacked value",
                context.resources.getDimensionPixelSize(R.dimen.space_4),
                params.topMargin,
            )

            row.measure(
                View.MeasureSpec.makeMeasureSpec(rowWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            val label = row.getChildAt(0) as android.widget.TextView
            assertTrue(
                "row $rowId label measured ${label.measuredWidth}px - a weighted 0dp label " +
                    "measures to 0 in a vertical row",
                label.measuredWidth > 0,
            )
            val expectedContentWidth = rowWidth - row.paddingStart - row.paddingEnd
            assertEquals(
                "row $rowId label fills the content width",
                expectedContentWidth,
                label.measuredWidth,
            )
            if (fills) {
                assertEquals(
                    "row $rowId value fills the content width",
                    expectedContentWidth,
                    value.measuredWidth,
                )
            } else {
                assertTrue(
                    "row $rowId badge measured ${value.measuredWidth}px",
                    value.measuredWidth in 1 until expectedContentWidth,
                )
            }
        }
    }

    @Test
    fun organizerEventTimeRow_atTheDefaultFontScale_keepsTheSharedRow() {
        val context = themedContext()
        val root = inflate(R.layout.activity_organizer_event_details, LinearLayout(context)) as ViewGroup
        val row = root.findViewById<LinearLayout>(R.id.oedRowEventTime)
        val value = root.findViewById<View>(R.id.oedEventTimeValue) as android.widget.TextView

        MetricRowStacking.apply(context, row, value)

        assertEquals(LinearLayout.HORIZONTAL, row.orientation)
        val params = value.layoutParams as LinearLayout.LayoutParams
        assertEquals(0, params.width)
        assertEquals(1f, params.weight, 0f)
    }

    /**
     * The editor (§6.4 v1.7): full-bleed head first, a read-only STATUS row opening the form column
     * (the header is the title alone), five sections headed `Widget.EventQR.Ui.SectionHeading` in
     * the view page's block order, four perforations between them, and §5.2's field anatomy -
     * labelFor at its own control, a wrap_content box on the 56dp floor.
     */
    @Test
    fun organizerEventEdit_hasTheSectionOrderAndTheFieldAnatomy() {
        val context = themedContext()
        val root = inflate(R.layout.activity_organizer_event_edit, LinearLayout(context)) as ViewGroup

        val scroll = root.getChildAt(1) as ScrollView
        val column = scroll.getChildAt(0) as LinearLayout
        assertEquals("the head is full-bleed: no padding on the scroll column", 0, column.paddingStart)
        assertEquals("block 1 of the column is the shared head", R.id.oeHead, column.getChildAt(0).id)
        // The head is identity only: no when/where lines to hide (venue is a field, schedule a row).
        listOf("oeHeadWhen", "oeHeadWhere").forEach { gone ->
            assertEquals(
                "$gone must be deleted from the shared head",
                0,
                context.resources.getIdentifier(gone, "id", context.packageName),
            )
        }

        val form = column.getChildAt(1) as ViewGroup
        assertEquals("STATUS row + hairline + locked note + 5 sections + 4 perforations", 12, form.childCount)
        // The badge is not a header ornament here: STATUS is the first thing in the form column.
        assertEquals(R.id.eeStatusRow, form.getChildAt(0).id)
        assertEquals(R.id.eeDividerStatus, form.getChildAt(1).id)
        assertEquals(R.id.eeSectionEvent, form.getChildAt(3).id)
        assertEquals(R.id.eeSectionSchedule, form.getChildAt(5).id)
        assertEquals(R.id.eeSectionRegistration, form.getChildAt(7).id)
        assertEquals(R.id.eeSectionAbout, form.getChildAt(9).id)
        assertEquals(R.id.eeSectionBanner, form.getChildAt(11).id)

        // Same anatomy as EVENT DATES / REGISTRATION WINDOW on this screen, badge on the trailing edge.
        val statusRow = root.findViewById<ViewGroup>(R.id.eeStatusRow)
        assertEquals(LinearLayout.HORIZONTAL, (statusRow as LinearLayout).orientation)
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.ui_metric_row_height),
            statusRow.minimumHeight,
        )
        val statusLabel = statusRow.getChildAt(0) as android.widget.TextView
        assertEquals(context.getString(R.string.ui_organizer_detail_status), statusLabel.text.toString())
        assertEquals(
            "the value hugs its label instead of filling the row",
            ViewGroup.LayoutParams.WRAP_CONTENT,
            (statusRow.getChildAt(1) as View).layoutParams.width,
        )

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
        listOf(
            R.id.eeStatusRow to R.id.eeStatusValue,
            R.id.eeEventDatesRow to R.id.eeEventDatesValue,
            R.id.eeWindowRow to R.id.eeWindowValue,
        )
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
        val dialog = DiscardChangesDialog.create(context, onDiscard = { discarded = true })
        dialog.show()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            context.getString(R.string.ui_organizer_edit_discard_title),
            dialog.findViewById<android.widget.TextView>(R.id.dialogTitle)?.text.toString(),
        )
        assertEquals(
            "the window title is what TalkBack reads as the dialog opens",
            context.getString(R.string.ui_organizer_edit_discard_title),
            dialog.window?.attributes?.title?.toString(),
        )
        assertEquals(
            context.getString(R.string.ui_organizer_edit_discard_body),
            dialog.findViewById<android.widget.TextView>(R.id.dialogBody)?.text.toString(),
        )
        val keep = dialog.findViewById<Button>(R.id.dialogNegative)!!
        assertTrue("the safe action takes focus, so a stray Enter never discards", keep.isFocused)
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

        val second = DiscardChangesDialog.create(context, onDiscard = { discarded = true })
        second.show()
        second.findViewById<Button>(R.id.dialogPositive)?.performClick()
        assertTrue("Discard must point at the discard callback", discarded)
        assertFalse(second.isShowing)
    }

    /** One component, per-screen copy: the caller may replace the body, the labels stay shared. */
    @Test
    fun discardChangesDialog_takesTheCallersBodyAndKeepsTheSharedLabels() {
        val context = themedContext()
        val body = context.getString(R.string.transaction_rules_discard_switch_body, "Entry", "Booth")
        val dialog = DiscardChangesDialog.create(context, onDiscard = {}, body = body)
        dialog.show()

        assertEquals(body, dialog.findViewById<android.widget.TextView>(R.id.dialogBody)?.text.toString())
        assertEquals(
            context.getString(R.string.ui_organizer_edit_discard_title),
            dialog.findViewById<android.widget.TextView>(R.id.dialogTitle)?.text.toString(),
        )
        assertEquals(
            context.getString(R.string.ui_organizer_edit_keep_editing),
            dialog.findViewById<Button>(R.id.dialogNegative)?.text.toString(),
        )
        assertEquals(
            "at the default scale the two actions share one end-aligned row",
            LinearLayout.HORIZONTAL,
            dialog.findViewById<LinearLayout>(R.id.dialogActions)?.orientation,
        )
    }

    /** §5.12: above fontScale 1.3 the actions stack, Discard last, and the panel still fits 360x640. */
    @Test
    @Config(
        sdk = [35],
        shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class],
        qualifiers = "w360dp-h640dp",
        fontScale = 2.0f,
    )
    fun discardChangesDialog_stacksItsActionsAtLargeType() {
        val context = themedContext()
        val dialog = DiscardChangesDialog.create(
            context,
            onDiscard = {},
            body = context.getString(R.string.transaction_rules_discard_switch_body, "Entry", "Booth"),
        )
        dialog.show()

        val actions = dialog.findViewById<LinearLayout>(R.id.dialogActions)!!
        val commit = dialog.findViewById<Button>(R.id.dialogPositive)!!
        assertEquals(LinearLayout.VERTICAL, actions.orientation)
        // `dialog_base` writes `end`, so the resolved value is the legacy 5, not `Gravity.END`
        // (8388613). Compare the horizontal component of each, which is what alignment means.
        assertEquals(
            "still end-aligned",
            Gravity.END and Gravity.HORIZONTAL_GRAVITY_MASK,
            actions.gravity and Gravity.HORIZONTAL_GRAVITY_MASK,
        )
        assertSame("Discard is the bottom row", commit, actions.getChildAt(actions.childCount - 1))
        val params = commit.layoutParams as LinearLayout.LayoutParams
        assertEquals("the row's start margin has no meaning in a column", 0, params.marginStart)
        assertEquals(
            context.resources.getDimensionPixelSize(R.dimen.space_8),
            params.topMargin,
        )

        // The dialog window is min(320dp, screen - 40dp): 320dp on a 360dp screen.
        val density = context.resources.displayMetrics.density
        val screenHeight = (640 * density).toInt()
        val root = dialog.findViewById<View>(R.id.dialogRoot)!!
        root.measure(
            View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screenHeight, View.MeasureSpec.AT_MOST),
        )
        val fitted = root.measuredHeight
        assertTrue(
            "the dialog was ${fitted}px of ${screenHeight}px at fontScale 2.0",
            fitted <= screenHeight,
        )
        // `AlertController` overwrites the custom view's layout params with match_parent, so the
        // XML's wrap_content is not observable here. Measure with no height bound instead: the
        // panel must be exactly as tall as its text, which is the §9.3 invariant.
        root.measure(
            View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        assertEquals(
            "the panel is as tall as its text, not pinned or clipped by a fixed height",
            fitted,
            root.measuredHeight,
        )
    }

    /**
     * Attendee profile (§6.2 v1.31): one scrolling surface with the identity block inside it, the
     * two sections built from the shared `list_row_icon` rows, and no role pill in the tree.
     *
     * The bindings themselves are driven through the real activity in `AttendeeProfileScreenTest`;
     * this pass is the cheap structural gate - a row that goes back to hand-built XML, or a block
     * that returns above the ScrollView, fails here on inflate.
     */
    @Test
    fun attendeeProfile_scrollsAsOneSurfaceAndUsesTheSharedRow() {
        val context = themedContext()
        val root = inflate(R.layout.activity_attendee_profile, LinearLayout(context))

        val header = root.findViewById<View>(R.id.headerProfile)
        assertNotNull("the profile lost its identity block", header)
        var node: View? = header
        var insideScroll = false
        while (node != null) {
            if (node is ScrollView) insideScroll = true
            node = node.parent as? View
        }
        assertTrue("the identity block must scroll with the page", insideScroll)

        assertEquals(
            "the role pill is deleted",
            0,
            context.resources.getIdentifier("txtProfileRole", "id", context.packageName),
        )

        val activityRows = root.findViewById<ViewGroup>(R.id.cardActivityMenu).getChildAt(0) as ViewGroup
        val accountRows = root.findViewById<ViewGroup>(R.id.cardAccountMenu).getChildAt(0) as ViewGroup
        assertEquals(
            "ACTIVITY is one card of three rows",
            listOf(R.id.rowTransactionHistory, R.id.rowClaimedRewards, R.id.rowMyEventRequests),
            (0 until activityRows.childCount).map { activityRows.getChildAt(it).id },
        )
        assertEquals(
            "ACCOUNT is one card of Edit profile only",
            listOf(R.id.rowEditProfile),
            (0 until accountRows.childCount).map { accountRows.getChildAt(it).id },
        )
        // Every row is the shared component: a 40dp tile, one trailing slot, one hairline.
        (0 until activityRows.childCount).forEach { index ->
            val row = activityRows.getChildAt(index)
            assertNotNull("row $index is not list_row_icon", row.findViewById<View>(R.id.listRowTile))
            assertNotNull("row $index lost its trailing slot", row.findViewById<View>(R.id.listRowTrailing))
        }

        assertNotNull(root.findViewById<View>(R.id.eyebrowActivity))
        assertNotNull(root.findViewById<View>(R.id.eyebrowAccount))
        assertNotNull(root.findViewById<View>(R.id.txtProfileVersion))
    }

    // ---- Form section headings (plan §3, §5.2, §6.7 v1.32) ----------------------------------

    /** The six forms: the only layouts where a section heading sits directly above a field label. */
    private val formLayouts = listOf(
        R.layout.activity_request_event,
        R.layout.activity_organizer_event_edit,
        R.layout.activity_edit_profile,
        R.layout.activity_signup,
        R.layout.activity_create_admin_account,
        R.layout.activity_change_password,
    )

    private fun resLayoutDir(): File =
        listOf(File("src/main/res/layout"), File("app/src/main/res/layout"))
            .firstOrNull { it.isDirectory }
            ?: error("res/layout not found from ${File(".").absolutePath}")

    private fun layoutSource(name: String): String = File(resLayoutDir(), "$name.xml").readText()

    private fun headingsIn(root: View): List<TextView> {
        val found = mutableListOf<TextView>()
        fun visit(view: View) {
            if (view is TextView && view.isAccessibilityHeading) found += view
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(root)
        return found
    }

    /**
     * §5.2 v1.32: a form section heading takes the `title` role - 18sp sentence case in primary ink -
     * because the field label under it is the 11sp caps overline. Identical type two lines apart is
     * the defect this replaces, so the heading is also a TalkBack heading and carries no caps.
     */
    @Test
    fun formSectionHeadings_areTalkBackHeadingsAtTheTitleStep_andNeverCaps() {
        val context = themedContext()
        val titlePx = context.resources.getDimension(R.dimen.ui_type_title)
        val styleUse = Regex("style=\"@style/Widget\\.EventQR\\.Ui\\.SectionHeading\"")

        formLayouts.forEach { layout ->
            val name = context.resources.getResourceEntryName(layout)
            val source = layoutSource(name)
            val declared = styleUse.findAll(source).count()
            assertTrue("$name declares no section heading", declared > 0)

            val headings = headingsIn(appCompatInflate(layout))
            assertEquals(
                "$name: one heading view per SectionHeading style use",
                declared,
                headings.size,
            )
            headings.forEach { heading ->
                assertEquals(
                    "$name heading must be the title step, not the overline",
                    titlePx,
                    heading.textSize,
                    0.5f,
                )
                assertFalse("$name heading must not be all caps", heading.isAllCaps)
                assertFalse(
                    "$name heading text is caps: ${heading.text}",
                    heading.text.toString() == heading.text.toString().uppercase(),
                )
            }

            // No per-view type override: the style is the only place the type is stated.
            Regex("<TextView[^>]*" + styleUse.pattern + "[^>]*>").findAll(source).forEach { view ->
                listOf(
                    "android:textSize", "android:textColor", "android:textStyle",
                    "android:fontFamily", "android:textAllCaps",
                    "android:maxLines", "android:ellipsize",
                ).forEach { attribute ->
                    assertFalse("$name heading overrides $attribute", view.value.contains(attribute))
                }
            }
        }
    }

    /**
     * An eyebrow and a field label are the same 11sp caps line, so they may never share a form as
     * peers: the heading takes the title role and the label keeps the overline. Every layout that
     * still carries both is named here with its reason, so a new mixed layout fails with its file
     * name and a stale entry fails too.
     */
    private val eyebrowWithFieldLabelAllowed = mapOf(
        "activity_forgot_password" to "STEP 1 OF 3 is the auth step eyebrow, above the page title",
        "activity_reset_password" to "STEP 2 OF 3 is the auth step eyebrow, above the page title",
        "activity_new_password" to "STEP 3 OF 3 is the auth step eyebrow, above the page title",
        "activity_staff_scanner" to "EVENT / PURPOSE are the captured-scope labels in the result block, not a section head",
        "activity_user_rewards" to "BALANCE is a pass-head stub label and AVAILABLE REWARDS heads a chip-row list",
    )

    @Test
    fun noLayoutPairsAnEyebrowWithAFieldLabelUnlessItIsNamed() {
        // Both families: the type appearances and the widget style, so a label written as
        // Widget.EventQR.Ui.FieldLabel escapes the gate no more than one written as Type.FieldLabel.
        val eyebrowRef = Regex("""(TextAppearance|Widget)\.EventQR\.[\w.]*Eyebrow""")
        val fieldLabelRef = Regex("""(TextAppearance|Widget)\.EventQR\.[\w.]*FieldLabel""")
        val mixed = resLayoutDir().listFiles { file -> file.extension == "xml" }
            .orEmpty()
            .filter { file ->
                val source = file.readText()
                eyebrowRef.containsMatchIn(source) && fieldLabelRef.containsMatchIn(source)
            }
            .map { it.nameWithoutExtension }
            .toSet()

        assertEquals(eyebrowWithFieldLabelAllowed.keys, mixed)
    }
}
