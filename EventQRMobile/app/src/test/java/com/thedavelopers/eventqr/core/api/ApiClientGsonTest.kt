package com.thedavelopers.eventqr.core.api

import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.thedavelopers.eventqr.core.api.dto.ApiResponse
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.format.DateTimeParseException
import java.time.Instant

class ApiClientGsonTest {

    @Test
    fun refreshResponseWithIsoTimestampParsesIntoInstant() {
        val json = """
            {
              "success": true,
              "message": "ok",
              "data": {
                "accessToken": "new-access",
                "userId": "11111111-2222-3333-4444-555555555555",
                "email": "user@example.com",
                "fullName": "Test User",
                "role": "ATTENDEE",
                "refreshToken": "new-refresh"
              },
              "timestamp": "2026-10-07T12:34:56Z"
            }
        """.trimIndent()
        val type = object : TypeToken<ApiResponse<LoginResponse>>() {}.type

        val parsed = sharedGson().fromJson<ApiResponse<LoginResponse>>(json, type)

        assertNotNull(parsed)
        assertNotNull(parsed.data)
        assertEquals(Instant.parse("2026-10-07T12:34:56Z"), parsed.timestamp)
    }

    @Test
    fun refreshResponseWithGarbageTimestampFailsAsJsonParseException() {
        val json = """
            {
              "success": true,
              "message": "ok",
              "data": {
                "accessToken": "new-access",
                "userId": "11111111-2222-3333-4444-555555555555",
                "email": "user@example.com",
                "fullName": "Test User",
                "role": "ATTENDEE",
                "refreshToken": "new-refresh"
              },
              "timestamp": "not-a-date"
            }
        """.trimIndent()
        val type = object : TypeToken<ApiResponse<LoginResponse>>() {}.type

        val thrown = assertThrows(JsonParseException::class.java) {
            sharedGson().fromJson<ApiResponse<LoginResponse>>(json, type)
        }

        assertNotNull(thrown.cause)
        assertEquals(DateTimeParseException::class.java, thrown.cause!!.javaClass)
    }
}
