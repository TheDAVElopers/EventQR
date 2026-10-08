package com.thedavelopers.eventqr.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class RetryAfterParsingTest {

    @Test
    fun parse_validSeconds() {
        assertEquals(120L, parseRetryAfterSeconds("120"))
        assertEquals(900L, parseRetryAfterSeconds(" 900 "))
    }

    @Test
    fun parse_invalidValuesReturnNull() {
        assertNull(parseRetryAfterSeconds(null))
        assertNull(parseRetryAfterSeconds(""))
        assertNull(parseRetryAfterSeconds("abc"))
        assertNull(parseRetryAfterSeconds("-5"))
        assertNull(parseRetryAfterSeconds("0"))
        assertNull(parseRetryAfterSeconds("1.5"))
        assertNull(parseRetryAfterSeconds("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertNull(parseRetryAfterSeconds("99999999999999999999999"))
    }

    @Test
    fun safeApiCall_populatesRetryAfterFromHeader() {
        val result = failure(429, "Retry-After" to "300")
        assertEquals(300L, (result as NetworkResult.Error).retryAfterSeconds)
    }

    @Test
    fun safeApiCall_missingOrBadHeaderLeavesNull() {
        assertNull((failure(429) as NetworkResult.Error).retryAfterSeconds)
        assertNull((failure(429, "Retry-After" to "soon") as NetworkResult.Error).retryAfterSeconds)
    }

    private fun failure(code: Int, vararg headers: Pair<String, String>): NetworkResult<Unit> {
        val raw = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("http://localhost/").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(code)
            .message("err")
            .headers(Headers.Builder().apply { headers.forEach { add(it.first, it.second) } }.build())
            .build()
        val response = Response.error<Any>("".toResponseBody(), raw)
        return runBlocking {
            safeApiCall<Unit> { throw HttpException(response) }
        }
    }
}
