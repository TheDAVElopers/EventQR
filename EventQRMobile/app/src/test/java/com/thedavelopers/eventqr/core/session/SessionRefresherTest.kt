package com.thedavelopers.eventqr.core.session

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonParser
import com.thedavelopers.eventqr.core.api.ApiService
import com.thedavelopers.eventqr.core.api.AuthInterceptor
import com.thedavelopers.eventqr.core.api.HttpRefreshCall
import com.thedavelopers.eventqr.core.api.SessionTokenRotator
import com.thedavelopers.eventqr.core.api.SessionTokenRotator.Rotation
import com.thedavelopers.eventqr.core.api.TokenAuthenticator
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.sharedGson
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID

/** The proactive session refresh rotates through POST auth/refresh (the old auth/refresh-token is gone). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class SessionRefresherTest {

    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var rotator: SessionTokenRotator
    private lateinit var api: ApiService
    private var expired = 0
    private val roleChanges = mutableListOf<String>()
    private var nowMs = 1_000_000L

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        session = SessionManager(ApplicationProvider.getApplicationContext())
        session.clearSession()
        session.saveLoginResponse(
            LoginResponse(
                accessToken = "old-access",
                userId = UUID.fromString("11111111-2222-3333-4444-555555555555"),
                email = "user@example.com",
                fullName = "Old Name",
                role = AccountRole.ATTENDEE,
                refreshToken = "old-refresh",
            )
        )
        expired = 0
        roleChanges.clear()
        rotator = SessionTokenRotator(
            session,
            HttpRefreshCall(server.url("/api/v1/").toString()),
            { expired++ },
            { nowMs },
            onRoleChanged = { roleChanges += it },
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session))
            .authenticator(TokenAuthenticator(rotator))
            .build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(sharedGson()))
            .build()
            .create(ApiService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun refreshOk(access: String = "new-access", refresh: String = "new-refresh", role: String = "ATTENDEE") = json(
        """{"success":true,"message":"ok","data":{"accessToken":"$access","refreshToken":"$refresh",""" +
            """"userId":"11111111-2222-3333-4444-555555555555","email":"user@example.com","fullName":"Old Name","role":"$role"}}"""
    )

    private fun meOk(role: String) = json(
        """{"success":true,"message":"ok","data":{"userId":"11111111-2222-3333-4444-555555555555",""" +
            """"email":"user@example.com","fullName":"New Name","phoneNumber":"+639171234567","role":"$role","status":"ACTIVE"}}"""
    )

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun refreshNow(minIntervalMs: Long = SessionRefresher.MIN_REFRESH_INTERVAL_MS) =
        runBlocking { SessionRefresher.refreshSession(rotator, api, session, minIntervalMs) }

    @Test
    fun refresh_rotatesThroughAuthRefresh_storesBothTokens_thenReadsRoleFromAuthMe() {
        server.enqueue(refreshOk())
        server.enqueue(meOk("ORGANIZER"))

        val result = refreshNow()

        assertEquals(Rotation.ROTATED, result.rotation)
        assertEquals("new-access", session.getAccessToken())
        assertEquals("new-refresh", session.getRefreshToken())

        val refreshRequest = server.takeRequest()
        assertEquals("POST", refreshRequest.method)
        assertEquals("/api/v1/auth/refresh", refreshRequest.path)
        assertEquals(
            "old-refresh",
            JsonParser.parseString(refreshRequest.body.readUtf8()).asJsonObject.get("refreshToken").asString,
        )
        val meRequest = server.takeRequest()
        assertEquals("/api/v1/auth/me", meRequest.path)
        assertEquals("Bearer new-access", meRequest.getHeader("Authorization"))

        assertEquals("ORGANIZER", session.getUserRole())
        assertEquals("New Name", session.getFullName())
        assertEquals("ORGANIZER", result.changedRole)
        assertEquals("the refresh response still said ATTENDEE", emptyList<String>(), roleChanges)
        assertEquals(0, expired)
    }

    private fun callDashboardWithOldToken(): Int {
        val client = OkHttpClient.Builder().authenticator(TokenAuthenticator(rotator)).build()
        return client.newCall(
            Request.Builder().url(server.url("/api/v1/staff/dashboard")).header("Authorization", "Bearer old-access").build()
        ).execute().code
    }

    @Test
    fun a401ForAStaleRole_rotatesStoresTheNewRole_andReportsItOnce() {
        // The server 401s a token whose role no longer matches the database (e.g. staff demoted to attendee).
        session.saveRole(AccountRole.STAFF)
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(refreshOk(role = "ATTENDEE"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        assertEquals(200, callDashboardWithOldToken())

        assertEquals("ATTENDEE", session.getUserRole())
        assertEquals("new-refresh", session.getRefreshToken())
        assertEquals(listOf("ATTENDEE"), roleChanges)

        // The dashboard's resume refresh right after is skipped and must not report it a second time.
        val resume = refreshNow()
        assertEquals(Rotation.RECENT, resume.rotation)
        assertNull(resume.changedRole)
        assertEquals(listOf("ATTENDEE"), roleChanges)
    }

    @Test
    fun a401RotationWithTheSameRole_reportsNoChange() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(refreshOk(role = "ATTENDEE"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        assertEquals(200, callDashboardWithOldToken())

        assertEquals(emptyList<String>(), roleChanges)
        assertEquals("ATTENDEE", session.getUserRole())
    }

    @Test
    fun proactiveRefreshCarryingTheNewRole_reportsItOnce_notAgainFromAuthMe() {
        server.enqueue(refreshOk(role = "ORGANIZER"))
        server.enqueue(meOk("ORGANIZER"))

        val result = refreshNow()

        assertEquals(listOf("ORGANIZER"), roleChanges)
        assertNull("auth/me agrees with the rotation; no second report", result.changedRole)
        assertEquals("ORGANIZER", session.getUserRole())
    }

    @Test
    fun refresh_sameRole_reportsNoChange() {
        server.enqueue(refreshOk())
        server.enqueue(meOk("ATTENDEE"))

        assertNull(refreshNow().changedRole)
    }

    @Test
    fun networkErrorDuringRefresh_keepsTheSession() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = refreshNow()

        assertEquals(Rotation.UNAVAILABLE, result.rotation)
        assertEquals("old-access", session.getAccessToken())
        assertEquals("old-refresh", session.getRefreshToken())
        assertEquals("ATTENDEE", session.getUserRole())
        assertEquals(0, expired)
        assertEquals("auth/me is skipped when nothing rotated", 1, server.requestCount)
    }

    @Test
    fun serverErrorDuringRefresh_keepsTheSession() {
        server.enqueue(json("""{"success":false,"message":"down"}""", 503))

        assertEquals(Rotation.UNAVAILABLE, refreshNow().rotation)
        assertEquals("old-refresh", session.getRefreshToken())
        assertEquals(0, expired)
    }

    @Test
    fun rejectedRefreshToken_expiresTheSession() {
        server.enqueue(json("""{"success":false,"message":"Invalid refresh token"}""", 401))

        val result = refreshNow()

        assertEquals(Rotation.EXPIRED, result.rotation)
        assertNull(session.getAccessToken())
        assertNull(session.getRefreshToken())
        assertEquals(1, expired)
        assertNull(result.changedRole)
    }

    @Test
    fun aRefreshWithinTheLastMinute_isSkipped() {
        server.enqueue(refreshOk())
        server.enqueue(meOk("ATTENDEE"))
        refreshNow()

        nowMs += 30_000
        assertEquals(Rotation.RECENT, refreshNow().rotation)
        assertEquals(2, server.requestCount)

        nowMs += 31_000
        server.enqueue(refreshOk("newer-access", "newer-refresh"))
        server.enqueue(meOk("ATTENDEE"))
        assertEquals(Rotation.ROTATED, refreshNow().rotation)
        assertEquals("newer-refresh", session.getRefreshToken())
    }

    @Test
    fun a401WithThePreRotationToken_reusesTheRotatedTokenWithoutSpendingAnotherRefresh() {
        server.enqueue(refreshOk())
        server.enqueue(meOk("ATTENDEE"))
        refreshNow()
        server.takeRequest()
        server.takeRequest()

        // A request that was sent with the old access token before the rotation finished.
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val client = OkHttpClient.Builder().authenticator(TokenAuthenticator(rotator)).build()
        val response = client.newCall(
            Request.Builder().url(server.url("/api/v1/dashboard")).header("Authorization", "Bearer old-access").build()
        ).execute()

        assertEquals(200, response.code)
        server.takeRequest()
        assertEquals("Bearer new-access", server.takeRequest().getHeader("Authorization"))
        assertEquals("no extra refresh call", 4, server.requestCount)
    }
}
