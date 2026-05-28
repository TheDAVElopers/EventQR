package com.thedavelopers.eventqr.features.staff

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintRequest
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.transactions.TransactionLogAdapter
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.features.staff.model.dto.ScanVerificationResponse
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.UUID

object StaffScreenExtras {
    const val EXTRA_EVENT_ID = "extra_event_id"
    const val EXTRA_EVENT_TITLE = "extra_event_title"
    const val EXTRA_SCAN_PURPOSE_ID = "extra_scan_purpose_id"
    const val EXTRA_SCAN_PURPOSE_NAME = "extra_scan_purpose_name"
    const val EXTRA_SCAN_PURPOSE_CODE = "extra_scan_purpose_code"
    const val EXTRA_QR_VALUE = "extra_qr_value"
    const val EXTRA_STAFF_USER_ID = "extra_staff_user_id"
    const val EXTRA_IS_VALID = "extra_is_valid"
    const val EXTRA_MESSAGE = "extra_message"
    const val EXTRA_ATTENDEE_ID = "extra_attendee_id"
    const val EXTRA_ATTENDEE_NAME = "extra_attendee_name"
    const val EXTRA_ATTENDEE_EMAIL = "extra_attendee_email"
    const val EXTRA_REGISTRATION_ID = "extra_registration_id"
    const val EXTRA_QR_CREDENTIAL_ID = "extra_qr_credential_id"
    const val EXTRA_REGISTRATION_STATUS = "extra_registration_status"
    const val EXTRA_VERIFIED_AT = "extra_verified_at"
    const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
    const val EXTRA_TRANSACTION_RESULT = "extra_transaction_result"
    const val EXTRA_TRANSACTION_TYPE = "extra_transaction_type"
    const val EXTRA_POINTS_DELTA = "extra_points_delta"
    const val EXTRA_REASON = "extra_reason"
    const val EXTRA_SCANNED_AT = "extra_scanned_at"
    const val EXTRA_EVENT_TITLE_FALLBACK = "extra_event_title_fallback"
    const val EXTRA_ATTENDEE_EVENT_STATUS = "extra_attendee_event_status"
    const val EXTRA_QR_ACTIVE = "extra_qr_active"
}

private fun String?.orUnknown(defaultValue: String = "Unknown"): String = this?.takeIf { it.isNotBlank() } ?: defaultValue

private fun showToast(activity: AppCompatActivity, message: String) {
    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
}

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
            showToast(this, "Missing scan context")
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
                    showToast(this@StaffScanResultActivity, result.message)
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
            showToast(this, "Attendee details are only available for valid scans")
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

open class StaffTransactionResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)
        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.STAFF.name) {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_transaction_result)

        val approved = intent.getStringExtra(StaffScreenExtras.EXTRA_TRANSACTION_RESULT).orUnknown() == TransactionResult.APPROVED.name
        findViewById<TextView>(R.id.txtTransactionState).text = if (approved) "Transaction Approved" else "Transaction Rejected"
        findViewById<TextView>(R.id.txtTransactionType).text = intent.getStringExtra(StaffScreenExtras.EXTRA_TRANSACTION_TYPE).orUnknown("Transaction")
        findViewById<TextView>(R.id.txtTransactionEvent).text = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_TITLE).orUnknown("Event")
        findViewById<TextView>(R.id.txtTransactionAttendee).text = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME).orUnknown("Attendee")
        findViewById<TextView>(R.id.txtTransactionTime).text = intent.getStringExtra(StaffScreenExtras.EXTRA_SCANNED_AT).orUnknown("Just now")
        findViewById<TextView>(R.id.txtTransactionPoints).text = intent.getIntExtra(StaffScreenExtras.EXTRA_POINTS_DELTA, 0).let { delta -> if (delta >= 0) "+$delta" else delta.toString() }
        findViewById<TextView>(R.id.txtTransactionReason).text = intent.getStringExtra(StaffScreenExtras.EXTRA_REASON).orUnknown(if (approved) "Approved by backend" else "Rejected by backend")

        findViewById<View>(R.id.layoutTransactionApproved).visibility = if (approved) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutTransactionRejected).visibility = if (approved) View.GONE else View.VISIBLE

        findViewById<Button>(R.id.btnViewTransactionAttendee).visibility = if (approved) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnViewTransactionAttendee).setOnClickListener { openAttendeeDetails() }
        findViewById<Button>(R.id.btnTransactionScanAgain).setOnClickListener { openScanner() }
        findViewById<Button>(R.id.btnTransactionDashboard).setOnClickListener {
            startActivity(Intent(this, StaffDashboardActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.btnTransactionBackToScanner).setOnClickListener { openScanner() }
    }

    private fun openScanner() {
        startActivity(Intent(this, ScannerActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID))
        })
        finish()
    }

    private fun openAttendeeDetails() {
        val attendeeId = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID).orEmpty()
        if (attendeeId.isBlank()) {
            Toast.makeText(this, "Attendee details are unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, StaffAttendeeDetailsActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID))
            putExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID, attendeeId)
            putExtra(StaffScreenExtras.EXTRA_REGISTRATION_ID, intent.getStringExtra(StaffScreenExtras.EXTRA_REGISTRATION_ID))
            putExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID, intent.getStringExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID))
            putExtra(StaffScreenExtras.EXTRA_EVENT_TITLE, intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_TITLE))
        })
    }
}

open class StaffAttendeeDetailsActivity : AppCompatActivity() {
    private lateinit var repository: StaffRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var transactionAdapter: TransactionLogAdapter
    private var eventId: String = ""
    private var attendeeId: String = ""
    private var registrationId: String = ""
    private var qrCredentialId: String = ""
    private var hasPrintedId: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.STAFF.name) {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_attendee_details)
        repository = StaffRepository(this)
        transactionAdapter = TransactionLogAdapter()
        eventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID).orEmpty()
        attendeeId = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID).orEmpty()
        registrationId = intent.getStringExtra(StaffScreenExtras.EXTRA_REGISTRATION_ID).orEmpty()
        qrCredentialId = intent.getStringExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID).orEmpty()

        findViewById<RecyclerView>(R.id.recyclerDetailTransactions).apply {
            layoutManager = LinearLayoutManager(this@StaffAttendeeDetailsActivity)
            adapter = transactionAdapter
        }

        findViewById<View>(R.id.btnBackToTransactionResult).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnPrintOrReprintId).setOnClickListener { printId() }

        loadDetails()
    }

    private fun loadDetails() {
        if (eventId.isBlank() || attendeeId.isBlank()) {
            Toast.makeText(this, "Missing attendee context", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<ProgressBar>(R.id.progressAttendeeDetails).visibility = View.VISIBLE
        MainScope().launch {
            when (val attendeeResult = repository.getAttendeeByEvent(eventId, attendeeId)) {
                is NetworkResult.Success -> {
                    renderRegistration(attendeeResult.data)
                    loadBalance()
                    loadTransactions()
                    loadPrintLogs()
                }
                is NetworkResult.Error -> Toast.makeText(this@StaffAttendeeDetailsActivity, attendeeResult.message, Toast.LENGTH_SHORT).show()
                NetworkResult.Loading -> Unit
            }
            findViewById<ProgressBar>(R.id.progressAttendeeDetails).visibility = View.GONE
        }
    }

    private fun renderRegistration(item: RegistrationResponse) {
        findViewById<TextView>(R.id.txtDetailAttendeeName).text = item.attendeeName.orUnknown()
        findViewById<TextView>(R.id.txtDetailAttendeeEmail).text = item.attendeeEmail.orUnknown()
        findViewById<TextView>(R.id.txtDetailEventName).text = item.eventTitle.orUnknown("Assigned event")
        findViewById<TextView>(R.id.txtDetailRegistrationStatus).text = item.status.name.replace('_', ' ')
        findViewById<TextView>(R.id.txtDetailQrStatus).text = if (item.qrCredentialId == null) "QR Credential: Pending" else "QR Credential: Issued"
        findViewById<TextView>(R.id.txtDetailEntryStatus).text = when (item.status.name) {
            "ENTERED" -> "Entered"
            "EXITED" -> "Exited"
            else -> "Not entered"
        }
        findViewById<TextView>(R.id.txtDetailAttendanceStatus).text = if (item.registeredAt != null) "Registered" else "Pending"
        findViewById<TextView>(R.id.txtDetailExitStatus).text = if (item.status.name == "EXITED") "Exited" else "Not exited"
        findViewById<TextView>(R.id.txtDetailRegistrationDate).text = item.registeredAt?.let { "Registered: ${DateFormatters.formatInstant(it)}" } ?: "Registered: Unknown"
        qrCredentialId = item.qrCredentialId?.toString().orEmpty()
        findViewById<Button>(R.id.btnPrintOrReprintId).visibility = if (qrCredentialId.isBlank()) View.GONE else View.VISIBLE
    }

    private fun loadBalance() {
        MainScope().launch {
            when (val balanceResult = repository.getRewardBalance(eventId, attendeeId)) {
                is NetworkResult.Success -> renderBalance(balanceResult.data)
                is NetworkResult.Error -> findViewById<TextView>(R.id.txtDetailPointsBalance).text = "Points: unavailable"
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun renderBalance(balance: PointBalanceResponse) {
        findViewById<TextView>(R.id.txtDetailPointsBalance).text = "Points: ${balance.pointsBalance}"
    }

    private fun loadTransactions() {
        MainScope().launch {
            when (val txResult = repository.getTransactionsByEvent(eventId)) {
                is NetworkResult.Success -> {
                    val filtered = txResult.data.filter { it.attendeeUserId.toString() == attendeeId }
                    transactionAdapter.submitItems(filtered)
                    findViewById<TextView>(R.id.txtDetailRecentTransactionsEmpty).visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                }
                is NetworkResult.Error -> Toast.makeText(this@StaffAttendeeDetailsActivity, txResult.message, Toast.LENGTH_SHORT).show()
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun loadPrintLogs() {
        if (qrCredentialId.isBlank()) {
            hasPrintedId = false
            findViewById<Button>(R.id.btnPrintOrReprintId).text = "Print ID"
            return
        }

        MainScope().launch {
            when (val result = repository.getIdPrintsByEvent(eventId)) {
                is NetworkResult.Success -> {
                    hasPrintedId = result.data.any { it.attendeeUserId.toString() == attendeeId }
                    findViewById<Button>(R.id.btnPrintOrReprintId).text = if (hasPrintedId) "Reprint ID" else "Print ID"
                }
                is NetworkResult.Error -> Unit
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun printId() {
        if (eventId.isBlank() || qrCredentialId.isBlank()) {
            Toast.makeText(this, "QR credential is required for printing", Toast.LENGTH_SHORT).show()
            return
        }

        val staffUserId = sessionManager.getUserId().orEmpty()
        if (staffUserId.isBlank()) {
            Toast.makeText(this, "Staff profile is missing", Toast.LENGTH_SHORT).show()
            return
        }

        findViewById<ProgressBar>(R.id.progressAttendeeDetails).visibility = View.VISIBLE
        MainScope().launch {
            when (val result = repository.printId(IdPrintRequest(UUID.fromString(eventId), UUID.fromString(qrCredentialId), UUID.fromString(staffUserId), hasPrintedId))) {
                is NetworkResult.Success -> Toast.makeText(this@StaffAttendeeDetailsActivity, result.data.message, Toast.LENGTH_SHORT).show()
                is NetworkResult.Error -> Toast.makeText(this@StaffAttendeeDetailsActivity, result.message, Toast.LENGTH_SHORT).show()
                NetworkResult.Loading -> Unit
            }
            findViewById<ProgressBar>(R.id.progressAttendeeDetails).visibility = View.GONE
            loadPrintLogs()
        }
    }
}