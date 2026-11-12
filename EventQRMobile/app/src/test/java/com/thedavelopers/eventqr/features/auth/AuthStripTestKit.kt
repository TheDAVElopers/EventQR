package com.thedavelopers.eventqr.features.auth

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.thedavelopers.eventqr.R
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/**
 * Shared Robolectric helpers for the §5.16 form-failure strip on the auth screens: build an activity
 * that is attached to a window (a detached view never sends an announcement), switch TalkBack on, and
 * read the strip and the announcements it produced.
 */
internal object AuthStripTestKit {

    fun <T : Activity> visible(clazz: Class<T>): T {
        val controller: ActivityController<T> = Robolectric.buildActivity(clazz)
        return controller.create().start().resume().visible().get()
    }

    private fun accessibilityManager(): AccessibilityManager =
        ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    fun enableAccessibility() {
        shadowOf(accessibilityManager()).setEnabled(true)
    }

    /** Text of every TYPE_ANNOUNCEMENT event sent so far, in order. */
    fun announcements(): List<String> =
        shadowOf(accessibilityManager()).sentAccessibilityEvents
            .filter { it.eventType == AccessibilityEvent.TYPE_ANNOUNCEMENT }
            .map { it.text.joinToString("") }

    /** Visible, with the controller kept so a test can [rotate] it. */
    fun <T : Activity> visibleController(clazz: Class<T>): ActivityController<T> =
        Robolectric.buildActivity(clazz).create().start().resume().visible()

    /** A configuration change: save state, destroy, rebuild the activity from that state. */
    fun <T : Activity> rotate(controller: ActivityController<T>): T {
        controller.recreate()
        return controller.get()
    }

    /** The helper line's text when it is showing, else null. */
    fun helper(activity: Activity, id: Int): String? =
        activity.findViewById<TextView>(id).takeIf { it.visibility == View.VISIBLE }?.text?.toString()

    fun strip(activity: Activity, includeId: Int): View = activity.findViewById(includeId)

    fun message(strip: View): String = strip.findViewById<TextView>(R.id.txtErrorStrip).text.toString()

    fun retry(strip: View): View = strip.findViewById(R.id.btnErrorStripRetry)
}
