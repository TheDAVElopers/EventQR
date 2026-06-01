package com.thedavelopers.eventqr.features.attendee

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AttendeeEventRequestDetailActivity : AppCompatActivity() {
    private lateinit var repository: AttendeeRepository
    private lateinit var requestId: String

    private lateinit var progress: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var content: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var noteCard: LinearLayout
    private lateinit var noteTitle: TextView
    private lateinit var noteBody: TextView

    private val eventDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Asia/Manila"))
    private val submittedDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.of("Asia/Manila"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendee_event_request_detail)

        repository = AttendeeRepository(this)
        requestId = intent.getStringExtra(EXTRA_EVENT_REQUEST_ID).orEmpty()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        progress = findViewById(R.id.progressRequestDetails)
        errorText = findViewById(R.id.txtRequestDetailsError)
        retryButton = findViewById(R.id.btnRequestDetailsRetry)
        content = findViewById(R.id.contentRequestDetails)
        statusText = findViewById(R.id.txtDetailStatus)
        noteCard = findViewById(R.id.noteCard)
        noteTitle = findViewById(R.id.txtNoteTitle)
        noteBody = findViewById(R.id.txtNoteBody)

        retryButton.setOnClickListener { loadRequest() }
        loadRequest()
    }

    private fun loadRequest() {
        if (requestId.isBlank()) {
            showError("Missing event request information.")
            return
        }

        showLoading()
        lifecycleScope.launch {
            when (val result = repository.getEventRequest(requestId)) {
                is NetworkResult.Success -> render(result.data)
                is NetworkResult.Error -> showError(result.message.ifBlank { "Unable to load request details." })
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun showLoading() {
        progress.visibility = View.VISIBLE
        errorText.visibility = View.GONE
        retryButton.visibility = View.GONE
        content.visibility = View.GONE
    }

    private fun showError(message: String) {
        progress.visibility = View.GONE
        content.visibility = View.GONE
        errorText.visibility = View.VISIBLE
        retryButton.visibility = View.VISIBLE
        errorText.text = message
    }

    private fun render(request: EventRequestResponse) {
        progress.visibility = View.GONE
        errorText.visibility = View.GONE
        retryButton.visibility = View.GONE
        content.visibility = View.VISIBLE

        findViewById<TextView>(R.id.txtDetailTitle).text = request.eventName.ifBlank { "Untitled Event" }
        findViewById<TextView>(R.id.txtDetailDescription).text = request.eventDescription?.takeIf { it.isNotBlank() }
            ?: "No description provided."
        findViewById<TextView>(R.id.txtDetailDate).text = formatDate(request.startDateTime, eventDateFormatter)
        findViewById<TextView>(R.id.txtDetailLocation).text = request.venue?.takeIf { it.isNotBlank() } ?: "TBD"
        findViewById<TextView>(R.id.txtDetailCapacity).text = request.capacity.toString()
        findViewById<TextView>(R.id.txtDetailSubmitted).text = formatDate(request.createdAt, submittedDateFormatter)

        bindStatus(request.status)
        bindNote(request)
    }

    private fun bindStatus(status: EventRequestStatus) {
        when (status) {
            EventRequestStatus.APPROVED -> {
                statusText.text = "Approved"
                statusText.setBackgroundResource(R.drawable.bg_admin_approved_badge)
                statusText.setTextColor(0xFF047857.toInt())
            }
            EventRequestStatus.PENDING -> {
                statusText.text = "Pending"
                statusText.setBackgroundResource(R.drawable.bg_admin_pending_badge)
                statusText.setTextColor(0xFFB45309.toInt())
            }
            EventRequestStatus.REJECTED -> {
                statusText.text = "Rejected"
                statusText.setBackgroundResource(R.drawable.bg_admin_rejected_badge)
                statusText.setTextColor(0xFFB91C1C.toInt())
            }
        }
    }

    private fun bindNote(request: EventRequestResponse) {
        when (request.status) {
            EventRequestStatus.APPROVED -> {
                noteCard.visibility = View.VISIBLE
                noteCard.setBackgroundResource(R.drawable.bg_request_note_approved)
                noteTitle.text = "Approval Note"
                noteBody.text = request.adminRemarks?.takeIf { it.isNotBlank() }
                    ?: "Approved. Venue confirmed. Please proceed to event setup."
                noteTitle.setTextColor(0xFF065F46.toInt())
                noteBody.setTextColor(0xFF065F46.toInt())
            }
            EventRequestStatus.REJECTED -> {
                noteCard.visibility = View.VISIBLE
                noteCard.setBackgroundResource(R.drawable.bg_request_note_rejected)
                noteTitle.text = "Rejection Note"
                noteBody.text = request.adminRemarks?.takeIf { it.isNotBlank() }
                    ?: "This request was rejected. Review the event details and submit a revised request if needed."
                noteTitle.setTextColor(0xFF991B1B.toInt())
                noteBody.setTextColor(0xFF991B1B.toInt())
            }
            EventRequestStatus.PENDING -> {
                noteCard.visibility = View.GONE
            }
        }
    }

    private fun formatDate(value: Instant?, formatter: DateTimeFormatter): String {
        return value?.let { formatter.format(it) } ?: "-"
    }

    companion object {
        const val EXTRA_EVENT_REQUEST_ID = "extra_event_request_id"
    }
}
