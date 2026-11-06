package com.thedavelopers.eventqr.features.organizer.events

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.thedavelopers.eventqr.R

/**
 * The dirty-back confirm (§6.4, §5.12): `dialog_base`, title / body, `Keep editing` as the ghost
 * negative and `Discard` as the filled red commit - the only place a filled destructive button
 * exists.
 *
 * Kept out of the activity so the wiring (labels, the confirm callback) is testable without a
 * window. [onDiscard] runs after the dialog dismisses.
 */
internal object DiscardChangesDialog {

    fun create(context: Context, onDiscard: () -> Unit): AlertDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_base, null, false)
        val dialog = AlertDialog.Builder(context).setView(view).create()

        view.findViewById<TextView>(R.id.dialogTitle).setText(R.string.ui_organizer_edit_discard_title)
        view.findViewById<TextView>(R.id.dialogBody).setText(R.string.ui_organizer_edit_discard_body)

        // The trigger is outlined; the commit is filled. `Keep editing` is the safe default.
        view.findViewById<Button>(R.id.dialogNegative).apply {
            setText(R.string.ui_organizer_edit_keep_editing)
            setOnClickListener { dialog.dismiss() }
        }
        view.findViewById<Button>(R.id.dialogPositive).apply {
            setText(R.string.ui_organizer_edit_discard)
            setBackgroundResource(R.drawable.bg_ui_button_destructive_commit)
            setTextColor(ContextCompat.getColor(context, R.color.ui_on_primary))
            setOnClickListener {
                dialog.dismiss()
                onDiscard()
            }
        }

        return dialog
    }
}
