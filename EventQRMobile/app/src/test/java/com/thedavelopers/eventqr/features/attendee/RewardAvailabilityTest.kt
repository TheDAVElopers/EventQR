package com.thedavelopers.eventqr.features.attendee

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.thedavelopers.eventqr.features.attendee.RewardAvailability.State

class RewardAvailabilityTest {

    @Test
    fun loadedBalanceEnough_isAvailable() {
        assertEquals(State.AVAILABLE, RewardAvailability.evaluate(true, null, 50, 50))
    }

    @Test
    fun loadedBalanceTooLow_needsPoints() {
        assertEquals(State.NEEDS_POINTS, RewardAvailability.evaluate(true, 3, 50, 10))
    }

    @Test
    fun loading_isCheckingNotAvailable() {
        assertEquals(State.CHECKING, RewardAvailability.evaluate(true, null, 50, null, balanceLoading = true))
    }

    @Test
    fun loading_inactiveOrOutOfStockStillDecidedImmediately() {
        assertEquals(State.UNAVAILABLE, RewardAvailability.evaluate(false, 5, 50, null, balanceLoading = true))
        assertEquals(State.OUT_OF_STOCK, RewardAvailability.evaluate(true, 0, 50, null, balanceLoading = true))
    }

    @Test
    fun balanceFailed_fallsBackToStatusAndStockOnly() {
        assertEquals(State.AVAILABLE, RewardAvailability.evaluate(true, 4, 50, null, balanceLoading = false))
        assertEquals(State.OUT_OF_STOCK, RewardAvailability.evaluate(true, 0, 50, null, balanceLoading = false))
        assertEquals(State.UNAVAILABLE, RewardAvailability.evaluate(false, null, 50, null, balanceLoading = false))
    }

    @Test
    fun unknownStatus_isNotTreatedAsInactiveOrActive() {
        assertEquals(State.AVAILABLE, RewardAvailability.evaluate(null, null, 50, null, balanceLoading = false))
        assertEquals(State.CHECKING, RewardAvailability.evaluate(null, null, 50, null, balanceLoading = true))
        assertEquals(State.OUT_OF_STOCK, RewardAvailability.evaluate(null, 0, 50, 100))
    }

    @Test
    fun remainingLabel_unlimitedOnlyForKnownNullStock() {
        assertEquals("Unlimited", RewardAvailability.remainingLabel(stockKnown = true, stockQuantity = null))
        assertNull(RewardAvailability.remainingLabel(stockKnown = false, stockQuantity = null))
        assertNull(RewardAvailability.remainingLabel(stockKnown = false, stockQuantity = -1))
        assertEquals("Out of stock", RewardAvailability.remainingLabel(true, 0))
        assertEquals("4 left", RewardAvailability.remainingLabel(true, 4))
    }
}
