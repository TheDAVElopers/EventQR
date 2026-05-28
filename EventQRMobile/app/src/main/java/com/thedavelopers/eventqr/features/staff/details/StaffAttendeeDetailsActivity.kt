package com.thedavelopers.eventqr.features.staff.details

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
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
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintRequest
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.staff.orUnknown
import com.thedavelopers.eventqr.features.staff.result.StaffTransactionResultActivity
import com.thedavelopers.eventqr.features.transactions.TransactionLogAdapter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
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