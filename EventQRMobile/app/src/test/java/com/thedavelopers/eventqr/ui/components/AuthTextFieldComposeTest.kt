package com.thedavelopers.eventqr.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AuthTextFieldComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersLabel() {
        setField(label = "Email Address")

        composeTestRule.onNodeWithText("Email Address").assertIsDisplayed()
    }

    @Test
    fun nullPlaceholder_doesNotCrashAndKeepsLabel() {
        setField(label = "Email Address", placeholder = null)

        composeTestRule.onNodeWithText("Email Address").assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction()).assertExists()
    }

    @Test
    fun rendersCurrentValue() {
        setField(label = "Email Address", value = "attendee@eventqr.app")

        composeTestRule.onNode(hasSetTextAction()).assertTextContains("attendee@eventqr.app")
    }

    @Test
    fun errorMessage_isRenderedUnderTheField() {
        setField(label = "Email Address", errorMessage = "Enter a valid email")

        composeTestRule.onNodeWithText("Enter a valid email").assertIsDisplayed()
    }

    @Test
    fun nullErrorMessage_publishesNoErrorSemantics() {
        setField(label = "Email Address", errorMessage = null)

        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)).assertDoesNotExist()
    }

    @Test
    fun errorMessage_publishesErrorSemantics() {
        setField(label = "Email Address", errorMessage = "Enter a valid email")

        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)).assertExists()
    }

    @Test
    fun helperMessage_isRenderedWhenNoError() {
        setField(label = "Password", helperMessage = "Use at least 8 characters")

        composeTestRule.onNodeWithText("Use at least 8 characters").assertIsDisplayed()
    }

    @Test
    fun errorMessage_takesPrecedenceOverHelperMessage() {
        setField(
            label = "Password",
            errorMessage = "Passwords do not match",
            helperMessage = "Use at least 8 characters",
        )

        composeTestRule.onNodeWithText("Passwords do not match").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use at least 8 characters").assertDoesNotExist()
    }

    @Test
    fun enabledField_acceptsTextInput() {
        setField(enabled = true)

        composeTestRule.onNode(hasSetTextAction()).assertExists()
        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)).assertDoesNotExist()
    }

    @Test
    fun disabledField_isNotEnabledAndRejectsTextInput() {
        setField(enabled = false)

        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)).assertExists()
        composeTestRule.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun disabledField_stillRendersItsLabel() {
        setField(label = "Email Address", enabled = false)

        composeTestRule.onNodeWithText("Email Address").assertIsDisplayed()
    }

    @Test
    fun field_meetsMinimumInputHeight() {
        setField()

        composeTestRule.onNode(hasSetTextAction()).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun nonPasswordField_hasNoVisibilityToggle() {
        setField(isPassword = false)

        composeTestRule.onNodeWithContentDescription("Show password").assertDoesNotExist()
    }

    @Test
    fun passwordField_togglesVisibilityAffordance() {
        setField(label = "Password", isPassword = true)

        composeTestRule.onNodeWithContentDescription("Show password").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Hide password").assertExists()
        composeTestRule.onNodeWithContentDescription("Show password").assertDoesNotExist()
    }

    @Test
    fun passwordField_togglesBackToHidden() {
        setField(label = "Password", isPassword = true)

        composeTestRule.onNodeWithContentDescription("Show password").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Hide password").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Show password").assertExists()
    }

    @Test
    fun visibilityToggle_doesNotChangeFieldValue() {
        var currentValue = "hunter2secret"
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                AuthTextField(
                    value = currentValue,
                    onValueChange = { currentValue = it },
                    label = "Password",
                    isPassword = true,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Show password").performClick()
        composeTestRule.waitForIdle()

        assertEquals("hunter2secret", currentValue)
        composeTestRule.onNode(hasSetTextAction()).assertTextContains("hunter2secret")
    }

    @Test
    fun multipleFields_areIndependentlyAddressable() {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                AuthTextField(value = "", onValueChange = {}, label = "Email Address")
                AuthTextField(value = "", onValueChange = {}, label = "Password", isPassword = true)
            }
        }

        composeTestRule.onNodeWithText("Email Address").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
        assertEquals(2, composeTestRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun nonPasswordValue_isNotObscured() {
        setField(label = "Email Address", value = "visible@eventqr.app", isPassword = false)

        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)).assertDoesNotExist()
        composeTestRule.onNode(hasSetTextAction()).assertTextContains("visible@eventqr.app")
    }

    @Test
    fun passwordValue_isFlaggedAsPassword() {
        setField(label = "Password", value = "hunter2secret", isPassword = true)

        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)).assertExists()
    }

    private fun setField(
        value: String = "",
        label: String = "Email Address",
        placeholder: String? = null,
        errorMessage: String? = null,
        helperMessage: String? = null,
        isPassword: Boolean = false,
        enabled: Boolean = true,
    ) {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                AuthTextField(
                    value = value,
                    onValueChange = {},
                    label = label,
                    placeholder = placeholder,
                    errorMessage = errorMessage,
                    helperMessage = helperMessage,
                    isPassword = isPassword,
                    enabled = enabled,
                )
            }
        }
    }
}
