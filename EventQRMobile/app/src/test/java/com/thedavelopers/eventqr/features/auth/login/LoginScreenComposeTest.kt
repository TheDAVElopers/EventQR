package com.thedavelopers.eventqr.features.auth.login

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LoginScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersHeaderTitleAndSubtitle() {
        setScreen()

        composeTestRule.onNodeWithText("Sign in to start scanning and streamline event entry.").assertIsDisplayed()
    }

    @Test
    fun rendersHeaderTitleAlongsideButtonCopy() {
        setScreen()

        assertEquals(
            2,
            composeTestRule.onAllNodes(hasText("Sign In", substring = false)).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun rendersBothFieldLabels() {
        setScreen()

        composeTestRule.onNodeWithText("Email Address").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun rendersTwoTextInputs() {
        setScreen()

        assertEquals(2, composeTestRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun forgotPassword_invokesCallback() {
        var forgot = 0
        setScreen(onForgotPassword = { forgot++ })

        composeTestRule.onNodeWithText("Forgot Password?").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, forgot)
    }

    @Test
    fun register_invokesCallback() {
        var register = 0
        setScreen(onRegister = { register++ })

        composeTestRule.onNodeWithText("Create Account").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, register)
    }

    @Test
    fun signIn_invokesCallback() {
        var signIn = 0
        setScreen(onSignIn = { signIn++ })

        composeTestRule.onNode(hasText("Sign In") and hasClickAction()).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, signIn)
    }

    @Test
    fun fieldErrors_areRendered() {
        setScreen(emailError = "Enter a valid email", passwordError = "Password is required")

        composeTestRule.onNodeWithText("Enter a valid email").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password is required").assertIsDisplayed()
    }

    @Test
    fun noErrors_rendersNoErrorCopy() {
        setScreen(emailError = null, passwordError = null)

        composeTestRule.onNodeWithText("Enter a valid email").assertDoesNotExist()
        composeTestRule.onNodeWithText("Password is required").assertDoesNotExist()
    }

    @Test
    fun loading_disablesSignInAndShowsProgressCopy() {
        setScreen(isLoading = true)

        composeTestRule.onNode(hasText("Signing in...") and isDisabledNode()).assertIsNotEnabled()
        composeTestRule.onNodeWithText("Signing in...").assertIsDisplayed()
        composeTestRule.onNode(hasText("Sign In") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun loading_disablesForgotPasswordAndCreateAccount() {
        setScreen(isLoading = true)

        composeTestRule.onNodeWithText("Forgot Password?").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Create Account").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun loading_doesNotFireCallbacks() {
        var signIn = 0
        var register = 0
        var forgot = 0
        setScreen(
            isLoading = true,
            onSignIn = { signIn++ },
            onRegister = { register++ },
            onForgotPassword = { forgot++ },
        )

        composeTestRule.onNode(hasText("Signing in...") and isDisabledNode()).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Forgot Password?").performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, signIn)
        assertEquals(0, register)
        assertEquals(0, forgot)
    }

    @Test
    fun notLoading_rendersIdleSignInCopy() {
        setScreen(isLoading = false)

        composeTestRule.onNodeWithText("Signing in...").assertDoesNotExist()
        composeTestRule.onNode(hasText("Sign In") and hasClickAction()).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun populatedFields_renderTheirValues() {
        setScreen(email = "staff@eventqr.app", password = "hunter2secret")

        assertEquals(2, composeTestRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }

    private fun isDisabledNode(): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)

    private fun setScreen(
        email: String = "",
        password: String = "",
        emailError: String? = null,
        passwordError: String? = null,
        isLoading: Boolean = false,
        onSignIn: () -> Unit = {},
        onRegister: () -> Unit = {},
        onForgotPassword: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                LoginScreen(
                    email = email,
                    onEmailChange = {},
                    password = password,
                    onPasswordChange = {},
                    emailError = emailError,
                    passwordError = passwordError,
                    isLoading = isLoading,
                    onSignIn = onSignIn,
                    onRegister = onRegister,
                    onForgotPassword = onForgotPassword,
                )
            }
        }
    }
}
