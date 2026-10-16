package com.thedavelopers.eventqr.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Free-form phone fields accept the "+63 912 345 6789" format their hint shows, and nothing the shared rule rejects. */
class PhoneInputTest {

    @Test
    fun hintFormat_withSpaces_isAcceptedAndNormalizedToE164() {
        assertEquals("+639123456789", PhoneInput.toE164OrNull("+63 912 345 6789"))
    }

    @Test
    fun dashesAndParentheses_areStripped() {
        assertEquals("+639123456789", PhoneInput.toE164OrNull(" +63-912-345-6789 "))
        assertEquals("+639123456789", PhoneInput.toE164OrNull("(63) 912.345.6789"))
    }

    @Test
    fun withoutPlus_isAcceptedAndGetsThePlus() {
        assertEquals("+639123456789", PhoneInput.toE164OrNull("639123456789"))
    }

    @Test
    fun tooShortOrWrongPrefixOrLetters_areRejected() {
        assertNull(PhoneInput.toE164OrNull("12"))
        assertNull(PhoneInput.toE164OrNull("+1 912 345 6789"))
        assertNull(PhoneInput.toE164OrNull("+63 912 345 678"))
        assertNull(PhoneInput.toE164OrNull("+63 912 345 67890"))
        assertNull(PhoneInput.toE164OrNull("+63 912 ABC 6789"))
        assertNull(PhoneInput.toE164OrNull(""))
    }
}
