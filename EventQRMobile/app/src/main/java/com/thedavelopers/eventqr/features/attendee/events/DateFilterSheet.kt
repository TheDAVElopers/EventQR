package com.thedavelopers.eventqr.features.attendee

import android.app.DatePickerDialog
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.ui.components.asFilterChip
import com.thedavelopers.eventqr.ui.components.asFilterChips
import com.thedavelopers.eventqr.ui.theme.dimenOffset
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * The date filter sheet (§6.2 Discover amendment), on the shared `sheet_bottom_base` anatomy.
 *
 * Three modes - Any date / Month / Exact day. The draft is local: [show] seeds it, Reset rewinds
 * it, Apply commits it through [onApply], and a dismiss throws it away. Apply is disabled while
 * Exact day has no valid day, because the filter cannot describe an empty window.
 */
class DateFilterSheet(private val context: Context) {

    private val sheet: View =
        LayoutInflater.from(context).inflate(R.layout.sheet_bottom_base, null, false)
    private val content: View = LayoutInflater.from(context)
        .inflate(
            R.layout.view_date_filter_content,
            sheet.findViewById<FrameLayout>(R.id.sheetContent),
            false,
        )

    private val primaryAction: Button = sheet.findViewById(R.id.sheetPrimaryAction)
    private val secondaryAction: Button = sheet.findViewById(R.id.sheetSecondaryAction)

    private val chipModeAny: TextView = content.findViewById(R.id.chipModeAny)
    private val chipModeMonth: TextView = content.findViewById(R.id.chipModeMonth)
    private val chipModeDay: TextView = content.findViewById(R.id.chipModeDay)
    private val lblMonth: TextView = content.findViewById(R.id.lblMonth)
    private val monthChipGrid: LinearLayout = content.findViewById(R.id.monthChipGrid)
    private val lblYear: TextView = content.findViewById(R.id.lblYear)
    private val yearScroll: HorizontalScrollView = content.findViewById(R.id.yearScroll)
    private val yearChipRow: LinearLayout = content.findViewById(R.id.yearChipRow)
    private val lblDay: TextView = content.findViewById(R.id.lblDay)
    private val btnPickDay: Button = content.findViewById(R.id.btnPickDay)

    private val monthChips = mutableListOf<TextView>()
    private val yearChips = mutableMapOf<Int, TextView>()

    private var dialog: BottomSheetDialog? = null
    private var picker: DatePickerDialog? = null
    private var onApply: ((DiscoverDateFilter?) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null

    /** The uncommitted draft. Readable by hosts/tests; the sheet owns every write. */
    var draft: DateFilterDraft = DateFilterDraft.current()
        private set

    /** The sheet root, already composed. Hosts that need to inspect it (tests) use this. */
    val view: View get() = sheet

    val isShowing: Boolean get() = dialog?.isShowing == true

    init {
        sheet.findViewById<TextView>(R.id.sheetTitle).text =
            context.getString(R.string.ui_attendee_filter_date)

        primaryAction.text = context.getString(R.string.ui_attendee_filter_apply)
        primaryAction.setOnClickListener { commit() }

        secondaryAction.text = context.getString(R.string.ui_attendee_filter_reset)
        secondaryAction.visibility = View.VISIBLE
        secondaryAction.setOnClickListener {
            // Draft only: the applied filter stays until Apply.
            draft = DateFilterDraft.current()
            refresh()
        }

        sheet.findViewById<FrameLayout>(R.id.sheetContent).addView(content)

        asFilterChips(chipModeAny, chipModeMonth, chipModeDay)
        chipModeAny.setOnClickListener { setMode(DateFilterMode.ANY) }
        chipModeMonth.setOnClickListener { setMode(DateFilterMode.MONTH) }
        chipModeDay.setOnClickListener { setMode(DateFilterMode.DAY) }
        btnPickDay.setOnClickListener { pickDay() }

        buildMonthGrid()

        // The year row is wider than the sheet once several event years exist. Scroll from the
        // row's first layout: a post from refresh() runs before that measure, and
        // scrollYearIntoView() bails on width 0, so the first open never scrolled.
        yearScroll.doOnLayout { scrollYearIntoView() }
    }

    /**
     * Opens the sheet. [years] seeds the year scroller (event years + the current/applied year);
     * [onDismiss] runs after the sheet closes, which is where the host returns focus to its chip.
     * A second call while the sheet is up is a no-op.
     */
    fun show(
        draft: DateFilterDraft,
        years: List<Int>,
        onApply: (DiscoverDateFilter?) -> Unit,
        onDismiss: (() -> Unit)? = null,
    ) {
        if (dialog?.isShowing == true) return
        this.draft = draft
        this.onApply = onApply
        this.onDismiss = onDismiss
        buildYearRow(years.ifEmpty { listOf(draft.year) })
        refresh()

        val sheetDialog = BottomSheetDialog(context).apply {
            setContentView(sheet)
            setOnDismissListener {
                dialog = null
                this@DateFilterSheet.onDismiss?.invoke()
                this@DateFilterSheet.onApply = null
                this@DateFilterSheet.onDismiss = null
            }
        }
        // Assign only once the dialog is actually up: a failed show() must not leave a dead sheet
        // that blocks every later open.
        sheetDialog.show()
        dialog = sheetDialog
        // Tall content in a short window (landscape, split screen) settles collapsed on the
        // first show; the sheet is the whole interaction here, so open it fully.
        sheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    /** Closes the day picker first, then the sheet: no window outlives the host's dismiss. */
    fun dismiss() {
        picker?.dismiss()
        picker = null
        dialog?.dismiss()
    }

    private fun buildMonthGrid() {
        monthChipGrid.removeAllViews()
        monthChips.clear()
        val gap = context.dimenOffset(R.dimen.ui_chip_gap)
        for (rowIndex in 0 until 3) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = context.dimenOffset(R.dimen.ui_touch_min)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (rowIndex > 0) topMargin = context.dimenOffset(R.dimen.space_sm)
                }
            }
            for (column in 0 until 4) {
                val month = Month.of(rowIndex * 4 + column + 1)
                val chip = inflateChip()
                // Short label fits four-up on a 360dp screen; TalkBack gets the full name.
                chip.text = month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                chip.contentDescription = month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                chip.layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    if (column > 0) marginStart = gap
                }
                chip.setOnClickListener { chooseMonth(month.value) }
                monthChips += chip
                row.addView(chip)
            }
            monthChipGrid.addView(row)
        }
    }

    private fun buildYearRow(years: List<Int>) {
        yearChipRow.removeAllViews()
        yearChips.clear()
        val gap = context.dimenOffset(R.dimen.ui_chip_gap)
        years.distinct().sorted().forEachIndexed { index, year ->
            val chip = inflateChip()
            chip.text = year.toString()
            chip.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (index > 0) marginStart = gap
            }
            chip.setOnClickListener { chooseYear(year) }
            yearChips[year] = chip
            yearChipRow.addView(chip)
        }
    }

    private fun inflateChip(): TextView =
        (LayoutInflater.from(context).inflate(R.layout.chip_option, monthChipGrid, false) as TextView)
            .also { it.asFilterChip() }

    private fun setMode(mode: DateFilterMode) {
        draft = draft.copy(mode = mode)
        refresh()
        // Picking "Exact day" is the first half of the choice: open the picker bounded to the draft
        // month unless the draft already carries a valid day (then the button shows it and changes it).
        if (mode == DateFilterMode.DAY && draft.dayOrNull() == null) pickDay()
    }

    private fun chooseMonth(month: Int) {
        draft = draft.copy(mode = DateFilterMode.MONTH, month = month)
        refresh()
    }

    private fun chooseYear(year: Int) {
        draft = draft.copy(mode = DateFilterMode.MONTH, year = year)
        refresh()
    }

    /** Seeded with the draft day (or today when it falls in the draft month), bounded to it. */
    private fun pickDay() {
        if (picker?.isShowing == true) return
        val month = YearMonth.of(draft.year, draft.month)
        val today = LocalDate.now(DateFilterRanges.ZONE)
        val seed = draft.dayOrNull()
            ?: if (YearMonth.from(today) == month) today else month.atDay(1)

        val picker = DatePickerDialog(
            context,
            { _, year, monthIndex, dayOfMonth ->
                draft = draft.copy(
                    mode = DateFilterMode.DAY,
                    year = year,
                    month = monthIndex + 1,
                    day = dayOfMonth,
                )
                refresh()
            },
            seed.year,
            seed.monthValue - 1,
            seed.dayOfMonth,
        )
        // DatePicker reads its bounds as calendar dates in the device zone, not in the event zone;
        // converting through Manila would shift the first/last selectable day on a non-Manila device.
        val pickerZone = ZoneId.systemDefault()
        picker.datePicker.minDate =
            month.atDay(1).atStartOfDay(pickerZone).toInstant().toEpochMilli()
        picker.datePicker.maxDate =
            month.atEndOfMonth().atStartOfDay(pickerZone).toInstant().toEpochMilli()
        picker.setOnDismissListener { this.picker = null }
        picker.show()
        // Assign only once the picker is actually up: a failed show() must not orphan a second
        // platform picker behind a dead reference.
        this.picker = picker
    }

    private fun refresh() {
        val mode = draft.mode
        chipModeAny.isSelected = mode == DateFilterMode.ANY
        chipModeMonth.isSelected = mode == DateFilterMode.MONTH
        chipModeDay.isSelected = mode == DateFilterMode.DAY

        val showMonth = mode == DateFilterMode.MONTH
        val monthVisibility = if (showMonth) View.VISIBLE else View.GONE
        lblMonth.visibility = monthVisibility
        monthChipGrid.visibility = monthVisibility
        lblYear.visibility = monthVisibility
        yearScroll.visibility = monthVisibility

        val dayVisibility = if (mode == DateFilterMode.DAY) View.VISIBLE else View.GONE
        lblDay.visibility = dayVisibility
        btnPickDay.visibility = dayVisibility

        monthChips.forEachIndexed { index, chip -> chip.isSelected = draft.month == index + 1 }
        yearChips.forEach { (year, chip) -> chip.isSelected = draft.year == year }

        val day = draft.dayOrNull()
        btnPickDay.text = day
            ?.let { DateFormatters.formatEventDate(it.atStartOfDay(DateFilterRanges.ZONE).toInstant()) }
            ?: context.getString(R.string.ui_attendee_filter_pick_day)
        primaryAction.isEnabled = mode != DateFilterMode.DAY || day != null

        // The year row is wider than the sheet once several event years exist; keep the selected /
        // applied year in view after every refresh (mode change, year tap, sheet open). A view that
        // is not laid out yet bails here and the init doOnLayout scrolls it once it has width.
        if (showMonth) scrollYearIntoView()
    }

    private fun scrollYearIntoView() {
        val chip = yearChips[draft.year] ?: return
        if (yearScroll.width == 0 || chip.width == 0) return
        // scrollX is content-relative and chip.left is measured in that same content space in both
        // layout directions, so one expression centres the chip in LTR and RTL alike.
        yearScroll.scrollTo((chip.left - (yearScroll.width - chip.width) / 2).coerceAtLeast(0), 0)
    }

    private fun commit() {
        val filter = when (draft.mode) {
            DateFilterMode.ANY -> null
            DateFilterMode.MONTH -> DiscoverDateFilter.MonthOf(YearMonth.of(draft.year, draft.month))
            DateFilterMode.DAY -> draft.dayOrNull()?.let { DiscoverDateFilter.DayOf(it) }
        }
        onApply?.invoke(filter)
        dismiss()
    }
}
