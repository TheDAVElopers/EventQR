package com.thedavelopers.eventqr.ui.icons

import android.graphics.drawable.ColorDrawable
import android.widget.TextView
import androidx.annotation.DimenRes
import androidx.core.content.ContextCompat
import com.thedavelopers.eventqr.R

/**
 * One password-rule row (`EventQR-Redesign-Plan.md` §6.1): a fixed 16dp leading slot that holds
 * `ic_ui_check` in `accent_deep` when the rule is met and nothing at rest. The empty slot keeps
 * every row's label on one left edge, and the presence of the tick (plus the row's description
 * `{rule}, met` / `{rule}, not met`) carries the state, never colour alone.
 */
object RuleMarks {

    fun apply(view: TextView, label: String, isMet: Boolean, @DimenRes size: Int = R.dimen.icon_sm) {
        val px = view.resources.getDimensionPixelSize(size)
        val mark = if (isMet) {
            ContextCompat.getDrawable(view.context, AppIcons.Check)
                ?.mutate()
                ?.apply { setTint(view.context.getColor(R.color.ui_accent_deep)) }
        } else {
            ColorDrawable(ContextCompat.getColor(view.context, R.color.transparent))
        }
        mark?.setBounds(0, 0, px, px)
        view.text = label
        view.setCompoundDrawablesRelative(mark, null, null, null)
        view.contentDescription = view.context.getString(
            if (isMet) R.string.ui_password_rule_met else R.string.ui_password_rule_not_met,
            label,
        )
    }
}
