package com.thedavelopers.eventqr.features.admin.logs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.admin.AuditDayGroup
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Sticky day headers (§6.5): the offsets must leave a 32dp band at every group start, and the
 * pinned header must still paint after its group's first row scrolls out - the regression that a
 * child-loop-only implementation hides.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AuditDayHeaderDecorationTest {

    private val context: Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_EventQR)

    private val headerHeight = context.resources.getDimensionPixelSize(R.dimen.ui_audit_day_header_height)
    private val rowHeight = context.resources.getDimensionPixelSize(R.dimen.ui_row_height_value)
    private val viewportWidth = context.resources.displayMetrics.widthPixels
    private val viewportHeight = context.resources.displayMetrics.heightPixels

    private val today = LocalDate.of(2026, 10, 12)
    private val groups = listOf(
        AuditDayGroup(LocalDate.of(2026, 10, 12), 0),
        AuditDayGroup(LocalDate.of(2026, 10, 11), 3),
    )

    private class RowAdapter(private val rows: Int, private val heightPx: Int) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = View(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)
            }
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit

        override fun getItemCount(): Int = rows
    }

    private class RecordingDecoration(context: Context) : AuditDayHeaderDecoration(context) {
        val drawn = mutableListOf<Pair<Int, String>>()

        override fun drawHeader(canvas: Canvas, top: Int, label: String) {
            drawn += top to label
        }
    }

    private fun recycler(decoration: AuditDayHeaderDecoration, rows: Int = 8): RecyclerView {
        val recycler = RecyclerView(context)
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = RowAdapter(rows, rowHeight)
        decoration.attachTo(recycler)
        return recycler
    }

    private fun layout(recycler: RecyclerView) {
        recycler.requestLayout()
        recycler.measure(
            View.MeasureSpec.makeMeasureSpec(viewportWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(viewportHeight, View.MeasureSpec.EXACTLY),
        )
        recycler.layout(0, 0, viewportWidth, viewportHeight)
    }

    private fun draw(recycler: RecyclerView) {
        recycler.draw(Canvas(Bitmap.createBitmap(viewportWidth, viewportHeight, Bitmap.Config.ARGB_8888)))
    }

    @Test
    fun groupStartsReserveTheHeaderBandAndOtherRowsDoNot() {
        val decoration = AuditDayHeaderDecoration(context)
        val recycler = recycler(decoration)
        decoration.submit(groups, today)
        layout(recycler)

        val firstRow = recycler.getChildAt(0)
        assertEquals(0, recycler.getChildAdapterPosition(firstRow))
        assertEquals(headerHeight, firstRow.top)

        val secondRow = recycler.getChildAt(1)
        assertEquals(firstRow.bottom, secondRow.top)

        val secondGroupRow = recycler.getChildAt(3)
        assertEquals(3, recycler.getChildAdapterPosition(secondGroupRow))
        assertEquals(recycler.getChildAt(2).bottom + headerHeight, secondGroupRow.top)
    }

    @Test
    fun eachVisibleGroupStartDrawsItsHeaderOnce() {
        val decoration = RecordingDecoration(context)
        val recycler = recycler(decoration)
        decoration.submit(groups, today)
        layout(recycler)

        draw(recycler)

        // The group owning the top edge is drawn pinned (last), so compare by label, not by order.
        val tops = decoration.drawn.groupBy({ it.second }, { it.first })
        assertEquals(setOf("TODAY", "YESTERDAY"), tops.keys)
        assertEquals(listOf(0), tops["TODAY"])
        assertEquals(listOf(3 * rowHeight + headerHeight), tops["YESTERDAY"])
    }

    @Test
    fun pinnedHeaderDrawsAfterItsGroupStartScrollsOut() {
        val decoration = RecordingDecoration(context)
        val recycler = recycler(decoration)
        decoration.submit(groups, today)
        layout(recycler)

        // Put row 1 directly under the pinned band, so row 0 (the 12 Oct start) is detached.
        (recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(1, headerHeight)
        layout(recycler)
        decoration.drawn.clear()

        draw(recycler)

        val pinned = decoration.drawn.filter { it.second == "TODAY" }
        assertEquals(1, pinned.size)
        assertEquals(0, pinned.single().first)
        assertEquals(1, decoration.drawn.count { it.second == "YESTERDAY" })
    }
}
