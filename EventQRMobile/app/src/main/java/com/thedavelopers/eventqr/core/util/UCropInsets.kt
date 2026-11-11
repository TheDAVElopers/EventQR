package com.thedavelopers.eventqr.core.util

import android.app.Activity
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.thedavelopers.eventqr.R
import com.yalantis.ucrop.R as UCropR

/**
 * UCrop's screen does not handle insets, and `EventQrApplication` turns off decor fitting for every activity,
 * so its toolbar would sit under the status bar and its controls under the navigation bar. The insets are added
 * to the padding the layout already ships with: the white toolbar keeps filling the status bar area while its
 * content moves down, and the dark controls bar keeps the gesture area.
 *
 * Safe to call more than once (the application hook runs again every time the activity is started).
 */
internal fun Activity.padUCropForSystemBars() {
    val root = findViewById<View>(UCropR.id.ucrop_photobox) ?: return
    // The callback can fire again on every start; re-capturing the now-inset padding would stack it.
    if (root.getTag(R.id.eventqr_crop_insets_applied) == true) return
    root.setTag(R.id.eventqr_crop_insets_applied, true)

    val toolbar = findViewById<View>(UCropR.id.toolbar)
    val controls = findViewById<View>(UCropR.id.controls_wrapper)
    // The bottom padding exposes a strip under the wrap_content controls bar; fill it with UCrop's own
    // controls color so the light navigation icons stay legible on it.
    controls?.setBackgroundColor(ContextCompat.getColor(this, UCropR.color.ucrop_color_widget_background))
    // Captured before the first dispatch; adding to the live padding would stack on every re-dispatch
    // (rotation, IME, cutout change).
    val toolbarBase = toolbar?.let { intArrayOf(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom) }
    val controlsBase = controls?.let { intArrayOf(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom) }

    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        if (toolbar != null && toolbarBase != null) {
            toolbar.setPadding(
                toolbarBase[0] + bars.left,
                toolbarBase[1] + bars.top,
                toolbarBase[2] + bars.right,
                toolbarBase[3],
            )
        }
        if (controls != null && controlsBase != null) {
            controls.setPadding(
                controlsBase[0] + bars.left,
                controlsBase[1],
                controlsBase[2] + bars.right,
                controlsBase[3] + bars.bottom,
            )
        }
        // Left unconsumed: no descendant of the photobox reads insets, and consuming would also swallow the
        // IME and cutout types for nothing.
        insets
    }
    ViewCompat.requestApplyInsets(root)
}
