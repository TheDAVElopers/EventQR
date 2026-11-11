package com.thedavelopers.eventqr.features.staff

import android.content.Intent
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity
import com.thedavelopers.eventqr.features.admin.model.dto.AdminStatsResponse
import com.thedavelopers.eventqr.features.idprinting.AndroidIdPrinter
import com.thedavelopers.eventqr.features.organizer.allTexts
import com.thedavelopers.eventqr.features.staff.model.dto.StaffTransactionSummary
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class StaffScreensTest {
    private val context: android.content.Context get() = ApplicationProvider.getApplicationContext()
    private fun unavailable() = context.getString(R.string.common_value_unavailable)

    @Before
    fun setUp() {
        SessionManager(context).apply { clearSession(); saveRole(AccountRole.STAFF) }
    }

    // ---- transaction log tiles

    @Test
    fun transactionTiles_comeFromServerSummary_andLabelIsMyScans() {
        val activity = Robolectric.buildActivity(StaffTransactionsActivity::class.java).create().get()
        activity.renderSummary(StaffTransactionSummary(total = 340, approved = 300, rejected = 40))

        assertEquals("340", activity.findViewById<TextView>(R.id.txtTotalScans).text.toString())
        assertEquals("300", activity.findViewById<TextView>(R.id.txtSuccessfulScans).text.toString())
        assertEquals("40", activity.findViewById<TextView>(R.id.txtRejectedScans).text.toString())
        assertEquals("My scans", context.getString(R.string.ui_staff_logs))
        assertEquals("My scans", context.getString(R.string.staff_transaction_logs_total_scans))
    }

    @Test
    fun transactionTiles_showDashesWhenSummaryUnavailable() {
        val activity = Robolectric.buildActivity(StaffTransactionsActivity::class.java).create().get()
        activity.renderSummary(null)

        assertEquals("--", activity.findViewById<TextView>(R.id.txtTotalScans).text.toString())
        assertEquals("--", activity.findViewById<TextView>(R.id.txtSuccessfulScans).text.toString())
        assertEquals("--", activity.findViewById<TextView>(R.id.txtRejectedScans).text.toString())
    }

    // ---- registrations tiles

    @Test
    fun registrationTiles_showDashesForUnknownValues() {
        val activity = Robolectric.buildActivity(EventRegistrationsActivity::class.java).create().get()
        activity.renderCounts(RegistrationCounts(total = 45, checkedIn = null, registered = null))

        assertEquals("45", activity.findViewById<TextView>(R.id.txtAttendeeTotal).text.toString())
        assertEquals(unavailable(), activity.findViewById<TextView>(R.id.txtAttendeeCheckedIn).text.toString())
        assertEquals(unavailable(), activity.findViewById<TextView>(R.id.txtAttendeeRegistered).text.toString())
    }

    // ---- staff dashboard tiles

    @Test
    fun dashboardTiles_showDashesNotZeroWhenUnknown_andAreLabelledHonestly() {
        val activity = Robolectric.buildActivity(StaffDashboardActivity::class.java).create().get()
        activity.updateStats(null, null)
        assertEquals("--", activity.findViewById<TextView>(R.id.passHeadStat1Value).text.toString())
        assertEquals("--", activity.findViewById<TextView>(R.id.passHeadStat2Value).text.toString())

        activity.updateStats(7, 3)
        assertEquals("7", activity.findViewById<TextView>(R.id.passHeadStat1Value).text.toString())
        assertEquals("3", activity.findViewById<TextView>(R.id.passHeadStat2Value).text.toString())

        assertEquals("Scans", context.getString(R.string.ui_staff_scans))
        assertEquals("Check-ins", context.getString(R.string.ui_staff_check_ins))
    }

    @Test
    fun dashboardRecentCount_usesServerSummaryNotTheTruncatedFiveRowList() {
        val activity = Robolectric.buildActivity(StaffDashboardActivity::class.java).create().get()
        val rows = (1..5).map {
            TransactionResponse(
                transactionId = UUID.randomUUID(),
                eventId = UUID.randomUUID(),
                attendeeUserId = UUID.randomUUID(),
                registrationId = UUID.randomUUID(),
                qrCredentialId = UUID.randomUUID(),
                scanPurposeId = UUID.randomUUID(),
                transactionType = TransactionType.ENTRY,
                transactionResult = TransactionResult.APPROVED,
                pointsDelta = 0,
            )
        }

        activity.renderRecentScans(rows)
        activity.updateStats(scans = 42, checkins = 17)

        assertEquals("42 today", activity.findViewById<TextView>(R.id.txtRecentCount).text.toString())
    }

    // ---- admin dashboard tiles

    @Test
    fun adminHead_showsDashesWhenStatsFail_andServerNumbersOtherwise() {
        SessionManager(context).saveRole(AccountRole.ADMIN)
        val activity = Robolectric.buildActivity(AdminDashboardActivity::class.java).create().get()

        activity.renderStats(null)
        assertEquals("--", activity.findViewById<TextView>(R.id.passHeadStat1Value).text.toString())
        assertEquals("--", activity.findViewById<TextView>(R.id.passHeadStat2Value).text.toString())
        assertEquals("--", activity.findViewById<TextView>(R.id.passHeadStat3Value).text.toString())

        activity.renderStats(
            AdminStatsResponse(totalAccounts = 12, activeEvents = 3, pendingEventRequests = 2),
        )
        assertEquals("2", activity.findViewById<TextView>(R.id.passHeadStat1Value).text.toString())
        assertEquals("3", activity.findViewById<TextView>(R.id.passHeadStat2Value).text.toString())
        assertEquals("12", activity.findViewById<TextView>(R.id.passHeadStat3Value).text.toString())
    }

    // ---- ID print

    private fun latestDialogTexts(): List<String> =
        org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.decorView.allTexts()

    @Test
    fun emptyRegistrations_showEventTitleFromLaunchIntent() {
        val intent = Intent().putExtra(StaffScreenExtras.EXTRA_EVENT_TITLE, "QA Test Mixer")
        val activity = Robolectric.buildActivity(EventRegistrationsActivity::class.java, intent).create().get()

        activity.renderRegistrations(emptyList(), hasQuery = false)

        assertEquals("QA Test Mixer", activity.findViewById<TextView>(R.id.txtEventRegistrationsEventTitle).text.toString())
    }

    @Test
    fun batchPrintCancelled_summarySaysCancelledNotPrintedSuccessfully() {
        val activity = Robolectric.buildActivity(EventRegistrationsActivity::class.java).create().get()

        activity.showBatchPrintResult(3, 3, emptyList(), AndroidIdPrinter.PrintOutcome.CANCELLED)

        val texts = latestDialogTexts()
        assertTrue(texts.contains("Print cancelled. Nothing was printed."))
        assertFalse(texts.contains("3 of 3 IDs printed successfully."))
        assertFalse(texts.any { it.contains("print log") })
    }

    @Test
    fun batchPrintJobFailed_summarySaysNothingPrinted() {
        val activity = Robolectric.buildActivity(EventRegistrationsActivity::class.java).create().get()

        activity.showBatchPrintResult(2, 2, emptyList(), AndroidIdPrinter.PrintOutcome.FAILED)

        val texts = latestDialogTexts()
        assertTrue(texts.contains(context.getString(R.string.event_registrations_batch_print_job_failed)))
        assertFalse(texts.contains("2 of 2 IDs printed successfully."))
    }

    @Test
    fun printCancelAndFailCopy_isShortEnoughForATwoLineToast() {
        listOf(
            R.string.staff_print_cancelled_toast,
            R.string.staff_print_job_failed_message,
            R.string.event_registrations_batch_print_cancelled,
            R.string.event_registrations_batch_print_job_failed,
        ).forEach { id ->
            val text = context.getString(id)
            assertTrue("'$text' too long", text.length <= 45)
            assertFalse(text.contains("print log"))
        }
    }
}

