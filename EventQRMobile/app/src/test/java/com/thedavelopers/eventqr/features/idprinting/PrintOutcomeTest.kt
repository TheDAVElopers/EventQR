package com.thedavelopers.eventqr.features.idprinting

import android.print.PrintJobInfo
import com.thedavelopers.eventqr.features.idprinting.AndroidIdPrinter.PrintOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/** The print dialog's final job state decides whether the app may say "Print Sent". */
class PrintOutcomeTest {

    @Test
    fun cancelledOrNeverSubmitted_isCancelled() {
        assertEquals(PrintOutcome.CANCELLED, AndroidIdPrinter.outcomeForJobState(PrintJobInfo.STATE_CANCELED))
        assertEquals(PrintOutcome.CANCELLED, AndroidIdPrinter.outcomeForJobState(PrintJobInfo.STATE_CREATED))
    }

    @Test
    fun failed_isFailed() {
        assertEquals(PrintOutcome.FAILED, AndroidIdPrinter.outcomeForJobState(PrintJobInfo.STATE_FAILED))
    }

    @Test
    fun handedToThePrintService_isSent() {
        listOf(
            PrintJobInfo.STATE_QUEUED,
            PrintJobInfo.STATE_STARTED,
            PrintJobInfo.STATE_BLOCKED,
            PrintJobInfo.STATE_COMPLETED,
        ).forEach { assertEquals(PrintOutcome.SENT, AndroidIdPrinter.outcomeForJobState(it)) }
    }
}
