package com.thedavelopers.eventqr.features.staff.result

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import com.thedavelopers.eventqr.features.staff.StaffDashboardActivity
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.staff.orUnknown
import com.thedavelopers.eventqr.features.staff.details.StaffAttendeeDetailsActivity

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