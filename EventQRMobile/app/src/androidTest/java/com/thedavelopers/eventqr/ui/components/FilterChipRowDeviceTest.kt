package com.thedavelopers.eventqr.ui.components

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.icons.FieldIcons
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The chip-row recipe on real hardware graphics (§5.6): a 360dp host at `fontScale` 1.0 (asserted) and 1.05-1.2 (logged).
 * Robolectric's legacy graphics report one pixel per character, so only a device proves where a
 * packed `Flow` really breaks the line. The Discover `Date` chip is measured as `Date` (the
 * default, asserted to stay on line 1) and as `Nov 2026` / `12 Nov 2026` (logged only: a long
 * applied value wraps to the start of line 2 by design). The 1.0 cases have little slack: at 360dp
 * the attendee `Date` chip ends 1dp inside the 340dp limit, so a wider label or a larger glyph moves
 * it to line 2. At 1.2 only a loose bound is asserted (at most two chip lines). Bounds and a PNG per case are written to the app's external files dir
 * (`Android/data/<package>/files/chip-rows`) and logged under the tag `ChipRowDevice`.
 */
@RunWith(AndroidJUnit4::class)
class FilterChipRowDeviceTest {

    private val target: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun themed(fontScale: Float): Context {
        val config = Configuration(target.resources.configuration).apply {
            this.fontScale = fontScale
            screenWidthDp = HOST_WIDTH_DP
        }
        return ContextThemeWrapper(target.createConfigurationContext(config), R.style.Theme_EventQR)
    }

    class Laid(val root: View, val chips: List<TextView>, val context: Context)

    private fun layOut(
        layout: Int,
        chipIds: List<Int>,
        fontScale: Float,
        tag: String,
        dateLabel: String = "Date",
    ): Laid {
        lateinit var laid: Laid
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = themed(fontScale)
            val root = LayoutInflater.from(context).inflate(layout, null, false)
            // The Compose bottom nav needs a window; it is not part of the chip-row geometry.
            root.findViewById<View>(R.id.composeBottomNav)?.let { (it.parent as ViewGroup).removeView(it) }
            val chips = chipIds.map { root.findViewById<TextView>(it) }
            chips.filter { it.id == R.id.chipDateFilter }.forEach {
                FieldIcons.apply(it, R.dimen.ui_chevron)
                // Relative drawables resolve with the layout direction, which an unattached view
                // never resolves on its own; without this the trailing chevron is not measured.
                it.layoutDirection = View.LAYOUT_DIRECTION_LTR
                it.text = dateLabel
            }
            val density = context.resources.displayMetrics.density
            val width = (HOST_WIDTH_DP * density).toInt()
            val height = (HOST_HEIGHT_DP * density).toInt()
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, width, height)
            laid = Laid(root, chips, context)
            chips.firstOrNull()?.isSelected = true
            capture(root, width, height, tag)
        }
        return laid
    }

    private fun capture(root: View, width: Int, height: Int, tag: String) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File(target.getExternalFilesDir(null), "chip-rows").apply { mkdirs() }
        File(dir, "$tag.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Top of each chip relative to the Flow's host, so two chips on one line share a value. */
    private fun tops(chips: List<TextView>): List<Int> = chips.map { (it.parent as ViewGroup).top + it.top }

    private fun log(tag: String, chips: List<TextView>) {
        val density = chips.first().resources.displayMetrics.density
        val rows = chips.joinToString { c ->
            "${c.text}@(${(c.left / density).toInt()},${(((c.parent as ViewGroup).top + c.top) / density).toInt()}) " +
                "${(c.width / density).toInt()}x${(c.height / density).toInt()}dp"
        }
        Log.i("ChipRowDevice", "$tag: $rows")
    }

    private val organizerIds = listOf(R.id.chipAll, R.id.chipUpcoming, R.id.chipActive, R.id.chipCompleted)
    private val attendeeIds = listOf(R.id.chipAll, R.id.chipThisWeek, R.id.chipRegistered, R.id.chipDateFilter)

    @Test
    fun organizerChips_shareOneLine_at360dp_fontScale1() = organizerOneLine(1.0f, "organizer-1.00")

    /** Above 1.0 the four labels no longer fit 320dp: the packed Flow wraps only the chip that overflows. */
    @Test
    fun organizerChips_logWhereTheyLand_atLargerFontScales() {
        listOf(1.05f, 1.1f, 1.15f, 1.2f).forEach { scale ->
            val tag = "organizer-$scale"
            val laid = layOut(R.layout.activity_organizer_events, organizerIds, scale, tag)
            log(tag, laid.chips)
            assertTrue("at most two chip lines at $scale", tops(laid.chips).toSet().size <= 2)
        }
    }

    private fun organizerOneLine(scale: Float, tag: String) {
        val laid = layOut(R.layout.activity_organizer_events, organizerIds, scale, tag)
        log(tag, laid.chips)
        assertEquals("four chips on one line", 1, tops(laid.chips).toSet().size)
        // Start-aligned: the first chip sits at the 20dp gutter, not centred or spread.
        val gutter = laid.context.resources.getDimensionPixelSize(R.dimen.ui_gutter)
        assertEquals(gutter, laid.chips.first().left)
    }

    @Test
    fun attendeeDate_staysOnLineOne_at360dp_fontScale1_0() {
        attendeeDate(1.0f, "attendee-1.00", "Date", assertLineOne = true)
    }

    @Test
    fun attendeeAppliedValues_logWhereDateLands() {
        listOf(1.05f, 1.1f, 1.15f, 1.2f).forEach { scale ->
            val laid = attendeeDate(scale, "attendee-$scale-Date", "Date", assertLineOne = false)
            assertTrue("at most two chip lines at $scale", tops(laid.chips).toSet().size <= 2)
        }
        listOf(1.0f, 1.05f, 1.15f).forEach { scale ->
            listOf("Nov 2026", "12 Nov 2026").forEach { label ->
                attendeeDate(scale, "attendee-${scale}-${label.replace(' ', '_')}", label, assertLineOne = false)
            }
        }
    }

    private fun attendeeDate(scale: Float, tag: String, dateLabel: String, assertLineOne: Boolean): Laid {
        val laid = layOut(R.layout.activity_user_events, attendeeIds, scale, tag, dateLabel)
        log(tag, laid.chips)
        val tops = tops(laid.chips)
        if (assertLineOne) assertTrue("Date wrapped to a second line: tops=$tops", tops.toSet().size == 1)
        // Each chip is a 48dp target; the 36dp fill is painted inside it by bg_ui_chip_inset.
        assertTrue(laid.chips.all { it.height >= laid.context.resources.getDimensionPixelSize(R.dimen.ui_touch_min) })
        return laid
    }
}

private const val HOST_WIDTH_DP = 360
private const val HOST_HEIGHT_DP = 780
