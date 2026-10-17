package com.thedavelopers.eventqr.features.staff.details

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.idprinting.AndroidIdPrinter
import com.thedavelopers.eventqr.features.idprinting.PrintPermission
import com.thedavelopers.eventqr.features.idprinting.PrintPermissionLoad
import com.thedavelopers.eventqr.features.idprinting.countsAsPrinted
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse
import com.thedavelopers.eventqr.features.registrations.RegistrationNumberFormatter
import com.thedavelopers.eventqr.features.registrations.RegistrationStatusBadgeStyler
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.staff.StaffRepository
import com.thedavelopers.eventqr.features.staff.StaffScreenExtras
import com.thedavelopers.eventqr.features.staff.orUnknown
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import com.thedavelopers.eventqr.features.transactions.TransactionLogAdapter
import androidx.lifecycle.lifecycleScope
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
    /** The caller's Print IDs permission for this event; false turns the print button into a disabled hint. */
    private var canPrintAllowed: Boolean = false
    /** False until the permission has been read; the button stays disabled (without the "no permission" notice) meanwhile. */
    private var printPermissionKnown: Boolean = false
    /** True while a permission read is in flight, so a tap can say "checking" instead of "check failed". */
    private var printPermissionLoading: Boolean = false
    private var permissionSnackbar: com.google.android.material.snackbar.Snackbar? = null

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH).withZone(ZoneId.of("Asia/Manila"))
        .withZone(ZoneId.of("Asia/Manila"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        if (!RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.STAFF)) {
            Toast.makeText(this, this.getString(R.string.staff_dashboard_access_denied_staff_or_above), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_attendee_details)
        repository = newRepository()
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

        syncPrintAffordance()
        loadDetails()
    }

    /** Overridden in tests to inject a fake repository. */
    @VisibleForTesting
    internal open fun newRepository(): StaffRepository = StaffRepository(this)

    private fun loadDetails() {
        if (eventId.isBlank() || attendeeId.isBlank()) {
            Toast.makeText(this, this.getString(R.string.staff_attendee_details_missing_attendee_context), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<ProgressBar>(R.id.progressAttendeeDetails).visibility = View.VISIBLE
        lifecycleScope.launch {
            loadPrintPermission()
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

    private suspend fun loadPrintPermission() {
        printPermissionLoading = true
        try {
            when (val load = repository.printPermission(eventId)) {
                is PrintPermissionLoad.Known -> applyPrintPermission(load.canPrintId)
                PrintPermissionLoad.Failed -> onPrintPermissionLoadFailed()
            }
        } finally {
            printPermissionLoading = false
        }
    }

    /** Keeps printing disabled and offers a retry; the server still enforces the permission either way. */
    @VisibleForTesting
    internal fun onPrintPermissionLoadFailed() {
        canPrintAllowed = false
        printPermissionKnown = false
        syncPrintAffordance()
        permissionSnackbar?.dismiss()
        permissionSnackbar = com.google.android.material.snackbar.Snackbar
            .make(findViewById(R.id.btnPrintOrReprintId), R.string.staff_print_permission_check_failed, com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.common_retry) { lifecycleScope.launch { loadPrintPermission() } }
            .also { it.show() }
    }

    override fun onDestroy() {
        permissionSnackbar?.dismiss()
        super.onDestroy()
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
        findViewById<TextView>(R.id.txtDetailCheckInTime).text = formatTime(item.enteredAt)
        findViewById<TextView>(R.id.txtDetailPointsBalance).text = "${item.pointsEarned} pts"
        findViewById<TextView>(R.id.txtDetailTransactionCount).text = getString(R.string.common_value_unavailable)

        findViewById<TextView>(R.id.txtDetailQrStatus).text = getString(if (item.qrCredentialId == null) R.string.staff_attendee_details_qr_credential_pending else R.string.staff_attendee_details_qr_credential_issued)
        findViewById<TextView>(R.id.txtDetailEntryStatus).text = RegistrationStatusBadgeStyler.displayLabel(item.status)
        findViewById<TextView>(R.id.txtDetailAttendanceStatus).text = getString(if (item.attendedAt != null || item.enteredAt != null) R.string.common_checked_in else R.string.common_registered)
        findViewById<TextView>(R.id.txtDetailExitStatus).text = getString(if (item.exitedAt != null) R.string.search_attendees_exited else R.string.staff_attendee_details_not_exited)
        findViewById<TextView>(R.id.txtDetailRegistrationDate).text = item.registeredAt?.let { "Registered: ${formatTime(it)}" } ?: "Registered: Unknown"
        syncPrintAffordance()
    }

    /**
     * Applies the caller's Print IDs permission: without it the print control stays visible but
     * disabled, with a line explaining how to get the permission.
     */
    @VisibleForTesting
    internal fun applyPrintPermission(canPrintId: Boolean?) {
        canPrintAllowed = PrintPermission.isPrintAllowed(canPrintId)
        printPermissionKnown = true
        permissionSnackbar?.dismiss()
        syncPrintAffordance()
    }

    private fun syncPrintAffordance() {
        val button = findViewById<View>(R.id.btnPrintOrReprintId)
        val notice = findViewById<TextView>(R.id.txtPrintPermissionNotice)
        when {
            !printPermissionKnown -> {
                // Permission still loading (or failed to load): disabled, but not yet "you lack permission".
                button.visibility = if (qrCredentialId.isBlank()) View.GONE else View.VISIBLE
                button.isEnabled = false
                button.isClickable = false
                button.alpha = DISABLED_ALPHA
                notice.visibility = View.GONE
            }
            !canPrintAllowed -> {
                button.visibility = View.VISIBLE
                button.isEnabled = false
                button.isClickable = false
                button.alpha = DISABLED_ALPHA
                notice.visibility = View.VISIBLE
            }
            qrCredentialId.isBlank() -> {
                button.visibility = View.GONE
                notice.visibility = View.GONE
            }
            else -> {
                button.visibility = View.VISIBLE
                button.isEnabled = true
                button.isClickable = true
                button.alpha = 1f
                notice.visibility = View.GONE
            }
        }
    }

    private fun loadTransactions() {
        val layoutTransactions = findViewById<View>(R.id.layoutRecentTransactions)
        val recyclerTransactions = findViewById<RecyclerView>(R.id.recyclerDetailTransactions)
        val emptyText = findViewById<TextView>(R.id.txtDetailRecentTransactionsEmpty)
        val countText = findViewById<TextView>(R.id.txtDetailTransactionCount)

        lifecycleScope.launch {
            // size=1: only totalElements is read, so the full list is never fetched. APPROVED only: rejected scans don't count.
            when (val txResult = repository.getTransactionsByEvent(eventId, attendeeId, page = 0, size = 1, result = "APPROVED")) {
                is NetworkResult.Success -> countText.text = txResult.data.totalElements.toString()
                is NetworkResult.Error -> countText.text = getString(R.string.common_value_unavailable)
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
            findViewById<TextView>(R.id.txtPrintOrReprintIdLabel).text = getString(R.string.staff_attendee_details_print_id)
            return
        }

        lifecycleScope.launch {
            when (val result = repository.getStaffPrintLogs(eventId)) {
                is NetworkResult.Success -> {
                    // Cancelled, failed and still-pending prints don't count; only a confirmed PRINTED log does.
                    showPrintedState(result.data.any { it.attendeeUserId.toString() == attendeeId && it.countsAsPrinted() })
                }
                is NetworkResult.Error -> Unit
                NetworkResult.Loading -> Unit
            }
        }
    }

    @VisibleForTesting
    internal fun printId() {
        if (eventId.isBlank() || attendeeId.isBlank()) {
            Toast.makeText(this, this.getString(R.string.staff_attendee_details_attendee_context_is_required_for_pri), Toast.LENGTH_SHORT).show()
            return
        }
        if (!printPermissionKnown) {
            val message = if (printPermissionLoading) R.string.staff_print_permission_checking else R.string.staff_print_permission_check_failed
            Toast.makeText(this, this.getString(message), Toast.LENGTH_LONG).show()
            return
        }
        if (!canPrintAllowed) {
            Toast.makeText(this, this.getString(R.string.staff_print_id_permission_required), Toast.LENGTH_LONG).show()
            return
        }

        findViewById<ProgressBar>(R.id.progressAttendeeDetails).visibility = View.VISIBLE
        lifecycleScope.launch {
            val apiService = com.thedavelopers.eventqr.core.api.ApiClient.getService(this@StaffAttendeeDetailsActivity)

            // Template visible fields (default QR + name when the event has none saved)
            val visibleFields = repository.getIdCardVisibleFields(eventId)

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

            onPrintResult(requestIdPrint(), visibleFields, qrValue)
            findViewById<ProgressBar>(R.id.progressAttendeeDetails).visibility = View.GONE
        }
    }

    /** One print request for the attendee: a reprint once a PRINTED log exists, otherwise a first print. */
    private suspend fun requestIdPrint(): NetworkResult<IdPrintResponse> =
        if (hasPrintedId) repository.reprintAttendeeId(eventId, attendeeId) else repository.printAttendeeId(eventId, attendeeId)

    /**
     * Handles the print request's outcome. A refusal — the permission can be revoked mid-session, so the
     * server answers 403 — surfaces its message and never opens the system print dialog.
     */
    @VisibleForTesting
    internal fun onPrintResult(result: NetworkResult<IdPrintResponse>, visibleFields: List<String>, qrValue: String) {
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
                // Logged PENDING before the dialog opens; confirmed with what the user actually did in it.
                // The Print/Reprint label is refreshed once the dialog's outcome is confirmed, not here:
                // the new log is still PENDING while the dialog is open.
                openSystemPrintDialog(cardData, result.data.printLogId, result.data.message)
            }
            is NetworkResult.Error -> {
                val message = if (PrintPermission.isPermissionDenied(result)) {
                    PrintPermission.deniedMessage(result, getString(R.string.staff_print_permission_denied))
                } else {
                    result.message
                }
                Toast.makeText(this@StaffAttendeeDetailsActivity, message, Toast.LENGTH_LONG).show()
                loadPrintLogs()
            }
            NetworkResult.Loading -> Unit
        }
    }

    /** Opens the system print dialog for a card the server already logged as PENDING. */
    @VisibleForTesting
    internal open fun openSystemPrintDialog(cardData: AndroidIdPrinter.CardData, printLogId: UUID, serverMessage: String) {
        AndroidIdPrinter.print(
            this@StaffAttendeeDetailsActivity,
            "EventQR ID — $cachedAttendeeName",
            cardData,
        ) { outcome -> onIdPrintOutcome(outcome, printLogId, serverMessage) }
    }

    /**
     * Confirms the PENDING log with what happened in the print dialog. A sent print flips the label to Reprint
     * right away; once the server has the final status the label is re-read from it. If the confirmation never
     * got through, the local state is kept rather than replaced by the server's still-PENDING view.
     */
    @VisibleForTesting
    internal fun onIdPrintOutcome(outcome: AndroidIdPrinter.PrintOutcome, printLogId: UUID, serverMessage: String) {
        repository.confirmPrintLogsInBackground(eventId, listOf(printLogId), outcome) { confirmedAll ->
            if (confirmedAll && !isFinishing && !isDestroyed) loadPrintLogs()
        }
        if (isFinishing || isDestroyed) return
        if (outcome == AndroidIdPrinter.PrintOutcome.SENT) showPrintedState(true)
        val message = when (outcome) {
            AndroidIdPrinter.PrintOutcome.SENT -> serverMessage
            AndroidIdPrinter.PrintOutcome.CANCELLED -> getString(R.string.staff_print_cancelled_toast)
            AndroidIdPrinter.PrintOutcome.FAILED -> getString(R.string.staff_print_job_failed_message)
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showPrintedState(printed: Boolean) {
        hasPrintedId = printed
        findViewById<TextView>(R.id.txtPrintOrReprintIdLabel).text =
            getString(if (printed) R.string.staff_attendee_details_reprint_id else R.string.staff_attendee_details_print_id)
    }

    private fun formatTime(value: Instant?): String = value?.let { timeFormatter.format(it) } ?: "--"

    private fun formatEventDate(value: Instant): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Manila"))
        return formatter.format(value)
    }

    private fun shortRegistrationId(value: UUID): String = "reg-${value.toString().take(8)}"

    private companion object {
        /** Matches the app's other disabled controls (see StaffTransactionsActivity's purpose card). */
        const val DISABLED_ALPHA = 0.5f
    }
}
