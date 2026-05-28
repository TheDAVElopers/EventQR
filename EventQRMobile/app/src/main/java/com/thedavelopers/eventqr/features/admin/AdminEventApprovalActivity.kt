package com.thedavelopers.eventqr.features.admin

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpLoad
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class AdminEventApprovalActivity : AppCompatActivity() {
    private lateinit var repository: AdminRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var eventList: LinearLayout
    private lateinit var detail: LinearLayout
    private lateinit var search: EditText
    private lateinit var statusSpinner: Spinner
    private var eventsSource: OrganizerMvpLoad<List<OrganizerMvpEvent>> =
        OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)

    private val PRIMARY = Color.parseColor("#25215F")
    private val PURPLE = Color.parseColor("#5B25C9")
    private val BG = Color.parseColor("#F7F7FA")
    private val CARD = Color.WHITE
    private val TEXT = Color.parseColor("#111827")
    private val MUTED = Color.parseColor("#6B7280")
    private val BORDER = Color.parseColor("#E5E7EB")
    private val SUCCESS = Color.parseColor("#009688")
    private val ERROR = Color.parseColor("#EF4444")
    private val WARNING = Color.parseColor("#F97316")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AdminRepository(this)
        sessionManager = SessionManager(this)

        if (sessionManager.getUserRole() != "ADMIN") {
            showUnauthorized()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        setContentView(root)

        val header = createHeader("Event Request Approval", "Admin Control Panel")
        root.addView(header)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(18))
        }
        scroll.addView(content)
        root.addView(scroll)

        search = EditText(this).apply {
            hint = "Search event, requester, or venue"
            inputType = InputType.TYPE_CLASS_TEXT
            background = rounded(Color.WHITE, 10, BORDER)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(12))
            }
        }
        content.addView(search)

        statusSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@AdminEventApprovalActivity,
                android.R.layout.simple_spinner_item,
                listOf("All", "Pending", "Approved", "Rejected"),
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(16))
            }
        }
        content.addView(statusSpinner)

        eventList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(eventList)

        content.addView(section("Request Details"))
        detail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(detail)

        search.afterTextChanged { render() }
        statusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = render()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        loadEvents()
    }

    private fun showUnauthorized() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BG)
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        root.addView(text("Access Denied", 24, true, ERROR))
        root.addView(spacer(16))
        root.addView(text("Only administrators can access the event approval panel.", 16, false, MUTED).apply { gravity = Gravity.CENTER })
        root.addView(spacer(24))
        root.addView(primaryButton("Go Back") { finish() })
        setContentView(root)
    }

    private fun loadEvents() {
        eventList.removeAllViews()
        eventList.addView(loadingState("Fetching event requests..."))
        MainScope().launch {
            val result = repository.loadAllEventRequests()
            eventsSource = when (result) {
                is NetworkResult.Success -> {
                    OrganizerMvpLoad(
                        result.data.map { it.toMvpEvent() },
                        OrganizerMvpDataSource.BACKEND
                    )
                }
                is NetworkResult.Error -> {
                    OrganizerMvpLoad(
                        emptyList(),
                        OrganizerMvpDataSource.ERROR,
                        result.message
                    )
                }
                NetworkResult.Loading -> OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR)
            }
            render()
        }
    }

    private fun EventRequestResponse.toMvpEvent(): OrganizerMvpEvent = OrganizerMvpEvent(
        id = eventRequestId.toString(),
        title = eventName,
        organizerName = requesterName ?: "Unknown Organizer",
        dateTime = listOf(DateFormatters.formatInstant(startDateTime), DateFormatters.formatInstant(endDateTime))
            .filter { it != "-" }
            .joinToString(" - ")
            .ifBlank { "-" },
        shortDate = DateFormatters.formatInstant(startDateTime),
        venue = venue ?: "TBD",
        status = status.name.lowercase().replaceFirstChar { it.uppercase() },
        submittedDate = DateFormatters.formatInstant(createdAt),
        adminRemarks = adminRemarks ?: "No remarks yet.",
        additionalOrganizers = emptyList(),
        registeredCount = capacity,
        enteredCount = 0,
        attendedCount = 0,
        exitedCount = 0,
        noShowCount = 0,
        totalTransactions = 0,
        successfulScans = 0,
        rejectedScans = 0,
        benefitClaims = 0,
        boothSessionVisits = 0,
        rewardRedemptions = 0,
        totalPointsAwarded = 0,
        idTemplateStatus = "Pending",
        rewardsStatus = if (requestedFeatures?.contains("Rewards") == true) "Requested" else "Not requested",
        staffCount = 0,
        scanPurposesCount = 0
    )

    private fun render() {
        val q = search.text.toString()
        val status = statusSpinner.selectedItem?.toString().orEmpty()
        val filtered = eventsSource.data.filter {
            (status == "All" || it.status.equals(status, true)) &&
                (it.title.contains(q, true) || it.organizerName.contains(q, true) || it.venue.contains(q, true))
        }
        
        eventList.removeAllViews()
        dataSourceBanner(eventsSource)?.let { eventList.addView(it) }

        if (filtered.isEmpty()) {
            eventList.addView(emptyState("No event requests match your filters.", "Refresh") { loadEvents() })
            detail.removeAllViews()
            detail.addView(emptyState("Select a request from the list to view details."))
            return
        }

        filtered.forEach { event ->
            eventList.addView(eventRequestCard(event))
        }

        // Auto-select first if detail is empty
        if (detail.childCount == 0 || detail.tag == null) {
            renderDetail(filtered.first())
        }
    }

    private fun eventRequestCard(event: OrganizerMvpEvent): LinearLayout =
        card().apply {
            val header = row()
            val titleBox = LinearLayout(this@AdminEventApprovalActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            titleBox.addView(text(event.title, 18, true))
            titleBox.addView(text(event.organizerName, 13, false, MUTED))
            titleBox.addView(text("Date: ${event.shortDate}\nVenue: ${event.venue}", 12, false, MUTED))
            header.addView(titleBox)
            header.addView(badge(event.status))
            addView(header)
            
            setOnClickListener { renderDetail(event) }
        }

    private fun renderDetail(event: OrganizerMvpEvent) {
        detail.removeAllViews()
        detail.tag = event.id
        detail.addView(card().apply {
            addView(text(event.title, 20, true))
            addView(spacer(8))
            addView(text("Requester", 12, true, MUTED))
            addView(text(event.organizerName, 15, false))
            addView(spacer(8))
            addView(text("Schedule & Venue", 12, true, MUTED))
            addView(text("${event.dateTime}\n${event.venue}", 14, false))
            addView(spacer(8))
            addView(text("Capacity & Settings", 12, true, MUTED))
            addView(text("Expected attendees: ${event.registeredCount}\nRewards: ${event.rewardsStatus}", 14, false))
            addView(spacer(8))
            addView(text("Submitted Date", 12, true, MUTED))
            addView(text(event.submittedDate, 14, false))
            
            if (event.status == "Pending") {
                addView(spacer(16))
                val actions = row()
                actions.addView(primaryButton("Approve", SUCCESS) { showApproveDialog(event) }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
                })
                actions.addView(primaryButton("Reject", ERROR) { showRejectDialog(event) }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(actions)
            } else {
                addView(spacer(12))
                addView(card(10).apply {
                    elevation = 0f
                    background = rounded(if (event.status == "Approved") Color.parseColor("#DCFCE7") else Color.parseColor("#FEF2F2"), 10)
                    addView(text("Admin Remarks:", 12, true, if (event.status == "Approved") SUCCESS else ERROR))
                    addView(text(event.adminRemarks, 14, false, if (event.status == "Approved") SUCCESS else ERROR))
                })
            }
        })
    }

    private fun showApproveDialog(event: OrganizerMvpEvent) {
        AlertDialog.Builder(this)
            .setTitle("Approve Event Request")
            .setMessage("Are you sure you want to approve \"${event.title}\"?")
            .setPositiveButton("Approve") { _, _ ->
                performAction(event.id, true, null)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRejectDialog(event: OrganizerMvpEvent) {
        val input = EditText(this).apply {
            hint = "Reason for rejection"
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        AlertDialog.Builder(this)
            .setTitle("Reject Event Request")
            .setMessage("Please provide a reason for rejecting \"${event.title}\".")
            .setView(input)
            .setPositiveButton("Reject") { _, _ ->
                val reason = input.text.toString()
                if (reason.isBlank()) {
                    Toast.makeText(this, "Rejection reason is required.", Toast.LENGTH_SHORT).show()
                } else {
                    performAction(event.id, false, reason)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performAction(eventId: String, approve: Boolean, reason: String?) {
        val message = if (approve) "Approving event..." else "Rejecting event..."
        detail.removeAllViews()
        detail.addView(loadingState(message))
        
        MainScope().launch {
            val result = if (approve) {
                repository.approveEvent(eventId, sessionManager.getUserId())
            } else {
                repository.rejectEvent(eventId, reason ?: "Rejected by admin.")
            }
            
            when (result) {
                is NetworkResult.Success -> {
                    Toast.makeText(this@AdminEventApprovalActivity, if (approve) "Event approved successfully" else "Event rejected", Toast.LENGTH_SHORT).show()
                    loadEvents()
                }
                is NetworkResult.Error -> {
                    Toast.makeText(this@AdminEventApprovalActivity, "Action failed: ${result.message}", Toast.LENGTH_LONG).show()
                    loadEvents()
                }
                else -> {}
            }
        }
    }

    // UI Helpers (re-implemented to be self-contained)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = BORDER): GradientDrawable = 
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
            strokeColor?.let { setStroke(dp(1), it) }
        }

    private fun text(value: String, size: Int = 14, bold: Boolean = false, color: Int = TEXT): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun card(padding: Int = 16): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
            background = rounded(CARD, 14, BORDER)
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(4), 0, dp(12))
            }
        }

    private fun row(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

    private fun section(title: String): TextView =
        text(title, 16, true).apply { setPadding(dp(2), dp(16), dp(2), dp(8)) }

    private fun spacer(height: Int): View =
        View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }

    private fun primaryButton(label: String, color: Int = PURPLE, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setAllCaps(false)
            setTextColor(Color.WHITE)
            background = rounded(color, 8, null)
            setOnClickListener { onClick() }
        }

    private fun badge(value: String): TextView {
        val color = when (value.lowercase()) {
            "approved", "active" -> SUCCESS
            "pending" -> WARNING
            "rejected" -> ERROR
            else -> PRIMARY
        }
        return text(value, 12, true, color).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = rounded(color and 0x11FFFFFF or 0x11000000, 18, null)
        }
    }

    private fun loadingState(message: String): LinearLayout =
        card(20).apply {
            gravity = Gravity.CENTER
            addView(text(message, 15, true, MUTED))
        }

    private fun emptyState(message: String, button: String? = null, action: (() -> Unit)? = null): LinearLayout =
        card(20).apply {
            gravity = Gravity.CENTER
            addView(text(message, 15, false, MUTED).apply { gravity = Gravity.CENTER })
            if (button != null && action != null) {
                addView(spacer(12))
                addView(primaryButton(button, PRIMARY, action))
            }
        }

    private fun dataSourceBanner(load: OrganizerMvpLoad<*>): LinearLayout? =
        if (load.source == OrganizerMvpDataSource.BACKEND) null else card(10).apply {
            elevation = 0f
            background = rounded(Color.parseColor("#FFF7ED"), 10, Color.parseColor("#FED7AA"))
            addView(text("Showing local demo data", 13, true, WARNING))
            addView(text(load.message ?: "Backend request failed. Showing an empty state.", 12, false, MUTED))
        }

    private fun createHeader(title: String, subtitle: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setBackgroundColor(Color.WHITE)
            val headerRow = row()
            headerRow.addView(text("<", 26, false, TEXT).apply {
                setPadding(0, 0, dp(16), 0)
                setOnClickListener { finish() }
            })
            val titleBox = LinearLayout(this@AdminEventApprovalActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            titleBox.addView(text(title, 21, true))
            titleBox.addView(text(subtitle, 13, false, MUTED))
            headerRow.addView(titleBox)
            addView(headerRow)
        }

    private fun EditText.afterTextChanged(onChanged: () -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onChanged()
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }
}
