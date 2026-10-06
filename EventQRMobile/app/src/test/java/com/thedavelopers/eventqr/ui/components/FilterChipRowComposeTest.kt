package com.thedavelopers.eventqr.ui.components

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FilterChipRowComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val statuses = listOf("PENDING", "APPROVED", "REJECTED")

    @Test
    fun rendersOneChipPerItem() {
        setChips()

        statuses.forEach { status ->
            composeTestRule.onNodeWithText(status).assertIsDisplayed()
        }
    }

    @Test
    fun chips_meetMinimumTouchTarget() {
        setChips()

        statuses.forEach { status ->
            composeTestRule.onNode(hasText(status) and hasClickAction()).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun clickingChip_invokesCallbackWithThatItem() {
        var selected: String? = null
        setChips(onItemSelected = { selected = it })

        composeTestRule.onNodeWithText("APPROVED").performClick()
        composeTestRule.waitForIdle()

        assertEquals("APPROVED", selected)
    }

    @Test
    fun clickingSecondChip_doesNotFireFirst() {
        var selected: String? = null
        setChips(onItemSelected = { selected = it })

        composeTestRule.onNodeWithText("REJECTED").performClick()
        composeTestRule.waitForIdle()

        assertEquals("REJECTED", selected)
    }

    @Test
    fun selectedItem_isSemanticallySelected() {
        setChips(selectedItem = "APPROVED")

        composeTestRule.onNode(hasText("APPROVED") and hasClickAction()).assertIsSelected()
        composeTestRule.onNode(hasText("PENDING") and hasClickAction()).assertIsNotSelected()
        composeTestRule.onNode(hasText("REJECTED") and hasClickAction()).assertIsNotSelected()
    }

    @Test
    fun labelProvider_isUsedForChipLabels() {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                FilterChipRow(
                    items = listOf(EventBadgeStatus.PENDING, EventBadgeStatus.ACTIVE),
                    selectedItem = EventBadgeStatus.PENDING,
                    onItemSelected = {},
                    labelProvider = { status ->
                        when (status) {
                            EventBadgeStatus.PENDING -> "Awaiting"
                            else -> "Live"
                        }
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Awaiting").assertIsDisplayed()
        composeTestRule.onNodeWithText("Live").assertIsDisplayed()
        composeTestRule.onNodeWithText("PENDING").assertDoesNotExist()
    }

    @Test
    fun emptyItems_rendersNoChips() {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                FilterChipRow(
                    items = emptyList<String>(),
                    selectedItem = "",
                    onItemSelected = {},
                )
            }
        }

        composeTestRule.onNode(hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun defaultLabelProvider_usesToString() {
        var selected: Int? = null
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                FilterChipRow(
                    items = listOf(7, 9),
                    selectedItem = 7,
                    onItemSelected = { selected = it },
                )
            }
        }

        composeTestRule.onNodeWithText("9").performClick()
        composeTestRule.waitForIdle()

        assertEquals(9, selected)
    }

    @Test
    fun noSelectionHappensWithoutInteraction() {
        var selected: String? = null
        setChips(onItemSelected = { selected = it })

        composeTestRule.waitForIdle()

        assertNull(selected)
    }

    private fun setChips(
        selectedItem: String = "PENDING",
        onItemSelected: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            EventQrTheme(darkTheme = false) {
                FilterChipRow(
                    items = statuses,
                    selectedItem = selectedItem,
                    onItemSelected = onItemSelected,
                )
            }
        }
    }
}
