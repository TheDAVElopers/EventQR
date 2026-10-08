package com.thedavelopers.eventqr.features.admin

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonParser
import com.thedavelopers.eventqr.core.api.sharedGson
import com.thedavelopers.eventqr.features.audit.model.dto.AuditLogResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AuditLogPagingTest {
    private fun log(action: String, details: String? = null, target: String? = null) = AuditLogResponse(
        auditLogId = UUID.randomUUID(), action = action, details = details,
        performedByUserId = UUID.randomUUID(), performedByFullName = "Admin",
        targetUserFullName = target, timestamp = Instant.now(),
    )

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
    fun targetPrefersNameThenDetailsThenNull() {
        assertEquals("Jane", auditTargetText(log("A", details = "d", target = "Jane")))
        assertEquals("d", auditTargetText(log("A", details = "d", target = null)))
        assertEquals("d", auditTargetText(log("A", details = "d", target = "  ")))
        assertNull(auditTargetText(log("A", details = null, target = null)))
        assertNull(auditTargetText(log("A", details = " ", target = "")))
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
