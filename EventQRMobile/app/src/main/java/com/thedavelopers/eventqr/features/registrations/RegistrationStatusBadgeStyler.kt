package com.thedavelopers.eventqr.features.registrations

import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import java.util.Locale

object RegistrationStatusBadgeStyler {

    fun bind(view: TextView, status: RegistrationStatus) {
        val style = when (status) {
            RegistrationStatus.REGISTERED -> BadgeStyle(
                backgroundResId = R.drawable.bg_badge_registered,
                textColorResId = R.color.badge_registered_text,
            )
            RegistrationStatus.ENTERED -> BadgeStyle(
                backgroundResId = R.drawable.bg_badge_entered,
                textColorResId = R.color.badge_entered_text,
            )
            RegistrationStatus.EXITED -> BadgeStyle(
                backgroundResId = R.drawable.bg_badge_exited,
                textColorResId = R.color.badge_exited_text,
            )
            RegistrationStatus.CANCELLED -> BadgeStyle(
                backgroundResId = R.drawable.bg_badge_cancelled,
                textColorResId = R.color.badge_cancelled_text,
            )
            RegistrationStatus.NO_SHOW -> BadgeStyle(
                backgroundResId = R.drawable.bg_badge_cancelled,
                textColorResId = R.color.badge_cancelled_text,
            )
        }

        view.text = displayLabel(status)
        view.setBackgroundResource(style.backgroundResId)
        view.setTextColor(ContextCompat.getColor(view.context, style.textColorResId))
    }

    fun displayLabel(status: RegistrationStatus): String = when (status) {
        RegistrationStatus.REGISTERED -> "Registered"
        RegistrationStatus.ENTERED -> "Checked In"
        RegistrationStatus.EXITED -> "Exited"
        RegistrationStatus.CANCELLED -> "Cancelled"
        RegistrationStatus.NO_SHOW -> "No Show"
    }

    private data class BadgeStyle(
        @DrawableRes val backgroundResId: Int,
        @ColorRes val textColorResId: Int,
    )
}