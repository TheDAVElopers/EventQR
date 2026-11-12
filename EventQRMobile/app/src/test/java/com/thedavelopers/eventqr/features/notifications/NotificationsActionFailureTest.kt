package com.thedavelopers.eventqr.features.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * §6.8 row 21: the strip sentence never changes, so a repeated failure must still re-emit. A counter
 * does that where an equal StateFlow string value would be conflated and the Retry would look dead.
 */
class NotificationsActionFailureTest {

    @Test
    fun `a repeated failure after retry re-emits`() {
        val state = NotificationActionFailure()

        state.fail()
        assertEquals(1, state.count.value)

        // Retry fails again with the same sentence: the value must still change so the screen
        // recomposes, keeps the strip visible and announces the failure once more.
        state.fail()
        assertEquals(2, state.count.value)
    }

    @Test
    fun `a success clears the strip`() {
        val state = NotificationActionFailure()
        state.fail()

        state.clear()

        assertEquals(0, state.count.value)
    }
}
