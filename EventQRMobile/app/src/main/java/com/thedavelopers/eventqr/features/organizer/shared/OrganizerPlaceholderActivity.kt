package com.thedavelopers.eventqr.features.organizer.shared

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.organizer.*

open class OrganizerPlaceholderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_PLACEHOLDER_TITLE).orEmpty().ifBlank { "Coming Soon" }
        val message = intent.getStringExtra(EXTRA_PLACEHOLDER_MESSAGE)
            .orEmpty()
            .ifBlank { "This feature is being prepared and will be connected in a follow-up release." }
        val selectedNav = intent.getStringExtra(EXTRA_PLACEHOLDER_NAV)
        organizerShell(title, selectedNav = selectedNav).addView(
            emptyState(message, "Open My Events") {
                openOrganizerPage(ManageEventsActivity::class.java)
            },
        )
    }
}
