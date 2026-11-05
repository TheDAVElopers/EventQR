package com.thedavelopers.eventqr.features.attendee

import android.app.Activity
import android.view.WindowManager
import android.widget.CompoundButton

/**
 * The credential screens are held up to a scanner, often in a dim venue, so they offer "keep the
 * screen bright". The override is scoped to the activity's window, so leaving the screen restores
 * the system brightness without any teardown code.
 */
object KeepScreenBright {

    fun bind(activity: Activity, switch: CompoundButton?) {
        switch ?: return
        switch.setOnCheckedChangeListener { _, isOn ->
            activity.window.attributes = activity.window.attributes.apply {
                screenBrightness = if (isOn) {
                    1f
                } else {
                    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }
}
