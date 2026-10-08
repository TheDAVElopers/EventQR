package com.thedavelopers.eventqr.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsPasswordTest {

    @Test
    fun allRequirementsMet_isValid() {
        val r = Validators.passwordRequirements("Strong1!Pass")
        assertTrue(r.hasMinLength && r.hasCapital && r.hasLowercase && r.hasNumber && r.hasSpecial)
        assertTrue(r.isValid)
        assertEquals(4, r.strengthLevel)
        assertTrue(Validators.isValidSignUpPassword("Abcdef1!"))
    }

    @Test
    fun tooShort_isInvalid() {
        val r = Validators.passwordRequirements("Ab1!xyz")
        assertFalse(r.hasMinLength)
        assertFalse(r.isValid)
    }

    @Test
    fun missingUppercase_isInvalid() {
        val r = Validators.passwordRequirements("password1!")
        assertFalse(r.hasCapital)
        assertTrue(r.hasLowercase)
        assertFalse(Validators.isValidSignUpPassword("password1!"))
    }

    @Test
    fun missingLowercase_isInvalid() {
        val r = Validators.passwordRequirements("PASSWORD1!")
        assertFalse(r.hasLowercase)
        assertTrue(r.hasCapital)
        assertFalse(r.isValid)
        assertFalse(Validators.isValidSignUpPassword("PASSWORD1!"))
        assertEquals(3, r.strengthLevel)
    }

    @Test
    fun missingNumber_isInvalid() {
        val r = Validators.passwordRequirements("Password!!")
        assertFalse(r.hasNumber)
        assertFalse(r.isValid)
    }

    @Test
    fun missingSpecial_isInvalid() {
        val r = Validators.passwordRequirements("Password12")
        assertFalse(r.hasSpecial)
        assertFalse(r.isValid)
    }

    @Test
    fun anyNonLetterOrDigit_countsAsSpecial() {
        assertTrue(Validators.passwordRequirements("Passw0rd ").hasSpecial)
        assertTrue(Validators.isValidSignUpPassword("Passw0rd_"))
    }

    @Test
    fun byteBoundary_72Accepted_73Rejected() {
        val base = "Aa1!"
        val p72 = base + "x".repeat(68)
        val p73 = base + "x".repeat(69)
        assertEquals(72, p72.toByteArray(Charsets.UTF_8).size)
        assertTrue(Validators.passwordRequirements(p72).withinMaxLength)
        assertTrue(Validators.isValidSignUpPassword(p72))
        assertFalse(Validators.passwordRequirements(p73).withinMaxLength)
        assertFalse(Validators.isValidSignUpPassword(p73))
        assertTrue(Validators.passwordRequirements(p73).isOtherwiseValid)
    }

    @Test
    fun multibyte_80BytesRejected() {
        val p = "Aa1!" + "é".repeat(40)
        assertEquals(84, p.toByteArray(Charsets.UTF_8).size)
        assertTrue(p.length < 72)
        assertFalse(Validators.isValidSignUpPassword(p))
        val e = "é".repeat(40)
        assertEquals(80, e.toByteArray(Charsets.UTF_8).size)
        assertFalse(Validators.passwordRequirements(e).withinMaxLength)
    }

    @Test
    fun supplementaryCharacters_countAsOneCodePointLikeTheBackend() {
        // "Aa1!" + two emoji = 6 code points (8 UTF-16 units): too short on the backend, so too short here.
        val r = Validators.passwordRequirements("Aa1!" + "\uD83D\uDE00\uD83D\uDE00")
        assertFalse(r.hasMinLength)
        assertFalse(r.isValid)
    }

    @Test
    fun surrogatePairIsOneSpecialNotTwo_andMathematicalLetterIsNotSpecial() {
        // U+1D41A (mathematical bold small a) is a letter: it must not be mistaken for a special character.
        val letterOnly = Validators.passwordRequirements("Abcdefg1" + "\uD835\uDC1A")
        assertFalse(letterOnly.hasSpecial)
        assertTrue(letterOnly.hasLowercase)

        // An emoji is one special character.
        val withEmoji = Validators.passwordRequirements("Abcdefg1" + "\uD83D\uDE00")
        assertTrue(withEmoji.hasSpecial)
        assertTrue(withEmoji.isValid)
    }
}
