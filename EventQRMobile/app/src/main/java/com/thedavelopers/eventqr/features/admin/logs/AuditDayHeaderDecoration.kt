package com.thedavelopers.eventqr.features.admin.logs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.admin.AuditDayGroup
import java.time.LocalDate
import java.util.Locale

/**
 * Sticky audit day headers (§6.5). The list stays a flat list of rows; this decoration reserves a
 * 32dp band above the first row of each day and paints the current day's header pinned to the top
 * of the RecyclerView - under the chip row, which sits outside the RecyclerView.
 *
 * [submit] replaces the header set from the accumulated page list and invalidates the decoration
 * itself, so a caller cannot forget. An unchanged set is a no-op: appending a page can never
 * duplicate a header or cause a redraw.
 *
 * Open only for the painting seam ([drawHeader]), which the Robolectric draw test records.
 */
open class AuditDayHeaderDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val headerHeight = context.resources.getDimensionPixelSize(R.dimen.ui_audit_day_header_height)
    private val startInset = context.resources.getDimensionPixelSize(R.dimen.ui_row_start)
    private val isRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

    private val background = Paint().apply {
        color = ContextCompat.getColor(context, R.color.ui_background)
    }

    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ui_text_secondary)
        typeface = ResourcesCompat.getFont(context, R.font.ibm_plex_sans_semibold)
        textSize = context.resources.getDimension(R.dimen.ui_type_overline)
        letterSpacing = ResourcesCompat.getFloat(context.resources, R.dimen.ui_track_overline)
    }

    /** The submitted groups and their rendering date, for the "unchanged is a no-op" check. */
    private var groups: List<AuditDayGroup> = emptyList()
    private var today: LocalDate? = null

    /** Adapter positions of the group starts, ascending, with the rendered label at each. */
    private var headerPositions: IntArray = IntArray(0)
    private var headerLabels: Array<String> = emptyArray()

    private var recyclerView: RecyclerView? = null

    /**
     * Adds the decoration to [recyclerView] and keeps the reference, so [submit] can invalidate the
     * list itself. The pair lives on one screen, so the reference cannot outlive the view.
     */
    fun attachTo(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        recyclerView.addItemDecoration(this)
    }

    /** Replaces the whole header set. Same input twice does nothing; a new set invalidates itself. */
    fun submit(groups: List<AuditDayGroup>, today: LocalDate) {
        if (groups == this.groups && today == this.today) return
        this.groups = groups
        this.today = today
        headerPositions = IntArray(groups.size) { groups[it].startIndex }
        headerLabels = Array(groups.size) { DateFormatters.auditDayLabel(groups[it].day, today).uppercase(Locale.ENGLISH) }
        recyclerView?.invalidateItemDecorations()
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.top = if (headerIndexAt(parent.getChildAdapterPosition(view)) >= 0) headerHeight else 0
    }

    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (headerPositions.isEmpty() || parent.childCount == 0) return
        val firstPosition = parent.getChildAdapterPosition(parent.getChildAt(0))
        if (firstPosition == RecyclerView.NO_POSITION) return

        // The owner of the top edge: the last group that starts at or before the first visible row.
        // Its start row may already be detached, which is why it is drawn outside the child loop.
        val search = headerPositions.binarySearch(firstPosition)
        val pinnedIndex = if (search >= 0) search else -search - 2

        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            val headerIndex = headerIndexAt(position)
            if (headerIndex < 0 || headerIndex == pinnedIndex) continue
            drawHeader(canvas, child.top - headerHeight, headerLabels[headerIndex])
        }

        if (pinnedIndex >= 0) drawHeader(canvas, pinnedTop(parent, pinnedIndex), headerLabels[pinnedIndex])
    }

    /**
     * Pinned header y: the top edge, pushed up by the next group's header as it arrives. The push
     * reads the next group's start row, which is attached exactly when the collision can happen.
     */
    private fun pinnedTop(parent: RecyclerView, pinnedIndex: Int): Int {
        var top = parent.paddingTop
        attachedTop(parent, headerPositions[pinnedIndex])?.let { top = maxOf(top, it - headerHeight) }
        val nextIndex = pinnedIndex + 1
        if (nextIndex < headerPositions.size) {
            attachedTop(parent, headerPositions[nextIndex])?.let { top = minOf(top, it - headerHeight) }
        }
        return top
    }

    private fun attachedTop(parent: RecyclerView, position: Int): Int? =
        parent.findViewHolderForAdapterPosition(position)?.itemView?.takeIf { it.isAttachedToWindow }?.top

    private fun headerIndexAt(position: Int): Int = headerPositions.binarySearch(position)

    /** Painting seam: production draws to the canvas, tests record the calls. */
    protected open fun drawHeader(canvas: Canvas, top: Int, label: String) {
        canvas.drawRect(0f, top.toFloat(), canvas.width.toFloat(), (top + headerHeight).toFloat(), background)
        val baseline = top + (headerHeight - (text.descent() + text.ascent())) / 2f
        val x = if (isRtl) canvas.width - startInset - text.measureText(label) else startInset.toFloat()
        canvas.drawText(label, x, baseline, text)
    }
}
