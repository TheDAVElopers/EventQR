package com.thedavelopers.eventqr.features.staff.result

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.idprinting.AndroidIdPrinter
import com.thedavelopers.eventqr.features.idprinting.PrintPermission
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.StaffBadges
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.staff.details.StaffAttendeeDetailsActivity
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.ui.theme.applyEventQrBottomInsetPadding
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Staff scan result (§6.3). A flat semantic block carries the state; a spec table carries the
 * facts; the footer carries exactly one primary action. All state text comes from strings so the
 * heading, the hint and the badge can never disagree.
 */
open class StaffScanResultActivity : AppCompatActivity() {
    private val tag = "StaffQrTransaction"
    private lateinit var repository: StaffRepository
    private lateinit var sessionManager: SessionManager
    private var savingTransaction = false
    private var scanState = ScanResultState.REJECTED

    // One scan-result screen is one scan. Reusing this id on every retry tap (and across a
    // rotation) is what stops a timed-out-but-logged scan from being logged twice.
    private lateinit var clientRequestId: UUID

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CLIENT_REQUEST_ID, clientRequestId.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clientRequestId = savedInstanceState?.getString(STATE_CLIENT_REQUEST_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: UUID.randomUUID()
        sessionManager = SessionManager(this)
        if (!RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.STAFF)) {
            Toast.makeText(this, this.getString(R.string.staff_dashboard_access_denied_staff_or_above), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_scan_result)
        findViewById<View>(R.id.headerTopBar).applyEventQrTopInsetPadding()
        findViewById<View>(R.id.layoutFooter).applyEventQrBottomInsetPadding()
        repository = newRepository()
        findViewById<TextView>(R.id.topBarTitle).text = getString(R.string.ui_staff_result_title)

        val isValid = intent.getBooleanExtra(StaffScreenExtras.EXTRA_IS_VALID, false)
        scanState = ScanResultState.from(
            isValid = isValid,
            qrActive = intent.getBooleanExtra(StaffScreenExtras.EXTRA_QR_ACTIVE, true),
            registrationStatus = intent.getStringExtra(StaffScreenExtras.EXTRA_REGISTRATION_STATUS),
            eligible = intent.getBooleanExtra(StaffScreenExtras.EXTRA_ELIGIBLE, true),
        )
        bindStaticFields(isValid)
        applyActionLabels()

        findViewById<View>(R.id.btnContinueTransaction).setOnClickListener {
            if (scanState == ScanResultState.ACTIVE) {
                recordTransaction()
            }
        }
        findViewById<View>(R.id.btnViewAttendeeDetails).setOnClickListener { openAttendeeDetails() }
        findViewById<View>(R.id.btnScanAgain).setOnClickListener { finish() }
        // Header back behaves like system back: the scanner that opened this screen is still
        // underneath, so starting a new one would stack duplicate scanners.
        findViewById<View>(R.id.topBarBack).setOnClickListener { finish() }
    }

    /** Overridden in tests to inject a fake repository. */
    @VisibleForTesting
    internal open fun newRepository(): StaffRepository = StaffRepository(this)

    private fun applyActionLabels() {
        val purposeCode = intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_CODE).orEmpty()
        findViewById<TextView>(R.id.btnContinueTransaction).text =
            getString(
                if (purposeCode == ScanPurposeCode.ID_PRINT.name) {
                    R.string.ui_staff_result_print_id
                } else {
                    R.string.ui_staff_result_log_transaction
                },
            )
    }

    private fun bindStaticFields(isValid: Boolean) {
        findViewById<TextView>(R.id.txtScanResultEvent).text =
            intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_TITLE)?.takeIf { it.isNotBlank() }
                ?: getString(R.string.staff_scan_result_event)
        findViewById<TextView>(R.id.txtScanResultPurpose).text =
            intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_NAME)?.takeIf { it.isNotBlank() }
                ?: getString(R.string.ui_staff_result_purpose)
        findViewById<TextView>(R.id.txtScanResultReason).text =
            intent.getStringExtra(StaffScreenExtras.EXTRA_MESSAGE)?.takeIf { it.isNotBlank() }
                ?: getString(R.string.ui_staff_result_no_reason)

        if (isValid) {
            findViewById<View>(R.id.headerApproved).visibility = View.VISIBLE
            findViewById<View>(R.id.headerRejected).visibility = View.GONE
            findViewById<View>(R.id.layoutApprovedDetails).visibility = View.VISIBLE
            findViewById<View>(R.id.layoutRejectedReason).visibility = View.GONE
            findViewById<View>(R.id.cardVerificationDetails).visibility = View.VISIBLE

            findViewById<TextView>(R.id.txtScanResultAttendeeName).text =
                intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME)?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.ui_staff_result_attendee)
            findViewById<TextView>(R.id.txtScanResultAttendeeEmail).text =
                intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_EMAIL).orEmpty()
            val registrationStatus = currentRegistrationStatus()
            findViewById<View>(R.id.txtScanResultRegistrationStatus).visibility =
                if (registrationStatus == null) View.GONE else View.VISIBLE
            StaffBadges.bind(findViewById(R.id.txtScanResultRegistrationStatus), registrationStatus)
            findViewById<TextView>(R.id.txtScanResultStatusHint).text =
                intent.getStringExtra(StaffScreenExtras.EXTRA_MESSAGE)?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.ui_staff_result_valid_hint)
            findViewById<View>(R.id.btnContinueTransaction).visibility = View.VISIBLE
            findViewById<View>(R.id.btnViewAttendeeDetails).visibility = View.VISIBLE

            findViewById<TextView>(R.id.txtScanResultVerifiedAt).text =
                formatVerifiedAt(intent.getStringExtra(StaffScreenExtras.EXTRA_VERIFIED_AT))

            if (scanState == ScanResultState.INACTIVE) bindInactiveState()
            if (scanState == ScanResultState.NOT_ELIGIBLE) bindNotEligibleState()
        } else {
            findViewById<View>(R.id.headerApproved).visibility = View.GONE
            findViewById<View>(R.id.headerRejected).visibility = View.VISIBLE
            findViewById<TextView>(R.id.txtScanResultStateRejected).text = getString(R.string.ui_staff_result_rejected)
            findViewById<TextView>(R.id.txtScanResultStatusHintRejected).text = getString(R.string.staff_scan_result_backend_verification_rejected_the_sc)
            findViewById<View>(R.id.layoutApprovedDetails).visibility = View.GONE
            findViewById<View>(R.id.layoutRejectedReason).visibility = View.VISIBLE
            findViewById<View>(R.id.cardVerificationDetails).visibility = View.GONE
            findViewById<View>(R.id.btnContinueTransaction).visibility = View.GONE
            findViewById<View>(R.id.btnViewAttendeeDetails).visibility = View.GONE
        }
    }

    /** Verified attendee, but the QR / registration is not active: show why and make logging impossible. */
    private fun bindInactiveState() {
        demoteBadge()
        findViewById<View>(R.id.headerApproved).visibility = View.GONE
        findViewById<View>(R.id.headerRejected).visibility = View.VISIBLE
        findViewById<TextView>(R.id.txtScanResultStateRejected).text = getString(R.string.ui_staff_result_inactive)
        findViewById<TextView>(R.id.txtScanResultStatusHintRejected).text = getString(R.string.ui_staff_result_inactive_hint)
        findViewById<TextView>(R.id.txtScanResultReason).text =
            intent.getStringExtra(StaffScreenExtras.EXTRA_MESSAGE)?.takeIf { it.isNotBlank() }
                ?: getString(R.string.staff_scan_result_qr_inactive_reason)
        findViewById<View>(R.id.layoutRejectedReason).visibility = View.VISIBLE
        findViewById<View>(R.id.cardVerificationDetails).visibility = View.GONE
        findViewById<View>(R.id.btnContinueTransaction).visibility = View.GONE
    }

    /** Active QR, but the backend says this scan would be rejected: show the backend reason. */
    private fun bindNotEligibleState() {
        demoteBadge()
        findViewById<View>(R.id.headerApproved).visibility = View.GONE
        findViewById<View>(R.id.headerRejected).visibility = View.VISIBLE
        findViewById<TextView>(R.id.txtScanResultStateRejected).text = getString(R.string.ui_staff_result_not_allowed)
        findViewById<TextView>(R.id.txtScanResultStatusHintRejected).text = getString(R.string.ui_staff_result_not_allowed_hint)
        findViewById<TextView>(R.id.txtScanResultReason).text =
            intent.getStringExtra(StaffScreenExtras.EXTRA_MESSAGE)?.takeIf { it.isNotBlank() }
                ?: getString(R.string.staff_scan_result_not_allowed_reason)
        findViewById<View>(R.id.layoutRejectedReason).visibility = View.VISIBLE
        findViewById<View>(R.id.cardVerificationDetails).visibility = View.GONE
        findViewById<View>(R.id.btnContinueTransaction).visibility = View.GONE
    }

    private fun bindRejectedResult(message: String) {
        demoteBadge()
        findViewById<View>(R.id.headerApproved).visibility = View.GONE
        findViewById<View>(R.id.headerRejected).visibility = View.VISIBLE
        findViewById<TextView>(R.id.txtScanResultStateRejected).text = getString(R.string.ui_staff_result_rejected)
        findViewById<TextView>(R.id.txtScanResultStatusHintRejected).text = getString(R.string.staff_scan_result_transaction_could_not_be_completed)
        findViewById<TextView>(R.id.txtScanResultReason).text = message
        findViewById<View>(R.id.layoutApprovedDetails).visibility = View.GONE
        findViewById<View>(R.id.layoutRejectedReason).visibility = View.VISIBLE
        findViewById<View>(R.id.cardVerificationDetails).visibility = View.GONE
        findViewById<View>(R.id.btnContinueTransaction).visibility = View.GONE
    }

    private fun currentRegistrationStatus(): com.thedavelopers.eventqr.core.api.dto.RegistrationStatus? =
        intent.getStringExtra(StaffScreenExtras.EXTRA_REGISTRATION_STATUS)
            ?.let { raw ->
                runCatching {
                    com.thedavelopers.eventqr.core.api.dto.RegistrationStatus.valueOf(raw.trim().uppercase())
                }.getOrNull()
            }

    private fun demoteBadge() {
        val badge = findViewById<TextView>(R.id.txtScanResultRegistrationStatus)
        if (badge.visibility == View.VISIBLE) {
            StaffBadges.bind(badge, currentRegistrationStatus(), confirmed = false)
        }
    }

    private fun recordTransaction() {
        if (scanState != ScanResultState.ACTIVE) return
        if (savingTransaction) {
            Toast.makeText(this, this.getString(R.string.staff_scan_result_transaction_save_already_in_progress), Toast.LENGTH_SHORT).show()
            return
        }
        val eventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID).orEmpty()
        val purposeId = intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_ID).orEmpty()
        val qrValue = intent.getStringExtra(StaffScreenExtras.EXTRA_QR_VALUE).orEmpty()
        val staffUserId = intent.getStringExtra(StaffScreenExtras.EXTRA_STAFF_USER_ID).orEmpty().ifBlank { sessionManager.getUserId().orEmpty() }
        val purposeCode = intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_CODE).orEmpty()
        val attendeeId = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID).orEmpty()

        val parsedPurposeCode = runCatching { ScanPurposeCode.valueOf(purposeCode) }.getOrNull()

        if (parsedPurposeCode == ScanPurposeCode.ID_PRINT) {
            triggerPrint(eventId, attendeeId)
            return
        }

        if (eventId.isBlank() || purposeId.isBlank() || qrValue.isBlank() || staffUserId.isBlank() || purposeCode.isBlank()) {
            Toast.makeText(this, this.getString(R.string.staff_scan_result_transaction_failed_missing_scan_cont), Toast.LENGTH_SHORT).show()
            return
        }

        val parsedEventId = runCatching { UUID.fromString(eventId) }.getOrNull()
        val parsedPurposeId = runCatching { UUID.fromString(purposeId) }.getOrNull()
        val parsedStaffUserId = runCatching { UUID.fromString(staffUserId) }.getOrNull()
        if (parsedEventId == null || parsedPurposeId == null || parsedStaffUserId == null || parsedPurposeCode == null) {
            Toast.makeText(this, this.getString(R.string.staff_scan_result_transaction_failed_invalid_scan_cont), Toast.LENGTH_SHORT).show()
            Log.w(tag, "invalid context eventId=$eventId purposeId=$purposeId staffUserId=$staffUserId purposeCode=$purposeCode")
            return
        }

        savingTransaction = true
        findViewById<LinearProgressIndicator>(R.id.progressTransactionInline).visibility = View.VISIBLE
        findViewById<View>(R.id.btnContinueTransaction).isEnabled = false
        findViewById<TextView>(R.id.btnContinueTransaction).text = getString(R.string.ui_staff_result_logging)

        lifecycleScope.launch {
            val request = TransactionRequest(
                eventId = parsedEventId,
                scanPurposeId = parsedPurposeId,
                qrValue = qrValue,
                staffUserId = parsedStaffUserId,
                clientRequestId = clientRequestId,
            )
            when (val result = repository.createTransaction(request, parsedPurposeCode)) {
                is NetworkResult.Success -> {
                    Log.d(
                        tag,
                        "backendSaveResult success=true transactionId=${result.data.transactionId} eventId=${result.data.eventId} scanPurposeId=${result.data.scanPurposeId} transactionResult=${result.data.transactionResult}",
                    )
                    openTransactionResult(result.data)
                }
                is NetworkResult.Error -> {
                    val message = "Transaction failed: ${result.message}"
                    Log.w(tag, "backendSaveResult success=false eventId=$eventId scanPurposeId=$purposeId")
                    Toast.makeText(this@StaffScanResultActivity, message, Toast.LENGTH_SHORT).show()
                    bindRejectedResult(message)
                }
                NetworkResult.Loading -> Unit
            }
            savingTransaction = false
            findViewById<LinearProgressIndicator>(R.id.progressTransactionInline).visibility = View.GONE
            findViewById<View>(R.id.btnContinueTransaction).isEnabled = true
            applyActionLabels()
        }
    }

    private fun triggerPrint(eventId: String, attendeeId: String) {
        if (eventId.isBlank() || attendeeId.isBlank()) {
            Toast.makeText(this, this.getString(R.string.staff_scan_result_print_failed_missing_attendee_contex), Toast.LENGTH_SHORT).show()
            return
        }

        savingTransaction = true
        findViewById<LinearProgressIndicator>(R.id.progressTransactionInline).visibility = View.VISIBLE
        findViewById<View>(R.id.btnContinueTransaction).isEnabled = false
        findViewById<TextView>(R.id.btnContinueTransaction).text = getString(R.string.ui_staff_result_printing)

        lifecycleScope.launch {
            val eventName = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_TITLE).orEmpty()
            val attendeeName = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME).orEmpty()
            val qrValue = intent.getStringExtra(StaffScreenExtras.EXTRA_QR_VALUE).orEmpty()

            // Template visible fields (default QR + name when the event has none saved) and attendee details
            var registrationNumber: Int? = null
            var role = ""
            var eventDate = ""

            val visibleFields = repository.getIdCardVisibleFields(eventId)

            val attendeeResult = repository.getAttendeeByEvent(eventId, attendeeId)
            if (attendeeResult is NetworkResult.Success) {
                registrationNumber = attendeeResult.data.registrationNumber
                role = attendeeResult.data.attendeeRole.orEmpty()
                eventDate = attendeeResult.data.eventStartAt?.let {
                    java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH)
                        .withZone(java.time.ZoneId.of("Asia/Manila")).format(it)
                }.orEmpty()
            }

            // Call backend print endpoint
            when (val result = repository.printAttendeeId(eventId, attendeeId)) {
                is NetworkResult.Success -> {
                    Log.d(tag, "printResult success=true eventId=$eventId")

                    // Open system print dialog with rendered ID card
                    val cardData = AndroidIdPrinter.CardData(
                        attendeeName = attendeeName,
                        eventName = eventName,
                        registrationNumber = registrationNumber,
                        role = role,
                        eventDate = eventDate,
                        visibleFields = visibleFields,
                        qrValue = qrValue,
                    )
                    // The print is logged PENDING before the dialog opens (the log call is also the
                    // server-side check); it is then confirmed with, and the result shown for, what
                    // the user did in the dialog.
                    openSystemPrintDialog(eventId, cardData, result.data.printLogId, result.data.message)
                }
                is NetworkResult.Error -> {
                    Log.w(tag, "printResult success=false eventId=$eventId")
                    bindPrintFailure(result)
                }
                NetworkResult.Loading -> Unit
            }
            savingTransaction = false
            findViewById<LinearProgressIndicator>(R.id.progressTransactionInline).visibility = View.GONE
            findViewById<View>(R.id.btnContinueTransaction).isEnabled = true
            applyActionLabels()
        }
    }

    /**
     * Opens the system print dialog for a card the server already logged as PENDING; the log is
     * confirmed with what the user actually did in the dialog.
     */
    @VisibleForTesting
    internal open fun openSystemPrintDialog(
        eventId: String,
        cardData: AndroidIdPrinter.CardData,
        printLogId: UUID,
        serverMessage: String,
    ) {
        val printRepository = repository
        AndroidIdPrinter.print(
            this@StaffScanResultActivity,
            "EventQR ID - ${cardData.attendeeName}",
            cardData,
        ) { outcome ->
            printRepository.confirmPrintLogsInBackground(eventId, listOf(printLogId), outcome)
            onPrintOutcome(outcome, serverMessage)
        }
    }

    /**
     * A failed ID-print request. A refusal - the permission can be revoked mid-session, so the
     * server answers 403 - gets the permission copy instead of the generic ID-print-failed panel:
     * this screen is reachable without the scanner's permission-filtered purpose picker, so the
     * refusal must not look like a printer or template failure. Nothing is printed in either case.
     */
    @VisibleForTesting
    internal fun bindPrintFailure(result: NetworkResult.Error) {
        if (PrintPermission.isPermissionDenied(result)) {
            Toast.makeText(this, getString(R.string.staff_print_id_permission_required), Toast.LENGTH_LONG).show()
            return
        }
        // Verification succeeded; only the print step failed (e.g. no saved ID template).
        bindPrintFailed(result.message)
    }

    @VisibleForTesting
    internal fun onPrintOutcome(outcome: AndroidIdPrinter.PrintOutcome, serverMessage: String) {
        if (isFinishing || isDestroyed) return
        when (outcome) {
            AndroidIdPrinter.PrintOutcome.SENT -> showPrintSuccessDialog(serverMessage)
            AndroidIdPrinter.PrintOutcome.CANCELLED ->
                showPrintNoticeDialog(getString(R.string.staff_print_cancelled_title), getString(R.string.staff_print_cancelled_message))
            AndroidIdPrinter.PrintOutcome.FAILED ->
                bindPrintFailed(getString(R.string.staff_print_job_failed_message))
        }
    }

    /** Print-specific failure: the scan was verified, so it must not read as "Verification Rejected". */
    @VisibleForTesting
    internal fun bindPrintFailed(reason: String) {
        demoteBadge()
        findViewById<View>(R.id.headerApproved).visibility = View.GONE
        findViewById<View>(R.id.headerRejected).visibility = View.VISIBLE
        findViewById<TextView>(R.id.txtScanResultStateRejected).text = getString(R.string.ui_staff_result_print_failed)
        findViewById<TextView>(R.id.txtScanResultStatusHintRejected).text = getString(R.string.ui_staff_result_print_failed_hint)
        findViewById<TextView>(R.id.txtScanResultReason).text = reason.ifBlank { getString(R.string.ui_staff_result_print_failed_hint) }
        findViewById<View>(R.id.layoutApprovedDetails).visibility = View.GONE
        findViewById<View>(R.id.layoutRejectedReason).visibility = View.VISIBLE
        findViewById<View>(R.id.cardVerificationDetails).visibility = View.GONE
        findViewById<View>(R.id.btnContinueTransaction).visibility = View.GONE
        findViewById<View>(R.id.btnViewAttendeeDetails).visibility = View.VISIBLE
        findViewById<View>(R.id.btnScanAgain).visibility = View.VISIBLE
    }

    private fun showPrintNoticeDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.staff_print_ok), null)
            .show()
    }

    private fun showPrintSuccessDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.staff_print_sent_title))
            .setMessage(message.ifBlank { getString(R.string.staff_print_sent_default_message) })
            .setCancelable(false)
            .setPositiveButton(getString(R.string.staff_print_done)) { _, _ -> openAttendeeDetails() }
            .show()
    }

    private fun formatVerifiedAt(raw: String?): String {
        val fallback = getString(R.string.ui_staff_result_just_now)
        if (raw.isNullOrBlank()) return fallback
        val instant = runCatching { java.time.Instant.parse(raw) }.getOrNull()
        return if (instant != null) DISPLAY_FORMATTER.format(instant) else fallback
    }

    private fun openTransactionResult(result: TransactionResponse) {
        startActivity(Intent(this, StaffTransactionResultActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, result.eventId.toString())
            putExtra(StaffScreenExtras.EXTRA_EVENT_TITLE, result.eventTitle.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID, result.attendeeUserId.toString())
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME, result.attendeeName.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_REGISTRATION_ID, result.registrationId.toString())
            putExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID, result.qrCredentialId.toString())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_ID, result.scanPurposeId.toString())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_NAME, result.scanPurposeName.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_TRANSACTION_ID, result.transactionId.toString())
            putExtra(StaffScreenExtras.EXTRA_TRANSACTION_RESULT, result.transactionResult.name)
            putExtra(StaffScreenExtras.EXTRA_TRANSACTION_TYPE, result.transactionType.name)
            putExtra(StaffScreenExtras.EXTRA_POINTS_DELTA, result.pointsDelta)
            putExtra(StaffScreenExtras.EXTRA_REASON, result.reason.orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCANNED_AT, result.scannedAt?.toString().orEmpty())
            putExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_CODE, intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_CODE).orEmpty())
        })
        finish()
    }

    private fun openAttendeeDetails() {
        val attendeeId = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID)
        if (attendeeId.isNullOrBlank()) {
            Toast.makeText(this, this.getString(R.string.ui_staff_result_attendee_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, StaffAttendeeDetailsActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID))
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID, attendeeId)
            putExtra(StaffScreenExtras.EXTRA_REGISTRATION_ID, intent.getStringExtra(StaffScreenExtras.EXTRA_REGISTRATION_ID))
            putExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID, intent.getStringExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID))
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME, intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME))
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_EMAIL, intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_EMAIL))
            putExtra(StaffScreenExtras.EXTRA_EVENT_TITLE, intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_TITLE))
        })
    }

    private companion object {
        const val STATE_CLIENT_REQUEST_ID = "client_request_id"
        val DISPLAY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
                .withZone(ZoneId.of("Asia/Manila"))
    }
}
