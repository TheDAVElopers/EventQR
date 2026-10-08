package com.thedavelopers.eventqr.features.organizer.reports

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFilterStatus
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFiltersDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReportPresentationTest {
    private val context: android.content.Context get() = ApplicationProvider.getApplicationContext()
    private val jan1 = LocalDate.of(2026, 1, 1)
    private val jan5 = LocalDate.of(2026, 1, 5)

    // ---- filter enablement

    @Test
    fun generate_isEnabledWithNoDates_onlyStart_onlyEnd_orBoth() {
        assertTrue(ReportFilterRules.canGenerate(null, null))
        assertTrue(ReportFilterRules.canGenerate(jan1, null))
        assertTrue(ReportFilterRules.canGenerate(null, jan5))
        assertTrue(ReportFilterRules.canGenerate(jan1, jan5))
        assertTrue(ReportFilterRules.canGenerate(jan1, jan1))
    }

    @Test
    fun generate_isDisabledOnlyWhenEndIsBeforeStart() {
        assertFalse(ReportFilterRules.canGenerate(jan5, jan1))
        assertTrue(ReportFilterRules.isRangeInverted(jan5, jan1))
    }

    @Test
    fun skip_clearsOnlyDates_andKeepsAttendeeAndStatusFilters() {
        val cleared = ReportFilterRules.clearDates(
            EventReportFiltersDto(jan1, jan5, "maria", EventReportFilterStatus.REJECTED),
        )
        assertNull(cleared.startDate)
        assertNull(cleared.endDate)
        assertEquals("maria", cleared.attendeeQuery)
        assertEquals(EventReportFilterStatus.REJECTED, cleared.status)
    }

    @Test
    fun searchHint_mentionsNameIdAndEmail() {
        assertEquals("Type a name, ID or email", context.getString(R.string.event_reports_attendee_search_hint))
        assertEquals("Attendee Search (Name or ID)", context.getString(R.string.event_reports_attendee_search_label))
    }

    // ---- points sign rendering

    @Test
    fun pointsCell_rendersPlusMinusAndZero() {
        assertEquals(PointsCell("+50", PointsSign.POSITIVE), formatPointsCell("50"))
        assertEquals(PointsCell("+50", PointsSign.POSITIVE), formatPointsCell("+50"))
        assertEquals(PointsCell("−40", PointsSign.NEGATIVE), formatPointsCell("-40"))
        assertEquals(PointsCell("0", PointsSign.ZERO), formatPointsCell("0"))
        assertEquals(PointsCell("—", PointsSign.ZERO), formatPointsCell(null))
    }

    @Test
    fun pointsColumnHeader_isPlusMinus_evenFromOlderServers() {
        assertEquals("Points (+/-)", displayColumnName(EventReportType.POINTS, 1, "Points Earned"))
        assertEquals("Points (+/-)", displayColumnName(EventReportType.POINTS, 1, "Points (+/-)"))
        assertEquals("Name", displayColumnName(EventReportType.POINTS, 0, "Name"))
        assertEquals("Points Earned", displayColumnName(EventReportType.ROSTER, 1, "Points Earned"))
    }

    // ---- chart total and Other slice

    private fun report(series: Map<String?, Long>, total: Long) = EventReportDto(
        reportType = EventReportType.POINTS,
        chartSeries = series,
        total = total,
    )

    @Test
    fun chart_usesServerTotal_andKeepsOtherLastWithItsOwnEntry() {
        val data = chartDataOf(
            report(linkedMapOf("Other" to 7L, "Entry" to 40L, "Booth" to 30L), total = 77L),
            sortByValue = true,
        )
        assertEquals(77L, data.total)
        assertEquals(listOf("Entry", "Booth", "Other"), data.entries.map { it.first })
        assertTrue(data.hasOther)
        assertEquals(7L, data.entries.last().second)
    }

    @Test
    fun chart_fallsBackToSeriesSum_whenServerSendsNoTotal() {
        val data = chartDataOf(report(linkedMapOf("A" to 3L, "B" to 4L), total = 0L), sortByValue = false)
        assertEquals(7L, data.total)
        assertFalse(data.hasOther)
    }

    @Test
    fun claimsChartSubtitle_matchesApprovedVsRejected() {
        assertEquals("Approved vs rejected scans", context.getString(R.string.report_preview_claims_chart_subtitle))
    }

    // ---- export fallback

    @Test
    fun fallbackExport_neverReadsAsPlainExportSaved() {
        assertNull(fallbackSavedMessageRes(fromFallback = false))
        val res = fallbackSavedMessageRes(fromFallback = true)!!
        assertEquals("Server export failed, saved a locally generated copy", context.getString(res))
    }
}
