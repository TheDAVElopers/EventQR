package com.thedavelopers.eventqr.features.organizer.rewards

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.organizer.*

open class PointRulesPlaceholderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intentEventId() == null) return showMissingEventScreen("Point Rules")
        organizerShell("Point Rules", "Point rule editing is not enabled for this event yet.", showBack = true)
            .addView(emptyState("Coming soon. Point rule editing is not configured for MVP yet."))
    }
}
