package com.thedavelopers.eventqr.features.organizer.idtemplate

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.organizer.*

open class IdTemplatePlaceholderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intentEventId() == null) return showMissingEventScreen("ID Template")
        organizerShell("ID Template", "Template preview is not configured for this event yet.", showBack = true)
            .addView(emptyState("Coming soon. ID template management is not configured for MVP yet."))
    }
}
