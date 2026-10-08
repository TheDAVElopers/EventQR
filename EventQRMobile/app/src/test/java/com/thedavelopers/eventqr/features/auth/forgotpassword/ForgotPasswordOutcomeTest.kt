package com.thedavelopers.eventqr.features.auth.forgotpassword

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ForgotPasswordOutcomeTest {

    private fun http(code: Int) =
        HttpException(Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType())))

    @Test
    fun ioException_isNetworkFailure() {
        assertEquals(ForgotPasswordOutcome.NetworkFailure, ForgotPasswordOutcome.classify(IOException("offline")))
    }

    @Test
    fun http429_isRateLimited() {
        assertEquals(ForgotPasswordOutcome.RateLimited, ForgotPasswordOutcome.classify(http(429)))
    }

    @Test
    fun http5xx_isServerError() {
        assertEquals(ForgotPasswordOutcome.ServerError, ForgotPasswordOutcome.classify(http(500)))
        assertEquals(ForgotPasswordOutcome.ServerError, ForgotPasswordOutcome.classify(http(503)))
    }

    @Test
    fun http4xxValidation_staysNeutral() {
        assertEquals(ForgotPasswordOutcome.Neutral, ForgotPasswordOutcome.classify(http(400)))
        assertEquals(ForgotPasswordOutcome.Neutral, ForgotPasswordOutcome.classify(http(404)))
        assertEquals(ForgotPasswordOutcome.Neutral, ForgotPasswordOutcome.classify(null))
    }
}
