/*
 * On-device auth smoke tests (Espresso). Offline-safe: every flow stops at client-side validation or a local
 * navigation, so no request reaches the backend, no credentials are needed, and no backend data is created.
 *
 * Run (device/emulator connected, screen unlocked):
 *   ./gradlew.bat :app:connectedDebugAndroidTest
 * Only this class:
 *   ./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.thedavelopers.eventqr.smoke.AuthSmokeTest"
 *
 * Side effects on the device:
 *  - Each test clears the app's local session (SessionManager), so you are signed out of the installed app after a
 *    run. The remembered sign-in e-mail is restored after each test.
 *  - connectedAndroidTest uninstalls the app and test APKs after the run by default; this project sets
 *    android.injected.androidTest.leaveApksInstalledAfterRun=true in gradle.properties so both stay installed.
 *    The install itself is `pm install -r` (data kept), but it replaces the installed build with this debug build.
 *  - Window animations are turned off for the run only (testOptions.animationsDisabled in app/build.gradle.kts).
 */
package com.thedavelopers.eventqr.smoke

import android.content.Context
import androidx.annotation.StringRes
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.VerificationModes.times
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.auth.forgotpassword.ForgotPasswordActivity
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import com.thedavelopers.eventqr.features.auth.register.RegistrationActivity
import com.thedavelopers.eventqr.features.auth.resetpassword.ResetPasswordActivity
import com.thedavelopers.eventqr.features.landing.LandingActivity
import com.thedavelopers.eventqr.features.terms.TermsActivity
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class AuthSmokeTest {

    // Order matters: the session is cleared before anything is launched, then autofill is suppressed and intents are
    // recorded for each test. Activities are launched inside the tests (not via ActivityScenarioRule) so that the
    // session is already gone when LandingActivity decides whether to auto-route to a dashboard.
    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(LoggedOutStateRule())
        .around(DisableAutofillRule())
        .around(IntentsRule())

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun str(@StringRes id: Int): String = context.getString(id)

    // ---- 1. Landing (no session) ------------------------------------------------------------------------------

    @Test
    fun landing_withoutSession_showsSignInAndCreateAccount() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            onView(withId(R.id.btnSignIn))
                .check(matches(allOf(isDisplayed(), withText(R.string.common_sign_in))))
            onView(withId(R.id.btnCreateAccount))
                .check(matches(allOf(isDisplayed(), withText(R.string.common_create_account))))
        }
    }

    @Test
    fun landing_signIn_opensLogin() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            onView(withId(R.id.btnSignIn)).perform(click())

            intended(hasComponent(LoginActivity::class.java.name))
            onView(withId(R.id.edtEmail)).check(matches(isDisplayed()))
            onView(withId(R.id.edtPassword)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun landing_createAccount_opensRegistration() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            onView(withId(R.id.btnCreateAccount)).perform(click())

            intended(hasComponent(RegistrationActivity::class.java.name))
            onView(withId(R.id.edtFirstName)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun landing_signIn_thenBack_returnsToLanding() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            onView(withId(R.id.btnSignIn)).perform(click())
            onView(withId(R.id.edtEmail)).check(matches(isDisplayed()))

            closeSoftKeyboard()
            pressBack()

            onView(withId(R.id.btnCreateAccount)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun landing_signIn_toSignUp_thenBack_returnsToLanding() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            onView(withId(R.id.btnSignIn)).perform(click())
            closeSoftKeyboard()
            onView(withId(R.id.btnRegister)).perform(click())
            onView(withId(R.id.edtFirstName)).check(matches(isDisplayed()))

            closeSoftKeyboard()
            pressBack()

            onView(withId(R.id.btnCreateAccount)).check(matches(isDisplayed()))
        }
    }

    // ---- 2. Login client validation ---------------------------------------------------------------------------

    @Test
    fun login_emptyPassword_showsPasswordError_andSendsNothing() {
        ActivityScenario.launch(LoginActivity::class.java).use {
            onView(withId(R.id.edtEmail)).perform(replaceText("smoke.test@example.com"))
            onView(withId(R.id.edtPassword)).perform(replaceText(""))
            closeSoftKeyboard()
            onView(withId(R.id.btnSignIn)).perform(click())

            onView(withId(R.id.edtPassword)).check(matches(hasErrorText(str(R.string.login_enter_your_password))))
            assertSignInIdle()
        }
    }

    @Test
    fun login_malformedEmail_showsEmailError_andSendsNothing() {
        ActivityScenario.launch(LoginActivity::class.java).use {
            onView(withId(R.id.edtEmail)).perform(replaceText("not-an-email"))
            onView(withId(R.id.edtPassword)).perform(replaceText("Anything1!"))
            closeSoftKeyboard()
            onView(withId(R.id.btnSignIn)).perform(click())

            onView(withId(R.id.edtEmail))
                .check(matches(hasErrorText(str(R.string.create_admin_account_enter_a_valid_email_address))))
            assertSignInIdle()
        }
    }

    /** showLoading(true) disables the button and swaps its label; staying idle means no login request started. */
    private fun assertSignInIdle() {
        onView(withId(R.id.btnSignIn)).check(matches(allOf(isEnabled(), withText(R.string.common_sign_in))))
    }

    // ---- 3. Sign-up validation --------------------------------------------------------------------------------

    @Test
    fun signUp_weakPassword_keepsCreateAccountDisabled_evenWithTermsChecked() {
        ActivityScenario.launch(RegistrationActivity::class.java).use {
            onView(withId(R.id.edtPassword)).perform(scrollTo(), replaceText(WEAK_PASSWORD))
            onView(withId(R.id.edtConfirmPassword)).perform(scrollTo(), replaceText(WEAK_PASSWORD))
            closeSoftKeyboard()
            onView(withId(R.id.chkTerms)).perform(scrollTo(), tapCheckBoxBox())
            onView(withId(R.id.chkTerms)).check(matches(isChecked()))

            onView(withId(R.id.btnRegister)).perform(scrollTo()).check(matches(isNotEnabled()))

            // Control: the same form with a strong password enables the button, so the gate is the password.
            onView(withId(R.id.edtPassword)).perform(scrollTo(), replaceText(STRONG_PASSWORD))
            onView(withId(R.id.btnRegister)).perform(scrollTo()).check(matches(isEnabled()))
        }
    }

    @Test
    fun signUp_termsUnchecked_keepsCreateAccountDisabled() {
        ActivityScenario.launch(RegistrationActivity::class.java).use {
            fillValidSignUpForm(confirmPassword = STRONG_PASSWORD)
            onView(withId(R.id.chkTerms)).perform(scrollTo()).check(matches(isNotChecked()))

            onView(withId(R.id.btnRegister)).perform(scrollTo()).check(matches(isNotEnabled()))

            // Control: ticking the box enables it, unticking disables it again.
            onView(withId(R.id.chkTerms)).perform(scrollTo(), tapCheckBoxBox()).check(matches(isChecked()))
            onView(withId(R.id.btnRegister)).perform(scrollTo()).check(matches(isEnabled()))
            onView(withId(R.id.chkTerms)).perform(scrollTo(), tapCheckBoxBox()).check(matches(isNotChecked()))
            onView(withId(R.id.btnRegister)).perform(scrollTo()).check(matches(isNotEnabled()))
        }
    }

    @Test
    fun signUp_passwordMismatch_showsError_andSendsNothing() {
        ActivityScenario.launch(RegistrationActivity::class.java).use {
            fillValidSignUpForm(confirmPassword = "Different9#")
            onView(withId(R.id.chkTerms)).perform(scrollTo(), tapCheckBoxBox()).check(matches(isChecked()))

            closeSoftKeyboard()
            onView(withId(R.id.btnRegister)).perform(click())

            onView(withId(R.id.edtConfirmPassword))
                .perform(scrollTo())
                .check(matches(hasErrorText(str(R.string.password_error_mismatch))))
            // showLoading(true) would relabel the button; the presenter stopped at validation.
            onView(withId(R.id.btnRegister))
                .perform(scrollTo())
                .check(matches(allOf(isEnabled(), withText(R.string.common_create_account))))
        }
    }

    private fun fillValidSignUpForm(confirmPassword: String) {
        onView(withId(R.id.edtFirstName)).perform(scrollTo(), replaceText("Smoke"))
        onView(withId(R.id.edtLastName)).perform(scrollTo(), replaceText("Test"))
        onView(withId(R.id.edtEmail)).perform(scrollTo(), replaceText("smoke.test@example.com"))
        // Ten local digits under the fixed +63 prefix.
        onView(withId(R.id.edtPhoneNumber)).perform(scrollTo(), replaceText("9171234567"))
        onView(withId(R.id.edtPassword)).perform(scrollTo(), replaceText(STRONG_PASSWORD))
        onView(withId(R.id.edtConfirmPassword)).perform(scrollTo(), replaceText(confirmPassword))
        closeSoftKeyboard()
    }

    // ---- 4. Terms link ----------------------------------------------------------------------------------------

    @Test
    fun signUp_termsLink_opensTerms() {
        ActivityScenario.launch(RegistrationActivity::class.java).use {
            onView(withId(R.id.chkTerms)).perform(scrollTo(), clickOnLinkText(str(R.string.terms_link_signup)))

            intended(hasComponent(TermsActivity::class.java.name))
            onView(withId(R.id.layoutTermsContent)).check(matches(isDisplayed()))
        }
    }

    // ---- 5. Forgot password -----------------------------------------------------------------------------------

    @Test
    fun forgotPassword_invalidEmail_showsError_andSendsNothing() {
        ActivityScenario.launch(LoginActivity::class.java).use {
            onView(withId(R.id.txtForgotPassword)).perform(click())
            intended(hasComponent(ForgotPasswordActivity::class.java.name))

            onView(withId(R.id.editEmail)).perform(scrollTo(), replaceText("bad@"))
            closeSoftKeyboard()
            onView(withId(R.id.btnSendCode)).perform(scrollTo(), click())

            onView(withId(R.id.editEmail))
                .check(matches(hasErrorText(str(R.string.create_admin_account_enter_a_valid_email_address))))
            onView(withId(R.id.btnSendCode))
                .check(matches(allOf(isEnabled(), withText(R.string.forgot_password_send_code))))
            intended(hasComponent(ResetPasswordActivity::class.java.name), times(0))
        }
    }

    private companion object {
        /** Fails length, uppercase, number and special-character rules. */
        const val WEAK_PASSWORD = "weak"

        /** Meets every sign-up rule (8+, upper, lower, digit, special). */
        const val STRONG_PASSWORD = "Str0ng!Pass"
    }
}
