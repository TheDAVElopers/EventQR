package com.thedavelopers.eventqr.features.staff.details

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintRequest
import com.thedavelopers.eventqr.features.registrations.RegistrationStatusBadgeStyler
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.staff.orUnknown
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

open class StaffAttendeeDetailsActivity : AppCompatActivity() {
    private lateinit var repository: StaffRepository
    private lateinit var sessionManager: SessionManager
    private var eventId: String = ""
    private var attendeeId: String = ""
    private var registrationId: String = ""
    private var qrCredentialId: String = ""
    private var hasPrintedId: Boolean = false

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        .withZone(ZoneId.of("Asia/Manila"))

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
        eventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID).orEmpty()
        attendeeId = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID).orEmpty()
        registrationId = intent.getStringExtra(StaffScreenExtras.EXTRA_REGISTRATION_ID).orEmpty()
        qrCredentialId = intent.getStringExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID).orEmpty()

        findViewById<View>(R.id.btnBackToTransactionResult).setOnClickListener { finish() }
        findViewById<View>(R.id.btnPrintOrReprintId).setOnClickListener { printId() }
        findViewById<View>(R.id.btnScanAgain).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java).apply {
                putExtra(StaffScreenExtras.EXTRA_EVENT_ID, eventId)
            })
        }

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
        registrationId = item.registrationId.toString()
        qrCredentialId = item.qrCredentialId?.toString().orEmpty()

        val attendeeName = item.attendeeName.orUnknown()
        findViewById<TextView>(R.id.txtDetailAvatar).text = attendeeName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "A"
        findViewById<TextView>(R.id.txtDetailAttendeeName).text = attendeeName
        findViewById<TextView>(R.id.txtDetailAttendeeEmail).text = item.attendeeEmail.orUnknown()
        findViewById<TextView>(R.id.txtDetailAttendeePhone).text = item.attendeePhoneNumber?.takeIf { it.isNotBlank() } ?: "No phone number"
        findViewById<TextView>(R.id.txtDetailEventName).text = item.eventTitle.orUnknown("Assigned event")
        findViewById<TextView>(R.id.txtDetailRegistrationId).text = shortRegistrationId(item.registrationId)
        RegistrationStatusBadgeStyler.bind(findViewById(R.id.txtDetailRegistrationStatus), item.status)
        findViewById<TextView>(R.id.txtDetailCheckInTime).text = formatTime(item.enteredAt ?: item.attendedAt)
        findViewById<TextView>(R.id.txtDetailPointsBalance).text = "${item.pointsEarned} pts"
        findViewById<TextView>(R.id.txtDetailTransactionCount).text = "0"

        findViewById<TextView>(R.id.txtDetailQrStatus).text = if (item.qrCredentialId == null) "QR Credential: Pending" else "QR Credential: Issued"
        findViewById<TextView>(R.id.txtDetailEntryStatus).text = RegistrationStatusBadgeStyler.displayLabel(item.status)
        findViewById<TextView>(R.id.txtDetailAttendanceStatus).text = if (item.attendedAt != null || item.enteredAt != null) "Checked In" else "Registered"
        findViewById<TextView>(R.id.txtDetailExitStatus).text = if (item.exitedAt != null) "Exited" else "Not exited"
        findViewById<TextView>(R.id.txtDetailRegistrationDate).text = item.registeredAt?.let { "Registered: ${formatTime(it)}" } ?: "Registered: Unknown"
        findViewById<View>(R.id.btnPrintOrReprintId).visibility = if (qrCredentialId.isBlank()) View.GONE else View.VISIBLE
    }

    private fun loadTransactions() {
        MainScope().launch {
            when (val txResult = repository.getTransactionsByEvent(eventId)) {
                is NetworkResult.Success -> {
                    val count = txResult.data.count { it.attendeeUserId.toString() == attendeeId }
                    findViewById<TextView>(R.id.txtDetailTransactionCount).text = count.toString()
                }
                is NetworkResult.Error -> findViewById<TextView>(R.id.txtDetailTransactionCount).text = "0"
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun loadPrintLogs() {
        if (qrCredentialId.isBlank()) {
            hasPrintedId = false
            findViewById<TextView>(R.id.txtPrintOrReprintIdLabel).text = "Print ID"
            return
        }

        MainScope().launch {
            when (val result = repository.getIdPrintsByEvent(eventId)) {
                is NetworkResult.Success -> {
                    hasPrintedId = result.data.any { it.attendeeUserId.toString() == attendeeId }
                    findViewById<TextView>(R.id.txtPrintOrReprintIdLabel).text = if (hasPrintedId) "Reprint ID" else "Print ID"
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

    private fun formatTime(value: Instant?): String = value?.let { timeFormatter.format(it) } ?: "--"

    private fun shortRegistrationId(value: UUID): String = "reg-${value.toString().take(8)}"
}
