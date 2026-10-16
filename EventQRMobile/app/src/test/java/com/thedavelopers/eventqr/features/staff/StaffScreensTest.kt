package com.thedavelopers.eventqr.features.staff

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity
import com.thedavelopers.eventqr.features.admin.model.dto.AdminStatsResponse
import com.thedavelopers.eventqr.features.idprinting.AndroidIdPrinter
import com.thedavelopers.eventqr.features.organizer.allTexts
import com.thedavelopers.eventqr.features.staff.model.dto.StaffTransactionSummary
import com.thedavelopers.eventqr.features.staff.result.StaffScanResultActivity
import com.thedavelopers.eventqr.features.staff.result.StaffTransactionResultActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config

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
        assertEquals("--", activity.findViewById<TextView>(R.id.txtScansToday).text.toString())
        assertEquals("--", activity.findViewById<TextView>(R.id.txtCheckinsToday).text.toString())

        activity.updateStats(7, 3)
        assertEquals("7", activity.findViewById<TextView>(R.id.txtScansToday).text.toString())
        assertEquals("3", activity.findViewById<TextView>(R.id.txtCheckinsToday).text.toString())

        assertEquals("My Scans Today", context.getString(R.string.staff_dashboard_scanned_today))
        assertEquals("Check-ins Today", context.getString(R.string.staff_dashboard_successful_check_ins))
    }

    // ---- admin dashboard tiles

    @Test
    fun adminTiles_showDashesWhenStatsFail_andServerNumbersOtherwise() {
        SessionManager(context).saveRole(AccountRole.ADMIN)
        val activity = Robolectric.buildActivity(AdminDashboardActivity::class.java).create().get()

        activity.renderStats(null)
        assertEquals("--", activity.findViewById<TextView>(R.id.textTotalAccountsValue).text.toString())
        assertEquals("--", activity.findViewById<TextView>(R.id.textActiveEventsValue).text.toString())
        assertEquals("--", activity.findViewById<TextView>(R.id.textAuditLogsValue).text.toString())

        activity.renderStats(AdminStatsResponse(totalAccounts = 12, activeEvents = 3, auditLogCount = 2500))
        assertEquals("12", activity.findViewById<TextView>(R.id.textTotalAccountsValue).text.toString())
        assertEquals("3", activity.findViewById<TextView>(R.id.textActiveEventsValue).text.toString())
        assertEquals("2.5k", activity.findViewById<TextView>(R.id.textAuditLogsValue).text.toString())
    }

    // ---- scan result

    private fun scanResult(qrActive: Boolean, status: String = "REGISTERED"): StaffScanResultActivity {
        val intent = Intent().apply {
            putExtra(StaffScreenExtras.EXTRA_IS_VALID, true)
            putExtra(StaffScreenExtras.EXTRA_QR_ACTIVE, qrActive)
            putExtra(StaffScreenExtras.EXTRA_REGISTRATION_STATUS, status)
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME, "Jane")
        }
        return Robolectric.buildActivity(StaffScanResultActivity::class.java, intent).create().get()
    }

    @Test
    fun scanResult_activeQrKeepsSuccessStateAndLogButton() {
        val activity = scanResult(qrActive = true)

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.headerApproved).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<Button>(R.id.btnContinueTransaction).visibility)
    }

    @Test
    fun scanResult_inactiveQrShowsInactiveStateAndHidesLogButton() {
        val activity = scanResult(qrActive = false)

        assertEquals(View.GONE, activity.findViewById<View>(R.id.headerApproved).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.headerRejected).visibility)
        assertEquals(
            context.getString(R.string.staff_scan_result_qr_inactive),
            activity.findViewById<TextView>(R.id.txtScanResultStateRejected).text.toString(),
        )
        assertEquals(View.GONE, activity.findViewById<Button>(R.id.btnContinueTransaction).visibility)
    }

    @Test
    fun scanResult_ineligibleScanShowsBackendReasonNotInactiveCopy() {
        val intent = Intent().apply {
            putExtra(StaffScreenExtras.EXTRA_IS_VALID, true)
            putExtra(StaffScreenExtras.EXTRA_QR_ACTIVE, true)
            putExtra(StaffScreenExtras.EXTRA_ELIGIBLE, false)
            putExtra(StaffScreenExtras.EXTRA_MESSAGE, "Already scanned for this purpose")
            putExtra(StaffScreenExtras.EXTRA_REGISTRATION_STATUS, "REGISTERED")
        }
        val activity = Robolectric.buildActivity(StaffScanResultActivity::class.java, intent).create().get()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.headerRejected).visibility)
        assertEquals(
            context.getString(R.string.staff_scan_result_not_allowed),
            activity.findViewById<TextView>(R.id.txtScanResultStateRejected).text.toString(),
        )
        assertEquals("Already scanned for this purpose", activity.findViewById<TextView>(R.id.txtScanResultReason).text.toString())
        assertEquals(View.GONE, activity.findViewById<Button>(R.id.btnContinueTransaction).visibility)
    }

    @Test
    fun scanResult_noHardCodedVerifiedSuccessfullyCopyForActiveScans() {
        val activity = scanResult(qrActive = true)
        val hint = activity.findViewById<TextView>(R.id.txtScanResultStatusHint).text.toString()
        assertEquals(context.getString(R.string.staff_scan_result_qr_active_hint), hint)
    }

    @Test
    fun scanResult_headerBackReturnsToExistingScannerWithoutStartingANewOne() {
        val activity = scanResult(qrActive = true)

        activity.findViewById<View>(R.id.nav_header_back).performClick()

        assertTrue(activity.isFinishing)
        assertNull(Shadows.shadowOf(activity).nextStartedActivity)
    }

    // ---- ID print (ID_PRINT purpose)

    private fun latestDialogTexts(): List<String> =
        org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.decorView.allTexts()

    @Test
    fun printRejectedByServer_showsIdPrintFailedNotVerificationRejected() {
        val activity = scanResult(qrActive = true)

        activity.bindPrintFailed("Active ID template not found for event")

        assertEquals(
            context.getString(R.string.staff_print_failed_title),
            activity.findViewById<TextView>(R.id.txtScanResultStateRejected).text.toString(),
        )
        assertEquals(
            context.getString(R.string.staff_print_failed_hint),
            activity.findViewById<TextView>(R.id.txtScanResultStatusHintRejected).text.toString(),
        )
        assertEquals("Active ID template not found for event", activity.findViewById<TextView>(R.id.txtScanResultReason).text.toString())
        assertEquals(View.VISIBLE, activity.findViewById<Button>(R.id.btnViewAttendeeDetails).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<Button>(R.id.btnScanAgain).visibility)
        assertEquals(View.GONE, activity.findViewById<Button>(R.id.btnContinueTransaction).visibility)
    }

    @Test
    fun printDialogCancelled_saysCancelledNotPrintSent() {
        val activity = scanResult(qrActive = true)

        activity.onPrintOutcome(AndroidIdPrinter.PrintOutcome.CANCELLED, "Print logged (no physical printer connected)")

        val texts = latestDialogTexts()
        assertTrue(texts.contains(context.getString(R.string.staff_print_cancelled_title)))
        assertFalse(texts.contains(context.getString(R.string.staff_print_sent_title)))
    }

    @Test
    fun printJobSent_showsPrintSent() {
        val activity = scanResult(qrActive = true)

        activity.onPrintOutcome(AndroidIdPrinter.PrintOutcome.SENT, "Print logged (no physical printer connected)")

        assertTrue(latestDialogTexts().contains(context.getString(R.string.staff_print_sent_title)))
    }

    @Test
    fun printJobFailed_showsTheIdPrintFailedState() {
        val activity = scanResult(qrActive = true)

        activity.onPrintOutcome(AndroidIdPrinter.PrintOutcome.FAILED, "Print logged (no physical printer connected)")

        assertEquals(
            context.getString(R.string.staff_print_failed_title),
            activity.findViewById<TextView>(R.id.txtScanResultStateRejected).text.toString(),
        )
        assertEquals(
            context.getString(R.string.staff_print_job_failed_message),
            activity.findViewById<TextView>(R.id.txtScanResultReason).text.toString(),
        )
    }

    @Test
    fun batchPrintCancelled_summarySaysCancelledNotPrintedSuccessfully() {
        val activity = Robolectric.buildActivity(EventRegistrationsActivity::class.java).create().get()

        activity.showBatchPrintResult(3, 3, emptyList(), AndroidIdPrinter.PrintOutcome.CANCELLED)

        val texts = latestDialogTexts()
        assertTrue(texts.contains(context.getString(R.string.event_registrations_batch_print_cancelled, 3, 3)))
        assertFalse(texts.contains("3 of 3 IDs printed successfully."))
    }

    // ---- transaction result copy

    private fun txResult(result: String, points: Int): StaffTransactionResultActivity {
        val intent = Intent().apply {
            putExtra(StaffScreenExtras.EXTRA_TRANSACTION_RESULT, result)
            putExtra(StaffScreenExtras.EXTRA_POINTS_DELTA, points)
        }
        return Robolectric.buildActivity(StaffTransactionResultActivity::class.java, intent).create().get()
    }

    @Test
    fun txResult_pointsLineShownOnlyForPositivePoints() {
        val withPoints = txResult("APPROVED", 5)
        assertEquals(View.VISIBLE, withPoints.findViewById<View>(R.id.layoutTransactionApproved).visibility)
        assertEquals("Points awarded to attendee", withPoints.findViewById<TextView>(R.id.txtTransactionStateHint).text.toString())

        val noPoints = txResult("APPROVED", 0)
        assertEquals(View.GONE, noPoints.findViewById<View>(R.id.layoutTransactionApproved).visibility)
        assertEquals("Scan recorded", noPoints.findViewById<TextView>(R.id.txtTransactionStateHint).text.toString())
    }

    @Test
    fun txResult_rejectedSaysScanRejectedAndLogged() {
        val rejected = txResult("REJECTED", 0)
        assertEquals(View.GONE, rejected.findViewById<View>(R.id.layoutTransactionApproved).visibility)
        assertEquals("Scan rejected and logged", rejected.findViewById<TextView>(R.id.txtTransactionStateHintRejected).text.toString())
    }
}
