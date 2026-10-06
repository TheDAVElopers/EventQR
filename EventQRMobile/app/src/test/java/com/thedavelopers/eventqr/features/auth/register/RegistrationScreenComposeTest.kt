package com.thedavelopers.eventqr.features.auth.register

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.ui.theme.EventQrSpacing
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val VALID_PASSWORD = "Str0ng!Pass"
private const val CREATE_ACCOUNT_LABEL = "Create Account"
private const val SUBMITTING_LABEL = "Creating account..."

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RegistrationScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun termsControl_isUncheckedByDefault() {
        setScreen()

        termsControl().assertIsOff()
    }

    @Test
    fun termsControl_exposesCheckboxRoleForAssistiveTech() {
        setScreen()

        termsControl().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
    }

    @Test
    fun tappingTermsRow_opensCreateAccountGate() {
        val terms = setScreen()

        termsControl().performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(true), terms.changes)
        termsControl().assertIsOn()
        createAccountButton().assertIsEnabled()
    }

    @Test
    fun tappingTermsRow_whenChecked_closesCreateAccountGate() {
        val terms = setScreen(initialTerms = true)

        termsControl().performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(false), terms.changes)
        termsControl().assertIsOff()
        createAccountButton().assertIsNotEnabled()
    }

    @Test
    fun tappingCheckboxBox_opensCreateAccountGate() {
        val terms = setScreen()

        termsCheckbox().performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(true), terms.changes)
        createAccountButton().assertIsEnabled()
    }

    @Test
    fun tappingTermsLabel_opensCreateAccountGate() {
        val terms = setScreen()

        termsLabel().performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(true), terms.changes)
        termsControl().assertIsOn()
        createAccountButton().assertIsEnabled()
    }

    @Test
    fun tappingTermsLabel_whenChecked_closesCreateAccountGate() {
        val terms = setScreen(initialTerms = true)

        termsLabel().performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(false), terms.changes)
        termsControl().assertIsOff()
        createAccountButton().assertIsNotEnabled()
    }

    @Test
    fun createAccount_staysDisabledUntilTermsAccepted() {
        setScreen(initialTerms = false)

        createAccountButton().performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun createAccount_invokesRegisterOnceFormIsValid() {
        var registerClicks = 0
        setScreen(initialTerms = true, onRegister = { registerClicks++ })

        createAccountButton().performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, registerClicks)
    }

    @Test
    fun weakPassword_keepsCreateAccountDisabledEvenWithTerms() {
        setScreen(initialTerms = true, password = "short")

        createAccountButton().performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun submittingState_blocksTermsChangesAndShowsProgressLabel() {
        val terms = setScreen(isLoading = true)

        termsControl().performScrollTo().assertIsNotEnabled()
        createAccountButton(SUBMITTING_LABEL).performScrollTo().assertIsDisplayed().assertIsNotEnabled()

        termsControl().performClick()
        termsLabel().performClick()
        composeTestRule.waitForIdle()

        assertTrue(terms.changes.isEmpty())
    }

    @Test
    @Config(sdk = [35], qualifiers = "w600dp-h900dp-xhdpi")
    fun foldWidth_keepsTermsControlAndPrimaryActionReachable() {
        val terms = setScreen()

        termsCheckbox().performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(true), terms.changes)
        termsControl().assertIsOn()
        createAccountButton().performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h480dp", fontScale = 2.0f)
    fun largeFontScale_smallViewport_keepsTermsControlAndPrimaryActionReachable() {
        val terms = setScreen()

        termsCheckbox().performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(true), terms.changes)
        termsControl().assertIsOn()
        createAccountButton().performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h480dp", fontScale = 2.0f)
    fun largeFontScale_termsRow_meetsTouchTargetAndNeverClipsItsLabel() {
        setScreen()

        termsCheckbox().performScrollTo()
        termsLabel().performScrollTo()

        val minTouchTargetPx = with(composeTestRule.density) { EventQrSpacing().minTouchTarget.roundToPx() }
        val rowBounds: Rect = termsControl().fetchSemanticsNode().boundsInRoot
        val labelBounds: Rect = termsLabel().fetchSemanticsNode().boundsInRoot

        assertTrue("terms row height was ${rowBounds.height}px", rowBounds.height >= minTouchTargetPx)
        assertTrue("terms row was shorter than its label", rowBounds.height >= labelBounds.height)
        assertTrue("terms label started above its row", rowBounds.top <= labelBounds.top)
        assertTrue("terms label was cut off at the row bottom", rowBounds.bottom >= labelBounds.bottom)
    }

    private fun termsControl() = composeTestRule.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState),
        useUnmergedTree = true,
    )

    private fun termsLabel() = composeTestRule.onNodeWithText(TERMS_LABEL, useUnmergedTree = true)

    private fun termsCheckbox() = composeTestRule.onNodeWithTag(TERMS_CHECKBOX_TAG, useUnmergedTree = true)

    private fun createAccountButton(label: String = CREATE_ACCOUNT_LABEL) = composeTestRule.onNode(
        hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and hasText(label),
    )

    private class TermsChanges {
        val changes = mutableListOf<Boolean>()
    }

    private fun setScreen(
        initialTerms: Boolean = false,
        isLoading: Boolean = false,
        password: String = VALID_PASSWORD,
        onRegister: () -> Unit = {},
    ): TermsChanges {
        val terms = TermsChanges()
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                var termsAccepted by rememberSaveable { mutableStateOf(initialTerms) }
                RegistrationScreen(
                    firstName = "Ana",
                    onFirstNameChange = {},
                    lastName = "Dela Cruz",
                    onLastNameChange = {},
                    email = "ana@example.com",
                    onEmailChange = {},
                    phoneDigits = "9171234567",
                    onPhoneDigitsChange = {},
                    password = password,
                    onPasswordChange = {},
                    confirmPassword = password,
                    onConfirmPasswordChange = {},
                    termsAccepted = termsAccepted,
                    onTermsAcceptedChange = {
                        terms.changes += it
                        termsAccepted = it
                    },
                    fieldErrors = emptyMap(),
                    isLoading = isLoading,
                    onRegister = onRegister,
                    onSignIn = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        return terms
    }
}
