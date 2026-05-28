package com.thedavelopers.eventqr.features.organizer.attendees

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.graphics.Color
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.organizer.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

open class AttendeeManagementActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var summary: LinearLayout
    private lateinit var search: EditText
    private lateinit var list: LinearLayout
    private lateinit var detail: LinearLayout
    private var filter = "All"
    private var attendeesSource: OrganizerMvpLoad<List<OrganizerMvpAttendee>> =
        OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        val eventId = intentEventId() ?: return showMissingEventScreen("Attendee Management")
        selectedEvent = resolveSelectedEvent(repository.getApprovedOrganizerEvents(), eventId) ?: return showMissingEventScreen("Attendee Management")
        val content = organizerShell("Attendee Management", selectedEvent.title, NAV_ATTENDEES)
        val approved = repository.getApprovedOrganizerEvents().approvedOnly()
        if (approved.size > 1) content.addView(eventSelector(repository.getApprovedOrganizerEvents(), selectedEvent.id) {
            selectedEvent = it
            repository.saveSelectedEventId(it.id)
            saveSelectedEventId(it.id)
            loadAttendees()
        })
        summary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(summary)
        search = EditText(this).apply {
            hint = "Search attendee"
            background = rounded(Color.WHITE, 10, BORDER, density = resources.displayMetrics.density)
            setPadding(dp(12), 0, dp(12), 0)
        }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        detail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(search)
        content.addView(filterChips(listOf("All", "Registered", "Entered", "Exited")) {
            filter = it
            render()
        })
        content.addView(list)
        content.addView(section("Attendee Detail"))
        content.addView(detail)
        search.afterTextChanged { render() }
        list.addView(loadingState("Loading attendees..."))
        loadAttendees()
    }

    private fun loadAttendees() {
        MainScope().launch {
            attendeesSource = repository.loadAttendeesForMvp(selectedEvent.id)
            render()
        }
    }

    private fun renderSummary(attendees: List<OrganizerMvpAttendee>) {
        summary.removeAllViews()
        summary.addView(row().apply {
            addView(summaryCard("Total Registered", attendees.size.toString()))
            addView(summaryCard("Checked In", attendees.count { it.currentEventStatus == "Checked In / Entered" }.toString(), SUCCESS))
        })
        summary.addView(row().apply {
            addView(summaryCard("Attended", attendees.count { it.currentEventStatus == "Attended" }.toString(), SUCCESS))
            addView(summaryCard("No-shows", attendees.count { it.currentEventStatus == "No-show" }.toString(), ERROR))
        })
        dataSourceBanner(attendeesSource)?.let { summary.addView(it) }
    }

    private fun filterChips(labels: List<String>, onClick: (String) -> Unit): LinearLayout =
        row().apply {
            gravity = Gravity.START
            labels.forEach { label ->
                addView(chip(label, label == filter).apply {
                    setOnClickListener { onClick(label) }
                })
            }
        }

    private fun render() {
        val q = search.text.toString()
        val allAttendees = attendeesSource.data
        renderSummary(allAttendees)
        val attendees = allAttendees.filter {
            val matchesStatus = when (filter) {
                "All" -> true
                "Registered" -> it.registrationStatus.equals("Registered", true)
                "Entered" -> it.currentEventStatus == "Checked In / Entered" || it.currentEventStatus == "Attended"
                "Exited" -> it.currentEventStatus == "Exited"
                else -> it.currentEventStatus.equals(filter, true) || it.registrationStatus.equals(filter, true)
            }
            matchesStatus && (
                it.name.contains(q, true) ||
                    it.email.contains(q, true) ||
                    it.id.contains(q, true) ||
                    it.qrCredentialStatus.contains(q, true)
                )
        }
        list.removeAllViews()
        if (attendees.isEmpty()) {
            list.addView(if (attendeesSource.source == OrganizerMvpDataSource.ERROR) {
                errorState(attendeesSource.message ?: "Attendees could not be loaded.") { loadAttendees() }
            } else {
                emptyState("No attendees registered yet.")
            })
            detail.removeAllViews()
            return
        }
        attendees.forEach { list.addView(attendeeCard(it)) }
        renderDetail(attendees.first())
    }

    private fun attendeeCard(attendee: OrganizerMvpAttendee): LinearLayout =
        card().apply {
            val top = row()
            top.addView(text(attendee.name, 17, true).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            top.addView(badge(if (attendee.currentEventStatus == "No-show") "Rejected" else "Accepted"))
            addView(top)
            addView(text(attendee.email, 13, false, MUTED))
            addView(row().apply {
                addView(chip("${attendee.points} pts"))
                addView(chip(attendee.lastTransactionTime, false, SUCCESS))
            })
            addView(text("Registered: ${attendee.registeredDate}", 12, false, MUTED))
            addView(text("Registration: ${attendee.registrationStatus} | Current: ${attendee.currentEventStatus}", 12, false, MUTED))
            setOnClickListener { renderDetail(attendee) }
        }

    private fun renderDetail(attendee: OrganizerMvpAttendee) {
        detail.removeAllViews()
        detail.addView(card().apply {
            addView(text(attendee.name, 18, true))
            addView(text("${attendee.email}\n${attendee.phone}", 13, false, MUTED))
            addView(text("Registration status: ${attendee.registrationStatus}"))
            addView(text("QR credential status: ${attendee.qrCredentialStatus}"))
            addView(text("Current event status: ${attendee.currentEventStatus}"))
            addView(text("Event-specific points: ${attendee.points}"))
            addView(section("Recent Transactions"))
            addView(text(attendee.recentTransactions.ifEmpty { listOf("No recent transactions.") }.joinToString("\n"), 13, false, MUTED))
            addView(section("Recent Rejected Scans"))
            addView(text(attendee.recentRejectedScans.ifEmpty { listOf("No recent rejected scans.") }.joinToString("\n"), 13, false, MUTED))
            addView(ghostButton("View transactions") { openOrganizerPage(TransactionLogsActivity::class.java, selectedEvent.id, selectedEvent.title) })
            addView(ghostButton("Reprint ID") { Toast.makeText(this@AttendeeManagementActivity, "ID reprint flow is not wired on this screen.", Toast.LENGTH_SHORT).show() })
            addView(ghostButton("Manual support note") { Toast.makeText(this@AttendeeManagementActivity, "Support notes are not wired on this screen.", Toast.LENGTH_SHORT).show() })
        })
        detail.addView(stateCard())
    }
}
