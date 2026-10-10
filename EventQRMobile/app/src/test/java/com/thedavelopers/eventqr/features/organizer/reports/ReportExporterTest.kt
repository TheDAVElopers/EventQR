package com.thedavelopers.eventqr.features.organizer.reports

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportRowDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReportExporterTest {
    private val labels = ReportExportLabels(
        event = "Event",
        generated = "Generated",
        summary = "Summary",
        category = "Category",
        count = "Count",
        total = "Total",
        noRecords = "No records",
        combinedTitle = "Combined Event Report",
        sectionFormat = "Section %1\$d",
        recordsFormat = "%1\$d records",
        pageFormat = "Page %1\$d of %2\$d",
    )
    private val exporter = ReportExporter(labels, "Tech Summit 2026", "Oct 10, 2026 9:00 AM")
    private val day = LocalDate.of(2026, 10, 10)

    private fun report(
        type: EventReportType = EventReportType.ATTENDANCE,
        title: String? = "Attendance",
        columns: List<String?> = listOf("Name", "Points"),
        rows: List<List<String?>> = emptyList(),
        series: Map<String?, Long> = emptyMap(),
    ) = EventReportDto(
        reportType = type,
        reportTitle = title,
        columns = columns,
        rows = rows.map { EventReportRowDto(it) },
        chartSeries = series,
    )

    private fun csv(vararg reports: EventReportDto): String = exporter.csv(reports.toList()).toString(Charsets.UTF_8)

    // ---- file names

    @Test
    fun slug_stripsAccentsSpacesAndSymbols() {
        assertEquals("cafe-nino-2026", ReportFileNames.slug("  Café  Niño @ 2026!! "))
        assertEquals("tech-summit-2026", ReportFileNames.slug("Tech Summit 2026"))
    }

    @Test
    fun slug_isEmptyForNullOrSymbolOnlyNames() {
        assertEquals("", ReportFileNames.slug(null))
        assertEquals("", ReportFileNames.slug("!!!"))
    }

    @Test
    fun slug_isCappedAndNeverEndsInDash() {
        val slug = ReportFileNames.slug("a".repeat(39) + " " + "b".repeat(30))
        assertTrue(slug.length <= 40)
        assertFalse(slug.endsWith("-"))
    }

    @Test
    fun build_singleReportUsesItsType() {
        val name = ReportFileNames.build("Tech Summit 2026", listOf(report()), ReportExportFormat.PDF, day)
        assertEquals("tech-summit-2026_attendance_2026-10-10.pdf", name)
    }

    @Test
    fun build_multipleReportsUseFullReport() {
        val name = ReportFileNames.build("Tech Summit 2026", listOf(report(), report(EventReportType.NO_SHOWS)), ReportExportFormat.CSV, day)
        assertEquals("tech-summit-2026_full-report_2026-10-10.csv", name)
    }

    @Test
    fun build_emptyEventFallsBackToEvent() {
        assertEquals("event_no-shows_2026-10-10.csv", ReportFileNames.build("", listOf(report(EventReportType.NO_SHOWS)), ReportExportFormat.CSV, day))
        assertEquals("event_roster_2026-10-10.csv", ReportFileNames.build(null, listOf(report(EventReportType.ROSTER)), ReportExportFormat.CSV, day))
    }

    // ---- CSV

    @Test
    fun csv_startsWithUtf8BomBytes() {
        val bytes = exporter.csv(listOf(report()))
        assertEquals(0xEF, bytes[0].toInt() and 0xFF)
        assertEquals(0xBB, bytes[1].toInt() and 0xFF)
        assertEquals(0xBF, bytes[2].toInt() and 0xFF)
    }

    @Test
    fun csv_usesCrlfAndNoBareLf() {
        val text = csv(report(rows = listOf(listOf("Ana", "5"))))
        assertTrue(text.contains("\r\n"))
        assertFalse(text.replace("\r\n", "").contains("\n"))
    }

    @Test
    fun csv_hasHeaderBlockAndColumnRow() {
        val lines = csv(report(rows = listOf(listOf("Ana", "5")))).removePrefix("﻿").split("\r\n")
        assertEquals("\"Attendance\"", lines[0])
        assertEquals("\"Event\",\"Tech Summit 2026\"", lines[1])
        assertEquals("\"Generated\",\"Oct 10, 2026 9:00 AM\"", lines[2])
        assertTrue(lines.contains("\"Name\",\"Points\""))
        assertTrue(lines.contains("\"Ana\",\"5\""))
        assertTrue(lines.contains("\"1 records\""))
    }

    @Test
    fun csv_quotesAndEscapesEmbeddedQuotesCommasAndNewlines() {
        val text = csv(report(rows = listOf(listOf("Smith, \"Bob\"", "line1\nline2"))))
        assertTrue(text.contains("\"Smith, \"\"Bob\"\"\",\"line1\nline2\""))
    }

    @Test
    fun csv_defusesFormulasButNotNumbersPhonesOrDates() {
        val text = csv(report(rows = listOf(listOf("=cmd|' /C calc'!A0", "-5"), listOf("+63917", "2026-10-10"), listOf("@SUM(A1)", "-1+2"))))
        assertTrue(text.contains("\"'=cmd|' /C calc'!A0\""))
        assertTrue(text.contains("\"-5\""))
        assertTrue(text.contains("\"+63917\""))
        assertTrue(text.contains("\"2026-10-10\""))
        assertTrue(text.contains("\"'@SUM(A1)\""))
        assertTrue(text.contains("\"'-1+2\""))
    }

    @Test
    fun csv_leavesSpacedAndHyphenatedPhonesAndBareDashAlone() {
        val text = csv(report(rows = listOf(listOf("+63 917 123 4567", "+63-917-123-4567"), listOf("-", "+1 (555) 010-9999"))))
        assertTrue(text.contains("\"+63 917 123 4567\",\"+63-917-123-4567\"\r\n"))
        assertTrue(text.contains("\"-\",\"+1 (555) 010-9999\"\r\n"))
    }

    @Test
    fun csv_guardsEqualsAtSignAndSignedNonNumbers() {
        val text = csv(report(rows = listOf(listOf("=1+1", "@SUM(A1)"), listOf("-cmd", "+SUM(1,2)"), listOf("\tx", "ok"))))
        assertTrue(text.contains("\"'=1+1\",\"'@SUM(A1)\"\r\n"))
        assertTrue(text.contains("\"'-cmd\",\"'+SUM(1,2)\"\r\n"))
        assertTrue(text.contains("\"'\tx\",\"ok\"\r\n"))
    }

    @Test
    fun csv_neverGuardsGeneratedLabelsOrHeaders() {
        val exporter = ReportExporter(labels.copy(event = "=Event", category = "-Category"), "=Evil Event", "Now")
        val text = exporter.csv(listOf(report(columns = listOf("-Name", "@Pts"), series = linkedMapOf<String?, Long>("=Cat" to 1L)))).toString(Charsets.UTF_8)
        assertTrue(text.contains("\"=Event\",\"'=Evil Event\"\r\n"))
        assertTrue(text.contains("\"-Name\",\"@Pts\"\r\n"))
        assertTrue(text.contains("\"-Category\",\"Count\"\r\n"))
        assertTrue(text.contains("\"'=Cat\",\"1\"\r\n"))
    }

    @Test
    fun csv_nullCellsAreEmptyAndRaggedRowsArePadded() {
        val text = csv(report(columns = listOf("A", "B", "C"), rows = listOf(listOf("x", null), listOf("y"))))
        assertTrue(text.contains("\"x\",\"\",\"\"\r\n"))
        assertTrue(text.contains("\"y\",\"\",\"\"\r\n"))
    }

    @Test
    fun csv_summaryHasCategoryRowsAndTotal() {
        val text = csv(report(series = linkedMapOf<String?, Long>("Entered" to 3L, "No show" to 2L)))
        assertTrue(text.contains("\"Summary\"\r\n"))
        assertTrue(text.contains("\"Category\",\"Count\"\r\n"))
        assertTrue(text.contains("\"Entered\",\"3\"\r\n"))
        assertTrue(text.contains("\"Total\",\"5\"\r\n"))
    }

    @Test
    fun csv_noSummaryWithoutChartSeriesAndShowsNoRecordsForEmptyReport() {
        val text = csv(report())
        assertFalse(text.contains("\"Summary\""))
        assertTrue(text.contains("\"No records\""))
    }

    @Test
    fun csv_combinedHasTitleAndOneHeadingPerSection() {
        val text = csv(report(title = "Attendance"), report(title = null, type = EventReportType.NO_SHOWS))
        assertTrue(text.removePrefix("﻿").startsWith("\"Combined Event Report\"\r\n"))
        assertTrue(text.contains("\"Attendance\"\r\n"))
        assertTrue(text.contains("\"Section 2\"\r\n"))
    }
}
