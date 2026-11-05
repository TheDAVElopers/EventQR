package com.thedavelopers.eventqr.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §3.3 of `MyFiles/design/04-logo-direction.md`: the wordmark drops to the compact size (and the
 * ACCESS PASS eyebrow goes) on a narrow screen or at a large font scale - never the mark.
 */
class WordmarkTest {

    @Test
    fun phoneWidthAtDefaultFontScale_keepsTheFullLockup() {
        assertFalse(Wordmark.isCompact(screenWidthDp = 411, fontScale = 1.0f))
    }

    @Test
    fun compactStartsAt340dp() {
        assertTrue(Wordmark.isCompact(screenWidthDp = 320, fontScale = 1.0f))
        assertFalse(Wordmark.isCompact(screenWidthDp = 340, fontScale = 1.0f))
    }

    @Test
    fun compactStartsAtFontScale1_3() {
        assertTrue(Wordmark.isCompact(screenWidthDp = 411, fontScale = 1.3f))
        assertFalse(Wordmark.isCompact(screenWidthDp = 411, fontScale = 1.29f))
    }
}
