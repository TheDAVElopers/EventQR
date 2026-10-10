package com.thedavelopers.eventqr.features.organizer.reports

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportDto
import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneId

/** Strings the exported files show, resolved by the caller so this file stays free of Context. */
data class ReportExportLabels(
    val event: String,
    val generated: String,
    val summary: String,
    val category: String,
    val count: String,
    val total: String,
    val noRecords: String,
    val combinedTitle: String,
    /** "Section %1$d" */
    val sectionFormat: String,
    /** "%1$d records" */
    val recordsFormat: String,
    /** "Page %1$d of %2$d" */
    val pageFormat: String,
)

enum class ReportExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    PDF("pdf", "application/pdf"),
}

/** File names look like `tech-summit-2026_attendance_2026-10-10.pdf`: event first so a folder groups by event, ISO date last so it sorts. */
object ReportFileNames {
    private const val MAX_SLUG = 40
    private val MANILA: ZoneId = ZoneId.of("Asia/Manila")

    fun slug(value: String?): String {
        val ascii = Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return ascii.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(MAX_SLUG)
            .trim('-')
    }

    fun build(
        eventName: String?,
        reports: List<EventReportDto>,
        format: ReportExportFormat,
        date: LocalDate = LocalDate.now(MANILA),
    ): String {
        val event = slug(eventName).ifEmpty { "event" }
        val kind = if (reports.size == 1) slug(reports.first().reportType.name) else "full-report"
        return "${event}_${kind}_$date.${format.extension}"
    }
}

/** Builds the CSV and PDF files for one or more report sections. Both formats share one layout: header block, then each section's table and summary. */
class ReportExporter(
    private val labels: ReportExportLabels,
    private val eventName: String,
    private val generatedAt: String,
) {

    private fun titleOf(reports: List<EventReportDto>): String =
        if (reports.size == 1) reports.first().reportTitle?.takeIf { it.isNotBlank() } ?: labels.combinedTitle else labels.combinedTitle

    private fun sectionTitle(report: EventReportDto, index: Int): String =
        report.reportTitle?.takeIf { it.isNotBlank() } ?: labels.sectionFormat.format(index + 1)

    private fun columnsOf(report: EventReportDto): List<String> =
        report.columns.mapIndexed { i, name -> displayColumnName(report.reportType, i, name) }

    private fun cellsOf(report: EventReportDto, row: List<String?>): List<String> =
        List(report.columns.size) { row.getOrNull(it).orEmpty() }

    // ---------------------------------------------------------------- CSV

    fun csv(reports: List<EventReportDto>): ByteArray {
        val out = StringBuilder("\uFEFF") // BOM: without it Excel reads UTF-8 names as mojibake
        // Cells arrive already quoted: lit() for generated labels, data() for user-controlled text.
        fun line(vararg cells: String) {
            out.append(cells.joinToString(",")).append("\r\n")
        }
        fun lit(value: String) = csvCell(value, guardFormula = false)
        fun data(value: String) = csvCell(value, guardFormula = true)
        fun blank() { out.append("\r\n") }

        line(lit(titleOf(reports)))
        line(lit(labels.event), data(eventName))
        line(lit(labels.generated), lit(generatedAt))

        reports.forEachIndexed { index, report ->
            blank()
            if (reports.size > 1) line(lit(sectionTitle(report, index)))
            line(lit(labels.recordsFormat.format(report.rows.size)))
            blank()
            line(*columnsOf(report).map(::lit).toTypedArray())
            if (report.rows.isEmpty()) line(lit(labels.noRecords))
            report.rows.forEach { line(*cellsOf(report, it.values).map(::data).toTypedArray()) }

            val chart = chartDataOf(report, sortByValue = false)
            if (chart.entries.isNotEmpty()) {
                blank()
                line(lit(labels.summary))
                line(lit(labels.category), lit(labels.count))
                chart.entries.forEach { (name, value) -> line(data(name), lit(value.toString())) }
                line(lit(labels.total), lit(chart.total.toString()))
            }
        }
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    /** Quotes the value; with [guardFormula] also defuses spreadsheet formulas in user-controlled text. */
    private fun csvCell(value: String, guardFormula: Boolean): String {
        val safe = if (guardFormula && isFormulaLike(value)) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    /** `=`, `@`, a leading TAB/CR, and `+`/`-` that is not a number or phone number (`-cmd`, `+SUM(1)`). A bare `-` is left alone. */
    private fun isFormulaLike(value: String): Boolean {
        if (value.isEmpty()) return false
        return when (value[0]) {
            '=', '@', '\t', '\r' -> true
            '+', '-' -> value.length > 1 && !PHONE_OR_NUMBER.matches(value)
            else -> false
        }
    }

    // ---------------------------------------------------------------- PDF

    fun pdf(reports: List<EventReportDto>): ByteArray {
        // Two passes: the first only counts pages so every footer can read "Page X of Y".
        val landscape = reports.any { it.columns.size > LANDSCAPE_FROM_COLUMNS }
        val pages = PdfRenderer(landscape, totalPages = null).render(reports).second
        return PdfRenderer(landscape, totalPages = pages).render(reports).first
    }

    private inner class PdfRenderer(landscape: Boolean, private val totalPages: Int?) {
        private val pageW = if (landscape) 842 else 595
        private val pageH = if (landscape) 595 else 842
        private val margin = 36f
        private val contentW = pageW - 2 * margin
        private val bottomLimit = pageH - margin - 24f
        private val cellPadX = 5f
        private val cellPadY = 4f
        private val maxCellLines = 4

        private fun paint(sizePt: Float, hex: String, bold: Boolean = false) = Paint().apply {
            color = Color.parseColor(hex)
            textSize = sizePt
            isAntiAlias = true
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        private val titlePaint = paint(20f, "#1E1B4B", bold = true)
        private val metaPaint = paint(10f, "#6B7280")
        private val sectionPaint = paint(13f, "#3730A3", bold = true)
        private val headPaint = paint(9f, "#111827", bold = true)
        private val cellPaint = paint(9f, "#374151")
        private val mutedPaint = paint(9f, "#9CA3AF")
        private val summaryTitlePaint = paint(11f, "#3730A3", bold = true)
        private val footerPaint = paint(8f, "#9CA3AF")
        private val boldCellPaint = paint(9f, "#111827", bold = true)
        private val bannerFill = Paint().apply { color = Color.parseColor("#EEF2FF") }
        private val headFill = Paint().apply { color = Color.parseColor("#E0E7FF") }
        private val zebraFill = Paint().apply { color = Color.parseColor("#F9FAFB") }
        private val rule = Paint().apply { color = Color.parseColor("#E5E7EB"); strokeWidth = 1f }

        private val lineH = cellPaint.fontSpacing
        private val document = PdfDocument()
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f

        private val canvas get() = page!!.canvas

        /** Returns the file bytes (empty on the counting pass, [totalPages] == null) and the page count. */
        fun render(reports: List<EventReportDto>): Pair<ByteArray, Int> {
            try {
                newPage()
                canvas.drawText(titleOf(reports).ellipsize(contentW, titlePaint), margin, y + 16f, titlePaint)
                y += 36f
                canvas.drawText("${labels.event}: $eventName".ellipsize(contentW, metaPaint), margin, y, metaPaint)
                y += 14f
                canvas.drawText("${labels.generated}: $generatedAt".ellipsize(contentW, metaPaint), margin, y, metaPaint)
                y += 12f
                canvas.drawLine(margin, y, margin + contentW, y, rule)
                y += 24f

                reports.forEachIndexed { index, report -> section(report, sectionTitle(report, index)) }

                finishPage()
                val bytes = if (totalPages == null) ByteArray(0) else ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
                return bytes to pageNo
            } finally {
                document.close()
            }
        }

        private fun newPage() {
            pageNo++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            y = margin
        }

        private fun finishPage() {
            val footer = labels.pageFormat.format(pageNo, totalPages ?: pageNo)
            canvas.drawLine(margin, pageH - margin - 12f, margin + contentW, pageH - margin - 12f, rule)
            canvas.drawText(footer, margin + contentW - footerPaint.measureText(footer), pageH - margin, footerPaint)
            canvas.drawText(eventName.ellipsize(contentW - 100f, footerPaint), margin, pageH - margin, footerPaint)
            document.finishPage(page)
        }

        private fun breakPage() {
            finishPage()
            newPage()
        }

        private fun section(report: EventReportDto, title: String) {
            val columns = columnsOf(report)
            val rows = report.rows.map { cellsOf(report, it.values) }
            val widths = columnWidths(columns, rows)
            val rightAligned = columns.indices.map { c -> rows.isNotEmpty() && rows.all { r -> r[c].isBlank() || NUMERIC.matches(r[c].trim()) } && rows.any { r -> r[c].isNotBlank() } }
            val headerCells = columns.mapIndexed { c, text -> wrap(text, widths[c] - 2 * cellPadX, headPaint) }
            val headerH = if (columns.isEmpty()) 0f else headerCells.maxOf { it.size } * headPaint.fontSpacing + 2 * cellPadY
            val wrappedRows = rows.map { cells -> cells.mapIndexed { c, text -> wrap(text.ifBlank { "—" }, widths[c] - 2 * cellPadX, cellPaint) } }
            val rowHeights = wrappedRows.map { r -> r.maxOf { it.size } * lineH + 2 * cellPadY }
            val firstRowH = if (columns.isEmpty()) 0f else rowHeights.firstOrNull() ?: NO_RECORDS_H

            // Keep the banner, the column header and the first row (or the no-records line) together.
            if (y > margin && y + BANNER_H + headerH + firstRowH > bottomLimit) breakPage()
            canvas.drawRect(margin, y, margin + contentW, y + 24f, bannerFill)
            val records = labels.recordsFormat.format(rows.size)
            val recordsW = metaPaint.measureText(records)
            canvas.drawText(title.ellipsize(contentW - 16f - recordsW - 12f, sectionPaint), margin + 8f, y + 16f, sectionPaint)
            canvas.drawText(records, margin + contentW - 8f - recordsW, y + 16f, metaPaint)
            y += BANNER_H

            if (columns.isNotEmpty()) {
                fun header() {
                    canvas.drawRect(margin, y, margin + contentW, y + headerH, headFill)
                    drawCells(headerCells, widths, rightAligned, List(columns.size) { false }, y, headPaint)
                    y += headerH
                }
                header()
                if (rows.isEmpty()) {
                    canvas.drawText(labels.noRecords, margin + cellPadX, y + 10f, mutedPaint)
                    y += NO_RECORDS_H
                }
                wrappedRows.forEachIndexed { i, wrapped ->
                    val height = rowHeights[i]
                    if (y + height > bottomLimit) {
                        breakPage()
                        header()
                    }
                    if (i % 2 == 1) canvas.drawRect(margin, y, margin + contentW, y + height, zebraFill)
                    drawCells(wrapped, widths, rightAligned, rows[i].map { it.isBlank() }, y)
                    y += height
                }
                canvas.drawLine(margin, y, margin + contentW, y, rule)
                y += 18f
            }

            summary(report)
        }

        private fun drawCells(
            wrapped: List<List<String>>,
            widths: List<Float>,
            rightAligned: List<Boolean>,
            blank: List<Boolean>,
            top: Float,
            paint: Paint = cellPaint,
        ) {
            var x = margin
            wrapped.forEachIndexed { c, lines ->
                val p = if (blank[c]) mutedPaint else paint
                lines.forEachIndexed { n, text ->
                    val baseline = top + cellPadY + (n + 1) * lineH - p.descent()
                    val tx = if (rightAligned[c]) x + widths[c] - cellPadX - p.measureText(text) else x + cellPadX
                    canvas.drawText(text, tx, baseline, p)
                }
                x += widths[c]
            }
        }

        private fun summary(report: EventReportDto) {
            val chart = chartDataOf(report, sortByValue = false)
            if (chart.entries.isEmpty()) return
            val colW = minOf(contentW, 320f)
            val rowH = lineH + 2 * cellPadY
            val titleH = 18f
            val wholeH = titleH + rowH * (chart.entries.size + 2) + 16f // title, header, entries, total
            val fitsOnOnePage = wholeH <= bottomLimit - margin

            // Whole block together when it fits a page; otherwise at least title, header and one row.
            val keepTogether = if (fitsOnOnePage) wholeH else titleH + rowH * 3
            if (y > margin && y + keepTogether > bottomLimit) breakPage()

            fun draw(left: String, right: String, fill: Paint?, paint: Paint) {
                if (fill != null) canvas.drawRect(margin, y, margin + colW, y + rowH, fill)
                canvas.drawText(left.ellipsize(colW - 90f, paint), margin + cellPadX, y + cellPadY + lineH - paint.descent(), paint)
                canvas.drawText(right, margin + colW - cellPadX - paint.measureText(right), y + cellPadY + lineH - paint.descent(), paint)
                y += rowH
            }
            fun header() = draw(labels.category, labels.count, headFill, headPaint)
            fun body(left: String, right: String, fill: Paint?, paint: Paint) {
                if (y + rowH > bottomLimit) {
                    breakPage()
                    header()
                }
                draw(left, right, fill, paint)
            }

            canvas.drawText(labels.summary, margin, y + 10f, summaryTitlePaint)
            y += titleH
            header()
            chart.entries.forEachIndexed { i, (name, value) -> body(name, value.toString(), if (i % 2 == 1) zebraFill else null, cellPaint) }
            canvas.drawLine(margin, y, margin + colW, y, rule)
            body(labels.total, chart.total.toString(), null, boldCellPaint)
            y += 16f
        }

        /** Content-sized columns: narrow ones keep their natural width, wide ones share what is left. */
        private fun columnWidths(columns: List<String>, rows: List<List<String>>): List<Float> {
            if (columns.isEmpty()) return emptyList()
            val sample = rows.take(300)
            val cap = contentW * 0.45f
            val natural = columns.indices.map { c ->
                val widest = (sample.map { cellPaint.measureText(it[c]) } + headPaint.measureText(columns[c])).max()
                (widest + 2 * cellPadX + 2f).coerceIn(36f, cap)
            }
            val fair = contentW / columns.size
            val fixed = natural.map { it <= fair }
            val fixedSum = natural.filterIndexed { i, _ -> fixed[i] }.sum()
            val flexNatural = natural.filterIndexed { i, _ -> !fixed[i] }.sum()
            val raw = if (flexNatural == 0f) {
                natural.map { it * contentW / natural.sum() }
            } else {
                natural.mapIndexed { i, w -> if (fixed[i]) w else w * (contentW - fixedSum) / flexNatural }
            }
            val scale = contentW / raw.sum()
            return raw.map { it * scale }
        }

        /** Word wrap; a word wider than the column is split by character. Overflow past [maxCellLines] ends in an ellipsis. */
        private fun wrap(text: String, width: Float, paint: Paint): List<String> {
            if (width <= 0f) return listOf("")
            val lines = mutableListOf<String>()
            var current = ""
            fun push() { lines += current; current = "" }
            for (word in text.split(' ', '\n').filter { it.isNotEmpty() }) {
                var rest = word
                while (paint.measureText(rest) > width) {
                    if (current.isNotEmpty()) push()
                    var fit = paint.breakText(rest, true, width, null).coerceAtLeast(1)
                    if (fit < rest.length && rest[fit - 1].isHighSurrogate()) {
                        // Never split a surrogate pair: back off one, or take the whole pair when only one char fits.
                        fit = if (fit > 1) fit - 1 else 2
                    }
                    lines += rest.substring(0, fit)
                    rest = rest.substring(fit)
                }
                val candidate = if (current.isEmpty()) rest else "$current $rest"
                if (paint.measureText(candidate) <= width) current = candidate else { push(); current = rest }
            }
            if (current.isNotEmpty()) push()
            if (lines.isEmpty()) return listOf("")
            if (lines.size <= maxCellLines) return lines
            return lines.take(maxCellLines - 1) + lines[maxCellLines - 1].ellipsize(width, paint)
        }

        private fun String.ellipsize(width: Float, paint: Paint): String {
            if (paint.measureText(this) <= width) return this
            var n = paint.breakText(this, true, (width - paint.measureText("…")).coerceAtLeast(0f), null)
            if (n in 1 until length && this[n - 1].isHighSurrogate()) n--
            return substring(0, n) + "…"
        }
    }

    private companion object {
        const val LANDSCAPE_FROM_COLUMNS = 5
        const val BANNER_H = 34f
        const val NO_RECORDS_H = 24f
        val NUMERIC = Regex("[+\\-\u2212]?\\d[\\d,.]*%?")
        /** Signed number or phone-like: +/- then a digit, then only digits, spaces, hyphens, parentheses, dots, commas. */
        val PHONE_OR_NUMBER = Regex("[+\\-]\\d[\\d \\-().,%]*")
    }
}
