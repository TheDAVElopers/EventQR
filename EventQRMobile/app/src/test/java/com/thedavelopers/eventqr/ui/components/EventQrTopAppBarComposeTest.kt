package com.thedavelopers.eventqr.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import com.thedavelopers.eventqr.ui.theme.TextOnPrimary
import com.thedavelopers.eventqr.ui.theme.TextPrimary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EventQrTopAppBarComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun title_rendersText() {
        setBar(title = "Registrations")

        composeTestRule.onNodeWithText("Registrations").assertIsDisplayed()
    }

    @Test
    fun subtitle_rendersWhenPresent() {
        setBar(title = "Registrations", subtitle = "12 pending")

        composeTestRule.onNodeWithText("Registrations").assertIsDisplayed()
        composeTestRule.onNodeWithText("12 pending").assertIsDisplayed()
    }

    @Test
    fun nullSubtitle_rendersNoSubtitleRow() {
        setBar(title = "Registrations", subtitle = null)

        composeTestRule.onNodeWithText("Registrations").assertIsDisplayed()
        composeTestRule.onNodeWithText("12 pending").assertDoesNotExist()
    }

    @Test
    fun blankSubtitle_rendersNoSubtitleRow() {
        setBar(title = "Registrations", subtitle = "   ")

        composeTestRule.onNodeWithText("12 pending").assertDoesNotExist()
    }

    @Test
    fun titleContentColor_reachesAppBarContentColor() {
        var actionContentColor: Color? = null

        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                EventQrTopAppBar(
                    title = "Registrations",
                    titleContentColor = Color(0xFFAA0000),
                    actions = {
                        actionContentColor = LocalContentColor.current
                    },
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(Color(0xFFAA0000), actionContentColor)
    }

    @Test
    fun titleContentColor_reachesNavigationIconContentColor() {
        var navigationContentColor: Color? = null

        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                EventQrTopAppBar(
                    title = "Registrations",
                    titleContentColor = Color(0xFF00AA00),
                    navigationIcon = {
                        navigationContentColor = LocalContentColor.current
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    },
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(Color(0xFF00AA00), navigationContentColor)
    }

    @Test
    fun titleContentColor_overrideWinsOverBrandHeaderDefault() {
        var actionContentColor: Color? = null

        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                EventQrTopAppBar(
                    title = "Registrations",
                    isBrandHeader = true,
                    titleContentColor = Color(0xFF0000AA),
                    actions = {
                        actionContentColor = LocalContentColor.current
                    },
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(Color(0xFF0000AA), actionContentColor)
        assertNotEquals(TextOnPrimary, actionContentColor)
    }

    @Test
    fun brandHeader_defaultsToOnPrimaryContentColor() {
        var actionContentColor: Color? = null

        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                EventQrTopAppBar(
                    title = "EventQR",
                    isBrandHeader = true,
                    actions = {
                        actionContentColor = LocalContentColor.current
                    },
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(TextOnPrimary, actionContentColor)
    }

    @Test
    fun nonBrandHeader_defaultsToOnSurfaceContentColor() {
        var actionContentColor: Color? = null

        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                EventQrTopAppBar(
                    title = "Registrations",
                    actions = {
                        actionContentColor = LocalContentColor.current
                    },
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(TextPrimary, actionContentColor)
    }

    @Test
    fun onBackClick_invokesCallback() {
        var backClicks = 0
        setBar(title = "Registrations", onBackClick = { backClicks++ })

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, backClicks)
    }

    @Test
    fun nullOnBackClick_rendersNoBackIcon() {
        setBar(title = "Registrations", onBackClick = null)

        composeTestRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun customNavigationIcon_replacesBackIcon() {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                EventQrTopAppBar(
                    title = "Registrations",
                    onBackClick = {},
                    navigationIcon = {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss")
                    },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Dismiss").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun actionSlot_contentIsRendered() {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                EventQrTopAppBar(
                    title = "Registrations",
                    actions = {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Overflow")
                    },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Overflow").assertIsDisplayed()
        composeTestRule.onNodeWithText("Registrations").assertIsDisplayed()
    }

    private fun setBar(title: String, subtitle: String? = null, onBackClick: (() -> Unit)? = null) {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                EventQrTopAppBar(
                    title = title,
                    subtitle = subtitle,
                    onBackClick = onBackClick,
                )
            }
        }
    }
}
