package com.thedavelopers.eventqr.features.auth.resetpassword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetResendPolicyTest {

    @Test
    fun cooldown_doublesWithEachResend() {
        assertEquals(30, ResetResendPolicy.cooldownSeconds(0))
        assertEquals(60, ResetResendPolicy.cooldownSeconds(1))
        assertEquals(120, ResetResendPolicy.cooldownSeconds(2))
        assertEquals(240, ResetResendPolicy.cooldownSeconds(3))
        assertEquals(480, ResetResendPolicy.cooldownSeconds(4))
    }

    @Test
    fun cooldown_isCappedAndNeverOverflows() {
        assertEquals(ResetResendPolicy.MAX_COOLDOWN_SECONDS, ResetResendPolicy.cooldownSeconds(5))
        assertEquals(ResetResendPolicy.MAX_COOLDOWN_SECONDS, ResetResendPolicy.cooldownSeconds(1_000))
    }

    @Test
    fun cooldown_negativeCountFallsBackToBase() {
        assertEquals(ResetResendPolicy.BASE_COOLDOWN_SECONDS, ResetResendPolicy.cooldownSeconds(-3))
    }

    @Test
    fun canResend_stopsAtTheCap() {
        assertTrue(ResetResendPolicy.canResend(0))
        assertTrue(ResetResendPolicy.canResend(ResetResendPolicy.MAX_RESENDS - 1))
        assertFalse(ResetResendPolicy.canResend(ResetResendPolicy.MAX_RESENDS))
        assertEquals(5, ResetResendPolicy.MAX_RESENDS)
    }
}
