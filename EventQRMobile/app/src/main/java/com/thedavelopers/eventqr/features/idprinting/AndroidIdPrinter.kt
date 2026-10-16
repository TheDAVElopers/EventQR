package com.thedavelopers.eventqr.features.idprinting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Log
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintJob
import android.print.PrintJobInfo
import android.print.PrintManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.thedavelopers.eventqr.features.registrations.RegistrationNumberFormatter
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders CR80-ratio ID cards onto an A4 bond-paper page and opens the system
 * print dialog via [PrintManager].
 *
 * Layout proportions are driven by [IdCardLayoutConfig] so both the print
 * output and the Organizer Preview remain in sync.
 *
 * Rendering strategy: each card's content is rasterised onto an offscreen
 * bitmap at [IdCardLayoutConfig.RENDER_SCALE]× to produce crisp text when the
 * PDF is printed, then composited onto the final PDF page at its physical size.
 *
 * Batch print: a fixed 3×3 grid (9 cards per A4 sheet) is drawn with thin
 * dashed cut guides and corner crop marks around every cell so cards can be cut
 * apart. Cells beyond the supplied data list are left blank and never
 * duplicated; batches larger than 9 paginate to additional sheets.
 *
 * Single-attendee reprint (see [print]) stays independent: one card at native
 * CR80 size, centered on its own page.
 */
object AndroidIdPrinter {

    private const val FIELD_ATTENDEE_ID = IdCardLayoutConfig.FIELD_ATTENDEE_ID
    private const val FIELD_ROLE = IdCardLayoutConfig.FIELD_ROLE
    private const val FIELD_EVENT_NAME = IdCardLayoutConfig.FIELD_EVENT_NAME
    private const val FIELD_EVENT_DATE = IdCardLayoutConfig.FIELD_EVENT_DATE

    /** Hard business cap: at most 3 columns × 3 rows = 9 cards per sheet. */
    private const val MAX_GRID_COLS = 3
    private const val MAX_GRID_ROWS = 3

    /** Number of sheets a batch of [totalCards] spans at the 9-cap grid (UI estimate). */
    private fun pagesFor(totalCards: Int): Int =
        if (totalCards <= 0) 0 else (totalCards + MAX_GRID_COLS * MAX_GRID_ROWS - 1) / (MAX_GRID_COLS * MAX_GRID_ROWS)

    data class CardData(
        val attendeeName: String,
        val eventName: String = "",
        val registrationNumber: Int? = null,
        val role: String = "",
        val eventDate: String = "",
        val visibleFields: List<String> = emptyList(),
        val qrValue: String = "",
    )

    /** What happened in the system print dialog. */
    enum class PrintOutcome {
        /** The job was handed to a print service (queued, printing, blocked or completed). */
        SENT,
        /** The dialog was closed without printing. */
        CANCELLED,
        /** The print service reported a failure. */
        FAILED,
    }

    /**
     * Maps the job state seen when the print dialog finishes. A job still in CREATED was never
     * submitted (the dialog was dismissed), so it counts as cancelled.
     */
    fun outcomeForJobState(state: Int): PrintOutcome = when (state) {
        PrintJobInfo.STATE_CANCELED, PrintJobInfo.STATE_CREATED -> PrintOutcome.CANCELLED
        PrintJobInfo.STATE_FAILED -> PrintOutcome.FAILED
        else -> PrintOutcome.SENT
    }

    /**
     * Single-attendee reprint — one card at native CR80 size, centered on its
     * own page. Independent of the batch 3×3 grid.
     *
     * [onOutcome] runs on the main thread once the system print dialog closes.
     */
    fun print(context: Context, jobName: String, data: CardData, onOutcome: (PrintOutcome) -> Unit = {}) {
        startPrint(context, jobName, IdCardPrintAdapter(listOf(data), 1, 1), onOutcome)
    }

    /**
     * Batch print: [cards] holds each attendee's own [CardData], laid out on a
     * fixed 3×3 grid (at most 9 per page, adapting down to what fits the loaded
     * paper), split across multiple pages as needed (last page blank-fills
     * unused cells). Each attendee appears exactly once.
     */
    fun print(context: Context, jobName: String, cards: List<CardData>, onOutcome: (PrintOutcome) -> Unit = {}) {
        startPrint(context, jobName, IdCardPrintAdapter(cards, MAX_GRID_COLS, MAX_GRID_ROWS), onOutcome)
    }

    private fun startPrint(
        context: Context,
        jobName: String,
        adapter: IdCardPrintAdapter,
        onOutcome: (PrintOutcome) -> Unit,
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        var job: PrintJob? = null
        // onFinish runs after the dialog closes, by which time the spooler has set the job's final state.
        adapter.onFinished = {
            val state = runCatching { job?.info?.state }.getOrNull() ?: PrintJobInfo.STATE_CANCELED
            onOutcome(outcomeForJobState(state))
        }
        job = printManager.print(jobName, adapter, null)
    }

    /**
     * Number of A4 sheets a batch of [totalCards] spans at the fixed 3×3 grid.
     */
    fun batchPageCount(totalCards: Int): Int = pagesFor(totalCards)

    /**
     * Renders a single page's 3×3 grid into a preview [Bitmap] for a print
     * confirmation dialog. [pageIndex] selects which sheet (0-based).
     *
     * The preview is a fixed A4 reference (paper isn't known until the system
     * print dialog's onLayout); the actual print uses the real media size.
     */
    fun renderGridPreview(
        cards: List<CardData?>,
        targetWidthPx: Int,
        pageIndex: Int = 0,
    ): Bitmap {
        val pageW = IdCardLayoutConfig.PAGE_W_PT.toFloat()
        val pageH = IdCardLayoutConfig.PAGE_H_PT.toFloat()
        val perPage = MAX_GRID_COLS * MAX_GRID_ROWS
        val start = pageIndex * perPage
        val slice = mutableListOf<CardData?>()
        for (i in start until min(start + perPage, cards.size)) slice.add(cards[i])
        while (slice.size < perPage) slice.add(null)

        val pageScale = targetWidthPx.toFloat() / pageW
        val targetHeight = (pageH * pageScale).toInt()
        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(pageScale, pageScale)
        drawPageGrid(canvas, slice, MAX_GRID_COLS, MAX_GRID_ROWS, pageW, pageH)
        return bitmap
    }

    // ------------------------------------------------------------------
    // Physical dimensions derived from IdCardLayoutConfig
    // ------------------------------------------------------------------

    private val cardW = IdCardLayoutConfig.CR80_WIDTH_IN * 72f // 153pt
    private val cardH = IdCardLayoutConfig.CR80_HEIGHT_IN * 72f // 243pt
    private val margin = cardW * IdCardLayoutConfig.MARGIN_RATIO // ~10.2pt
    private val innerW = cardW - 2 * margin // ~129pt
    private val bleed = cardW * IdCardLayoutConfig.CUT_BLEED_RATIO // ~6pt

    // Font sizes derived from ratios × card height
    private val labelFont = cardH * IdCardLayoutConfig.LABEL_FONT_RATIO
    private val nameFont = cardH * IdCardLayoutConfig.NAME_FONT_RATIO
    private val eventNameFont = cardH * IdCardLayoutConfig.EVENT_NAME_FONT_RATIO
    private val roleFont = cardH * IdCardLayoutConfig.ROLE_FONT_RATIO
    private val idFont = cardH * IdCardLayoutConfig.ID_FONT_RATIO

    // Spacing derived from ratios × card height
    private val qrSpacing = cardH * IdCardLayoutConfig.QR_SPACING_RATIO
    private val fieldSpacing = cardH * IdCardLayoutConfig.FIELD_SPACING_RATIO
    private val qrSize = cardW * IdCardLayoutConfig.QR_SIZE_RATIO

    private val textMuted = IdCardLayoutConfig.COLOR_MUTED
    private val textDark = IdCardLayoutConfig.COLOR_TEXT
    private val lineLight = IdCardLayoutConfig.COLOR_BORDER_LIGHT

    /** Page margin / cell gap (points), applied to whatever paper is loaded. */
    private const val PAGE_MARGIN_PT = 30f
    private const val CELL_GAP_PT = 16f

    /**
     * Physical minimum printable margin of a real printer (~14pt ≈ 5mm).
     * Used ONLY for the "can this grid fit" check in onLayout — an actual
     * hardware constraint, not an aesthetic default like [PAGE_MARGIN_PT].
     */
    private const val PRINTER_MIN_MARGIN_PT = 14f

    // ------------------------------------------------------------------
    // Grid layout
    // ------------------------------------------------------------------

    private class GridLayout(
        val cols: Int,
        val rows: Int,
        val scale: Float,
        val gapX: Float,
        val gapY: Float,
        val cellW: Float,
        val cellH: Float,
        val gridW: Float,
        val gridH: Float,
    ) {
        fun cellLeft(col: Int) = col * (cellW + gapX)
        fun cellTop(row: Int) = row * (cellH + gapY)
    }

    /**
     * Computes the grid layout for [cols] × [rows] on the given page
     * ([pageW] × [pageH] points, read from the actual media size).
     *
     * Cards are ALWAYS rendered at native CR80 size — never shrunk or resized
     * based on paper. Callers pass a grid (via the adaptive fit in onLayout)
     * that is guaranteed to fit at native size; leftover space becomes margins
     * and the area is centered by [drawPageGrid].
     *
     * [gridW]/[gridH] hold the total occupied area (cards + gaps). Actual
     * placement — centering that area on the page — is applied by [drawPageGrid].
     */
    private fun computeLayout(cols: Int, rows: Int, pageW: Float, pageH: Float): GridLayout {
        val scale = 1f // native CR80 size — cards never resize with paper
        val cellW = cardW * scale
        val cellH = cardH * scale
        val gridW = cols * cellW + (cols - 1) * CELL_GAP_PT
        val gridH = rows * cellH + (rows - 1) * CELL_GAP_PT
        return GridLayout(
            cols = cols,
            rows = rows,
            scale = scale,
            gapX = CELL_GAP_PT,
            gapY = CELL_GAP_PT,
            cellW = cellW,
            cellH = cellH,
            gridW = gridW,
            gridH = gridH,
        )
    }

    // ------------------------------------------------------------------
    // Print adapter
    // ------------------------------------------------------------------

    private class IdCardPrintAdapter(
        private val cards: List<CardData>,
        private val cols: Int,
        private val rows: Int,
    ) : PrintDocumentAdapter() {

        private val cardCount: Int get() = cards.size

        /** Called once from [onFinish], when the system print dialog is done with this document. */
        var onFinished: (() -> Unit)? = null

        // Page size in points, resolved from the actual media at onLayout time
        // (fallback: A4 in case onLayout isn't reached with a media size).
        private var pageW = IdCardLayoutConfig.PAGE_W_PT.toFloat()
        private var pageH = IdCardLayoutConfig.PAGE_H_PT.toFloat()
        // Grid actually fitting the loaded paper (capped at the requested cols×rows).
        private var fitCols = cols
        private var fitRows = rows

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }

            // 1. Read the actual paper size (mils) and convert to points (1 mil = 0.072 pt).
            var wPt = pageW
            var hPt = pageH
            newAttributes.mediaSize?.let { media ->
                // DEBUG: raw mils BEFORE the *0.072f conversion
                Log.d("IdPrinter", "onLayout raw mediaSize: widthMils=${media.widthMils} heightMils=${media.heightMils} " +
                    "wPt=${wPt}->${media.widthMils * 0.072f} hPt=${hPt}->${media.heightMils * 0.072f}")
                wPt = media.widthMils * 0.072f
                hPt = media.heightMils * 0.072f
            }
            pageW = wPt
            pageH = hPt
            Log.d("IdPrinter", "onLayout final pageW=$pageW pageH=$pageH")

            // 5. Too small to fit even one native CR80 card (physical min. margin)?
            val minW = cardW + 2 * PRINTER_MIN_MARGIN_PT
            val minH = cardH + 2 * PRINTER_MIN_MARGIN_PT
            if (wPt < minW || hPt < minH) {
                callback.onLayoutFailed(
                    "Paper too small for even one ID card (min ${minW.roundToInt()}×${minH.roundToInt()} pt). Pick a larger paper size.",
                )
                return
            }

            // 4/6. Grid adapts down to what fits at native CR80 size, never exceeding
            // the requested 3×3 (9 cards) hard cap. Cards themselves never shrink.
            // Uses the physical min margin — not the aesthetic PAGE_MARGIN_PT — so
            // paper that fits 3×3 at a realistic margin (e.g. Letter) is kept at 3×3.
            val availW = wPt - 2 * PRINTER_MIN_MARGIN_PT
            val availH = hPt - 2 * PRINTER_MIN_MARGIN_PT
            val maxCols = max(1, ((availW + CELL_GAP_PT) / (cardW + CELL_GAP_PT)).toInt())
            val maxRows = max(1, ((availH + CELL_GAP_PT) / (cardH + CELL_GAP_PT)).toInt())
            fitCols = min(cols, maxCols)
            fitRows = min(rows, maxRows)

            val pages = if (cardCount == 0) 0 else (cardCount + fitCols * fitRows - 1) / (fitCols * fitRows)
            val info = PrintDocumentInfo.Builder("id_card.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pages)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback,
        ) {
            val perPage = fitCols * fitRows
            val pageCount = if (cardCount == 0) 0 else (cardCount + perPage - 1) / perPage
            val doc = PdfDocument()
            for (p in 0 until pageCount) {
                // 3. PDF page sized from the dynamic media size, not fixed A4.
                val pageInfo = PdfDocument.PageInfo.Builder(
                    pageW.roundToInt(),
                    pageH.roundToInt(),
                    p + 1,
                ).create()
                val page = doc.startPage(pageInfo)
                drawPageGrid(page.canvas, pageCards(p, perPage), fitCols, fitRows, pageW, pageH)
                doc.finishPage(page)
            }

            try {
                FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
            } finally {
                doc.close()
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        }

        override fun onFinish() {
            super.onFinish()
            onFinished?.invoke()
            onFinished = null
        }

        /** Returns the cells for page [p], blank-filling any unused trailing cells. */
        private fun pageCards(p: Int, perPage: Int): List<CardData?> {
            val start = p * perPage
            val slice = mutableListOf<CardData?>()
            for (i in start until min(start + perPage, cardCount)) slice.add(cards[i])
            while (slice.size < perPage) slice.add(null)
            return slice
        }
    }

    // ------------------------------------------------------------------
    // Page composition: grid of cut guides + high-res card bitmaps
    // ------------------------------------------------------------------

    /**
     * Draws every cell (cut guides always; card content only for non-null
     * entries). Unfilled cells are greyed out — never duplicated/stretched.
     *
     * The whole grid is centered on the page: the total occupied area
     * (cards + gaps) is subtracted from the page size and the leftover is
     * split evenly on all sides via [startX]/[startY].
     */
    private fun drawPageGrid(canvas: Canvas, cards: List<CardData?>, cols: Int, rows: Int, pageW: Float, pageH: Float) {
        val layout = computeLayout(cols, rows, pageW, pageH)

        // Center the total grid area horizontally AND vertically on the page.
        val startX = (pageW - layout.gridW) / 2f
        val startY = (pageH - layout.gridH) / 2f

        // DEBUG: actual runtime values for the real print job
        Log.d("IdPrinter", "drawPageGrid pageW=$pageW pageH=$pageH " +
            "gridW=${layout.gridW} gridH=${layout.gridH} " +
            "startX=$startX startY=$startY cols=$cols rows=$rows (fitCols/fitRows)")

        for (row in 0 until layout.rows) {
            for (col in 0 until layout.cols) {
                val index = row * layout.cols + col
                val cellLeft = startX + layout.cellLeft(col)
                val cellTop = startY + layout.cellTop(row)
                drawCellGuides(canvas, cellLeft, cellTop, layout.cellW, layout.cellH)

                val cell = RectF(cellLeft, cellTop, cellLeft + layout.cellW, cellTop + layout.cellH)
                val data = cards.getOrNull(index)
                if (data == null) {
                    val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5E7EB.toInt() }
                    canvas.drawRoundRect(cell, 5f, 5f, emptyPaint)
                    continue
                }
                val bitmap = renderCardBitmap(data)
                canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), cell, null)
                bitmap.recycle()
            }
        }
    }

    private fun renderCardBitmap(data: CardData): Bitmap {
        val scale = IdCardLayoutConfig.RENDER_SCALE.toFloat()
        val bmpW = (cardW * scale).toInt()
        val bmpH = (cardH * scale).toInt()
        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val offCanvas = Canvas(bitmap)
        offCanvas.scale(scale, scale)
        drawCardContent(offCanvas, data)
        return bitmap
    }

    // ------------------------------------------------------------------
    // Per-cell cut guides (drawn at PDF native resolution — thin strokes only)
    // ------------------------------------------------------------------

    private fun drawCellGuides(canvas: Canvas, cellLeft: Float, cellTop: Float, cellW: Float, cellH: Float) {
        val cutLeft = cellLeft - bleed
        val cutTop = cellTop - bleed
        val cutRight = cellLeft + cellW + bleed
        val cutBottom = cellTop + cellH + bleed

        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineLight
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
        }
        canvas.drawRect(cutLeft, cutTop, cutRight, cutBottom, dashPaint)

        val tickLen = 8f
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineLight
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }
        canvas.drawLine(cutLeft, cutTop - tickLen, cutLeft, cutTop + tickLen, tickPaint)
        canvas.drawLine(cutLeft - tickLen, cutTop, cutLeft + tickLen, cutTop, tickPaint)
        canvas.drawLine(cutRight, cutTop - tickLen, cutRight, cutTop + tickLen, tickPaint)
        canvas.drawLine(cutRight - tickLen, cutTop, cutRight + tickLen, cutTop, tickPaint)
        canvas.drawLine(cutLeft, cutBottom - tickLen, cutLeft, cutBottom + tickLen, tickPaint)
        canvas.drawLine(cutLeft - tickLen, cutBottom, cutLeft + tickLen, cutBottom, tickPaint)
        canvas.drawLine(cutRight, cutBottom - tickLen, cutRight, cutBottom + tickLen, tickPaint)
        canvas.drawLine(cutRight - tickLen, cutBottom, cutRight + tickLen, cutBottom, tickPaint)
    }

    // ------------------------------------------------------------------
    // Card content (drawn onto the scaled offscreen canvas at full CR80 size)
    // ------------------------------------------------------------------

    private fun drawCardContent(canvas: Canvas, data: CardData) {
        val cardBg = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        val cardBorder = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRoundRect(RectF(0f, 0f, cardW, cardH), 5f, 5f, cardBg)
        canvas.drawRoundRect(RectF(0f, 0f, cardW, cardH), 5f, 5f, cardBorder)

        val centerX = cardW / 2f
        var y = margin

        val qrLeft = (cardW - qrSize) / 2f
        if (data.qrValue.isNotBlank()) {
            val qrBitmap = renderQrBitmap(data.qrValue, (qrSize * IdCardLayoutConfig.RENDER_SCALE).toInt())
            val src = Rect(0, 0, qrBitmap.width, qrBitmap.height)
            canvas.drawBitmap(qrBitmap, src, RectF(qrLeft, y, qrLeft + qrSize, y + qrSize), null)
            qrBitmap.recycle()
        } else {
            val qrBg = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
            val qrBorder = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 0.7f }
            canvas.drawRoundRect(RectF(qrLeft, y, qrLeft + qrSize, y + qrSize), 3f, 3f, qrBg)
            canvas.drawRoundRect(RectF(qrLeft, y, qrLeft + qrSize, y + qrSize), 3f, 3f, qrBorder)
            val qrText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textDark; textSize = idFont; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            }
            canvas.drawText("QR CODE", centerX, y + qrSize / 2f + idFont / 2f, qrText)
        }
        y += qrSize + qrSpacing

        // Attendee Name is the prominent (largest/bold) identifier and sits first below the QR
        // per the canonical FIELD_ORDER, before Role, Event Name, Attendee ID and Event Date.
        y = drawLabel(canvas, "ATTENDEE NAME", centerX, y)
        y = drawValue(canvas, data.attendeeName, nameFont, centerX, y)
        y += fieldSpacing

        if (data.visibleFields.contains(FIELD_ROLE) && data.role.isNotBlank()) {
            y = drawLabel(canvas, "ROLE", centerX, y)
            y = drawValue(canvas, data.role, roleFont, centerX, y)
            y += fieldSpacing
        }

        if (data.visibleFields.contains(FIELD_EVENT_NAME) && data.eventName.isNotBlank()) {
            y = drawLabel(canvas, "EVENT NAME", centerX, y)
            y = drawValue(canvas, data.eventName.uppercase(), eventNameFont, centerX, y)
            y += fieldSpacing
        }

        if (data.visibleFields.contains(FIELD_ATTENDEE_ID) && data.registrationNumber != null) {
            val formatted = RegistrationNumberFormatter.format(data.registrationNumber) ?: "N/A"
            y = drawLabel(canvas, "ATTENDEE ID", centerX, y)
            y = drawValue(canvas, formatted, idFont, centerX, y)
            y += fieldSpacing
        }

        if (data.visibleFields.contains(FIELD_EVENT_DATE) && data.eventDate.isNotBlank()) {
            y = drawLabel(canvas, "EVENT DATE", centerX, y)
            y = drawValue(canvas, data.eventDate, idFont, centerX, y)
        }
    }

    // ------------------------------------------------------------------
    // Drawing helpers
    // ------------------------------------------------------------------

    private fun drawLabel(canvas: Canvas, text: String, centerX: Float, y: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted; textSize = labelFont; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        canvas.drawText(text.uppercase(), centerX, y + labelFont, paint)
        return y + labelFont + labelFont * 0.3f
    }

    private fun drawValue(canvas: Canvas, text: String, size: Float, centerX: Float, y: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textDark; textSize = size; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val truncated = if (paint.measureText(text) > innerW) {
            var end = text.length
            while (end > 0 && paint.measureText(text.substring(0, end) + "...") > innerW) end--
            text.substring(0, end) + "..."
        } else text
        canvas.drawText(truncated, centerX, y + size, paint)
        return y + size + size * 0.25f
    }

    private fun renderQrBitmap(value: String, pixelSize: Int): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q)
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, pixelSize, pixelSize, hints)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) {
            for (row in 0 until matrix.height) {
                bitmap.setPixel(x, row, if (matrix[x, row]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
