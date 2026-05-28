package com.thedavelopers.eventqr.features.organizer.events

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.organizer.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

open class EventManagementHubActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        val eventId = intentEventId() ?: return showMissingEventScreen("Event Management")
        val content = organizerShell("Event Management", intentEventTitle(), showBack = true)
        content.addView(loadingState("Loading event details..."))

        MainScope().launch {
            val load = repository.loadEventForMvp(eventId)
            val event = load.data
            content.removeAllViews()
            dataSourceBanner(load)?.let { content.addView(it) }
            if (event == null) {
                content.addView(
                    if (load.source == OrganizerMvpDataSource.ERROR) {
                        errorState(load.message ?: "Event details could not be loaded.") { recreate() }
                    } else {
                        emptyState("Event not found or not available for organizer management.", "Open My Events") {
                            openOrganizerPage(ManageEventsActivity::class.java)
                        }
                    },
                )
                return@launch
            }

            content.addView(card().apply {
                addView(text(event.title, 18, true))
                addView(text("${event.dateTime}\n${event.venue}", 13, false, MUTED))
                addView(text("Status: ${event.status}", 13, true, PRIMARY))
                addView(text("Registered: ${event.registeredCount} / Capacity: ${event.capacity}", 13, false, MUTED))
                addView(text("Available slots: ${event.availableSlots}", 13, false, MUTED))
                addView(text(event.description.ifBlank { "No description available." }, 13, false, MUTED))
            })

            content.addView(section("Management Options"))
            listOf(
                "Attendees" to AttendeeManagementActivity::class.java,
                "Staff Access" to ManageUsersActivity::class.java,
                "Scan Purposes" to ManageScanPurposesActivity::class.java,
                "Transaction Rules" to TransactionRulesActivity::class.java,
                "Transaction Logs" to TransactionLogsActivity::class.java,
                "Reports" to EventReportsActivity::class.java,
                "ID Template" to IdTemplatePlaceholderActivity::class.java,
                "Rewards" to ManageRewardsActivity::class.java,
                "Point Rules" to PointRulesPlaceholderActivity::class.java,
            ).forEach { (label, target) ->
                content.addView(ghostButton(label) { openOrganizerPage(target, event.id, event.title) })
            }
        }
    }
}
