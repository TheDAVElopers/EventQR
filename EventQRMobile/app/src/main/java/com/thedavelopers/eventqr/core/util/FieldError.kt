package com.thedavelopers.eventqr.core.util

import android.view.View
import android.widget.EditText
import android.widget.TextView

/**
 * Drives a field's inline error line (§5.2: `micro` 11sp under the box, 4dp gap, `ui_error`).
 *
 * The platform `.error` tooltip is never shown: it floats over the primary button on the auth
 * screens. `bg_ui_field` draws its 2dp error stroke from `state_activated`, so the stroke and the
 * line are set by the same call and can never disagree.
 */
fun EditText.setInlineError(message: CharSequence?, errorView: TextView) {
    error = null
    isActivated = message != null
    errorView.text = message
    errorView.visibility = if (message == null) View.GONE else View.VISIBLE
}
