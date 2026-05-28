package com.thedavelopers.eventqr.features.organizer.notifications

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.organizer.*

open class NotificationManagementActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        organizerShell("Notifications", "Organizer notification placeholder.", showBack = true)
            .addView(emptyState("Notifications are not configured for this event yet."))
    }
}
