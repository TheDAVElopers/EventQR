package com.thedavelopers.eventqr.features.auth

import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.thedavelopers.eventqr.core.util.setInlineError

/**
 * Inline field errors for an auth form (§5.2, §6.8 row 6): each field owns a `micro` helper line in
 * `ui_error` under its box and takes the 2dp error stroke through `isActivated`. The platform
 * `EditText.error` balloon is never used.
 *
 * Fields are registered in visual top-to-bottom order, which is also the order the presenters
 * validate in. That order is the whole focus rule: [raise] moves focus to a field and announces its
 * message only when no earlier field is already in error, so a submit that fails on several fields
 * lands on the first one and says it once.
 *
 * A typed error goes away on the first edit of its field. [save] / [restore] keep the messages across
 * a configuration change; [restore] is silent (no focus move, no announcement) because the failure
 * was already announced when it happened. Call it after the view state is restored: putting the typed
 * text back runs the edit watchers that clear an error.
 */
internal class AuthFieldErrors {
    private class Field(val input: EditText, val helper: TextView)

    private val fields = LinkedHashMap<String, Field>()

    // Keys whose message is derived from the typed text on every keystroke (a password that is too
    // long). The screen recomputes it itself, so the edit watcher must not clear it.
    private val live = HashSet<String>()

    fun register(key: String, input: EditText, helper: TextView) {
        fields[key] = Field(input, helper)
        input.doAfterTextChanged { if (key !in live) set(key, null) }
        // The helper is a separate node and focus lands on the field, so the field reports itself
        // as invalid and carries the message: TalkBack reads them together.
        input.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                if (helper.visibility == View.VISIBLE) {
                    info.isContentInvalid = true
                    info.error = helper.text
                }
            }
        }
    }

    /** The message currently shown under [key], or null. */
    fun message(key: String): CharSequence? =
        fields.getValue(key).helper.takeIf { it.visibility == View.VISIBLE }?.text

    /** Shows or clears [key]'s error without moving focus or announcing. */
    fun set(key: String, message: CharSequence?) {
        live.remove(key)
        val field = fields.getValue(key)
        // Every keystroke lands here through the edit watcher; skip the work when nothing shows.
        if (message == null && field.helper.visibility != View.VISIBLE && !field.input.isActivated) return
        field.input.setInlineError(message, field.helper)
    }

    /**
     * A failed validation (or a server answer that names the field): shows [message] and, when no
     * earlier field is in error, focuses [key]'s field and announces it. [live] is for an error the
     * screen derives from the text while the user types (a password that is too long): it stays until
     * the screen clears it, the field already has focus, and the message is announced only when it
     * changed.
     */
    fun raise(key: String, message: CharSequence?, live: Boolean = false) {
        if (message == null) {
            set(key, null)
            return
        }
        val changed = this.message(key) != message
        set(key, message)
        val field = fields.getValue(key)
        if (live) {
            this.live.add(key)
            if (changed) field.input.announceForAccessibility(message)
            return
        }
        val first = fields.entries.firstOrNull { it.value.helper.visibility == View.VISIBLE }?.key == key
        if (!first) return
        if (!field.input.requestFocus()) field.input.requestFocusFromTouch()
        field.input.announceForAccessibility(message)
    }

    fun save(outState: Bundle) {
        val errors = Bundle()
        fields.forEach { (key, field) ->
            if (field.helper.visibility == View.VISIBLE) errors.putString(key, field.helper.text.toString())
        }
        outState.putBundle(STATE_KEY, errors)
    }

    fun restore(state: Bundle?) {
        val errors = state?.getBundle(STATE_KEY) ?: return
        fields.keys.forEach { key -> errors.getString(key)?.let { set(key, it) } }
    }

    private companion object {
        const val STATE_KEY = "auth_field_errors"
    }
}
