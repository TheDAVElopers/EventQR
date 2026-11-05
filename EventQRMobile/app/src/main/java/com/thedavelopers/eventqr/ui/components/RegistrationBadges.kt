package com.thedavelopers.eventqr.ui.components

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus

/**
 * The ONE registration-status -> badge mapping (§5.4).
 *
 * Owned by the shared layer so staff and organizer screens cannot drift: prior to this object the
 * staff batch carried `StaffBadges` and the organizer screens still rendered the pre-redesign pill
 * (`RegistrationStatusBadgeStyler`). Both now delegate here.
 *
 * Tones follow §5.4: `Registered` / `Exited` / `Cancelled` / `No show` neutral, `Entered` valid
 * (lime - the one confirmation colour). Labels are sentence case and live in
 * `values/strings_ui_attendee.xml`, never Title Cased in code.
 */
object RegistrationBadges {

    @StringRes
    fun labelRes(status: RegistrationStatus?): Int = when (status) {
        RegistrationStatus.REGISTERED -> R.string.ui_attendee_registered
        RegistrationStatus.ENTERED -> R.string.ui_attendee_entered
        RegistrationStatus.EXITED -> R.string.ui_attendee_exited
        RegistrationStatus.CANCELLED -> R.string.ui_attendee_cancelled
        RegistrationStatus.NO_SHOW -> R.string.ui_attendee_no_show
        null -> R.string.ui_attendee_registered
    }

    /** The `Widget.EventQR.Ui.StatusBadge.*` style for the status, for XML-inflated badges. */
    @StyleRes
    fun badgeStyleFor(status: RegistrationStatus?): Int = when (status) {
        RegistrationStatus.ENTERED -> R.style.Widget_EventQR_Ui_StatusBadge_Valid
        else -> R.style.Widget_EventQR_Ui_StatusBadge_Neutral
    }

    @ColorRes
    fun backgroundResFor(status: RegistrationStatus?, confirmed: Boolean): Int =
        if (confirmed && status == RegistrationStatus.ENTERED) {
            R.color.ui_accent
        } else {
            R.color.ui_surface_sunken
        }

    @ColorRes
    fun foregroundResFor(status: RegistrationStatus?, confirmed: Boolean): Int =
        if (confirmed && status == RegistrationStatus.ENTERED) {
            R.color.ui_on_accent
        } else {
            R.color.ui_text_secondary
        }

    /**
     * Binds the label and the tone onto an already-inflated `Widget.EventQR.Ui.StatusBadge` view.
     *
     * @param confirmed false where the surrounding screen is reporting a failure; lime is a
     *   confirmation only, so the badge demotes to neutral rather than contradicting the screen.
     */
    fun bind(view: TextView, status: RegistrationStatus?, confirmed: Boolean = true) {
        view.setText(labelRes(status))
        view.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(view.context, backgroundResFor(status, confirmed)),
        )
        view.setTextColor(
            ContextCompat.getColor(view.context, foregroundResFor(status, confirmed)),
        )
    }
}
