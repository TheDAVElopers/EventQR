package com.thedavelopers.eventqr.features.attendee

import android.content.Context
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.api.dto.RedemptionStatus
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.api.dto.RewardStatus

/**
 * The §5.4 badge mapping, applied to an XML `TextView` that already carries one of the
 * `Widget.EventQR.Ui.StatusBadge` shapes.
 *
 * `EventStatusBadgeStyler` / `RegistrationStatusBadgeStyler` still express status with the forty
 * legacy `bg_*_badge_*` drawables; the attendee screens must not, so they set the tone from the
 * semantic roles instead. One place, three verbs, no per-screen colour table.
 *
 * The badge always reads as words, never as a tone (§9.5): the label is the only content.
 */
object AttendeeBadges {

    /** container fill + content colour, both semantic roles. */
    data class Tone(@ColorRes val container: Int, @ColorRes val content: Int)

    private val neutral = Tone(R.color.ui_surface_sunken, R.color.ui_text_secondary)
    private val info = Tone(R.color.ui_info_container, R.color.ui_on_info_container)
    private val warning = Tone(R.color.ui_warning_container, R.color.ui_on_warning_container)
    private val error = Tone(R.color.ui_error_container, R.color.ui_on_error_container)
    private val success = Tone(R.color.ui_success_container, R.color.ui_on_success_container)
    private val valid = Tone(R.color.ui_accent, R.color.ui_on_accent)

    fun tone(status: EventStatus): Tone = when (status) {
        EventStatus.ACTIVE -> valid
        EventStatus.APPROVED -> info
        EventStatus.PENDING_REVIEW -> warning
        EventStatus.REJECTED -> error
        EventStatus.DRAFT, EventStatus.ENDED, EventStatus.CANCELLED, EventStatus.UNKNOWN -> neutral
    }

    /**
     * The dot fill for the hub's quiet status line, which sits on the dark `ui_primary` band.
     * The [Tone.container] roles are light-surface fills and disappear there, so this is the one
     * on-dark mapping: tone -> token. It is a fill only - the word beside the dot always stays
     * `ui_text_on_dark_muted`, never lime text.
     */
    @ColorRes
    fun onDarkDotColorRes(status: EventStatus): Int = when (status) {
        EventStatus.ACTIVE -> R.color.ui_accent
        EventStatus.APPROVED -> R.color.ui_status_dot_on_dark_info
        EventStatus.PENDING_REVIEW -> R.color.ui_status_dot_on_dark_warning
        EventStatus.REJECTED -> R.color.ui_status_dot_on_dark_error
        EventStatus.DRAFT, EventStatus.ENDED, EventStatus.CANCELLED, EventStatus.UNKNOWN ->
            R.color.ui_text_on_dark_muted
    }

    fun labelRes(status: EventStatus): Int = when (status) {
        EventStatus.DRAFT -> R.string.ui_attendee_draft
        EventStatus.PENDING_REVIEW -> R.string.ui_attendee_pending_review
        EventStatus.APPROVED -> R.string.ui_attendee_upcoming
        EventStatus.REJECTED -> R.string.ui_attendee_rejected
        EventStatus.ACTIVE -> R.string.ui_attendee_active
        EventStatus.ENDED -> R.string.ui_attendee_completed
        EventStatus.CANCELLED -> R.string.ui_attendee_cancelled
        EventStatus.UNKNOWN -> R.string.ui_attendee_unknown
    }

    fun label(context: Context, status: EventStatus): String = context.getString(labelRes(status))

    fun bind(view: TextView, status: EventStatus, label: String? = null) {
        view.text = label ?: view.context.getString(labelRes(status))
        tint(view, tone(status))
    }

    // ── Redemptions (claimed rewards) ────────────────────────────────────────
    fun tone(status: RedemptionStatus): Tone = when (status) {
        RedemptionStatus.REDEEMED -> success
        RedemptionStatus.PENDING -> warning
        RedemptionStatus.REJECTED -> error
    }

    fun labelRes(status: RedemptionStatus): Int = when (status) {
        RedemptionStatus.REDEEMED -> R.string.ui_attendee_redeemed
        RedemptionStatus.PENDING -> R.string.ui_attendee_pending
        RedemptionStatus.REJECTED -> R.string.ui_attendee_rejected
    }

    fun bind(view: TextView, status: RedemptionStatus) {
        view.text = view.context.getString(labelRes(status))
        tint(view, tone(status))
    }

    // ── Rewards (Available / Unavailable) ────────────────────────────────────

    fun tone(status: RewardStatus): Tone = when (status) {
        // Available is inventory, not a confirmation: lime is credential-only (§5.4 line 597).
        RewardStatus.ACTIVE -> info
        RewardStatus.INACTIVE -> neutral
    }

    fun bind(view: TextView, status: RewardStatus) {
        view.text = view.context.getString(
            if (status == RewardStatus.ACTIVE) {
                R.string.ui_attendee_available
            } else {
                R.string.ui_attendee_unavailable
            },
        )
        tint(view, tone(status))
    }

    // ── Registrations (staff attendee rows) ──────────────────────────────────

    fun tone(status: RegistrationStatus): Tone = when (status) {
        RegistrationStatus.ENTERED -> valid
        RegistrationStatus.REGISTERED, RegistrationStatus.EXITED, RegistrationStatus.CANCELLED,
        RegistrationStatus.NO_SHOW,
        -> neutral
    }

    fun labelRes(status: RegistrationStatus): Int = when (status) {
        RegistrationStatus.REGISTERED -> R.string.ui_attendee_registered
        RegistrationStatus.ENTERED -> R.string.ui_attendee_entered
        RegistrationStatus.EXITED -> R.string.ui_attendee_exited
        RegistrationStatus.CANCELLED -> R.string.ui_attendee_cancelled
        RegistrationStatus.NO_SHOW -> R.string.ui_attendee_no_show
    }

    fun bind(view: TextView, status: RegistrationStatus) {
        view.text = view.context.getString(labelRes(status))
        tint(view, tone(status))
    }

    private fun tint(view: TextView, tone: Tone) {
        ViewCompat.setBackgroundTintList(
            view,
            ContextCompat.getColorStateList(view.context, tone.container),
        )
        view.setTextColor(ContextCompat.getColor(view.context, tone.content))
    }
}
