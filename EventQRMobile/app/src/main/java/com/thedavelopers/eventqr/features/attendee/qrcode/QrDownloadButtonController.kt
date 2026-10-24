package com.thedavelopers.eventqr.features.attendee

import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.dimenFloat
import com.thedavelopers.eventqr.ui.theme.integer

/**
 * Drives the "Download QR" button through idle -> saving -> saved states.
 *
 * While saving, the label is
 * swapped for an inline spinner and the button keeps its exact size (no layout
 * jump). On success the spinner is replaced by a scale-pulse checkmark, the
 * caller is notified via [showSaved]'s callback once the pulse lands (so it can
 * show the confirmation toast), and the button settles back to idle shortly
 * after. On failure the caller calls [resetToIdle] to restore the button
 * immediately.
 */
class QrDownloadButtonController(
    private val button: Button,
    private val spinner: ProgressBar,
    private val checkmark: ImageView,
) {
    var isSaving: Boolean = false
        private set

    /** Enter the saving state: disabled button, inline spinner, dimmed label area. */
    fun showSaving() {
        isSaving = true
        button.isEnabled = false
        button.alpha = button.dimenFloat(R.dimen.alpha_saving)
        button.text = ""
        button.contentDescription = "Saving QR to gallery"
        checkmark.animate().cancel()
        checkmark.visibility = View.GONE
        spinner.visibility = View.VISIBLE
    }

    /**
     * Swap the spinner for a springy checkmark pulse, then invoke [onFinished]
     * once the pulse completes. The button is restored to idle a beat later so
     * the success state lingers while the confirmation toast is shown.
     */
    fun showSaved(onFinished: () -> Unit) {
        isSaving = false
        button.alpha = 1f
        button.contentDescription = BUTTON_LABEL
        spinner.visibility = View.GONE

        checkmark.visibility = View.VISIBLE
        val checkStartScale = checkmark.dimenFloat(R.dimen.scale_check_start)
        checkmark.scaleX = checkStartScale
        checkmark.scaleY = checkStartScale
        checkmark.alpha = 0f
        button.post {
            checkmark.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setInterpolator(OvershootInterpolator(OVERSHOOT_TENSION))
                .setDuration(button.integer(R.integer.motion_pulse_ms).toLong())
                .withEndAction {
                    onFinished()
                    button.postDelayed({ resetToIdle() }, button.integer(R.integer.motion_restore_ms).toLong())
                }
                .start()
        }
    }

    /** Immediately restore the idle state (used for the failure path). */
    fun resetToIdle() {
        isSaving = false
        button.isEnabled = true
        button.alpha = 1f
        button.text = BUTTON_LABEL
        button.contentDescription = BUTTON_LABEL
        checkmark.animate().cancel()
        checkmark.visibility = View.GONE
        checkmark.alpha = 1f
        checkmark.scaleX = 1f
        checkmark.scaleY = 1f
        spinner.visibility = View.GONE
    }

    private companion object {
        const val BUTTON_LABEL = "Download QR"
        const val OVERSHOOT_TENSION = 2.0f
    }
}