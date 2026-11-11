package com.thedavelopers.eventqr.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * The activation gate lives on the backend; the app must show its 409 text as written instead of
 * replacing it with a generic failure line.
 */
class ApiCallHelperMessageTest {

    private val activateBeforeStartMessage = "This event cannot be activated until its start date."

    @Test
    fun activateConflict_surfacesBackendMessageVerbatim() {
        val body = """
            {"timestamp":"2026-01-10T12:00:00Z","status":409,"error":"Conflict",
             "message":"$activateBeforeStartMessage","path":"/events/e1/activate"}
        """.trimIndent()

        val result = runBlocking {
            safeApiCall<Unit> {
                throw HttpException(Response.error<Any>(body.toResponseBody("application/json".toMediaType()), conflictResponse()))
            }
        } as NetworkResult.Error

        assertEquals(activateBeforeStartMessage, result.message)
        assertEquals(activateBeforeStartMessage, result.serverMessage)
    }

    private fun conflictResponse() = okhttp3.Response.Builder()
        .request(Request.Builder().url("http://localhost/").build())
        .protocol(Protocol.HTTP_1_1)
        .code(409)
        .message("Conflict")
        .build()
}
