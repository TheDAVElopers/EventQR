package com.thedavelopers.eventqr.features.auth

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.navigation.AppNavigator
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.auth.login.LoginActivity
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse
import com.thedavelopers.eventqr.features.auth.register.RegistrationActivity
import com.thedavelopers.eventqr.features.dashboard.DashboardActivity
import com.thedavelopers.eventqr.features.landing.LandingActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * D13: Landing stays beneath Sign In / Sign Up, the two replace each other instead of stacking, so system Back from
 * either returns to Landing (and Back on Landing exits). Robolectric has no real back stack, so "returns to Landing"
 * is asserted as: Landing did not finish when it opened the screen, and Back on the screen only finishes it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class AuthBackStackTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun signedOut() {
        SessionManager(context).clearSession()
    }

    private fun landing(): LandingActivity = Robolectric.buildActivity(LandingActivity::class.java).setup().get()

    private fun login(): LoginActivity = Robolectric.buildActivity(LoginActivity::class.java).setup().get()

    private fun registration(): RegistrationActivity =
        Robolectric.buildActivity(RegistrationActivity::class.java).setup().get()

    private fun pressBack(activity: androidx.appcompat.app.AppCompatActivity) {
        activity.onBackPressedDispatcher.onBackPressed()
    }

    @Test
    fun landing_signIn_keepsLandingBeneath_andBackFromSignInOnlyClosesSignIn() {
        val landing = landing()

        landing.findViewById<View>(R.id.btnSignIn).performClick()

        assertEquals(LoginActivity::class.java.name, shadowOf(landing).nextStartedActivity.component?.className)
        assertFalse("Landing must stay beneath Sign In", landing.isFinishing)

        val login = login()
        pressBack(login)
        assertTrue(login.isFinishing)
        assertNull("Back opens nothing new: the Landing beneath shows", shadowOf(login).nextStartedActivity)
    }

    @Test
    fun landing_createAccount_keepsLandingBeneath_andBackFromSignUpOnlyClosesSignUp() {
        val landing = landing()

        landing.findViewById<View>(R.id.btnCreateAccount).performClick()

        assertEquals(RegistrationActivity::class.java.name, shadowOf(landing).nextStartedActivity.component?.className)
        assertFalse("Landing must stay beneath Sign Up", landing.isFinishing)

        val registration = registration()
        pressBack(registration)
        assertTrue(registration.isFinishing)
        assertNull(shadowOf(registration).nextStartedActivity)
    }

    @Test
    fun signIn_toSignUp_replacesSignIn_soBackFromSignUpReturnsToLanding() {
        val login = login()

        login.findViewById<View>(R.id.btnRegister).performClick()

        assertEquals(RegistrationActivity::class.java.name, shadowOf(login).nextStartedActivity.component?.className)
        assertTrue("Sign In is replaced, not stacked under Sign Up", login.isFinishing)

        val registration = registration()
        pressBack(registration)
        assertTrue(registration.isFinishing)
        assertNull(shadowOf(registration).nextStartedActivity)
    }

    @Test
    fun signUp_toSignIn_replacesSignUp() {
        val registration = registration()

        registration.navigateToSignIn()

        assertEquals(LoginActivity::class.java.name, shadowOf(registration).nextStartedActivity.component?.className)
        assertTrue(registration.isFinishing)
    }

    @Test
    fun successfulSignIn_startsTheDashboardInAFreshTask() {
        val login = login()

        login.navigateToDashboard(AccountRole.ATTENDEE.name)

        val intent = shadowOf(login).nextStartedActivity
        assertEquals(DashboardActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(login.isFinishing)
    }

    @Test
    fun signedOutStack_isSignInOverLandingInAFreshTask() {
        val stack = AppNavigator.signedOutStack(context)

        assertEquals(
            listOf(LandingActivity::class.java.name, LoginActivity::class.java.name),
            stack.map { it.component?.className },
        )
        assertTrue(stack[0].flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        assertTrue(stack[0].flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun landing_withASession_stillRoutesStraightToTheDashboard() {
        // No refresh token: the background refresh has nothing to rotate and makes no network call.
        SessionManager(context).saveLoginResponse(
            LoginResponse(
                accessToken = "access",
                userId = UUID.fromString("11111111-2222-3333-4444-555555555555"),
                email = "user@example.com",
                fullName = "User",
                role = AccountRole.ATTENDEE,
            )
        )

        val landing = Robolectric.buildActivity(LandingActivity::class.java).create().get()

        assertEquals(DashboardActivity::class.java.name, shadowOf(landing).nextStartedActivity.component?.className)
        assertTrue(landing.isFinishing)
    }
}
