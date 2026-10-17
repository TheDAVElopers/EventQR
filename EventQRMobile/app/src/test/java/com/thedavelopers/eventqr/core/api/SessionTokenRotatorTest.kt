package com.thedavelopers.eventqr.core.api

import com.thedavelopers.eventqr.core.api.SessionTokenRotator.Rotation
import com.thedavelopers.eventqr.core.session.TokenStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A refresh rejected because a password change revoked the old tokens must not log the user out. */
class SessionTokenRotatorTest {

    private class FakeStore(var access: String?, var refresh: String?) : TokenStore {
        override fun getAccessToken() = access
        override fun getRefreshToken() = refresh
        override fun saveTokens(accessToken: String, refreshToken: String?) {
            access = accessToken
            if (refreshToken != null) refresh = refreshToken
        }
        override fun clear() {
            access = null
            refresh = null
        }
    }

    private var expired = 0

    private fun rotator(store: FakeStore, call: RefreshCall) = SessionTokenRotator(store, call, { expired++ })

    @Test
    fun rejectedRefresh_withNoPasswordChangeInFlight_expiresTheSession() {
        val store = FakeStore("access", "refresh")

        assertEquals(Rotation.EXPIRED, rotator(store) { RefreshOutcome.Rejected }.rotate())
        assertNull(store.refresh)
        assertEquals(1, expired)
    }

    @Test
    fun rejectedRefresh_duringAPasswordChange_keepsTheSession_andTheChangesTokensWin() = runBlocking {
        val store = FakeStore("old-access", "old-refresh")
        val rotator = rotator(store) { RefreshOutcome.Rejected }

        rotator.duringExternalRotation {
            // A 401 elsewhere races the change and spends the (now revoked) refresh token.
            assertEquals(Rotation.UNAVAILABLE, rotator.rotate())
            assertNull(rotator.renewAfterUnauthorized("old-access"))
            rotator.adoptTokens("changed-access", "changed-refresh")
        }

        assertEquals("changed-access", store.access)
        assertEquals("changed-refresh", store.refresh)
        assertEquals(0, expired)
    }

    @Test
    fun guardEndsWithTheChange_soALaterRejectionStillExpires() = runBlocking {
        val store = FakeStore("access", "refresh")
        val rotator = rotator(store) { RefreshOutcome.Rejected }

        rotator.duringExternalRotation { Unit }

        assertEquals(Rotation.EXPIRED, rotator.rotate())
        assertEquals(1, expired)
    }

    @Test
    fun guardIsReleasedEvenIfTheChangeThrows() {
        val store = FakeStore("access", "refresh")
        val rotator = rotator(store) { RefreshOutcome.Rejected }

        runCatching { runBlocking { rotator.duringExternalRotation { error("network") } } }

        assertEquals(Rotation.EXPIRED, rotator.rotate())
    }

    @Test
    fun rejectedRefresh_afterTheStoredRefreshTokenChangedDuringTheCall_keepsTheNewSession() {
        val store = FakeStore("old-access", "old-refresh")
        val rotator = rotator(store) {
            // e.g. a sign-in stored a new session while this refresh was on the wire.
            store.access = "fresh-access"
            store.refresh = "fresh-refresh"
            RefreshOutcome.Rejected
        }

        assertEquals(Rotation.UNAVAILABLE, rotator.rotate())
        assertEquals("fresh-refresh", store.refresh)
        assertEquals(0, expired)
    }
}
