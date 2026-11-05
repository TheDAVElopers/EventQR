package com.thedavelopers.eventqr.features.registrations

import android.widget.TextView
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.ui.components.RegistrationBadges

/**
 * Kept as the entry point the registration adapters already call, so no call site changes.
 *
 * The body no longer owns a mapping: it delegates to the shared [RegistrationBadges] table, which
 * binds `Widget.EventQR.Ui.StatusBadge.*` tones and the sentence-case labels (§5.4). The legacy
 * `bg_badge_*` drawables and `badge_*_text` colours are no longer referenced anywhere.
 *
 * For a label without a view, use `RegistrationBadges.labelRes(status)` - do not add a second
 * label table here.
 */
object RegistrationStatusBadgeStyler {

    fun bind(view: TextView, status: RegistrationStatus) {
        RegistrationBadges.bind(view, status)
    }
}
