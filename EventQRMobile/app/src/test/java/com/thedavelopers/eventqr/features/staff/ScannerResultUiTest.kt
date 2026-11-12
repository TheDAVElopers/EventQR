package com.thedavelopers.eventqr.features.staff

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.idprinting.AndroidIdPrinter
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse
import com.thedavelopers.eventqr.features.organizer.allTexts
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanRequest
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanResponse
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import kotlinx.coroutines.CompletableDeferred
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import retrofit2.HttpException
import retrofit2.Response
import java.util.UUID

/**
 * The inline result block on the one-screen scanner (§6.2/§6.3): the copy follows the decision the
 * server returned, and the ID-print path only ever claims a print that really went out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ScannerResultUiTest {
    private val eventId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val attendeeId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val qrCredentialId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val purposeId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val logId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val denialMessage = "You don't have permission to print IDs for this event"

    /** Records the ID_PRINT hand-off instead of opening the system print dialog. */
    class RecordingScannerActivity : ScannerActivity() {
        val openedCards = mutableListOf<AndroidIdPrinter.CardData>()

        override fun newRepository(): StaffRepository = injectedRepository ?: super.newRepository()

        override fun openSystemPrintDialog(
            eventId: String,
            cardData: AndroidIdPrinter.CardData,
            printLogId: UUID,
            serverMessage: String,
        ) {
            openedCards += cardData
        }

        companion object {
            var injectedRepository: StaffRepository? = null
        }
    }

    private val context: android.content.Context get() = ApplicationProvider.getApplicationContext()

    private inner class FakeRepository(
        private val printResult: NetworkResult<IdPrintResponse> = NetworkResult.Loading,
        private val events: List<StaffAssignedEventResponse> = emptyList(),
        private val purposes: List<ScanPurposeResponse> = emptyList(),
        private val verifyResult: NetworkResult<ScanVerificationResponse> = NetworkResult.Loading,
        private val recordResults: List<NetworkResult<TransactionResponse>> = emptyList(),
        /** Per-call event-list outcomes, so a failed first load can succeed on retry. */
        private val eventsOutcomes: List<NetworkResult<List<StaffAssignedEventResponse>>> = emptyList(),
        /** Held open while the assigned-event read is in flight, so focus can be observed first. */
        private val eventsGate: CompletableDeferred<Unit>? = null,
        /** Per-call purpose outcomes, so one event's load can fail where the next one succeeds. */
        private val purposeOutcomes: List<NetworkResult<List<ScanPurposeResponse>>> = emptyList(),
        private val rewardResult: NetworkResult<RewardRedemptionScanResponse> = NetworkResult.Error("unexpected redemption"),
        /** Held open while a save is in flight, so the screen can be observed mid-request. */
        private val recordGate: CompletableDeferred<Unit>? = null,
    ) : StaffRepository(context) {
        var printCalls = 0
        var purposeCalls = 0
        var eventCalls = 0
        var verifyCalls = 0
        var rewardCalls = 0
        val createRequests = mutableListOf<TransactionRequest>()

        override suspend fun getEvents(): NetworkResult<List<StaffAssignedEventResponse>> {
            eventCalls++
            eventsGate?.await()
            return eventsOutcomes.getOrNull(eventCalls - 1) ?: NetworkResult.Success(events)
        }

        override suspend fun getScanPurposesByEvent(eventId: String): NetworkResult<List<ScanPurposeResponse>> {
            purposeCalls++
            return purposeOutcomes.getOrNull(purposeCalls - 1) ?: NetworkResult.Success(purposes)
        }

        override suspend fun verifyScan(request: TransactionRequest): NetworkResult<ScanVerificationResponse> {
            verifyCalls++
            return verifyResult
        }

        override suspend fun createTransaction(
            request: TransactionRequest,
            purposeCode: ScanPurposeCode,
        ): NetworkResult<TransactionResponse> {
            createRequests += request
            recordGate?.await()
            return recordResults.getOrElse(createRequests.size - 1) { NetworkResult.Error("unexpected scan") }
        }

        override suspend fun rewardRedemptionScan(
            request: RewardRedemptionScanRequest,
        ): NetworkResult<RewardRedemptionScanResponse> {
            rewardCalls++
            return rewardResult
        }

        override suspend fun getIdCardVisibleFields(eventId: String): List<String> = emptyList()

        override suspend fun getAttendeeByEvent(eventId: String, attendeeId: String) = NetworkResult.Success(registration())

        override suspend fun printAttendeeId(eventId: String, attendeeId: String): NetworkResult<IdPrintResponse> {
            printCalls++
            return printResult
        }
    }

    @Before
    fun setUp() {
        SessionManager(ApplicationProvider.getApplicationContext()).apply {
            clearSession()
            saveRole(AccountRole.STAFF)
        }
        RecordingScannerActivity.injectedRepository = null
    }

    private fun scanner(): RecordingScannerActivity {
        val intent = Intent().putExtra(StaffScreenExtras.EXTRA_EVENT_ID, eventId.toString())
        return Robolectric.buildActivity(RecordingScannerActivity::class.java, intent).create().get()
    }

    private fun scanner(fake: StaffRepository): RecordingScannerActivity {
        RecordingScannerActivity.injectedRepository = fake
        return scanner()
    }

    private fun scannerIntent() =
        Intent().putExtra(StaffScreenExtras.EXTRA_EVENT_ID, eventId.toString())

    /** Same as [scanner], but keeps the controller so the activity can be recreated with its state. */
    private fun controller(fake: StaffRepository): ActivityController<RecordingScannerActivity> {
        RecordingScannerActivity.injectedRepository = fake
        return Robolectric.buildActivity(RecordingScannerActivity::class.java, scannerIntent()).create()
    }

    private fun ActivityController<RecordingScannerActivity>.rotate(): RecordingScannerActivity {
        val state = Bundle()
        saveInstanceState(state)
        destroy()
        return Robolectric.buildActivity(RecordingScannerActivity::class.java, scannerIntent())
            .create(state)
            .get()
    }

    private fun assignedEvent() = StaffAssignedEventResponse(
        assignmentId = UUID.randomUUID(),
        eventId = eventId,
        title = "Tech Summit 2026",
        status = EventStatus.ACTIVE,
        canScan = true,
        canPrintId = true,
    )

    private fun scanPurpose(code: ScanPurposeCode = ScanPurposeCode.ENTRY) = ScanPurposeResponse(
        scanPurposeId = purposeId,
        eventId = eventId,
        name = if (code == ScanPurposeCode.ID_PRINT) "ID print" else "Entry",
        code = code,
        active = true,
        trackingOnly = false,
    )

    private fun transaction(
        result: TransactionResult = TransactionResult.APPROVED,
        pointsDelta: Int = 0,
        reason: String? = null,
    ) = TransactionResponse(
        transactionId = UUID.randomUUID(),
        eventId = eventId,
        attendeeUserId = attendeeId,
        attendeeName = "Jane Dela Cruz",
        registrationId = UUID.randomUUID(),
        registrationStatus = RegistrationStatus.ENTERED.name,
        qrCredentialId = qrCredentialId,
        scanPurposeId = purposeId,
        transactionType = TransactionType.ENTRY,
        transactionResult = result,
        pointsDelta = pointsDelta,
        reason = reason,
    )

    private fun verification(
        qrActive: Boolean = true,
        eligible: Boolean? = null,
        message: String? = null,
    ) = ScanVerificationResponse(
        eventId = eventId,
        attendeeUserId = attendeeId,
        registrationId = UUID.randomUUID(),
        qrCredentialId = qrCredentialId,
        qrValue = qrCredentialId.toString(),
        attendeeName = "Jane Dela Cruz",
        registrationStatus = RegistrationStatus.REGISTERED,
        scanPurposeId = purposeId,
        scanPurposeCode = ScanPurposeCode.ID_PRINT,
        qrActive = qrActive,
        message = message,
        eligible = eligible,
    )

    private fun registration() = RegistrationResponse(
        registrationId = UUID.randomUUID(),
        eventId = eventId,
        attendeeUserId = attendeeId,
        attendeeEmail = "jane@test.com",
        attendeeName = "Jane Dela Cruz",
        status = RegistrationStatus.REGISTERED,
        qrCredentialId = qrCredentialId,
    )

    private fun printResponse() = IdPrintResponse(
        printLogId = logId,
        eventId = eventId,
        attendeeUserId = attendeeId,
        registrationId = UUID.randomUUID(),
        qrCredentialId = qrCredentialId,
        reprint = false,
        success = true,
        message = "Print logged",
    )

    private fun rewardScanResponse() = RewardRedemptionScanResponse(
        eventId = eventId,
        attendeeUserId = attendeeId,
        registrationId = UUID.randomUUID(),
        qrCredentialId = qrCredentialId,
        attendeeName = "Jane Dela Cruz",
        registrationStatus = RegistrationStatus.REGISTERED,
        pointsBalance = 40,
        scanPurposeId = purposeId,
        redemptionScanLogId = UUID.randomUUID(),
    )

    private fun httpError(code: Int, message: String): NetworkResult.Error {
        val body = """{"success":false,"message":"$message"}""".toResponseBody("application/json".toMediaType())
        return NetworkResult.Error(message, HttpException(Response.error<Any>(code, body)), message)
    }

    private fun awaitMain(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun text(activity: ScannerActivity, id: Int) =
        activity.findViewById<TextView>(id).text.toString()

    private fun latestDialogTexts(): List<String> =
        org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.decorView.allTexts()

    // ---- verification result

    @Test
    fun verifiedActiveQr_saysVerifiedNotCheckedIn() {
        val activity = scanner()

        activity.showVerifiedResult(verification())

        assertEquals(activity.getString(R.string.ui_staff_result_verified), text(activity, R.id.scanResultState))
        assertEquals(activity.getString(R.string.ui_staff_result_verified_hint), text(activity, R.id.scanResultHint))
        // Verification does not record an ENTRY, so it never borrows the check-in state word.
        assertNotEquals(activity.getString(R.string.ui_staff_result_valid), text(activity, R.id.scanResultState))
        assertEquals("Jane Dela Cruz", text(activity, R.id.scanResultAttendee))
    }

    @Test
    fun verifiedInactiveQr_showsInactiveCopy() {
        val activity = scanner()

        activity.showVerifiedResult(verification(qrActive = false))

        assertEquals(activity.getString(R.string.ui_staff_result_inactive), text(activity, R.id.scanResultState))
        assertEquals(activity.getString(R.string.ui_staff_result_inactive_hint), text(activity, R.id.scanResultHint))
    }

    @Test
    fun verifiedIneligible_showsNotAllowedCopyAndTheServerReason() {
        val activity = scanner()

        activity.showVerifiedResult(verification(eligible = false, message = "Already scanned for this purpose"))

        assertEquals(activity.getString(R.string.ui_staff_result_not_allowed), text(activity, R.id.scanResultState))
        assertEquals("Already scanned for this purpose", text(activity, R.id.scanResultDetail))
        // A blocked pass gets no registration row: the badge is state, not a verdict, and lime on
        // an error block would confirm the opposite of "Not allowed".
        assertEquals(View.GONE, activity.findViewById<View>(R.id.scanResultRegistrationRow).visibility)
    }

    // ---- ID print

    @Test
    fun printRejectedByServer_showsIdPrintFailedNotVerificationRejected() {
        val activity = scanner()
        activity.showVerifiedResult(verification())

        activity.bindPrintFailed("Active ID template not found for event")

        assertEquals(activity.getString(R.string.ui_staff_result_print_failed), text(activity, R.id.scanResultState))
        assertEquals(activity.getString(R.string.ui_staff_result_print_failed_hint), text(activity, R.id.scanResultHint))
        assertEquals("Active ID template not found for event", text(activity, R.id.scanResultDetail))
    }

    @Test
    fun printDialogCancelled_saysCancelledNotPrintSent() {
        val activity = scanner()

        activity.onPrintOutcome(AndroidIdPrinter.PrintOutcome.CANCELLED, "Print logged (no physical printer connected)")

        val texts = latestDialogTexts()
        assertTrue(texts.contains(activity.getString(R.string.staff_print_cancelled_title)))
        assertTrue(texts.contains(activity.getString(R.string.staff_print_cancelled_message)))
        assertFalse(texts.contains(activity.getString(R.string.staff_print_sent_title)))
    }

    @Test
    fun printJobSent_showsPrintSent() {
        val activity = scanner()

        activity.onPrintOutcome(AndroidIdPrinter.PrintOutcome.SENT, "Print logged (no physical printer connected)")

        assertTrue(latestDialogTexts().contains(activity.getString(R.string.staff_print_sent_title)))
    }

    @Test
    fun printJobFailed_showsTheIdPrintFailedState() {
        val activity = scanner()

        activity.onPrintOutcome(AndroidIdPrinter.PrintOutcome.FAILED, "Print logged (no physical printer connected)")

        assertEquals(activity.getString(R.string.ui_staff_result_print_failed), text(activity, R.id.scanResultState))
        assertEquals(activity.getString(R.string.staff_print_job_failed_message), text(activity, R.id.scanResultDetail))
    }

    @Test
    fun idPrintRefusedWith403_keepsThePassAndShowsThePermissionCopy() {
        val fake = FakeRepository(printResult = httpError(403, denialMessage))
        val activity = scanner(fake)
        activity.showVerifiedResult(verification())

        activity.findViewById<View>(R.id.btnScanResultPrimary).performClick()
        awaitMain { fake.printCalls > 0 }

        assertEquals(1, fake.printCalls)
        assertTrue(activity.openedCards.isEmpty())
        assertEquals(
            activity.getString(R.string.staff_print_id_permission_required),
            text(activity, R.id.txtScannerResult),
        )
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scanErrorGroup).visibility)
        // Not the generic ID-print-failed panel: the print was refused, the pass itself still stands.
        assertEquals(activity.getString(R.string.ui_staff_result_verified), text(activity, R.id.scanResultState))
    }

    @Test
    fun idPrintAllowed_opensThePrintDialog() {
        val fake = FakeRepository(printResult = NetworkResult.Success(printResponse()))
        val activity = scanner(fake)
        activity.showVerifiedResult(verification())

        activity.findViewById<View>(R.id.btnScanResultPrimary).performClick()
        awaitMain { activity.openedCards.isNotEmpty() }

        assertEquals(1, activity.openedCards.size)
    }

    // ---- recorded result is the server row, and it survives a recreate

    @Test
    fun recordedRejection_showsTheServerReasonVerbatimAndNoPoints() {
        val activity = scanner()

        activity.showRecordedResult(transaction(TransactionResult.REJECTED, pointsDelta = 5, reason = "Already checked in"))

        assertEquals(activity.getString(R.string.ui_staff_result_rejected), text(activity, R.id.scanResultState))
        assertEquals("Already checked in", text(activity, R.id.scanResultDetail))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.scanResultPointsRow).visibility)
    }

    @Test
    fun rejectedDuplicateScan_neverClaimsAnEnteredRegistration() {
        val activity = scanner()

        // The server sends the registration's CURRENT status on every recorded row, so a duplicate
        // entry rejection still carries ENTERED from the earlier scan. That is not a fact this scan
        // produced, and a lime chip under "Not valid" reads as a confirmation of the failed scan.
        activity.showRecordedResult(transaction(TransactionResult.REJECTED, reason = "Already checked in"))

        assertEquals(activity.getString(R.string.ui_staff_result_rejected), text(activity, R.id.scanResultState))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.scanResultRegistrationRow).visibility)
        assertNotEquals(
            activity.getString(R.string.ui_attendee_entered),
            text(activity, R.id.scanResultRegistrationBadge),
        )
    }

    @Test
    fun rejectedDuplicateScan_keepsTheRegistrationRowHiddenAcrossRecreate() {
        val controller = controller(FakeRepository())
        controller.get().showRecordedResult(transaction(TransactionResult.REJECTED, reason = "Already checked in"))

        val recreated = controller.rotate()

        // The restored panel goes through the same binder, so the rule survives rotation too.
        assertEquals(View.GONE, recreated.findViewById<View>(R.id.scanResultRegistrationRow).visibility)
    }

    @Test
    fun approvedEntry_showsTheRegistrationStatusAndTheEarnedLime() {
        val activity = scanner()

        activity.showRecordedResult(transaction(TransactionResult.APPROVED))

        val badge = activity.findViewById<TextView>(R.id.scanResultRegistrationBadge)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scanResultRegistrationRow).visibility)
        assertEquals(activity.getString(R.string.ui_attendee_entered), badge.text.toString())
        // Lime is a confirmation: on an accepted entry it is earned, never a permanent default.
        assertEquals(activity.getColor(R.color.ui_accent), badge.backgroundTintList?.defaultColor)
    }

    @Test
    fun approvedScanWithPoints_rendersPointsAndDetail_theRejectionRendersNeither() {
        val activity = scanner()

        activity.showRecordedResult(transaction(TransactionResult.APPROVED, pointsDelta = 10, reason = "Booth visit recorded"))

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scanResultPointsRow).visibility)
        assertEquals(activity.getString(R.string.ui_staff_result_points_value, 10), text(activity, R.id.scanResultPoints))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scanResultDetail).visibility)
        assertEquals("Booth visit recorded", text(activity, R.id.scanResultDetail))

        // The other direction: a rejection credits nothing and, with no server reason, adds no
        // detail line - the block must not keep the approved panel's rows.
        activity.showRecordedResult(transaction(TransactionResult.REJECTED, pointsDelta = 10, reason = null))

        assertEquals(View.GONE, activity.findViewById<View>(R.id.scanResultPointsRow).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.scanResultDetail).visibility)
    }

    @Test
    fun recordedResult_survivesRecreateInsteadOfDroppingBackToTheCamera() {
        val controller = controller(FakeRepository())
        val activity = controller.get()
        activity.showRecordedResult(
            transaction(TransactionResult.APPROVED, pointsDelta = 10, reason = "Booth visit recorded"),
        )

        val recreated = controller.rotate()

        assertEquals(recreated.getString(R.string.ui_staff_result_valid), text(recreated, R.id.scanResultState))
        assertEquals("Jane Dela Cruz", text(recreated, R.id.scanResultAttendee))
        assertEquals("Booth visit recorded", text(recreated, R.id.scanResultDetail))
        assertEquals(recreated.getString(R.string.ui_staff_result_points_value, 10), text(recreated, R.id.scanResultPoints))
        assertEquals(View.VISIBLE, recreated.findViewById<View>(R.id.groupScanResult).visibility)
        assertEquals(recreated.getString(R.string.ui_staff_result_scan_next), text(recreated, R.id.btnScanResultPrimary))
    }

    @Test
    fun retryAfterAFailedSave_reusesTheIdempotencyKey() {
        val fake = FakeRepository(
            events = listOf(assignedEvent()),
            purposes = listOf(scanPurpose()),
            recordResults = listOf(NetworkResult.Error("timeout"), NetworkResult.Success(transaction())),
        )
        val activity = controller(fake).get()
        awaitMain { fake.purposeCalls > 0 }

        activity.findViewById<TextView>(R.id.edtScannerQr).text = qrCredentialId.toString()
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        awaitMain { fake.createRequests.size == 1 }
        assertEquals(activity.getString(R.string.ui_staff_result_not_saved), text(activity, R.id.scanResultState))

        activity.findViewById<View>(R.id.btnScanResultPrimary).performClick()
        awaitMain { fake.createRequests.size == 2 }

        val firstId = fake.createRequests[0].clientRequestId
        assertNotNull(firstId)
        assertEquals(firstId, fake.createRequests[1].clientRequestId)
        assertEquals(activity.getString(R.string.ui_staff_result_valid), text(activity, R.id.scanResultState))
    }

    @Test
    fun resultHidesTheScanPanelAndScanNextBringsItBack() {
        val fake = FakeRepository(events = listOf(assignedEvent()), purposes = listOf(scanPurpose()))
        val activity = controller(fake).get()
        awaitMain { fake.purposeCalls > 0 }

        activity.showRecordedResult(transaction())
        assertEquals(View.GONE, activity.findViewById<View>(R.id.scanPanel).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.groupManualEntry).visibility)

        activity.findViewById<View>(R.id.btnScanResultPrimary).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, activity.findViewById<View>(R.id.groupScanResult).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scanPanel).visibility)
    }

    @Test
    fun purposeMicroLineHidesWithTheChromeAndComesBackUntouchedAfterScanNext() {
        val fake = FakeRepository(events = listOf(assignedEvent()), purposes = listOf(scanPurpose()))
        val activity = controller(fake).get()
        awaitMain { fake.purposeCalls > 0 }

        val note = activity.findViewById<TextView>(R.id.txtSelectedPurposePoints)
        assertEquals(View.VISIBLE, note.visibility)
        val noteText = note.text.toString()
        assertTrue(noteText.isNotBlank())

        // §5.13: the micro line belongs to the guidance row, so it cannot float under the context
        // bar while the result owns the screen.
        activity.showRecordedResult(transaction())
        assertEquals(View.GONE, note.visibility)

        activity.findViewById<View>(R.id.btnScanResultPrimary).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, note.visibility)
        assertEquals(noteText, note.text.toString())
    }

    // ---- a decode-failure strip never survives the next scan (§5.13, §9.6:18)

    @Test
    fun successfulScanAfterADecodeFailure_clearsTheErrorStrip() {
        val fake = FakeRepository(
            events = listOf(assignedEvent()),
            purposes = listOf(scanPurpose()),
            recordResults = listOf(NetworkResult.Success(transaction())),
        )
        val activity = controller(fake).get()
        awaitMain { fake.purposeCalls > 0 }

        activity.showScanError("Couldn't read that pass.")
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scanErrorGroup).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.txtScannerResult).visibility)

        activity.findViewById<TextView>(R.id.edtScannerQr).text = qrCredentialId.toString()
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        awaitMain { fake.createRequests.size == 1 }

        // The accepted scan owns the screen: no stale error banner with a live Retry over it.
        assertEquals(View.GONE, activity.findViewById<View>(R.id.scanErrorGroup).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.groupScanResult).visibility)
        assertEquals(activity.getString(R.string.ui_staff_result_valid), text(activity, R.id.scanResultState))
    }

    @Test
    fun scanNextAfterADecodeFailure_clearsTheErrorStrip() {
        val activity = scanner(FakeRepository())

        activity.showScanError("Couldn't read that pass.")
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scanErrorGroup).visibility)

        activity.showRecordedResult(transaction())
        activity.findViewById<View>(R.id.btnScanResultPrimary).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, activity.findViewById<View>(R.id.scanErrorGroup).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.txtScannerResult).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.groupScanResult).visibility)
    }

    // ---- the approved copy follows the purpose the staff actually used (§6.3)

    @Test
    fun approvedExit_saysCheckedOutNotCheckedIn() {
        val activity = scanner()

        activity.showRecordedResult(transaction(TransactionResult.APPROVED).copy(transactionType = TransactionType.EXIT))

        assertEquals(activity.getString(R.string.ui_staff_result_checked_out), text(activity, R.id.scanResultState))
        assertEquals(activity.getString(R.string.ui_staff_result_checked_out_hint), text(activity, R.id.scanResultHint))
    }

    @Test
    fun approvedBoothVisit_saysBoothVisitNotCheckedIn() {
        val activity = scanner()

        activity.showRecordedResult(
            transaction(TransactionResult.APPROVED).copy(transactionType = TransactionType.BOOTH_VISIT),
        )

        assertEquals(activity.getString(R.string.ui_staff_result_booth_visit), text(activity, R.id.scanResultState))
        assertEquals(activity.getString(R.string.ui_staff_result_booth_visit_hint), text(activity, R.id.scanResultHint))
    }

    @Test
    fun approvedWithPoints_keepsThePurposeStateAndThePointsHint() {
        val activity = scanner()

        activity.showRecordedResult(
            transaction(TransactionResult.APPROVED, pointsDelta = 5).copy(transactionType = TransactionType.EXIT),
        )

        // The action word still names the purpose; points win the hint line, never the state.
        assertEquals(activity.getString(R.string.ui_staff_result_checked_out), text(activity, R.id.scanResultState))
        assertEquals(
            activity.getString(R.string.staff_transaction_result_points_awarded_to_attendee),
            text(activity, R.id.scanResultHint),
        )
    }

    // ---- ID print: a lookup failure is not a failed save, and the verified panel survives rotation

    @Test
    fun idPrintVerifyFailure_keepsTheStripAndNeverClaimsScanNotSaved() {
        val fake = FakeRepository(
            events = listOf(assignedEvent()),
            purposes = listOf(scanPurpose(ScanPurposeCode.ID_PRINT)),
            verifyResult = NetworkResult.Error("No pass matches that ID."),
        )
        val controller = controller(fake)
        val activity = controller.get()
        awaitMain { fake.purposeCalls > 0 }

        activity.findViewById<TextView>(R.id.edtScannerQr).text = qrCredentialId.toString()
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        awaitMain { fake.verifyCalls == 1 }

        assertEquals(View.GONE, activity.findViewById<View>(R.id.groupScanResult).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.txtScannerResult).visibility)
        assertEquals("No pass matches that ID.", text(activity, R.id.txtScannerResult))

        // A rotation replays the held ID_PRINT lookup, and the failed replay still returns to the
        // camera with the strip instead of a save-failed panel.
        val recreated = controller.rotate()
        awaitMain { fake.verifyCalls == 2 }

        assertEquals(View.GONE, recreated.findViewById<View>(R.id.groupScanResult).visibility)
        assertEquals("No pass matches that ID.", text(recreated, R.id.txtScannerResult))
    }

    @Test
    fun verifiedIdPrintPanel_survivesRecreateAndStillPrints() {
        val fake = FakeRepository(
            printResult = NetworkResult.Success(printResponse()),
            events = listOf(assignedEvent()),
            purposes = listOf(scanPurpose(ScanPurposeCode.ID_PRINT)),
            verifyResult = NetworkResult.Success(verification()),
        )
        val controller = controller(fake)
        val activity = controller.get()
        awaitMain { fake.purposeCalls > 0 }

        activity.findViewById<TextView>(R.id.edtScannerQr).text = qrCredentialId.toString()
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        awaitMain { fake.verifyCalls == 1 }
        assertEquals(activity.getString(R.string.ui_staff_result_verified), text(activity, R.id.scanResultState))

        val recreated = controller.rotate()

        assertEquals(recreated.getString(R.string.ui_staff_result_verified), text(recreated, R.id.scanResultState))
        assertEquals(
            recreated.getString(R.string.ui_staff_result_print_id),
            text(recreated, R.id.btnScanResultPrimary),
        )

        recreated.findViewById<View>(R.id.btnScanResultPrimary).performClick()
        awaitMain { recreated.openedCards.isNotEmpty() }

        assertEquals(1, recreated.openedCards.size)
        assertEquals("Jane Dela Cruz", recreated.openedCards.single().attendeeName)
    }

    // ---- load failures are in-flow states, and a dead print panel never comes back as one

    @Test
    fun failedEventLoad_rendersAnInlineRetryInsteadOfABlankScreen() {
        val fake = FakeRepository(
            eventsOutcomes = listOf(
                NetworkResult.Error("Couldn't reach the server."),
                NetworkResult.Success(listOf(assignedEvent())),
            ),
            purposes = listOf(scanPurpose()),
        )
        val activity = controller(fake).get()
        awaitMain { fake.eventCalls == 1 }

        val empty = activity.findViewById<EventQrEmptyState>(R.id.txtScannerEmptyState)
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals(activity.getString(R.string.ui_staff_events_error_title), empty.title.toString())
        assertEquals("Couldn't reach the server.", empty.subtext.toString())

        activity.findViewById<View>(R.id.emptyAction).performClick()
        awaitMain { fake.eventCalls == 2 }

        assertEquals(View.GONE, empty.visibility)
    }

    @Test
    fun failedPurposeLoad_dropsThePreviousEventsPurposesAndLetsTheSameEventRetry() {
        val secondEventId = UUID.fromString("55555555-5555-5555-5555-555555555555")
        val fake = FakeRepository(
            events = listOf(assignedEvent(), assignedEvent().copy(eventId = secondEventId, title = "Second event")),
            purposeOutcomes = listOf(
                NetworkResult.Success(listOf(scanPurpose())),
                NetworkResult.Error("Purpose load failed."),
                NetworkResult.Success(listOf(scanPurpose())),
            ),
        )
        val activity = controller(fake).get()
        awaitMain { fake.purposeCalls == 1 }

        activity.commitEventSelection(1)
        awaitMain { fake.purposeCalls >= 2 }

        // The failed load left no purpose bound: the field reads the unavailable copy and no
        // purpose is selected, so the next scan cannot post event B under event A's purpose.
        assertEquals(
            activity.getString(R.string.scanner_no_scan_purposes_enabled),
            text(activity, R.id.scanContextPurpose),
        )
        assertNull(activity.selectedPurpose())

        // The de-dupe marker was cleared, so the same event asks again instead of trusting a list
        // that never arrived.
        val callsBeforeRetry = fake.purposeCalls
        activity.commitEventSelection(1)
        awaitMain { fake.purposeCalls > callsBeforeRetry }

        assertEquals(callsBeforeRetry + 1, fake.purposeCalls)
        assertEquals(activity.getString(R.string.scan_purpose_type_event_entry), text(activity, R.id.scanContextPurpose))
    }

    @Test
    fun switchingEvent_clearsTheOldPurposesBeforeTheNewListLands() {
        val secondEventId = UUID.fromString("55555555-5555-5555-5555-555555555555")
        val fake = FakeRepository(
            events = listOf(assignedEvent(), assignedEvent().copy(eventId = secondEventId, title = "Second event")),
            // The new event's list never settles: the field must stay closed instead of holding
            // the previous event's purposes.
            purposeOutcomes = listOf(NetworkResult.Success(listOf(scanPurpose())), NetworkResult.Loading),
        )
        val activity = controller(fake).get()
        awaitMain { fake.purposeCalls >= 1 }

        activity.commitEventSelection(1)
        awaitMain { fake.purposeCalls >= 2 }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            activity.getString(R.string.manage_scan_purposes_loading),
            text(activity, R.id.scanContextPurpose),
        )
        assertNull(activity.selectedPurpose())

        // A manual submit before the list is known must post nothing: no event B request under
        // event A's scanPurposeId.
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(fake.createRequests.isEmpty())
    }

    @Test
    fun switchingEvent_toAnEventWithNoPurposesLeavesTheScanClosed() {
        val secondEventId = UUID.fromString("55555555-5555-5555-5555-555555555555")
        val fake = FakeRepository(
            events = listOf(assignedEvent(), assignedEvent().copy(eventId = secondEventId, title = "Second event")),
            purposeOutcomes = listOf(NetworkResult.Success(listOf(scanPurpose())), NetworkResult.Success(emptyList())),
        )
        val activity = controller(fake).get()
        awaitMain { fake.purposeCalls >= 1 }

        activity.commitEventSelection(1)
        awaitMain { fake.purposeCalls >= 2 }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            activity.getString(R.string.scanner_no_scan_purposes_enabled),
            text(activity, R.id.scanContextPurpose),
        )
        assertNull(activity.selectedPurpose())

        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(fake.createRequests.isEmpty())
    }

    @Test
    fun committingTheAlreadySelectedEventKeepsItsPurposes() {
        val fake = FakeRepository(
            events = listOf(assignedEvent(), assignedEvent().copy(eventId = UUID.randomUUID(), title = "Second event")),
            purposes = listOf(scanPurpose()),
        )
        val activity = controller(fake).get()
        awaitMain { fake.purposeCalls >= 1 }
        val callsBefore = fake.purposeCalls

        // The sheet lists the current event too; tapping it is not a switch and must not leave the
        // purpose field waiting on a load that the de-dupe marker would never start.
        activity.commitEventSelection(0)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(callsBefore, fake.purposeCalls)
        assertNotNull(activity.selectedPurpose())
        assertEquals(activity.getString(R.string.scan_purpose_type_event_entry), text(activity, R.id.scanContextPurpose))
    }

    @Test
    fun printFailedPanel_rotatesIntoScanNextNotPrintId() {
        val fake = FakeRepository(
            events = listOf(assignedEvent()),
            purposes = listOf(scanPurpose(ScanPurposeCode.ID_PRINT)),
            verifyResult = NetworkResult.Success(verification()),
        )
        val controller = controller(fake)
        val activity = controller.get()
        awaitMain { fake.purposeCalls > 0 }

        activity.findViewById<TextView>(R.id.edtScannerQr).text = qrCredentialId.toString()
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        awaitMain { fake.verifyCalls == 1 }
        activity.bindPrintFailed("Printer offline")

        val recreated = controller.rotate()

        // The print context died with the failed print: a rotation may not offer an enabled
        // "Print ID" for a pass that is no longer on the panel.
        assertEquals(recreated.getString(R.string.ui_staff_result_scan_next), text(recreated, R.id.btnScanResultPrimary))
        awaitMain { recreated.findViewById<View>(R.id.btnScanResultPrimary).isEnabled }
        assertTrue(recreated.findViewById<View>(R.id.btnScanResultPrimary).isEnabled)
    }

    // ---- submission: one key per decoded pass, one request per pass, one endpoint per purpose

    @Test
    fun eachDecodedPassPostsOnce_andDistinctPassesGetDistinctKeys() {
        val fake = FakeRepository(
            events = listOf(assignedEvent()),
            purposes = listOf(scanPurpose()),
            recordResults = listOf(NetworkResult.Success(transaction()), NetworkResult.Success(transaction())),
        )
        val activity = controller(fake).get()
        awaitMain { fake.purposeCalls > 0 }

        val qr = activity.findViewById<TextView>(R.id.edtScannerQr)
        qr.text = qrCredentialId.toString()
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        awaitMain { fake.createRequests.size == 1 }
        assertEquals(1, fake.createRequests.size)

        // The same pass again inside the duplicate window is not a second scan.
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, fake.createRequests.size)

        qr.text = UUID.randomUUID().toString()
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        awaitMain { fake.createRequests.size == 2 }

        assertEquals(1, fake.createRequests.count { it.qrValue == qrCredentialId.toString() })
        assertNotEquals(fake.createRequests[0].clientRequestId, fake.createRequests[1].clientRequestId)
    }

    @Test
    fun rewardRedemptionUsesItsOwnEndpoint_andIsNeverReplayedAsATransaction() {
        val fake = FakeRepository(
            events = listOf(assignedEvent()),
            purposes = listOf(scanPurpose(ScanPurposeCode.REWARD_REDEMPTION_SCAN)),
            rewardResult = NetworkResult.Success(rewardScanResponse()),
        )
        val controller = controller(fake)
        val activity = controller.get()
        awaitMain { fake.purposeCalls > 0 }

        activity.findViewById<TextView>(R.id.edtScannerQr).text = qrCredentialId.toString()
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        awaitMain { fake.rewardCalls == 1 }

        // The redemption endpoint takes no idempotency key, so the pass must be released at once:
        // a rotation that replayed it through the transaction path would write the points twice.
        controller.rotate()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, fake.rewardCalls)
        assertTrue(fake.createRequests.isEmpty())
    }

    @Test
    fun failedReplayLandsOnSaveFailedWithARetry_notOnADeadSavingScan() {
        val fake = FakeRepository(
            events = listOf(assignedEvent()),
            purposes = listOf(scanPurpose()),
            recordResults = listOf(NetworkResult.Error("timeout"), NetworkResult.Error("timeout again")),
        )
        val controller = controller(fake)
        val activity = controller.get()
        awaitMain { fake.purposeCalls > 0 }

        activity.findViewById<TextView>(R.id.edtScannerQr).text = qrCredentialId.toString()
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        awaitMain { fake.createRequests.size == 1 }
        assertEquals(activity.getString(R.string.ui_staff_result_not_saved), text(activity, R.id.scanResultState))

        val recreated = controller.rotate()
        awaitMain { fake.createRequests.size == 2 }
        awaitMain { text(recreated, R.id.scanResultState) != recreated.getString(R.string.ui_staff_result_logging) }

        // The failed replay keeps its key and offers the one way out instead of a dead primary.
        assertEquals(fake.createRequests[0].clientRequestId, fake.createRequests[1].clientRequestId)
        assertEquals(recreated.getString(R.string.ui_staff_result_not_saved), text(recreated, R.id.scanResultState))
        assertEquals(recreated.getString(R.string.ui_staff_result_try_again), text(recreated, R.id.btnScanResultPrimary))
        assertTrue(recreated.findViewById<View>(R.id.btnScanResultPrimary).isEnabled)
        assertEquals(View.VISIBLE, recreated.findViewById<View>(R.id.resultActionBar).visibility)
        assertEquals(View.VISIBLE, recreated.findViewById<View>(R.id.groupScanResult).visibility)
        assertEquals(View.GONE, recreated.findViewById<View>(R.id.scanPanel).visibility)
    }

    @Test
    fun aReplayInFlightKeepsThePrimaryDisabled() {
        val gate = CompletableDeferred<Unit>()
        val fake = FakeRepository(
            events = listOf(assignedEvent()),
            purposes = listOf(scanPurpose()),
            recordResults = listOf(NetworkResult.Success(transaction())),
            recordGate = gate,
        )
        val controller = controller(fake)
        val activity = controller.get()
        awaitMain { fake.purposeCalls > 0 }

        activity.findViewById<TextView>(R.id.edtScannerQr).text = qrCredentialId.toString()
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        awaitMain { fake.createRequests.size == 1 }

        // The rotation replays the held pass: the panel reads "Saving scan." with no action bound.
        val recreated = controller.rotate()
        awaitMain { fake.createRequests.size == 2 }
        assertEquals(recreated.getString(R.string.ui_staff_result_logging), text(recreated, R.id.scanResultState))
        assertFalse(recreated.findViewById<View>(R.id.btnScanResultPrimary).isEnabled)
        assertEquals(View.GONE, recreated.findViewById<View>(R.id.resultActionBar).visibility)

        // The events and purposes loads settle while the save is still in flight: a racing
        // showLoading(false) must not turn the "Saving scan." primary into a tappable no-op.
        recreated.showLoading(false)
        assertFalse(recreated.findViewById<View>(R.id.btnScanResultPrimary).isEnabled)

        gate.complete(Unit)
        awaitMain { text(recreated, R.id.scanResultState) == recreated.getString(R.string.ui_staff_result_valid) }
        assertTrue(recreated.findViewById<View>(R.id.btnScanResultPrimary).isEnabled)
    }

    @Test
    fun switchingEventDropsTheStaleFailureAndItsIdempotencyKey() {
        val secondEventId = UUID.fromString("55555555-5555-5555-5555-555555555555")
        val fake = FakeRepository(
            events = listOf(assignedEvent(), assignedEvent().copy(eventId = secondEventId, title = "Second event")),
            purposes = listOf(scanPurpose()),
            recordResults = listOf(NetworkResult.Error("timeout")),
        )
        val controller = controller(fake)
        val activity = controller.get()
        awaitMain { fake.purposeCalls > 0 }

        activity.findViewById<TextView>(R.id.edtScannerQr).text = qrCredentialId.toString()
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        awaitMain { fake.createRequests.size == 1 }
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.groupScanResult).visibility)

        activity.commitEventSelection(1)
        awaitMain { fake.purposeCalls >= 2 }

        // The pass, its key and the result belong to the event just left: none of them survives.
        assertEquals(View.GONE, activity.findViewById<View>(R.id.groupScanResult).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.resultActionBar).visibility)

        controller.rotate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, fake.createRequests.size)
    }

    @Test
    fun stripRetryWithNothingHeldReturnsToTheLiveCamera() {
        val fake = FakeRepository(events = listOf(assignedEvent()), purposes = listOf(scanPurpose()))
        val activity = controller(fake).get()
        awaitMain { fake.purposeCalls > 0 }

        activity.showScanError("Couldn't read that pass.")
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scanErrorGroup).visibility)

        activity.findViewById<View>(R.id.btnScanRetry).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, activity.findViewById<View>(R.id.scanErrorGroup).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.groupScanResult).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scanPanel).visibility)
        assertTrue(fake.createRequests.isEmpty())
    }

    @Test
    fun printPanelAfterRecreateUsesTheCapturedEventName_notAnEmptyList() {
        val fake = FakeRepository(
            printResult = NetworkResult.Success(printResponse()),
            events = listOf(assignedEvent()),
            purposes = listOf(scanPurpose(ScanPurposeCode.ID_PRINT)),
            verifyResult = NetworkResult.Success(verification()),
            // The recreated screen's event load never settles, so no event is selected.
            eventsOutcomes = listOf(NetworkResult.Success(listOf(assignedEvent())), NetworkResult.Loading),
        )
        val controller = controller(fake)
        val activity = controller.get()
        awaitMain { fake.purposeCalls > 0 }

        activity.findViewById<TextView>(R.id.edtScannerQr).text = qrCredentialId.toString()
        activity.findViewById<View>(R.id.btnSubmitScan).performClick()
        awaitMain { fake.verifyCalls == 1 }
        assertEquals(activity.getString(R.string.ui_staff_result_print_id), text(activity, R.id.btnScanResultPrimary))

        val recreated = controller.rotate()
        awaitMain { fake.eventCalls == 2 }
        assertNull(recreated.selectedEvent())

        // The event name comes from the verified pass, not from an options list that never loaded.
        recreated.findViewById<View>(R.id.btnScanResultPrimary).performClick()
        awaitMain { recreated.openedCards.isNotEmpty() }

        assertEquals("Tech Summit 2026", recreated.openedCards.single().eventName)
    }

    // ---- B1: the camera prompt follows the screen taking focus, not the async event load

    @Test
    fun cameraPermissionIsRequestedOnFocusBeforeTheEventListLoads() {
        val gate = CompletableDeferred<Unit>()
        val fake = FakeRepository(
            events = listOf(assignedEvent()),
            purposes = listOf(scanPurpose()),
            eventsGate = gate,
        )
        RecordingScannerActivity.injectedRepository = fake

        val controller = Robolectric.buildActivity(RecordingScannerActivity::class.java, scannerIntent())
            .create()
            .start()
            .resume()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        // The focus prompt is already out while the assigned-event read is still parked inside the
        // fake: the camera never waited for the list.
        val request = shadowOf(activity).lastRequestedPermission
        assertNotNull(request)
        assertTrue(request!!.requestedPermissions.contains(Manifest.permission.CAMERA))
        assertEquals(1, fake.eventCalls)
        assertNull(activity.selectedEvent())

        gate.complete(Unit)
        awaitMain { activity.selectedEvent() != null }
    }
}
