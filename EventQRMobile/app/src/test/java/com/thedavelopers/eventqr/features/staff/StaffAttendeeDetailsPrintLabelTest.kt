package com.thedavelopers.eventqr.features.staff

import android.content.Context
import android.os.Looper
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.idprinting.AndroidIdPrinter.PrintOutcome
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse
import com.thedavelopers.eventqr.features.staff.details.StaffAttendeeDetailsActivity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

/** The Print/Reprint label follows the dialog outcome now and the server's final status once it's confirmed. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class StaffAttendeeDetailsPrintLabelTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val eventId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val attendeeId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val logId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    private inner class FakeRepository(
        private val confirms: Boolean,
        private val serverStatus: String,
    ) : StaffRepository(context) {
        var confirmedOutcome: PrintOutcome? = null
        var printLogLoads = 0

        override suspend fun confirmPrintLogs(eventId: String, logIds: List<UUID>, outcome: PrintOutcome): Int {
            confirmedOutcome = outcome
            return if (confirms) logIds.size else 0
        }

        override suspend fun getStaffPrintLogs(eventId: String): NetworkResult<List<IdPrintResponse>> {
            printLogLoads++
            return NetworkResult.Success(listOf(log(serverStatus)))
        }
    }

    private fun log(status: String) = IdPrintResponse(
        printLogId = logId,
        eventId = eventId,
        attendeeUserId = attendeeId,
        registrationId = UUID.randomUUID(),
        qrCredentialId = UUID.randomUUID(),
        reprint = false,
        success = true,
        message = "ok",
        status = status,
    )

    @Before
    fun staffSession() {
        SessionManager(context).apply { clearSession(); saveRole(AccountRole.STAFF) }
    }

    private fun activityWith(fake: FakeRepository): StaffAttendeeDetailsActivity {
        // No attendee extras: onCreate skips the network load; the test wires the state the print flow needs.
        val activity = Robolectric.buildActivity(StaffAttendeeDetailsActivity::class.java).create().get()
        set(activity, "repository", fake)
        set(activity, "eventId", eventId.toString())
        set(activity, "attendeeId", attendeeId.toString())
        set(activity, "qrCredentialId", "qr-1")
        // Without extras onCreate finishes the screen (missing attendee context); extras would trigger live network
        // loads. Un-finish it so the print outcome is handled as on a live screen.
        android.app.Activity::class.java.getDeclaredField("mFinished").apply { isAccessible = true }.setBoolean(activity, false)
        return activity
    }

    private fun label(activity: StaffAttendeeDetailsActivity) =
        activity.findViewById<TextView>(R.id.txtPrintOrReprintIdLabel).text.toString()

    /** The confirmation runs on a background scope and posts back to the main looper (this thread). */
    private fun awaitMain(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun sentPrint_flipsToReprintImmediately_thenReloadsTheConfirmedStatus() {
        val fake = FakeRepository(confirms = true, serverStatus = "PRINTED")
        val activity = activityWith(fake)

        activity.onIdPrintOutcome(PrintOutcome.SENT, logId, "Print logged")

        assertEquals(context.getString(R.string.staff_attendee_details_reprint_id), label(activity))
        awaitMain { fake.printLogLoads > 0 }
        assertEquals(PrintOutcome.SENT, fake.confirmedOutcome)
        assertEquals(1, fake.printLogLoads)
        assertEquals(context.getString(R.string.staff_attendee_details_reprint_id), label(activity))
    }

    @Test
    fun cancelledPrint_staysPrint_afterTheReload() {
        val fake = FakeRepository(confirms = true, serverStatus = "CANCELLED")
        val activity = activityWith(fake)

        activity.onIdPrintOutcome(PrintOutcome.CANCELLED, logId, "Print logged")

        awaitMain { fake.printLogLoads > 0 }
        assertEquals(PrintOutcome.CANCELLED, fake.confirmedOutcome)
        assertEquals(context.getString(R.string.staff_attendee_details_print_id), label(activity))
    }

    @Test
    fun confirmationThatNeverGotThrough_keepsTheLocalPrintedState() {
        // The server would still say PENDING; that must not flip a real print back to "Print ID".
        val fake = FakeRepository(confirms = false, serverStatus = "PENDING")
        val activity = activityWith(fake)

        activity.onIdPrintOutcome(PrintOutcome.SENT, logId, "Print logged")

        awaitMain { fake.confirmedOutcome != null }
        Thread.sleep(50)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, fake.printLogLoads)
        assertEquals(context.getString(R.string.staff_attendee_details_reprint_id), label(activity))
    }

    private fun set(target: Any, name: String, value: Any) {
        StaffAttendeeDetailsActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }
}
