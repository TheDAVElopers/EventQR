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
    fun alreadyClaimed_outranksStockBalanceAndLoading() {
        assertEquals(State.CLAIMED, RewardAvailability.evaluate(true, null, 50, 100, alreadyClaimed = true))
        assertEquals(State.CLAIMED, RewardAvailability.evaluate(true, 0, 50, 0, balanceLoading = true, alreadyClaimed = true))
        // A status the screen cannot read still blocks: the claim is the stronger truth.
        assertEquals(State.CLAIMED, RewardAvailability.evaluate(null, null, 50, null, alreadyClaimed = true))
        // An inactive reward is unavailable before it is claimed.
        assertEquals(State.UNAVAILABLE, RewardAvailability.evaluate(false, null, 50, 100, alreadyClaimed = true))
    }

    @Test
    fun unknownClaimState_isCheckingNeverAvailable() {
        // The claim read failed: status/stock/balance may all look fine, but a claim the backend
        // would refuse must not read as Available.
        assertEquals(
            State.CLAIM_ERROR,
            RewardAvailability.evaluate(true, 4, 50, 100, claimStateFailed = true, claimStateKnown = false),
        )
        // Still in flight (not failed) is the only indeterminate state.
        assertEquals(State.CHECKING, RewardAvailability.evaluate(true, 4, 50, 100, claimStateKnown = false))
        // Status and stock are read from other data and still decide immediately.
        assertEquals(State.UNAVAILABLE, RewardAvailability.evaluate(false, 4, 50, 100, claimStateFailed = true, claimStateKnown = false))
        assertEquals(State.OUT_OF_STOCK, RewardAvailability.evaluate(true, 0, 50, 100, claimStateFailed = true, claimStateKnown = false))
        // A successful claim read may say Available again.
        assertEquals(State.AVAILABLE, RewardAvailability.evaluate(true, 4, 50, 100, claimStateKnown = true))
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
