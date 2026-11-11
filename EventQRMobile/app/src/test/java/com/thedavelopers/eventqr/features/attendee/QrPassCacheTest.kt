package com.thedavelopers.eventqr.features.attendee

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class QrPassCacheTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun pass(eventEndAtMillis: Long = System.currentTimeMillis() + 60_000) = CachedQrPass(
        qrValue = "qr-value",
        eventName = "DevFest Manila",
        serial = "R-000123",
        meta = "12 Oct 2026 - PICC, Pasay",
        eventEndAtMillis = eventEndAtMillis,
        savedAtMillis = System.currentTimeMillis(),
    )

    private fun registration(registrationId: UUID, status: RegistrationStatus) = RegistrationResponse(
        registrationId = registrationId,
        eventId = UUID.randomUUID(),
        attendeeUserId = UUID.randomUUID(),
        attendeeEmail = "a@test.com",
        attendeeName = "A",
        status = status,
    )

    @Test
    fun aSavedPassLoadsBackWithEveryRenderedField() {
        val registrationId = UUID.randomUUID().toString()

        QrPassCache.save(context, registrationId, pass())

        val cached = QrPassCache.load(context, registrationId)
        assertNotNull(cached)
        assertEquals("qr-value", cached!!.qrValue)
        assertEquals("DevFest Manila", cached.eventName)
        assertEquals("R-000123", cached.serial)
        assertEquals("12 Oct 2026 - PICC, Pasay", cached.meta)
    }

    @Test
    fun aPassWhoseEventEndedIsDroppedAndRemoved() {
        val registrationId = UUID.randomUUID().toString()
        QrPassCache.save(context, registrationId, pass(eventEndAtMillis = 1_000L))

        assertNull(QrPassCache.load(context, registrationId, nowMillis = 1_000L))
        // The expired entry is removed, not merely hidden, so it cannot come back on the next read.
        assertNull(QrPassCache.load(context, registrationId, nowMillis = 0L))
    }

    // A pass for a finished event (revoked, cancelled, or just over) must leave the disk even when
    // no screen ever reads it again: the session sweep is not tied to any registration state.
    @Test
    fun theSessionSweepDropsEveryPassWhoseEventEnded() {
        val ended = UUID.randomUUID().toString()
        val live = UUID.randomUUID().toString()
        QrPassCache.save(context, ended, pass(eventEndAtMillis = 1_000L))
        QrPassCache.save(context, live, pass())

        QrPassCache.resetSweepForTests()
        QrPassCache.sweepExpired(context, nowMillis = 2_000L)

        assertNull(QrPassCache.load(context, ended, nowMillis = 0L))
        assertNotNull(QrPassCache.load(context, live))
    }

    @Test
    fun clearDropsThePass() {
        val registrationId = UUID.randomUUID().toString()
        QrPassCache.save(context, registrationId, pass())

        QrPassCache.clear(context, registrationId)

        assertNull(QrPassCache.load(context, registrationId))
    }

    @Test
    fun aCancelledRegistrationLosesItsPassAndTheOthersKeepTheirs() {
        val cancelled = UUID.randomUUID()
        val active = UUID.randomUUID()
        QrPassCache.save(context, cancelled.toString(), pass())
        QrPassCache.save(context, active.toString(), pass())

        QrPassCache.clearCancelled(
            context,
            listOf(
                registration(cancelled, RegistrationStatus.CANCELLED),
                registration(active, RegistrationStatus.REGISTERED),
            ),
        )

        assertNull(QrPassCache.load(context, cancelled.toString()))
        assertNotNull(QrPassCache.load(context, active.toString()))
    }

    @Test
    fun logoutDropsEveryCachedPass() {
        val first = UUID.randomUUID().toString()
        val second = UUID.randomUUID().toString()
        QrPassCache.save(context, first, pass())
        QrPassCache.save(context, second, pass())

        SessionManager(context).clearSession()

        assertNull(QrPassCache.load(context, first))
        assertNull(QrPassCache.load(context, second))
    }
}
