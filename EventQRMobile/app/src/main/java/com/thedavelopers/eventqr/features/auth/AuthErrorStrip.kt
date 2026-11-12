package com.thedavelopers.eventqr.features.auth

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.components.showInlineError

/**
 * Host wiring for the shared §5.16 `include_error_strip` on a form screen (not a second component).
 *
 * One strip per screen, directly above the submit area. [show] announces on every call, so a repeat
 * of the same failure is spoken again (the strip has no live region); [clear] hides it and the host
 * calls it at the top of every submit, so a stale sentence never survives into the next attempt.
 *
 * `Retry` runs one of the host's named [actions]. A screen whose submit is not safe to repeat (a
 * consumed one-time code, account creation, a password change) passes `null`, which hides the
 * control. The strip remembers the action by name, not by lambda, so [save] / [restore] can put the
 * strip back after a configuration change with `Retry` still wired to the right action: the reset
 * code screen's `Retry` means Verify after a verify failure and Resend after a resend failure.
 *
 * [restore] never announces: the failure was announced when it happened, a rotation is not a new one.
 */
internal class AuthErrorStrip(
    private val strip: View,
    private val actions: Map<String, () -> Unit> = emptyMap(),
) {
    private val retry: View = strip.findViewById(R.id.btnErrorStripRetry)
    private var action: String? = null

    init {
        retry.setOnClickListener { action?.let { actions[it] }?.invoke() }
    }

    val isVisible: Boolean get() = strip.visibility == View.VISIBLE

    /** [retryAction] is a key of the host's `actions`, or `null` when repeating is not safe. */
    fun show(message: CharSequence, retryAction: String?) = render(message, retryAction, announce = true)

    fun clear() {
        strip.visibility = View.GONE
        action = null
    }

    fun save(outState: Bundle) {
        if (!isVisible) return
        outState.putBundle(
            STATE_KEY,
            Bundle().apply {
                putString(MESSAGE, strip.findViewById<TextView>(R.id.txtErrorStrip).text.toString())
                putString(ACTION, action)
            },
        )
    }

    fun restore(state: Bundle?) {
        val saved = state?.getBundle(STATE_KEY) ?: return
        val message = saved.getString(MESSAGE) ?: return
        render(message, saved.getString(ACTION), announce = false)
    }

    private fun render(message: CharSequence, retryAction: String?, announce: Boolean) {
        // An action the host no longer knows (an old saved state) hides Retry rather than doing nothing.
        action = retryAction?.takeIf { it in actions }
        retry.visibility = if (action != null) View.VISIBLE else View.GONE
        strip.showInlineError(message, announce)
    }

    companion object {
        /** The action key for a screen with a single submit. */
        const val SUBMIT = "submit"

        private const val STATE_KEY = "auth_error_strip"
        private const val MESSAGE = "message"
        private const val ACTION = "action"
    }
}
