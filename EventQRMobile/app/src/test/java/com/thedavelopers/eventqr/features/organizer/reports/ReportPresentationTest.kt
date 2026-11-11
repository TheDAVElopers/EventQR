package com.thedavelopers.eventqr.features.organizer.reports

import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFilterStatus
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFiltersDto
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        assertEquals("Attendee Search (Name, ID or Email)", context.getString(R.string.event_reports_attendee_search_label))
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

    // ---- chart order and colours

    @Test
    fun chart_ranksOtherByValue_andOnlyTiesPushItLast() {
        val leading = chartDataOf(
            report(linkedMapOf("Other" to 50L, "A" to 5L), total = 55L),
            sortByValue = true,
        )
        assertEquals(listOf("Other", "A"), leading.entries.map { it.first })

        val tied = chartDataOf(
            report(linkedMapOf("Other" to 5L, "A" to 5L, "B" to 9L), total = 19L),
            sortByValue = true,
        )
        assertEquals(listOf("B", "A", "Other"), tied.entries.map { it.first })
    }

    @Test
    fun rosterChart_givesEveryStatusItsOwnColour() {
        // Resource ids are not the test: two ids may resolve to the same ARGB.
        val tones = listOf("Registered", "Entered", "Exited", "Cancelled", "No Show")
            .map { argb(reportChartColor(EventReportType.ROSTER, it)) }
        assertEquals(5, tones.toSet().size)
    }

    private fun argb(@ColorRes colourRes: Int): Int = ContextCompat.getColor(context, colourRes)

    /**
     * Every label `EventReportGenerationService` can put in a chart series. A backend rename must
     * fail here instead of shipping a chart painted in the unmapped fallback. Tones resolve to
     * ARGB: an id comparison passes even when two roles alias one colour.
     */
    @Test
    fun everyServerChartLabel_isMapped_andEveryStatusInASetHasItsOwnTone() {
        val known = mapOf(
            EventReportType.ROSTER to listOf("Registered", "Entered", "Exited", "No Show", "Cancelled"),
            EventReportType.NO_SHOWS to listOf("Marked No Show", "Not Entered"),
            EventReportType.ENTRY_LOGS to listOf("Approved", "Rejected", "Unknown"),
            EventReportType.EXIT_LOGS to listOf("Approved", "Rejected", "Unknown"),
            EventReportType.CLAIMS to listOf("Approved", "Rejected", "Unknown"),
        )
        val other = argb(R.color.chart_other)
        val unmapped = argb(R.color.chart_unmapped)
        assertNotEquals("the fallback must not alias Other", other, unmapped)

        known.forEach { (type, labels) ->
            val tones = labels.associateWith { argb(reportChartColor(type, it)) }
            tones.forEach { (label, tone) ->
                assertNotEquals("$type/$label fell back", unmapped, tone)
                assertNotEquals("$type/$label borrowed Other", other, tone)
            }
            assertEquals("$type paints ${labels.size} statuses with fewer tones", labels.size, tones.values.toSet().size)
        }
    }

    @Test
    fun unknownLabel_takesTheDistinctFallback_notTheOtherBucket() {
        assertEquals(R.color.chart_unmapped, reportChartColor(EventReportType.ROSTER, "Waitlisted"))
    }

    @Test
    fun otherSegment_usesTheOtherTone_onEveryReportType() {
        EventReportType.entries.forEach { type ->
            assertEquals(R.color.chart_other, reportChartColor(type, OTHER_CHART_LABEL))
        }
    }

    @Test
    fun rankedReport_barsAreOneSeries_andShareOneColour() {
        val colours = listOf("Main hall", "Workshop", "Other")
            .map { reportChartColor(EventReportType.ATTENDANCE, it) }
        assertEquals(listOf(R.color.chart_1, R.color.chart_1), colours.dropLast(1))
        assertEquals(R.color.chart_other, colours.last())
    }

    // ---- chart label text

    @Test
    fun splitLabel_namesBothCategories() {
        assertEquals(
            "132 Approved · 8 Rejected",
            splitLabelText(context.resources, listOf("Approved" to 132L, "Rejected" to 8L)).toString(),
        )
    }

    @Test
    fun splitLabel_namesTheOnlyCategory() {
        assertEquals("132 Approved", splitLabelText(context.resources, listOf("Approved" to 132L)).toString())
    }

    @Test
    fun combinedSummary_countsOnlyCategoriesThatCarryAValue() {
        val data = ChartData(listOf("Registered" to 0L, "Entered" to 24L), total = 24L)
        assertEquals("1 category · 24 total", combinedSummaryText(context.resources, data).toString())
    }

    @Test
    fun combinedSummary_isNullWhenNothingCarriesAValue() {
        assertNull(combinedSummaryText(context.resources, ChartData(listOf("Registered" to 0L), total = 0L)))
        assertNull(combinedSummaryText(context.resources, ChartData(emptyList(), total = 0L)))
    }

    // ---- split bar geometry

    @Test
    fun splitBar_keepsTheFloorForTinySpans_andFillsTheWidth() {
        val widths = splitSpanWidths(values = listOf(1L, 1L, 998L), width = 300f, gap = 2f, floor = 4f)
        assertTrue(widths.all { it >= 4f })
        assertEquals(296f, widths.sum(), 0.01f)
    }

    @Test
    fun splitBar_oneSpanTakesTheWholeWidth() {
        val widths = splitSpanWidths(values = listOf(7L), width = 300f, gap = 2f, floor = 4f)
        assertEquals(1, widths.size)
        assertEquals(300f, widths.single(), 0.01f)
    }

    @Test
    fun splitBar_withNoFloor_sharesTheWidthByValue() {
        val widths = splitSpanWidths(values = listOf(1L, 3L), width = 100f, gap = 0f, floor = 0f)
        assertEquals(25f, widths[0], 0.01f)
        assertEquals(75f, widths[1], 0.01f)
    }

    @Test
    fun splitBar_paintsNothingWhenTheGapsEatTheWidth() {
        assertTrue(splitSpanWidths(values = listOf(1L, 1L, 1L), width = 4f, gap = 2f, floor = 4f).all { it == 0f })
    }

    @Test
    fun splitBar_isEmptyForAnAllZeroSeries() {
        assertTrue(splitSpanWidths(values = listOf(0L, 0L), width = 300f, gap = 2f, floor = 4f).all { it == 0f })
    }
}
