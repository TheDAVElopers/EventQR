package com.thedavelopers.eventqr.features.attendee

import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar

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
        button.alpha = SAVING_ALPHA
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
        checkmark.scaleX = CHECK_START_SCALE
        checkmark.scaleY = CHECK_START_SCALE
        checkmark.alpha = 0f
        button.post {
            checkmark.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setInterpolator(OvershootInterpolator(OVERSHOOT_TENSION))
                .setDuration(PULSE_DURATION_MS)
                .withEndAction {
                    onFinished()
                    button.postDelayed({ resetToIdle() }, RESTORE_DELAY_MS)
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
        const val SAVING_ALPHA = 0.7f
        const val CHECK_START_SCALE = 0.4f
        const val OVERSHOOT_TENSION = 2.0f
        const val PULSE_DURATION_MS = 280L
        const val RESTORE_DELAY_MS = 400L
    }
}