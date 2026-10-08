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
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import com.thedavelopers.eventqr.features.staff.StaffDashboardActivity
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.staff.orUnknown
import com.thedavelopers.eventqr.features.staff.details.StaffAttendeeDetailsActivity
import java.time.Instant

open class StaffTransactionResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)
        if (!RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.STAFF)) {
            Toast.makeText(this, this.getString(R.string.staff_dashboard_access_denied_staff_or_above), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_transaction_result)

        val approved = intent.getStringExtra(StaffScreenExtras.EXTRA_TRANSACTION_RESULT).orUnknown() == TransactionResult.APPROVED.name
        findViewById<TextView>(R.id.txtTransactionType).text = buildString {
            append(intent.getStringExtra(StaffScreenExtras.EXTRA_SCAN_PURPOSE_NAME).orUnknown("Scan Purpose"))
            append(" · ")
            append(intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_TITLE).orUnknown("Event"))
        }
        findViewById<TextView>(R.id.txtTransactionAttendee).text = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME).orUnknown("Attendee")
        findViewById<TextView>(R.id.txtTransactionTime).text = formatScannedAt(
            intent.getStringExtra(StaffScreenExtras.EXTRA_SCANNED_AT).orEmpty()
        )
        findViewById<TextView>(R.id.txtTransactionPoints).text = intent.getIntExtra(StaffScreenExtras.EXTRA_POINTS_DELTA, 0).let { delta -> if (delta >= 0) "+$delta pts" else "$delta pts" }
        findViewById<TextView>(R.id.txtTransactionReason).text = intent.getStringExtra(StaffScreenExtras.EXTRA_REASON).orUnknown(getString(if (approved) R.string.staff_transaction_result_approved_by_backend else R.string.staff_transaction_result_rejected_by_backend))

        findViewById<View>(R.id.headerApproved).visibility = if (approved) View.VISIBLE else View.GONE
        findViewById<View>(R.id.headerRejected).visibility = if (approved) View.GONE else View.VISIBLE
        val pointsDelta = intent.getIntExtra(StaffScreenExtras.EXTRA_POINTS_DELTA, 0)
        // Points card + "Points awarded" copy only when points were really awarded.
        findViewById<View>(R.id.layoutTransactionApproved).visibility =
            if (TransactionResultCopy.showsPoints(approved, pointsDelta)) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.txtTransactionStateHint).setText(TransactionResultCopy.hintRes(approved, pointsDelta))
        findViewById<TextView>(R.id.txtTransactionStateHintRejected).setText(TransactionResultCopy.hintRes(approved, pointsDelta))
        findViewById<View>(R.id.layoutTransactionRejected).visibility = if (approved) View.GONE else View.VISIBLE

        findViewById<Button>(R.id.btnViewTransactionAttendee).visibility = if (approved) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnViewTransactionAttendee).setOnClickListener { openAttendeeDetails() }
        findViewById<Button>(R.id.btnTransactionScanAgain).text = getString(R.string.staff_transaction_result_scan_next_attendee)
        findViewById<Button>(R.id.btnTransactionDashboard).text = getString(R.string.common_back_to_dashboard)
        findViewById<Button>(R.id.btnTransactionScanAgain).setOnClickListener { openScanner() }
        findViewById<Button>(R.id.btnTransactionDashboard).setOnClickListener {
            startActivity(Intent(this, StaffDashboardActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.nav_header_back).setOnClickListener { openScanner() }
    }

    private fun openScanner() {
        startActivity(Intent(this, ScannerActivity::class.java).apply {
            putExtra(StaffScreenExtras.EXTRA_EVENT_ID, intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID))
        })
        finish()
    }

    private fun formatScannedAt(raw: String): String {
        val instant = runCatching { Instant.parse(raw) }.getOrNull()
        return if (instant != null) DateFormatters.formatInstant(instant) else "Just now"
    }

    private fun openAttendeeDetails() {
        val attendeeId = intent.getStringExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID).orEmpty()
        if (attendeeId.isBlank()) {
            Toast.makeText(this, this.getString(R.string.staff_transaction_result_attendee_details_are_unavailable), Toast.LENGTH_SHORT).show()
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
