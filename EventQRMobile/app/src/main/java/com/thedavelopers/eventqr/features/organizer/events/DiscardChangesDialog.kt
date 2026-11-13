package com.thedavelopers.eventqr.features.organizer.events

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.thedavelopers.eventqr.R

/**
 * The dirty-back confirm (§6.4, §5.12): `dialog_base`, title / body, `Keep editing` as the ghost
 * negative and `Discard` as the filled red commit - the only place a filled destructive button
 * exists.
 *
 * One dialog for every surface holding uncommitted work (Edit event, Transaction rules): the
 * caller passes its own body copy, and the labels stay shared. Kept out of the activities so the
 * wiring (labels, the confirm callback) is testable without a window. [onDiscard] runs after the
 * dialog dismisses.
 */
internal object DiscardChangesDialog {

    /**
     * Above this scale the two actions stack (§5.12): `Keep editing` and `Discard` cannot share one
     * 272dp line at 2.0. The threshold is the app's single reflow rule (§6.4/§9.3).
     */
    private const val STACK_FONT_SCALE = 1.3f

    fun create(
        context: Context,
        onDiscard: () -> Unit,
        @StringRes title: Int = R.string.ui_organizer_edit_discard_title,
        body: CharSequence = context.getString(R.string.ui_organizer_edit_discard_body),
    ): AlertDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_base, null, false)
        val dialog = AlertDialog.Builder(context).setView(view).create()

        view.findViewById<TextView>(R.id.dialogTitle).setText(title)
        view.findViewById<TextView>(R.id.dialogBody).text = body

        // The trigger is outlined; the commit is filled. `Keep editing` is the safe default.
        view.findViewById<Button>(R.id.dialogNegative).apply {
            setText(R.string.ui_organizer_edit_keep_editing)
            setOnClickListener { dialog.dismiss() }
        }
        val commit = view.findViewById<Button>(R.id.dialogPositive).apply {
            setText(R.string.ui_organizer_edit_discard)
            setBackgroundResource(R.drawable.bg_ui_button_destructive_commit)
            setTextColor(ContextCompat.getColor(context, R.color.ui_on_primary))
            setOnClickListener {
                dialog.dismiss()
                onDiscard()
            }
        }

        stackActionsAbove(context, view.findViewById(R.id.dialogActions), commit)
        // The window title is what TalkBack reads as the dialog opens; the body follows the title
        // in the reading order. Focus lands on the safe action, so a stray Enter never discards.
        // `Dialog.setTitle`, not `window.setTitle`: it also publishes the title as the window's
        // manager attribute, which is the value the accessibility layer and a test can read back.
        dialog.setTitle(context.getString(title))
        dialog.setOnShowListener { view.findViewById<Button>(R.id.dialogNegative).requestFocus() }

        return dialog
    }

    /** At large type the actions become a column, still end-aligned, `Discard` last (bottom). */
    private fun stackActionsAbove(context: Context, actions: LinearLayout, commit: Button) {
        if (context.resources.configuration.fontScale <= STACK_FONT_SCALE) return
        actions.orientation = LinearLayout.VERTICAL
        (commit.layoutParams as LinearLayout.LayoutParams).apply {
            marginStart = 0
            topMargin = context.resources.getDimensionPixelSize(R.dimen.space_8)
        }
    }
}
