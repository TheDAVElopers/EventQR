package com.thedavelopers.eventqr.features.organizer.transactions

import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpLoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** N3: a failed purposes/rules load must block the form (and Save) instead of rendering defaults. */
class TransactionRulesLoadTest {

    private fun ok() = OrganizerMvpLoad(emptyList<String>(), OrganizerMvpDataSource.BACKEND)
    private fun failed(message: String?) = OrganizerMvpLoad(emptyList<String>(), OrganizerMvpDataSource.ERROR, message)

    @Test
    fun bothLoadsSucceeded_noError() {
        assertNull(transactionRulesLoadError(ok(), ok()))
    }

    @Test
    fun rulesLoadFailed_reportsItsMessage() {
        assertEquals("Timeout", transactionRulesLoadError(ok(), failed("Timeout")))
    }

    @Test
    fun purposesLoadFailed_reportsItsMessageFirst() {
        assertEquals("Offline", transactionRulesLoadError(failed("Offline"), failed("Timeout")))
    }

    @Test
    fun failureWithoutMessage_isStillAnError() {
        assertEquals("", transactionRulesLoadError(ok(), failed(null)))
    }
}
