package com.thedavelopers.eventqr.smoke

import android.content.Context
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.thedavelopers.eventqr.core.session.RememberedEmailStore
import com.thedavelopers.eventqr.core.session.SessionManager
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.rules.ExternalResource

/**
 * Puts the app in a signed-out state before each test, using the app's own storage APIs and touching nothing else:
 *  - [SessionManager.clearSession] empties the `eventqr_session` encrypted prefs (tokens, role, profile). It is local
 *    only: no logout call is made, so a real session on the device is dropped without being revoked server-side.
 *  - [RememberedEmailStore] (`eventqr_remembered_email`) is cleared so the sign-in screen starts empty, and the
 *    previously remembered address is written back afterwards so a developer's "Remember my email" survives.
 *
 * Both stores share the in-process EncryptedSharedPreferences instance the activities use (same application
 * context), so the cleared state is visible to the next activity immediately, even before the async disk write.
 */
class LoggedOutStateRule : ExternalResource() {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private var rememberedEmail: String? = null

    override fun before() {
        val session = SessionManager(context)
        session.clearSession()
        check(!session.hasUsableToken()) { "Session still holds a token after clearSession()" }

        val emailStore = RememberedEmailStore.create(context)
        rememberedEmail = emailStore.get()
        emailStore.clear()
    }

    override fun after() {
        rememberedEmail?.let { RememberedEmailStore.create(context).save(it) }
        rememberedEmail = null
    }
}

/**
 * Keeps the autofill framework (e.g. the Google Password Manager suggestion popup) off every activity launched while
 * the rule is active, by marking each window's decor view IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS right after
 * onCreate. Test-side only: production layouts are unchanged. The popup is a separate window that could otherwise
 * swallow a tap aimed at a button underneath it.
 */
class DisableAutofillRule : ExternalResource() {
    private val callback = ActivityLifecycleCallback { activity, stage ->
        if (stage == Stage.CREATED) {
            activity.window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
    }

    override fun before() {
        ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback)
    }

    override fun after() {
        ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback)
    }
}

/**
 * Taps the middle of [linkText] inside a TextView whose text carries a [ClickableSpan] there (e.g. the
 * "Terms & Conditions" link inside the sign-up checkbox label). A plain click() would tap the view centre, which may
 * land outside the link. Fails if the text or the span is missing, so a broken link fails the test loudly.
 */
fun clickOnLinkText(linkText: String): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> = allOf(isAssignableFrom(TextView::class.java), isDisplayed())

    override fun getDescription(): String = "tap the ClickableSpan over \"$linkText\""

    override fun perform(uiController: UiController, view: View) {
        val textView = view as TextView
        val text = textView.text
        val start = text.toString().indexOf(linkText)
        check(start >= 0) { "\"$linkText\" not found in \"$text\"" }
        val spans = (text as? Spanned)?.getSpans(start, start + linkText.length, ClickableSpan::class.java)
        check(!spans.isNullOrEmpty()) { "No ClickableSpan over \"$linkText\"" }

        val layout = checkNotNull(textView.layout) { "TextView has not been laid out" }
        val line = layout.getLineForOffset(start)
        // Stay on the link's first line in case it wraps.
        val endOnLine = minOf(start + linkText.length, layout.getLineEnd(line)) - 1
        val xInText = (layout.getPrimaryHorizontal(start) + layout.getPrimaryHorizontal(endOnLine)) / 2f
        val yInText = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
        // Mirror LinkMovementMethod's hit test: it subtracts total padding and adds scroll.
        val x = textView.totalPaddingLeft + xInText - textView.scrollX
        val y = textView.totalPaddingTop + yInText - textView.scrollY

        GeneralClickAction(
            Tap.SINGLE,
            CoordinatesProvider { v ->
                val location = IntArray(2)
                v.getLocationOnScreen(location)
                floatArrayOf(location[0] + x, location[1] + y)
            },
            Press.FINGER,
            InputDevice.SOURCE_UNKNOWN,
            MotionEvent.BUTTON_PRIMARY,
        ).perform(uiController, view)
    }
}

/**
 * Taps a CheckBox on its box (the button drawable at the start edge) rather than its centre, so a label that contains
 * a link (sign-up terms) toggles the box instead of opening the link.
 */
fun tapCheckBoxBox(): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> = allOf(isAssignableFrom(CompoundButton::class.java), isDisplayed())

    override fun getDescription(): String = "tap the CheckBox box"

    override fun perform(uiController: UiController, view: View) {
        GeneralClickAction(
            Tap.SINGLE,
            CoordinatesProvider { v ->
                val box = v as CompoundButton
                val location = IntArray(2)
                v.getLocationOnScreen(location)
                // compoundPaddingLeft = padding + button drawable width (LTR), so half of it is inside the box.
                floatArrayOf(location[0] + box.compoundPaddingLeft / 2f, location[1] + v.height / 2f)
            },
            Press.FINGER,
            InputDevice.SOURCE_UNKNOWN,
            MotionEvent.BUTTON_PRIMARY,
        ).perform(uiController, view)
    }
}
