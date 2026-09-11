package com.thedavelopers.eventqr.features.admin

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AdminEventRequestDetailActivity : AppCompatActivity() {

    private lateinit var repository: AdminRepository

    private lateinit var loadingDetail: ProgressBar
    private lateinit var textDetailError: TextView

    private lateinit var textDetailTitle: TextView
    private lateinit var textDetailStatus: TextView
    private lateinit var textDetailDescription: TextView
    private lateinit var textProposedDate: TextView
    private lateinit var textLocation: TextView
    private lateinit var textExpectedAttendees: TextView
    private lateinit var textSubmittedBy: TextView
    private lateinit var textSubmittedOn: TextView

    private lateinit var pendingActionBar: LinearLayout
    private lateinit var buttonApprove: Button
    private lateinit var buttonReject: Button

    private lateinit var upgradeContainer: LinearLayout
    private lateinit var buttonUpgradeOrganizer: Button

    private val submittedFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.of("Asia/Manila"))

    private var requestId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_event_request_detail)

        requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        if (requestId.isBlank()) {
            Toast.makeText(this, "Request not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        repository = AdminRepository(this)
        bindViews()
        verifyAdminAndLoad()
    }

    override fun onResume() {
        super.onResume()
        if (requestId.isNotBlank()) {
            verifyAdminAndLoad()
        }
    }

    private fun verifyAdminAndLoad() {
        loadingDetail.visibility = View.VISIBLE
        textDetailError.visibility = View.GONE

        lifecycleScope.launch {
            when (val result = repository.getCurrentUser()) {
                is NetworkResult.Success -> {
                    if (result.data.role != AccountRole.ADMIN && result.data.role != AccountRole.SUPER_ADMIN) {
                        loadingDetail.visibility = View.GONE
                        textDetailError.visibility = View.VISIBLE
                        textDetailError.text = "Admin access required."
                        pendingActionBar.visibility = View.GONE
                        upgradeContainer.visibility = View.GONE
                    } else {
                        loadRequest()
                    }
                }

                is NetworkResult.Error -> {
                    loadingDetail.visibility = View.GONE
                    textDetailError.visibility = View.VISIBLE
                    textDetailError.text = toFriendlyError(result.message)
                }

                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun bindViews() {
        loadingDetail = findViewById(R.id.loadingDetail)
        textDetailError = findViewById(R.id.textDetailError)

        textDetailTitle = findViewById(R.id.textDetailTitle)
        textDetailStatus = findViewById(R.id.textDetailStatus)
        textDetailDescription = findViewById(R.id.textDetailDescription)
        textProposedDate = findViewById(R.id.textProposedDate)
        textLocation = findViewById(R.id.textLocation)
        textExpectedAttendees = findViewById(R.id.textExpectedAttendees)
        textSubmittedBy = findViewById(R.id.textSubmittedBy)
        textSubmittedOn = findViewById(R.id.textSubmittedOn)

        pendingActionBar = findViewById(R.id.pendingActionBar)
        buttonApprove = findViewById(R.id.buttonApprove)
        buttonReject = findViewById(R.id.buttonReject)

        upgradeContainer = findViewById(R.id.upgradeContainer)
        buttonUpgradeOrganizer = findViewById(R.id.buttonUpgradeOrganizer)

        findViewById<ImageButton>(R.id.buttonBack).setOnClickListener { finish() }
    }

    private fun loadRequest() {
        loadingDetail.visibility = View.VISIBLE
        textDetailError.visibility = View.GONE

        lifecycleScope.launch {
            when (val result = repository.getEventRequest(requestId)) {
                is NetworkResult.Success -> {
                    loadingDetail.visibility = View.GONE
                    renderDetail(result.data)
                }
                is NetworkResult.Error -> {
                    loadingDetail.visibility = View.GONE
                    textDetailError.visibility = View.VISIBLE
                    textDetailError.text = toFriendlyError(result.message)
                    pendingActionBar.visibility = View.GONE
                    upgradeContainer.visibility = View.GONE
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun renderDetail(request: EventRequestResponse) {
        textDetailTitle.text = request.eventName.ifBlank { "Untitled Event" }
        textDetailDescription.text = request.eventDescription?.takeIf { it.isNotBlank() }
            ?: "No description provided."
        textProposedDate.text = formatDate(request.startDateTime, submittedFormatter)
        textLocation.text = request.venue?.takeIf { it.isNotBlank() } ?: "Not available"
        textExpectedAttendees.text = request.capacity.toString()

        val requester = request.requesterName?.takeIf { it.isNotBlank() }
            ?: request.contactEmail?.takeIf { it.isNotBlank() }
            ?: request.requesterUserId.toString()
        textSubmittedBy.text = requester
        textSubmittedOn.text = formatDate(request.createdAt, submittedFormatter)

        bindStatus(textDetailStatus, request.status)

        when (request.status) {
            EventRequestStatus.PENDING -> {
                pendingActionBar.visibility = View.VISIBLE
                upgradeContainer.visibility = View.GONE

                buttonApprove.setOnClickListener {
                    showConfirmSheet(
                        title = "Approve Request?",
                        message = "Approve this event creation request? The requestor will be notified and can proceed to set up their event.",
                        confirmLabel = "Approve",
                        requireRemarks = false,
                    ) { remarks ->
                        performAction(Action.APPROVE, remarks)
                    }
                }

                buttonReject.setOnClickListener {
                    showConfirmSheet(
                        title = "Reject Request?",
                        message = "Reject this event creation request? The requestor will be notified.",
                        confirmLabel = "Reject",
                        requireRemarks = false,
                    ) { remarks ->
                        performAction(Action.REJECT, remarks)
                    }
                }
            }

            EventRequestStatus.APPROVED -> {
                pendingActionBar.visibility = View.GONE
                upgradeContainer.visibility = View.VISIBLE

                if (request.organizerUpgraded) {
                    setUpgradeButtonUpgradedState()
                } else {
                    setUpgradeButtonActiveState()
                    buttonUpgradeOrganizer.setOnClickListener {
                        showConfirmSheet(
                            title = "Upgrade to Organizer?",
                            message = "This will upgrade the requester's account to Organizer role, allowing them to fully manage their event.",
                            confirmLabel = "Upgrade",
                            requireRemarks = false,
                        ) {
                            performAction(Action.UPGRADE, null)
                        }
                    }
                }
            }

            EventRequestStatus.REJECTED -> {
                pendingActionBar.visibility = View.GONE
                upgradeContainer.visibility = View.GONE
            }
        }
    }

    private fun setUpgradeButtonActiveState() {
        buttonUpgradeOrganizer.text = "Upgrade to Organizer"
        buttonUpgradeOrganizer.isEnabled = true
        buttonUpgradeOrganizer.isClickable = true
        buttonUpgradeOrganizer.alpha = 1.0f
    }

    private fun setUpgradeButtonUpgradedState() {
        buttonUpgradeOrganizer.text = "Upgraded"
        buttonUpgradeOrganizer.isEnabled = false
        buttonUpgradeOrganizer.isClickable = false
        buttonUpgradeOrganizer.alpha = 0.65f
        buttonUpgradeOrganizer.setOnClickListener(null)
    }

    private fun showConfirmSheet(
        title: String,
        message: String,
        confirmLabel: String,
        requireRemarks: Boolean,
        onConfirm: (String?) -> Unit,
    ) {
        val dialog = BottomSheetDialog(this)
        val sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_admin_confirm, null)
        dialog.setContentView(sheet)

        val textConfirmTitle = sheet.findViewById<TextView>(R.id.textConfirmTitle)
        val textConfirmMessage = sheet.findViewById<TextView>(R.id.textConfirmMessage)
        val inputRemarks = sheet.findViewById<EditText>(R.id.inputRemarks)
        val buttonConfirmAction = sheet.findViewById<Button>(R.id.buttonConfirmAction)
        val buttonCancelAction = sheet.findViewById<Button>(R.id.buttonCancelAction)

        textConfirmTitle.text = title
        textConfirmMessage.text = message
        buttonConfirmAction.text = confirmLabel

        if (requireRemarks) {
            inputRemarks.visibility = View.VISIBLE
            inputRemarks.hint = "Remarks"
        }

        buttonConfirmAction.setOnClickListener {
            val remarks = inputRemarks.text?.toString()?.trim().orEmpty()
            if (requireRemarks && remarks.isBlank()) {
                inputRemarks.error = "Please provide remarks."
                return@setOnClickListener
            }
            dialog.dismiss()
            onConfirm(remarks.ifBlank { null })
        }

        buttonCancelAction.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun performAction(action: Action, remarks: String?) {
        setActionButtonsEnabled(false)
        loadingDetail.visibility = View.VISIBLE
        textDetailError.visibility = View.GONE

        lifecycleScope.launch {
            val result = when (action) {
                Action.APPROVE -> repository.approveEvent(requestId, remarks)
                Action.REJECT -> repository.rejectEvent(requestId, remarks?.takeIf { it.isNotBlank() })
                Action.UPGRADE -> repository.upgradeOrganizer(requestId)
            }

            when (result) {
                is NetworkResult.Success -> {
                    loadingDetail.visibility = View.GONE
                    setActionButtonsEnabled(true)
                    when (action) {
                        Action.APPROVE -> showApprovedDialog()
                        Action.REJECT -> {
                            Toast.makeText(this@AdminEventRequestDetailActivity, "Request rejected.", Toast.LENGTH_SHORT).show()
                            loadRequest()
                        }
                        Action.UPGRADE -> {
                            Toast.makeText(this@AdminEventRequestDetailActivity, "Requester upgraded to Organizer.", Toast.LENGTH_SHORT).show()
                            setUpgradeButtonUpgradedState()
                            loadRequest()
                        }
                    }
                }

                is NetworkResult.Error -> {
                    loadingDetail.visibility = View.GONE
                    setActionButtonsEnabled(true)
                    textDetailError.visibility = View.VISIBLE
                    textDetailError.text = toFriendlyError(result.message)
                }

                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun showApprovedDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_request_approved, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(R.id.buttonDone).setOnClickListener {
            dialog.dismiss()
            loadRequest()
            setResult(RESULT_OK)
        }

        dialog.show()
    }

    private fun bindStatus(view: TextView, status: EventRequestStatus) {
        when (status) {
            EventRequestStatus.PENDING -> {
                view.text = "Pending"
                view.setBackgroundResource(R.drawable.bg_admin_pending_badge)
                view.setTextColor(0xFF92400E.toInt())
            }
            EventRequestStatus.APPROVED -> {
                view.text = "Approved"
                view.setBackgroundResource(R.drawable.bg_admin_approved_badge)
                view.setTextColor(0xFF065F46.toInt())
            }
            EventRequestStatus.REJECTED -> {
                view.text = "Rejected"
                view.setBackgroundResource(R.drawable.bg_admin_rejected_badge)
                view.setTextColor(0xFF991B1B.toInt())
            }
        }
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        buttonApprove.isEnabled = enabled
        buttonReject.isEnabled = enabled
        if (buttonUpgradeOrganizer.text.toString() != "Upgraded") {
            buttonUpgradeOrganizer.isEnabled = enabled
            buttonUpgradeOrganizer.isClickable = enabled
        }
    }

    private fun formatDate(value: Instant?, formatter: DateTimeFormatter): String {
        return if (value == null) "Not available" else formatter.format(value)
    }

    private fun toFriendlyError(message: String): String {
        val normalized = message.lowercase()
        return when {
            normalized.contains("disabled") || normalized.contains("suspend") -> "Account is disabled. Contact support."
            normalized.contains("401") || normalized.contains("unauthorized") -> "Session expired. Please sign in again."
            normalized.contains("403") || normalized.contains("forbidden") || normalized.contains("admin access") -> "Admin access required."
            normalized.contains("404") || normalized.contains("not found") -> "Request not found."
            normalized.contains("400") || normalized.contains("invalid") || normalized.contains("bad request") -> "Action cannot be completed in the current state."
            normalized.contains("500") -> "Server error. Please try again later."
            normalized.contains("unable to resolve host") || normalized.contains("failed to connect") || normalized.contains("timeout") -> "No internet connection. Check your network and try again."
            else -> message
        }
    }

    private enum class Action {
        APPROVE,
        REJECT,
        UPGRADE,
    }

    companion object {
        const val EXTRA_REQUEST_ID = "extra_request_id"
    }
}
