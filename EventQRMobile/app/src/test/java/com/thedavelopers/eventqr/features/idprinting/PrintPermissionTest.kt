package com.thedavelopers.eventqr.features.idprinting

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.api.ApiService
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.sharedGson
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.staff.AssignedEventsCache
import com.thedavelopers.eventqr.features.staff.StaffRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The staff Print IDs permission (`can_print_id`): what the UI may offer, where the flag is read from,
 * and how the server's refusal on the print routes is classified.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class PrintPermissionTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: StaffRepository
    private lateinit var cache: AssignedEventsCache
    private var now = 0L

    private val eventId = "11111111-1111-1111-1111-111111111111"
    private val otherEventId = "99999999-9999-9999-9999-999999999999"
    private val attendeeId = "22222222-2222-2222-2222-222222222222"
    private val denialMessage = "You don't have permission to print IDs for this event"

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(GsonConverterFactory.create(sharedGson()))
            .build()
            .create(ApiService::class.java)
        now = 0L
        cache = AssignedEventsCache(ttlMs = 60_000L, clock = { now })
        repository = StaffRepository(api, printConfirmRetryDelaysMs = listOf(0L), assignedEventsCache = cache)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"success":true,"message":"ok","data":$data}""")

    private fun error(code: Int, message: String) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"success":false,"message":"$message"}""")

    private fun assignedEvent(event: String, canPrintId: Boolean) =
        """{"assignmentId":"33333333-3333-3333-3333-333333333333","eventId":"$event","title":"Gala",""" +
            """"status":"ACTIVE","canScan":true,"canPrintId":$canPrintId}"""

    private fun printLog() =
        """{"printLogId":"44444444-4444-4444-4444-444444444444","eventId":"$eventId",""" +
            """"attendeeUserId":"$attendeeId","registrationId":"55555555-5555-5555-5555-555555555555",""" +
            """"qrCredentialId":"66666666-6666-6666-6666-666666666666","reprint":false,"success":true,"message":"ok"}"""

    // ---- what the UI is allowed to offer

    @Test
    fun permissionNotReportedForTheEvent_isAllowed_soTheServerDecides() {
        assertTrue(PrintPermission.isPrintAllowed(null))
    }

    @Test
    fun permissionGranted_isAllowed() {
        assertTrue(PrintPermission.isPrintAllowed(true))
    }

    @Test
    fun permissionRevoked_isNotAllowed() {
        assertFalse(PrintPermission.isPrintAllowed(false))
    }

    // ---- the flag the screens read

    @Test
    fun canPrintIdForEvent_readsTheFlagOffTheAssignedEvent() = runBlocking {
        server.enqueue(ok("[${assignedEvent(eventId, true)}]"))

        assertEquals(true, repository.canPrintIdForEvent(eventId))
        assertEquals("/api/v1/staff/events", server.takeRequest().path)
    }

    @Test
    fun canPrintIdForEvent_reportsARevokedPermissionAsFalse() = runBlocking {
        server.enqueue(ok("[${assignedEvent(eventId, false)},${assignedEvent(otherEventId, true)}]"))

        assertEquals(false, repository.canPrintIdForEvent(eventId))
    }

    @Test
    fun canPrintIdForEvent_isNullForAnEventTheCallerIsNotAssignedTo() = runBlocking {
        server.enqueue(ok("[${assignedEvent(otherEventId, true)}]"))

        assertNull(repository.canPrintIdForEvent(eventId))
    }

    @Test
    fun canPrintIdForEvent_isNullWhenTheAssignedEventListFails() = runBlocking {
        server.enqueue(error(500, "boom"))

        assertNull(repository.canPrintIdForEvent(eventId))
    }

    @Test
    fun printPermission_distinguishesAFailedReadFromAnUnassignedEvent() = runBlocking {
        server.enqueue(error(500, "boom"))
        assertEquals(PrintPermissionLoad.Failed, repository.printPermission(eventId))

        server.enqueue(ok("[${assignedEvent(otherEventId, true)}]"))
        assertEquals(PrintPermissionLoad.Known(null), repository.printPermission(eventId))
    }

    // ---- the assigned-event cache

    @Test
    fun assignedEvents_areServedFromCacheWithinTheTtl() = runBlocking {
        server.enqueue(ok("[${assignedEvent(eventId, true)}]"))

        repository.getEvents()
        now = 59_000L
        assertEquals(true, repository.canPrintIdForEvent(eventId))

        assertEquals(1, server.requestCount)
    }

    @Test
    fun assignedEvents_areRefetchedAfterTheTtl() = runBlocking {
        server.enqueue(ok("[${assignedEvent(eventId, true)}]"))
        server.enqueue(ok("[${assignedEvent(eventId, false)}]"))

        assertEquals(true, repository.canPrintIdForEvent(eventId))
        now = 60_000L
        assertEquals(false, repository.canPrintIdForEvent(eventId))

        assertEquals(2, server.requestCount)
    }

    @Test
    fun invalidateEvents_forcesARefetch() = runBlocking {
        server.enqueue(ok("[${assignedEvent(eventId, true)}]"))
        server.enqueue(ok("[${assignedEvent(eventId, false)}]"))

        assertEquals(true, repository.canPrintIdForEvent(eventId))
        repository.invalidateEvents()
        assertEquals(false, repository.canPrintIdForEvent(eventId))
    }

    @Test
    fun aFailedFetch_isNotCached() = runBlocking {
        server.enqueue(error(500, "boom"))
        server.enqueue(ok("[${assignedEvent(eventId, true)}]"))

        assertEquals(PrintPermissionLoad.Failed, repository.printPermission(eventId))
        assertEquals(PrintPermissionLoad.Known(true), repository.printPermission(eventId))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun putFromBeforeAClear_isIgnored() {
        val staleGeneration = cache.generation()
        cache.clear()

        cache.put(emptyList(), staleGeneration)

        assertNull(cache.get())
        cache.put(emptyList(), cache.generation())
        assertEquals(emptyList<Any>(), cache.get())
    }

    @Test
    fun aFetchInFlightDuringClear_doesNotRepopulateTheCache() = runBlocking {
        server.enqueue(ok("[${assignedEvent(eventId, true)}]").setBodyDelay(500, java.util.concurrent.TimeUnit.MILLISECONDS))

        val inFlight = async(kotlinx.coroutines.Dispatchers.IO) { repository.getEvents() }
        while (server.requestCount == 0) delay(10)
        repository.invalidateEvents()
        inFlight.await()

        assertNull(cache.get())
    }

    @Test
    fun clearingTheSession_dropsTheSharedCache() {
        AssignedEventsCache.shared.put(emptyList())

        com.thedavelopers.eventqr.core.session.SessionManager(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
        ).clearSession()

        assertNull(AssignedEventsCache.shared.get())
    }

    @Test
    fun cacheIsSharedAcrossRepositoryInstances() = runBlocking {
        server.enqueue(ok("[${assignedEvent(eventId, true)}]"))
        val other = StaffRepository(
            Retrofit.Builder().baseUrl(server.url("/api/v1/"))
                .addConverterFactory(GsonConverterFactory.create(sharedGson())).build().create(ApiService::class.java),
            assignedEventsCache = cache,
        )

        repository.getEvents()
        other.getEvents()

        assertEquals(1, server.requestCount)
    }

    // ---- the server's refusal, on every print route

    @Test
    fun printRefusedWith403_isAPermissionDenialCarryingTheServerMessage() = runBlocking {
        server.enqueue(error(403, denialMessage))

        val result = repository.printAttendeeId(eventId, attendeeId)

        val error = result as NetworkResult.Error
        assertEquals(denialMessage, error.message)
        assertEquals(denialMessage, error.serverMessage)
        assertTrue(PrintPermission.isPermissionDenied(error))
        assertEquals("/api/v1/staff/events/$eventId/attendees/$attendeeId/print-id", server.takeRequest().path)
    }

    @Test
    fun reprintRefusedWith403_isAPermissionDenial() = runBlocking {
        server.enqueue(error(403, denialMessage))

        val error = repository.reprintAttendeeId(eventId, attendeeId) as NetworkResult.Error

        assertTrue(PrintPermission.isPermissionDenied(error))
        assertEquals(denialMessage, error.message)
        assertEquals("/api/v1/staff/events/$eventId/attendees/$attendeeId/reprint-id", server.takeRequest().path)
    }

    @Test
    fun batchPrintRefusedWith403_isAPermissionDenialAsAWhole() = runBlocking {
        server.enqueue(error(403, denialMessage))

        val error = repository.printIdBatch(eventId, listOf(java.util.UUID.fromString(attendeeId)), false) as NetworkResult.Error

        assertTrue(PrintPermission.isPermissionDenied(error))
        assertEquals("/api/v1/staff/events/$eventId/print-id-batch", server.takeRequest().path)
    }

    @Test
    fun otherPrintFailures_areNotPermissionDenials() = runBlocking {
        server.enqueue(error(500, "boom"))

        val error = repository.printAttendeeId(eventId, attendeeId) as NetworkResult.Error

        assertFalse(PrintPermission.isPermissionDenied(error))
        assertEquals("boom", error.message)
    }

    @Test
    fun aRefusalWithoutABodyMessage_fallsBackToTheLocalCopy() {
        val error = NetworkResult.Error("Request failed", RuntimeException("no response"))

        assertEquals(denialMessage, PrintPermission.deniedMessage(error, denialMessage))
    }

    @Test
    fun aRefusalWithABodyMessage_usesTheServerCopy() {
        val error = NetworkResult.Error(denialMessage, RuntimeException("403"), serverMessage = denialMessage)

        assertEquals(denialMessage, PrintPermission.deniedMessage(error, "fallback copy"))
    }

    @Test
    fun aSuccessfulPrint_isNotADenial() = runBlocking {
        server.enqueue(ok(printLog()))

        val result = repository.printAttendeeId(eventId, attendeeId)

        assertTrue(result is NetworkResult.Success)
    }
}
