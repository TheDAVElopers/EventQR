package com.thedavelopers.eventqr.features.organizer.transactions

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.organizer.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

open class TransactionLogsActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var search: EditText
    private lateinit var summary: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var detail: LinearLayout
    private var filter = "All"
    private var logsSource: OrganizerMvpLoad<List<OrganizerMvpTransaction>> =
        OrganizerMvpLoad(emptyList(), OrganizerMvpDataSource.ERROR, null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        val eventId = intentEventId() ?: return showMissingEventScreen("Transaction Logs")
        selectedEvent = resolveSelectedEvent(repository.getApprovedOrganizerEvents(), eventId) ?: return showMissingEventScreen("Transaction Logs")
        val content = organizerShell("Transaction Logs", selectedEvent.title, NAV_LOGS, showBack = true)
        if (repository.getApprovedOrganizerEvents().approvedOnly().size > 1) {
            content.addView(eventSelector(repository.getApprovedOrganizerEvents(), selectedEvent.id) {
                selectedEvent = it
                repository.saveSelectedEventId(it.id)
                saveSelectedEventId(it.id)
                loadLogs()
            })
        }
        search = EditText(this).apply {
            hint = "Search attendee, QR ID, transaction ID, staff, or event"
            background = rounded(Color.WHITE, 10, BORDER, density = resources.displayMetrics.density)
            setPadding(dp(12), 0, dp(12), 0)
        }
        summary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        detail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(search)
        content.addView(filterChips(listOf("All", "Approved", "Rejected")) {
            filter = it
            render()
        })
        content.addView(summary)
        content.addView(list)
        content.addView(section("Transaction Details"))
        content.addView(detail)
        search.afterTextChanged { render() }
        list.addView(loadingState("Loading transaction logs..."))
        loadLogs()
    }

    private fun loadLogs() {
        MainScope().launch {
            logsSource = repository.loadTransactionsForMvp(selectedEvent.id, selectedEvent.title)
            render()
        }
    }

    private fun filterChips(labels: List<String>, onClick: (String) -> Unit): LinearLayout =
        row().apply {
            gravity = Gravity.START
            labels.forEach { label ->
                addView(chip(label, label == filter).apply { setOnClickListener { onClick(label) } })
            }
        }

    private fun renderSummary(logs: List<OrganizerMvpTransaction>) {
        summary.removeAllViews()
        summary.addView(row().apply {
            addView(summaryCard("Total Scans", logs.size.toString()))
            addView(summaryCard("Approved", logs.count { it.status == "Approved" }.toString(), SUCCESS))
            addView(summaryCard("Rejected", logs.count { it.status == "Rejected" }.toString(), ERROR))
        })
        dataSourceBanner(logsSource)?.let { summary.addView(it) }
    }

    private fun render() {
        val q = search.text.toString()
        val allLogs = logsSource.data
        renderSummary(allLogs)
        val logs = allLogs.filter {
            val matchesFilter = filter == "All" ||
                it.status.equals(filter, true)
            val matchesQuery = listOf(it.attendeeName, it.qrId, it.id, it.staffName, it.eventTitle).any { value ->
                value.contains(q, true)
            }
            matchesFilter && matchesQuery
        }
        list.removeAllViews()
        if (logs.isEmpty()) {
            list.addView(emptyState("No transaction logs match this view."))
            detail.removeAllViews()
            return
        }
        logs.forEach { list.addView(logCard(it)) }
        renderDetail(logs.first())
    }

    private fun logCard(log: OrganizerMvpTransaction): LinearLayout =
        card().apply {
            val top = row()
            top.addView(text(log.attendeeName, 16, true).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            top.addView(badge(log.status))
            addView(top)
            addView(text("${log.type} • ${log.status}", 12, true, if (log.status == "Rejected") ERROR else SUCCESS))
            addView(text(log.eventTitle, 12, false, MUTED))
            addView(text("Staff: ${log.staffName}", 12, false, MUTED))
            addView(text(log.timestamp, 12, false, MUTED))
            if (log.status == "Rejected") addView(text("Reason: ${log.reason}", 12, true, ERROR))
            setOnClickListener { renderDetail(log) }
        }

    private fun renderDetail(log: OrganizerMvpTransaction) {
        detail.removeAllViews()
        detail.addView(card().apply {
            addView(text("Transaction ID: ${log.id}", 15, true))
            addView(text("Event: ${log.eventTitle} / ${log.eventId}"))
            addView(text("Attendee: ${log.attendeeName} / ${log.attendeeId}"))
            addView(text("Staff: ${log.staffName} / ${log.staffId}"))
            addView(text("QR ID: ${log.qrId}"))
            addView(text("Scan purpose: ${log.scanPurpose}"))
            addView(text("Result status: ${log.status}"))
            addView(text("Reason/message: ${log.reason}"))
            addView(text("Created timestamp: ${log.timestamp}"))
            addView(text("Device/source: ${log.deviceSource}"))
            addView(text("Points awarded/deducted: ${log.pointsDelta}"))
            addView(text("Related reward/benefit/session: ${log.relatedItem}"))
        })
        detail.addView(stateCard())
    }
}
