package com.thedavelopers.eventqr.features.staff

import android.widget.TextView
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.ui.components.RegistrationBadges

/**
 * Registration status -> the one badge component (§5.4), staff side.
 *
 * Kept as a thin alias so the staff screens keep calling `StaffBadges.bind(view, status)`. The
 * mapping itself lives in the shared [RegistrationBadges] object, which organizer screens use too,
 * so the two roles can never disagree about a tone or a label.
 */
object StaffBadges {

    fun bind(view: TextView, status: RegistrationStatus?, confirmed: Boolean = true) {
        RegistrationBadges.bind(view, status, confirmed)
    }
}
