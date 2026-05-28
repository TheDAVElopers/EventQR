package com.thedavelopers.eventqr.features.staff.result

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
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
    private lateinit var repository: StaffRepository
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.STAFF.name) {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_scan_result)
        repository = StaffRepository(this)

        val isValid = intent.getBooleanExtra(StaffScreenExtras.EXTRA_IS_VALID, false)
        bindStaticFields(isValid)

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
        findViewById<View>(R.id.btnBackToScanner).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java).apply {
                putExtra(StaffScreenExtras.EXTRA_EVENT_ID, intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID))
            })
            finish()
        }
    }

    private fun bindStaticFields(isValid: Boolean) {
        findViewById<TextView>(R.id.txtScanResultState).text = if (isValid) "Verification Approved" else "Verification Rejected"
        findViewById<TextView>(R.id.txtScanResultTitle).text = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_TITLE).orUnknown("Assigned event")
        findViewById<TextView>(R.id.txtScanResultEvent).text = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_TITLE).orUnknown("Assigned event")
        findViewById<TextView>(R.id.txtScanResultPurpose).text = intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_NAME).orUnknown("Scan purpose")
        findViewById<TextView>(R.id.txtScanResultReason).text = intent.getStringExtra(StaffScreenExtras.EXTRA_MESSAGE).orUnknown("No reason supplied")

        if (isValid) {
            findViewById<View>(R.id.layoutApprovedDetails).visibility = View.VISIBLE
            findViewById<View>(R.id.layoutRejectedReason).visibility = View.GONE
            findViewById<TextView>(R.id.txtScanResultAttendeeName).text = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME).orUnknown()
            findViewById<TextView>(R.id.txtScanResultAttendeeEmail).text = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_EMAIL).orUnknown()
            findViewById<TextView>(R.id.txtScanResultRegistrationStatus).text = intent.getStringExtra(StaffScreenExtras.EXTRA_REGISTRATION_STATUS).orUnknown()
            findViewById<TextView>(R.id.txtScanResultStatusHint).text = "Backend verification succeeded. You can continue to record the transaction."
            findViewById<Button>(R.id.btnContinueTransaction).visibility = View.VISIBLE
            findViewById<Button>(R.id.btnViewAttendeeDetails).visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.layoutApprovedDetails).visibility = View.GONE
            findViewById<View>(R.id.layoutRejectedReason).visibility = View.VISIBLE
            findViewById<TextView>(R.id.txtScanResultStatusHint).text = "Backend verification rejected the scan."
            findViewById<Button>(R.id.btnContinueTransaction).visibility = View.GONE
            findViewById<Button>(R.id.btnViewAttendeeDetails).visibility = View.GONE
        }
    }

    private fun recordTransaction() {
        val eventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID).orEmpty()
        val purposeId = intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_ID).orEmpty()
        val qrValue = intent.getStringExtra(StaffScreenExtras.EXTRA_QR_VALUE).orEmpty()
        val staffUserId = intent.getStringExtra(StaffScreenExtras.EXTRA_STAFF_USER_ID).orEmpty().ifBlank { sessionManager.getUserId().orEmpty() }
        val purposeCode = intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_CODE).orEmpty()

        if (eventId.isBlank() || purposeId.isBlank() || qrValue.isBlank() || staffUserId.isBlank() || purposeCode.isBlank()) {
            Toast.makeText(this, "Missing scan context", Toast.LENGTH_SHORT).show()
            return
        }

        findViewById<ProgressBar>(R.id.progressScanResult).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnContinueTransaction).isEnabled = false

        MainScope().launch {
            val request = TransactionRequest(
                eventId = UUID.fromString(eventId),
                scanPurposeId = UUID.fromString(purposeId),
                qrValue = qrValue,
                staffUserId = UUID.fromString(staffUserId),
            )
            when (val result = repository.createTransaction(request, ScanPurposeCode.valueOf(purposeCode))) {
                is NetworkResult.Success -> openTransactionResult(result.data)
                is NetworkResult.Error -> {
                    Toast.makeText(this@StaffScanResultActivity, result.message, Toast.LENGTH_SHORT).show()
                    bindRejectedResult(result.message)
                }
                NetworkResult.Loading -> Unit
            }
            findViewById<ProgressBar>(R.id.progressScanResult).visibility = View.GONE
            findViewById<Button>(R.id.btnContinueTransaction).isEnabled = true
        }
    }

    private fun bindRejectedResult(message: String) {
        findViewById<TextView>(R.id.txtScanResultState).text = "Verification Rejected"
        findViewById<TextView>(R.id.txtScanResultReason).text = message
        findViewById<View>(R.id.layoutApprovedDetails).visibility = View.GONE
        findViewById<View>(R.id.layoutRejectedReason).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnContinueTransaction).visibility = View.GONE
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
        })
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
}