package com.thedavelopers.eventqr.features.staff.result

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.idprinting.AndroidIdPrinter
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdTemplateConfigResponse
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import com.thedavelopers.eventqr.features.staff.StaffDashboardActivity
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.staff.orUnknown
import com.thedavelopers.eventqr.features.staff.details.StaffAttendeeDetailsActivity
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.UUID

open class StaffScanResultActivity : AppCompatActivity() {
    private val tag = "StaffQrTransaction"
    private lateinit var repository: StaffRepository
    private lateinit var sessionManager: SessionManager
    private var savingTransaction = false

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
            Toast.makeText(this, "Access Denied: Staff or above", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_scan_result)
        repository = StaffRepository(this)
        
        val isValid = intent.getBooleanExtra(StaffScreenExtras.EXTRA_IS_VALID, false)
        bindStaticFields(isValid)
        applyActionLabels()

        findViewById<Button>(R.id.btnContinueTransaction).setOnClickListener {
            if (isValid) {
                recordTransaction()
            }
        }
        findViewById<Button>(R.id.btnViewAttendeeDetails).setOnClickListener {
            openAttendeeDetails()
        }
        findViewById<Button>(R.id.btnScanAgain).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.nav_header_back).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java).apply {
                putExtra(StaffScreenExtras.EXTRA_EVENT_ID, intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID))
            })
            finish()
        }
    }

    private fun applyActionLabels() {
        val purposeCode = intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_CODE).orEmpty()
        findViewById<Button>(R.id.btnContinueTransaction).text =
            if (purposeCode == ScanPurposeCode.ID_PRINT.name) "Print ID"
            else "Log Transaction"
    }

    private fun bindStaticFields(isValid: Boolean) {
        findViewById<TextView>(R.id.txtScanResultEvent).text = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_TITLE).orUnknown("Assigned event")
        findViewById<TextView>(R.id.txtScanResultPurpose).text = intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_NAME).orUnknown("Scan purpose")
        findViewById<TextView>(R.id.txtScanResultReason).text = intent.getStringExtra(StaffScreenExtras.EXTRA_MESSAGE).orUnknown("No reason supplied")

        if (isValid) {
            findViewById<View>(R.id.headerApproved).visibility = View.VISIBLE
            findViewById<View>(R.id.headerRejected).visibility = View.GONE
            findViewById<View>(R.id.layoutApprovedDetails).visibility = View.VISIBLE
            findViewById<View>(R.id.layoutRejectedReason).visibility = View.GONE
            findViewById<View>(R.id.cardVerificationDetails).visibility = View.VISIBLE
            
            findViewById<TextView>(R.id.txtScanResultAttendeeName).text = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME).orUnknown()
            findViewById<TextView>(R.id.txtScanResultAttendeeEmail).text = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_EMAIL).orUnknown()
            findViewById<TextView>(R.id.txtScanResultRegistrationStatus).text = intent.getStringExtra(StaffScreenExtras.EXTRA_REGISTRATION_STATUS).orUnknown()
            findViewById<TextView>(R.id.txtScanResultStatusHint).text = "Attendee verified successfully"
            findViewById<Button>(R.id.btnContinueTransaction).visibility = View.VISIBLE
            findViewById<Button>(R.id.btnViewAttendeeDetails).visibility = View.VISIBLE
            
            // New UI fields binding
            val verifiedAt = intent.getStringExtra(StaffScreenExtras.EXTRA_VERIFIED_AT)
            findViewById<TextView>(R.id.txtScanResultVerifiedAt).text = formatVerifiedAt(verifiedAt)

            findViewById<Button>(R.id.btnContinueTransaction).visibility = View.VISIBLE
            findViewById<Button>(R.id.btnViewAttendeeDetails).visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.headerApproved).visibility = View.GONE
            findViewById<View>(R.id.headerRejected).visibility = View.VISIBLE
            findViewById<TextView>(R.id.txtScanResultStateRejected).text = "Verification Rejected"
            findViewById<TextView>(R.id.txtScanResultStatusHintRejected).text = "Backend verification rejected the scan."
            findViewById<View>(R.id.layoutApprovedDetails).visibility = View.GONE
            findViewById<View>(R.id.layoutRejectedReason).visibility = View.VISIBLE
            findViewById<View>(R.id.cardVerificationDetails).visibility = View.GONE
            findViewById<Button>(R.id.btnContinueTransaction).visibility = View.GONE
            findViewById<Button>(R.id.btnViewAttendeeDetails).visibility = View.GONE
        }
    }

    private fun bindRejectedResult(message: String) {
        findViewById<View>(R.id.headerApproved).visibility = View.GONE
        findViewById<View>(R.id.headerRejected).visibility = View.VISIBLE
        findViewById<TextView>(R.id.txtScanResultStateRejected).text = "Verification Rejected"
        findViewById<TextView>(R.id.txtScanResultStatusHintRejected).text = "Transaction could not be completed."
        findViewById<TextView>(R.id.txtScanResultReason).text = message
        findViewById<View>(R.id.layoutApprovedDetails).visibility = View.GONE
        findViewById<View>(R.id.layoutRejectedReason).visibility = View.VISIBLE
        findViewById<View>(R.id.cardVerificationDetails).visibility = View.GONE
        findViewById<Button>(R.id.btnContinueTransaction).visibility = View.GONE
    }

    private fun recordTransaction() {
        if (savingTransaction) {
            Toast.makeText(this, "Transaction save already in progress.", Toast.LENGTH_SHORT).show()
            return
        }
        val eventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID).orEmpty()
        val purposeId = intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_ID).orEmpty()
        val qrValue = intent.getStringExtra(StaffScreenExtras.EXTRA_QR_VALUE).orEmpty()
        val staffUserId = intent.getStringExtra(StaffScreenExtras.EXTRA_STAFF_USER_ID).orEmpty().ifBlank { sessionManager.getUserId().orEmpty() }
        val purposeCode = intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_CODE).orEmpty()
        val attendeeId = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID).orEmpty()
        val registrationId = intent.getStringExtra(StaffScreenExtras.EXTRA_REGISTRATION_ID).orEmpty()
        val qrCredentialId = intent.getStringExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID).orEmpty()
        val purposeLabel = intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_NAME).orUnknown("Scan Purpose")

        val parsedPurposeCode = runCatching { ScanPurposeCode.valueOf(purposeCode) }.getOrNull()

        if (parsedPurposeCode == ScanPurposeCode.ID_PRINT) {
            triggerPrint(eventId, attendeeId)
            return
        }

        if (eventId.isBlank() || purposeId.isBlank() || qrValue.isBlank() || staffUserId.isBlank() || purposeCode.isBlank()) {
            Toast.makeText(this, "Transaction failed: Missing scan context", Toast.LENGTH_SHORT).show()
            return
        }

        val parsedEventId = runCatching { UUID.fromString(eventId) }.getOrNull()
        val parsedPurposeId = runCatching { UUID.fromString(purposeId) }.getOrNull()
        val parsedStaffUserId = runCatching { UUID.fromString(staffUserId) }.getOrNull()
        if (parsedEventId == null || parsedPurposeId == null || parsedStaffUserId == null || parsedPurposeCode == null) {
            Toast.makeText(this, "Transaction failed: Invalid scan context", Toast.LENGTH_SHORT).show()
            Log.w(
                tag,
                "invalid context eventId=$eventId purposeId=$purposeId staffUserId=$staffUserId purposeCode=$purposeCode"
            )
            return
        }

        savingTransaction = true
        findViewById<LinearProgressIndicator>(R.id.progressTransactionInline).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnContinueTransaction).isEnabled = false
        findViewById<Button>(R.id.btnContinueTransaction).text = "Logging..."

        MainScope().launch {
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
                        "backendSaveResult success=true transactionId=${result.data.transactionId} eventId=${result.data.eventId} scanPurposeId=${result.data.scanPurposeId} transactionResult=${result.data.transactionResult}"
                    )
                    openTransactionResult(result.data)
                }
                is NetworkResult.Error -> {
                    val message = "Transaction failed: ${result.message}"
                    Log.w(tag, "backendSaveResult success=false message=${result.message}")
                    Toast.makeText(this@StaffScanResultActivity, message, Toast.LENGTH_SHORT).show()
                    bindRejectedResult(message)
                }
                NetworkResult.Loading -> Unit
            }
            savingTransaction = false
            findViewById<LinearProgressIndicator>(R.id.progressTransactionInline).visibility = View.GONE
            findViewById<Button>(R.id.btnContinueTransaction).isEnabled = true
            applyActionLabels()
        }
    }

    private fun triggerPrint(eventId: String, attendeeId: String) {
        if (eventId.isBlank() || attendeeId.isBlank()) {
            Toast.makeText(this, "Print failed: Missing attendee context", Toast.LENGTH_SHORT).show()
            return
        }

        savingTransaction = true
        findViewById<LinearProgressIndicator>(R.id.progressTransactionInline).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnContinueTransaction).isEnabled = false
        findViewById<Button>(R.id.btnContinueTransaction).text = "Printing..."

        MainScope().launch {
            val apiService = com.thedavelopers.eventqr.core.api.ApiClient.getService(this@StaffScanResultActivity)
            val eventName = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_TITLE).orEmpty()
            val attendeeName = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME).orEmpty()
            val qrValue = intent.getStringExtra(StaffScreenExtras.EXTRA_QR_VALUE).orEmpty()

            // Fetch template config (visible fields) and attendee details in parallel
            var visibleFields = emptyList<String>()
            var registrationNumber: Int? = null
            var role = ""
            var eventDate = ""

            val configResult = com.thedavelopers.eventqr.core.api.safeApiCall {
                apiService.getIdTemplateConfig(eventId)
            }
            if (configResult is NetworkResult.Success) {
                visibleFields = configResult.data.visibleFields.filterNotNull()
            }

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
                    Log.d(tag, "printResult success=true message=${result.data.message}")

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
                    AndroidIdPrinter.print(
                        this@StaffScanResultActivity,
                        "EventQR ID — $attendeeName",
                        cardData,
                    )

                    showPrintSuccessDialog(result.data.message)
                }
                is NetworkResult.Error -> {
                    val message = "Print failed: ${result.message}"
                    Log.w(tag, "printResult success=false message=${result.message}")
                    Toast.makeText(this@StaffScanResultActivity, message, Toast.LENGTH_SHORT).show()
                    bindRejectedResult(message)
                }
                NetworkResult.Loading -> Unit
            }
            savingTransaction = false
            findViewById<LinearProgressIndicator>(R.id.progressTransactionInline).visibility = View.GONE
            findViewById<Button>(R.id.btnContinueTransaction).isEnabled = true
            applyActionLabels()
        }
    }

    private fun showPrintSuccessDialog(message: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundResource(R.drawable.bg_card)
        }

        container.addView(TextView(this).apply {
            text = "ID Print Sent"
            textSize = 28f
            setTextColor(0xFF111827.toInt())
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        })
        container.addView(TextView(this).apply {
            text = message.ifBlank { "Print request sent to printer." }
            textSize = 16f
            setTextColor(0xFF6B7280.toInt())
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, dp(12), 0, dp(20))
        })
        val doneButton = Button(this).apply {
            text = "Done"
            setBackgroundResource(R.drawable.bg_scanner_button)
            setTextColor(0xFFFFFFFF.toInt())
        }
        container.addView(doneButton)

        val dialog = AlertDialog.Builder(this)
            .setView(container)
            .setCancelable(false)
            .create()
        doneButton.setOnClickListener {
            dialog.dismiss()
            openAttendeeDetails()
        }
        dialog.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatVerifiedAt(raw: String?): String {
        if (raw.isNullOrBlank()) return "Just now"
        val instant = runCatching { java.time.Instant.parse(raw) }.getOrNull()
        return if (instant != null) {
            com.thedavelopers.eventqr.core.util.DateFormatters.formatInstant(instant)
        } else {
            "Just now"
        }
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
            Toast.makeText(this, "Attendee details are only available for valid scans", Toast.LENGTH_SHORT).show()
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
    }
}
