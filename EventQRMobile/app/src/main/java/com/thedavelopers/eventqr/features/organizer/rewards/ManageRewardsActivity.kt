package com.thedavelopers.eventqr.features.organizer.rewards

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.organizer.*

open class ManageRewardsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intentEventId() == null) return showMissingEventScreen("Rewards")
        organizerShell("Reward Management", "Existing organizer reward page placeholder.", showBack = true)
            .addView(emptyState("Coming soon. Reward management is not configured for MVP yet."))
    }
}
