package com.thedavelopers.eventqr.features.auth.changepassword

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.api.ApiService
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.RefreshOutcome
import com.thedavelopers.eventqr.core.api.SessionTokenRotator
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.sharedGson
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.auth.AuthRepository
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID

/** A password change revokes the old refresh tokens; the fresh session it returns keeps the user signed in (N6). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ChangePasswordSessionTest {

    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val context: Context = ApplicationProvider.getApplicationContext()
        session = SessionManager(context)
        session.clearSession()
        session.saveLoginResponse(
            LoginResponse(
                accessToken = "old-access",
                userId = UUID.fromString("11111111-2222-3333-4444-555555555555"),
                email = "user@example.com",
                fullName = "User",
                role = AccountRole.ATTENDEE,
                refreshToken = "old-refresh",
            )
        )
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(GsonConverterFactory.create(sharedGson()))
            .build()
            .create(ApiService::class.java)
        val rotator = SessionTokenRotator(session, { RefreshOutcome.Unavailable }, {})
        repository = AuthRepository(context, api, session, rotator)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun changePassword() = runBlocking { repository.changePassword("OldPass1!", "NewPass1!", "NewPass1!") }

    @Test
    fun responseWithAFreshSession_storesBothTokensAndRole() {
        server.enqueue(
            json(
                """{"success":true,"message":"Password changed","data":{"accessToken":"new-access","refreshToken":"new-refresh",""" +
                    """"userId":"11111111-2222-3333-4444-555555555555","email":"user@example.com","fullName":"User","role":"ATTENDEE"}}"""
            )
        )

        val result = changePassword()

        assertTrue(result is NetworkResult.Success)
        assertEquals("new-access", session.getAccessToken())
        assertEquals("new-refresh", session.getRefreshToken())
        assertEquals("ATTENDEE", session.getUserRole())
        assertEquals("/api/v1/auth/change-password", server.takeRequest().path)
    }

    @Test
    fun responseWithoutData_fromAnOlderServer_keepsTheStoredSession() {
        server.enqueue(json("""{"success":true,"message":"Password changed"}"""))

        val result = changePassword()

        assertTrue(result is NetworkResult.Success)
        assertEquals("old-access", session.getAccessToken())
        assertEquals("old-refresh", session.getRefreshToken())
    }

    @Test
    fun responseWithOnlyAnAccessToken_keepsTheStoredRefreshToken() {
        server.enqueue(json("""{"success":true,"data":{"accessToken":"new-access"}}"""))

        changePassword()

        assertEquals("new-access", session.getAccessToken())
        assertEquals("old-refresh", session.getRefreshToken())
        assertEquals("ATTENDEE", session.getUserRole())
    }

    @Test
    fun failedChange_leavesTheSessionAlone() {
        server.enqueue(json("""{"success":false,"message":"Current password is incorrect"}""", 400))

        val result = changePassword()

        assertEquals("Current password is incorrect", (result as NetworkResult.Error).message)
        assertEquals("old-access", session.getAccessToken())
        assertEquals("old-refresh", session.getRefreshToken())
    }
}
