package com.thedavelopers.eventqr.features.idprinting

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonParser
import com.thedavelopers.eventqr.core.api.ApiService
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.sharedGson
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.idprinting.AndroidIdPrinter.PrintOutcome
import com.thedavelopers.eventqr.features.staff.StaffRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID

/** Print logs start PENDING and are confirmed with the print dialog's outcome; only PRINTED counts. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class PrintLogConfirmationTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: StaffRepository

    private val eventId = "11111111-1111-1111-1111-111111111111"
    private val attendeeId = "22222222-2222-2222-2222-222222222222"
    private val logA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val logB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(GsonConverterFactory.create(sharedGson()))
            .build()
            .create(ApiService::class.java)
        repository = StaffRepository(api, printConfirmRetryDelaysMs = listOf(0L, 0L))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun logJson(logId: UUID, status: String?): String {
        val statusField = status?.let { ""","status":"$it","confirmedAt":"2026-10-17T08:00:00Z"""" }.orEmpty()
        return """{"printLogId":"$logId","eventId":"$eventId","attendeeUserId":"$attendeeId",""" +
            """"registrationId":"33333333-3333-3333-3333-333333333333",""" +
            """"qrCredentialId":"44444444-4444-4444-4444-444444444444",""" +
            """"templateId":null,"reprint":false,"success":true,"message":"ok"$statusField}"""
    }

    private fun ok(data: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"success":true,"message":"ok","data":$data}""")

    private fun error(code: Int, message: String) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"success":false,"message":"$message"}""")

    // ---- outcome mapping

    @Test
    fun dialogOutcome_mapsToConfirmOutcome() {
        assertEquals("PRINTED", PrintLogStatus.confirmOutcomeFor(PrintOutcome.SENT))
        assertEquals("CANCELLED", PrintLogStatus.confirmOutcomeFor(PrintOutcome.CANCELLED))
        assertEquals("FAILED", PrintLogStatus.confirmOutcomeFor(PrintOutcome.FAILED))
    }

    @Test
    fun onlyPrintedOrLegacyLogs_countAsPrinted() {
        assertTrue(PrintLogStatus.countsAsPrinted("PRINTED"))
        assertTrue(PrintLogStatus.countsAsPrinted(null)) // older server, no status field
        assertFalse(PrintLogStatus.countsAsPrinted("PENDING"))
        assertFalse(PrintLogStatus.countsAsPrinted("CANCELLED"))
        assertFalse(PrintLogStatus.countsAsPrinted("FAILED"))
    }

    // ---- confirm endpoint

    @Test
    fun confirm_patchesEachLogWithTheOutcome() = runBlocking {
        server.enqueue(ok(logJson(logA, "PRINTED")))
        server.enqueue(ok(logJson(logB, "PRINTED")))

        val confirmed = repository.confirmPrintLogs(eventId, listOf(logA, logB), PrintOutcome.SENT)

        assertEquals(2, confirmed)
        listOf(logA, logB).forEach { logId ->
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/v1/staff/events/$eventId/print-logs/$logId", request.path)
            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals("PRINTED", body.get("outcome").asString)
        }
    }

    @Test
    fun confirm_cancelledSendsCancelled() = runBlocking {
        server.enqueue(ok(logJson(logA, "CANCELLED")))

        repository.confirmPrintLogs(eventId, listOf(logA), PrintOutcome.CANCELLED)

        val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals("CANCELLED", body.get("outcome").asString)
    }

    @Test
    fun confirm_failureOnOneLog_doesNotStopTheRest() = runBlocking {
        repeat(3) { server.enqueue(error(500, "boom")) } // every attempt for log A
        server.enqueue(ok(logJson(logB, "FAILED")))

        val confirmed = repository.confirmPrintLogs(eventId, listOf(logA, logB), PrintOutcome.FAILED)

        assertEquals(1, confirmed)
        assertEquals("3 attempts for A, 1 for B", 4, server.requestCount)
    }

    @Test
    fun confirm_serverError_isRetriedUntilItGoesThrough() = runBlocking {
        server.enqueue(error(503, "busy"))
        server.enqueue(ok(logJson(logA, "PRINTED")))

        assertEquals(1, repository.confirmPrintLogs(eventId, listOf(logA), PrintOutcome.SENT))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun confirm_givesUpAfterThreeAttempts() = runBlocking {
        repeat(4) { server.enqueue(error(502, "bad gateway")) }

        assertEquals(0, repository.confirmPrintLogs(eventId, listOf(logA), PrintOutcome.SENT))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun confirm_clientError_isNeverRetried() = runBlocking {
        server.enqueue(error(403, "Not your event"))
        server.enqueue(ok(logJson(logA, "PRINTED")))

        assertEquals(0, repository.confirmPrintLogs(eventId, listOf(logA), PrintOutcome.SENT))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun confirm_alreadyFinal409_isNotFatalOrRetried() = runBlocking {
        server.enqueue(error(409, "Print log already confirmed"))
        server.enqueue(ok(logJson(logA, "PRINTED")))

        assertEquals(0, repository.confirmPrintLogs(eventId, listOf(logA), PrintOutcome.SENT))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun confirm_networkFailure_isRetriedThenSwallowed() = runBlocking {
        // OkHttp may itself retry a dropped connection once, so allow up to two per attempt.
        repeat(6) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }

        assertEquals(0, repository.confirmPrintLogs(eventId, listOf(logA), PrintOutcome.SENT))
        assertTrue("retried: ${server.requestCount}", server.requestCount in 3..6)
    }

    @Test
    fun confirm_networkFailureThenSuccess_confirms() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(ok(logJson(logA, "PRINTED")))

        assertEquals(1, repository.confirmPrintLogs(eventId, listOf(logA), PrintOutcome.SENT))
    }

    @Test
    fun confirmInBackground_reportsCompletion() {
        server.enqueue(ok(logJson(logA, "PRINTED")))
        server.enqueue(error(400, "bad"))
        var confirmedAll: Boolean? = null

        repository.confirmPrintLogsInBackground(eventId, listOf(logA, logB), PrintOutcome.SENT) { confirmedAll = it }
        // The callback is posted to the main looper, which is this test thread: pump it until it arrives.
        val deadline = System.currentTimeMillis() + 5_000
        while (confirmedAll == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }

        assertEquals("one of two logs failed", false, confirmedAll)
    }

    @Test
    fun confirm_duplicateIds_areSentOnce() = runBlocking {
        server.enqueue(ok(logJson(logA, "PRINTED")))

        assertEquals(1, repository.confirmPrintLogs(eventId, listOf(logA, logA), PrintOutcome.SENT))
        assertEquals(1, server.requestCount)
    }

    // ---- print-log listing (Print vs Reprint)

    @Test
    fun printLogs_parseStatusAndOnlyPrintedCounts() = runBlocking {
        server.enqueue(ok("[${logJson(logA, "CANCELLED")},${logJson(logB, "PENDING")}]"))

        val logs = (repository.getStaffPrintLogs(eventId) as NetworkResult.Success).data

        assertEquals(listOf("CANCELLED", "PENDING"), logs.map { it.status })
        assertFalse(logs.any { it.countsAsPrinted() })
    }

    @Test
    fun printLogs_fromOlderServerWithoutStatus_countAsPrinted() = runBlocking {
        server.enqueue(ok("[${logJson(logA, null)}]"))

        val logs = (repository.getStaffPrintLogs(eventId) as NetworkResult.Success).data

        assertEquals(null, logs.single().status)
        assertEquals(null, logs.single().confirmedAt)
        assertTrue(logs.single().countsAsPrinted())
    }

    // ---- ID template fallback

    @Test
    fun idTemplate404_fallsBackToDefaultFields() = runBlocking {
        server.enqueue(error(404, "Active ID template not found for event"))

        assertEquals(IdCardLayoutConfig.DEFAULT_VISIBLE_FIELDS, repository.getIdCardVisibleFields(eventId))
    }

    @Test
    fun idTemplateSaved_usesItsFields() = runBlocking {
        server.enqueue(ok("""{"eventId":"$eventId","visibleFields":["ROLE","EVENT_DATE"],"lockedFields":["QR_CODE","ATTENDEE_NAME"]}"""))

        assertEquals(listOf("ROLE", "EVENT_DATE"), repository.getIdCardVisibleFields(eventId))
        assertEquals("/api/v1/events/$eventId/id-template", server.takeRequest().path)
    }

    @Test
    fun defaultFields_areQrAndName() {
        assertEquals(
            listOf(IdCardLayoutConfig.FIELD_QR_CODE, IdCardLayoutConfig.FIELD_ATTENDEE_NAME),
            IdCardLayoutConfig.DEFAULT_VISIBLE_FIELDS,
        )
        assertEquals(IdCardLayoutConfig.DEFAULT_VISIBLE_FIELDS, IdCardLayoutConfig.visibleFieldsOrDefault(emptyList()))
        assertEquals(IdCardLayoutConfig.DEFAULT_VISIBLE_FIELDS, IdCardLayoutConfig.visibleFieldsOrDefault(null))
    }
}
