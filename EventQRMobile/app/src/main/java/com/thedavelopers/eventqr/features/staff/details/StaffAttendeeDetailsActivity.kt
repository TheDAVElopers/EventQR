package com.thedavelopers.eventqr.features.staff.details

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.idprinting.AndroidIdPrinter
import com.thedavelopers.eventqr.features.registrations.RegistrationNumberFormatter
import com.thedavelopers.eventqr.features.registrations.RegistrationStatusBadgeStyler
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.staff.orUnknown
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import com.thedavelopers.eventqr.features.transactions.TransactionLogAdapter
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
    private lateinit var transactionAdapter: TransactionLogAdapter
    private var eventId: String = ""
    private var attendeeId: String = ""
    private var registrationId: String = ""
    private var qrCredentialId: String = ""
    private var hasPrintedId: Boolean = false
    private var cachedAttendeeName: String = ""
    private var cachedEventName: String = ""
    private var cachedRegistrationNumber: Int? = null
    private var cachedRole: String = ""
    private var cachedEventDate: String = ""

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        .withZone(ZoneId.of("Asia/Manila"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        if (!RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.STAFF)) {
            Toast.makeText(this, "Access Denied: Staff or above", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_attendee_details)
        repository = StaffRepository(this)
        eventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID).orEmpty()
        attendeeId = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID).orEmpty()
        registrationId = intent.getStringExtra(StaffScreenExtras.EXTRA_REGISTRATION_ID).orEmpty()
        qrCredentialId = intent.getStringExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID).orEmpty()

        transactionAdapter = TransactionLogAdapter()
        val recyclerTransactions = findViewById<RecyclerView>(R.id.recyclerDetailTransactions)
        recyclerTransactions.layoutManager = LinearLayoutManager(this)
        recyclerTransactions.adapter = transactionAdapter

        findViewById<View>(R.id.nav_header_back).setOnClickListener { finish() }
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
        cachedAttendeeName = item.attendeeName.orUnknown()
        cachedEventName = item.eventTitle.orUnknown("Assigned event")
        cachedRegistrationNumber = item.registrationNumber
        cachedRole = item.attendeeRole.orEmpty()
        cachedEventDate = item.eventStartAt?.let { formatEventDate(it) }.orEmpty()

        val attendeeName = item.attendeeName.orUnknown()
        findViewById<TextView>(R.id.txtDetailAvatar).text = attendeeName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "A"
        findViewById<TextView>(R.id.txtDetailAttendeeName).text = attendeeName
        findViewById<TextView>(R.id.txtDetailAttendeeEmail).text = item.attendeeEmail.orUnknown()
        findViewById<TextView>(R.id.txtDetailAttendeePhone).text = item.attendeePhoneNumber?.takeIf { it.isNotBlank() } ?: "No phone number"
        findViewById<TextView>(R.id.txtDetailEventName).text = item.eventTitle.orUnknown("Assigned event")
        findViewById<TextView>(R.id.txtDetailRegistrationId).text =
            RegistrationNumberFormatter.format(item.registrationNumber) ?: shortRegistrationId(item.registrationId)
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
        val layoutTransactions = findViewById<View>(R.id.layoutRecentTransactions)
        val recyclerTransactions = findViewById<RecyclerView>(R.id.recyclerDetailTransactions)
        val emptyText = findViewById<TextView>(R.id.txtDetailRecentTransactionsEmpty)
        val countText = findViewById<TextView>(R.id.txtDetailTransactionCount)

        MainScope().launch {
            when (val txResult = repository.getTransactionsByEvent(eventId)) {
                is NetworkResult.Success -> {
                    val count = txResult.data.count { it.attendeeUserId.toString() == attendeeId }
                    countText.text = count.toString()
                }
                is NetworkResult.Error -> countText.text = "0"
                NetworkResult.Loading -> Unit
            }

            when (val recentResult = repository.getAttendeeTransactions(eventId, attendeeId)) {
                is NetworkResult.Success -> {
                    layoutTransactions.visibility = View.VISIBLE
                    val transactions = recentResult.data
                    if (transactions.isEmpty()) {
                        recyclerTransactions.visibility = View.GONE
                        emptyText.visibility = View.VISIBLE
                    } else {
                        recyclerTransactions.visibility = View.VISIBLE
                        emptyText.visibility = View.GONE
                        transactionAdapter.submitItems(transactions)
                    }
                }
                is NetworkResult.Error -> {
                    layoutTransactions.visibility = View.VISIBLE
                    recyclerTransactions.visibility = View.GONE
                    emptyText.visibility = View.VISIBLE
                }
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
            when (val result = repository.getStaffPrintLogs(eventId)) {
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
        if (eventId.isBlank() || attendeeId.isBlank()) {
            Toast.makeText(this, "Attendee context is required for printing", Toast.LENGTH_SHORT).show()
            return
        }

        findViewById<ProgressBar>(R.id.progressAttendeeDetails).visibility = View.VISIBLE
        MainScope().launch {
            val apiService = com.thedavelopers.eventqr.core.api.ApiClient.getService(this@StaffAttendeeDetailsActivity)

            // Fetch template config for visible fields
            var visibleFields = emptyList<String>()
            val configResult = com.thedavelopers.eventqr.core.api.safeApiCall {
                apiService.getIdTemplateConfig(eventId)
            }
            if (configResult is NetworkResult.Success) {
                visibleFields = configResult.data.visibleFields.filterNotNull()
            }

            // Fetch QR credential value for the printed QR code
            var qrValue = ""
            if (qrCredentialId.isNotBlank()) {
                val qrResult = com.thedavelopers.eventqr.core.api.safeApiCall {
                    apiService.getQrCredentialById(qrCredentialId)
                }
                if (qrResult is NetworkResult.Success) {
                    qrValue = qrResult.data.qrValue
                }
            }

            val result = if (hasPrintedId) {
                repository.reprintAttendeeId(eventId, attendeeId)
            } else {
                repository.printAttendeeId(eventId, attendeeId)
            }
            when (result) {
                is NetworkResult.Success -> {
                    val cardData = AndroidIdPrinter.CardData(
                        attendeeName = cachedAttendeeName,
                        eventName = cachedEventName,
                        registrationNumber = cachedRegistrationNumber,
                        role = cachedRole,
                        eventDate = cachedEventDate,
                        visibleFields = visibleFields,
                        qrValue = qrValue,
                    )
                    AndroidIdPrinter.print(
                        this@StaffAttendeeDetailsActivity,
                        "EventQR ID — $cachedAttendeeName",
                        cardData,
                    )
                    Toast.makeText(this@StaffAttendeeDetailsActivity, result.data.message, Toast.LENGTH_SHORT).show()
                }
                is NetworkResult.Error -> Toast.makeText(this@StaffAttendeeDetailsActivity, result.message, Toast.LENGTH_SHORT).show()
                NetworkResult.Loading -> Unit
            }
            findViewById<ProgressBar>(R.id.progressAttendeeDetails).visibility = View.GONE
            loadPrintLogs()
        }
    }

    private fun formatTime(value: Instant?): String = value?.let { timeFormatter.format(it) } ?: "--"

    private fun formatEventDate(value: Instant): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Manila"))
        return formatter.format(value)
    }

    private fun shortRegistrationId(value: UUID): String = "reg-${value.toString().take(8)}"
}
