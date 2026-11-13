package com.thedavelopers.eventqr.features.organizer.reports

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFilterStatus
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportFiltersDto
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReportFilterProvenanceTest {
    private val resources: Resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val jan1 = LocalDate.of(2026, 1, 1)
    private val jan5 = LocalDate.of(2026, 1, 5)

    @Test
    fun noFilters_rendersTheNoneLine() {
        assertEquals("No filters applied", appliedFiltersText(resources, EventReportFiltersDto()))
    }

    @Test
    fun blankAttendeeAndAllStatus_doNotCount() {
        assertEquals(
            "No filters applied",
            appliedFiltersText(resources, EventReportFiltersDto(attendeeQuery = "  ")),
        )
    }

    @Test
    fun dateSpan_countsAsOneFilter() {
        assertEquals(
            "1 filter · 1\u00A0Jan\u00A02026 - 5\u00A0Jan\u00A02026",
            appliedFiltersText(resources, EventReportFiltersDto(startDate = jan1, endDate = jan5)),
        )
    }

    @Test
    fun singleDate_keepsTheOtherEndWord() {
        assertEquals(
            "1 filter · 1\u00A0Jan\u00A02026 - End",
            appliedFiltersText(resources, EventReportFiltersDto(startDate = jan1)),
        )
    }

    @Test
    fun endDateOnly_keepsTheStartWord() {
        assertEquals(
            "1 filter · Start - 5\u00A0Jan\u00A02026",
            appliedFiltersText(resources, EventReportFiltersDto(endDate = jan5)),
        )
    }

    @Test
    fun everyDimension_countsSeparately() {
        assertEquals(
            "3 filters · 1\u00A0Jan\u00A02026 - 5\u00A0Jan\u00A02026 · Attendee: ana · Status: APPROVED",
            appliedFiltersText(
                resources,
                EventReportFiltersDto(
                    startDate = jan1,
                    endDate = jan5,
                    attendeeQuery = "ana",
                    status = EventReportFilterStatus.APPROVED,
                ),
            ),
        )
    }
}
