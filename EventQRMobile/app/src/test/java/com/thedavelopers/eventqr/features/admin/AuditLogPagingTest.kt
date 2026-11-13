package com.thedavelopers.eventqr.features.admin

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonParser
import com.thedavelopers.eventqr.core.api.sharedGson
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.audit.AuditActionCopy
import com.thedavelopers.eventqr.features.audit.model.dto.AuditLogResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AuditLogPagingTest {
    private fun log(
        action: String,
        details: String? = null,
        target: String? = null,
        timestamp: Instant = Instant.now(),
    ) = AuditLogResponse(
        auditLogId = UUID.randomUUID(), action = action, details = details,
        performedByUserId = UUID.randomUUID(), performedByFullName = "Admin",
        targetUserFullName = target, timestamp = timestamp,
    )

    /** A wall-clock time in the audit zone (Asia/Manila). */
    private fun auditZoneTime(day: String, time: String): Instant =
        ZonedDateTime.of(LocalDate.parse(day), LocalTime.parse(time), ZoneId.of("Asia/Manila")).toInstant()

    @Test
    fun chipSetIsAllEventRequestsAccountOnly() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val labels = AuditCategory.entries.map { context.getString(it.labelRes) }
        assertEquals(listOf("All", "Event requests", "Account"), labels)
    }

    @Test
    fun categoriesMapToServerActionPrefixes() {
        assertNull(AuditCategory.ALL.actionPrefix())
        assertEquals("EVENT_REQUEST_", AuditCategory.EVENT_REQUEST.actionPrefix())
        assertEquals("ACCOUNT_", AuditCategory.ACCOUNT.actionPrefix())
    }

    @Test
    fun targetIsTheNameOnlyNeverServerDetails() {
        assertEquals("Jane", auditTargetText(log("A", details = "d", target = "Jane")))
        assertNull(auditTargetText(log("A", details = "d", target = null)))
        assertNull(auditTargetText(log("A", details = "d", target = "  ")))
        assertNull(auditTargetText(log("A", details = null, target = null)))
    }

    @Test
    fun everyEmittedCodeHasVerbFirstCopy() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val expected = mapOf(
            "EVENT_REQUEST_APPROVED" to "Approved an event request",
            "EVENT_REQUEST_REJECTED" to "Rejected an event request",
            "ACCOUNT_ENABLED" to "Enabled an account",
            "ACCOUNT_DISABLED" to "Disabled an account",
            "ACCOUNT_UPDATED" to "Updated an account",
            "ACCOUNT_STATUS_UPDATED" to "Changed an account status",
            "ACCOUNT_ROLE_UPDATED" to "Changed an account role",
            "ACCOUNT_DELETED" to "Deleted an account",
        )
        assertEquals(8, expected.size)
        expected.forEach { (code, copy) ->
            assertEquals(code, copy, AuditActionCopy.copyFor(context, code))
        }
    }

    @Test
    fun unmappedCodeFallsBackToSentenceCase() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("Event request pending", AuditActionCopy.copyFor(context, "EVENT_REQUEST_PENDING"))
        assertNull(AuditActionCopy.resFor("EVENT_REQUEST_PENDING"))
    }

    @Test
    fun dayHeadersAreDerivedOnceFromTheAccumulatedList() {
        val groups = auditDayGroups(
            listOf(
                log("A", timestamp = auditZoneTime("2026-10-12", "23:30")),
                log("B", timestamp = auditZoneTime("2026-10-12", "09:00")),
                log("C", timestamp = auditZoneTime("2026-10-11", "10:00")),
            )
        )
        assertEquals(
            listOf(LocalDate.of(2026, 10, 12) to 0, LocalDate.of(2026, 10, 11) to 2),
            groups.map { it.day to it.startIndex },
        )
    }

    @Test
    fun appendedPageExtendsTheLastGroupInsteadOfRepeatingIt() {
        val first = listOf(log("A", timestamp = auditZoneTime("2026-10-12", "09:00")))
        val appended = first + log("B", timestamp = auditZoneTime("2026-10-12", "08:00"))
        assertEquals(1, auditDayGroups(first).size)
        assertEquals(1, auditDayGroups(appended).size)
        assertEquals(0, auditDayGroups(appended).single().startIndex)
    }

    @Test
    fun dayLabelIsRelativeThenShortDateThenYear() {
        val today = LocalDate.of(2026, 10, 12)
        assertEquals("Today", DateFormatters.auditDayLabel(today, today))
        assertEquals("Yesterday", DateFormatters.auditDayLabel(today.minusDays(1), today))
        assertEquals("12\u00A0Sep", DateFormatters.auditDayLabel(LocalDate.of(2026, 9, 12), today))
        assertEquals("2\u00A0Jan", DateFormatters.auditDayLabel(LocalDate.of(2026, 1, 2), today))
        assertEquals("12\u00A0Oct\u00A02025", DateFormatters.auditDayLabel(LocalDate.of(2025, 10, 12), today))
    }

    @Test
    fun auditTimeAndDayUseTheAuditZoneNotUtc() {
        val instant = Instant.parse("2026-10-11T16:30:00Z") // 00:30 on 12 Oct in Manila
        assertEquals(LocalDate.of(2026, 10, 12), DateFormatters.auditDay(instant))
        assertEquals("12:30\u00A0AM", DateFormatters.auditTime(instant))
    }

    @Test
    fun parsesBareArrayAsOneLastPage() {
        val json = JsonParser.parseString(
            "[{\"auditLogId\":\"${UUID.randomUUID()}\",\"action\":\"ACCOUNT_ENABLED\"," +
                "\"performedByUserId\":\"${UUID.randomUUID()}\",\"timestamp\":\"2026-01-01T00:00:00Z\"}]"
        )
        val page = parseAuditLogPage(json, sharedGson())
        assertEquals(1, page.content.size)
        assertTrue(page.last)
    }

    @Test
    fun parsesSpringPageShape() {
        val json = JsonParser.parseString(
            "{\"content\":[{\"auditLogId\":\"${UUID.randomUUID()}\",\"action\":\"EVENT_REQUEST_APPROVED\"," +
                "\"targetUserFullName\":\"Jane\",\"performedByUserId\":\"${UUID.randomUUID()}\"," +
                "\"timestamp\":\"2026-01-01T00:00:00Z\"}],\"totalElements\":120,\"last\":false,\"number\":0,\"size\":50}"
        )
        val page = parseAuditLogPage(json, sharedGson())
        assertEquals(1, page.content.size)
        assertEquals("Jane", page.content[0].targetUserFullName)
        assertFalse(page.last)
        assertEquals(120L, page.totalElements)
    }
}
