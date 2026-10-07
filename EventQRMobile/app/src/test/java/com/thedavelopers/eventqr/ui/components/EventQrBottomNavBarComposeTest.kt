package com.thedavelopers.eventqr.ui.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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
class EventQrBottomNavBarComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun attendeeNavItems_exposeExpectedIdsAndLabels() {
        assertEquals(
            listOf("home", "events", "registered", "rewards", "profile"),
            AttendeeNavItems.map { it.id },
        )
        assertEquals(
            listOf("Home", "Events", "Registered", "Rewards", "Profile"),
            AttendeeNavItems.map { it.label },
        )
    }

    @Test
    fun staffNavItems_exposeExpectedIdsAndLabels() {
        assertEquals(
            listOf("dashboard", "scanner", "events", "logs"),
            StaffNavItems.map { it.id },
        )
        assertEquals(
            listOf("Dashboard", "Scan", "Events", "Logs"),
            StaffNavItems.map { it.label },
        )
    }

    @Test
    fun attendeeNavBar_rendersEveryItemLabel() {
        setNavBar(items = AttendeeNavItems, selectedId = "home")

        listOf("Home", "Events", "Registered", "Rewards", "Profile").forEach { label ->
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun staffNavBar_rendersEveryItemLabel() {
        setNavBar(items = StaffNavItems, selectedId = "dashboard")

        listOf("Dashboard", "Scan", "Events", "Logs").forEach { label ->
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun adminNavItems_exposeExpectedIdsAndLabels() {
        assertEquals(
            listOf("dashboard", "requests", "accounts", "logs"),
            AdminNavItems.map { it.id },
        )
        assertEquals(
            listOf("Dashboard", "Requests", "Accounts", "Logs"),
            AdminNavItems.map { it.label },
        )
    }

    @Test
    fun organizerNavItems_exposeExpectedIdsAndLabels() {
        assertEquals(
            listOf("dashboard", "events", "attendees", "reports", "rewards"),
            OrganizerNavItems.map { it.id },
        )
        assertEquals(
            listOf("Dashboard", "Events", "Attendees", "Reports", "Rewards"),
            OrganizerNavItems.map { it.label },
        )
    }

    @Test
    fun adminNavBar_rendersEveryItemLabel() {
        setNavBar(items = AdminNavItems, selectedId = "dashboard")

        listOf("Dashboard", "Requests", "Accounts", "Logs").forEach { label ->
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun organizerNavBar_rendersEveryItemLabel() {
        setNavBar(items = OrganizerNavItems, selectedId = "events")

        listOf("Dashboard", "Events", "Attendees", "Reports", "Rewards").forEach { label ->
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun adminNavBar_selectedRequestsIsSemanticallySelected() {
        setNavBar(items = AdminNavItems, selectedId = "requests")

        composeTestRule.onNode(hasText("Requests")).assertIsSelected()
        composeTestRule.onNode(hasText("Dashboard")).assertIsNotSelected()
        composeTestRule.onNode(hasText("Logs")).assertIsNotSelected()
    }

    @Test
    fun everyItem_exposesTabRole() {
        setNavBar(items = AttendeeNavItems, selectedId = "home")

        listOf("Home", "Events", "Registered", "Rewards", "Profile").forEach { label ->
            assertEquals(Role.Tab, roleOf(label))
        }
    }

    @Test
    fun selectedItem_isSemanticallySelected() {
        setNavBar(items = AttendeeNavItems, selectedId = "events")

        composeTestRule.onNode(hasText("Events")).assertIsSelected()
        composeTestRule.onNode(hasText("Home")).assertIsNotSelected()
        composeTestRule.onNode(hasText("Rewards")).assertIsNotSelected()
    }

    @Test
    fun clickingItem_firesCallbackWithThatId() {
        var selected: String? = null
        setNavBar(items = AttendeeNavItems, selectedId = "home", onItemSelected = { selected = it })

        composeTestRule.onNodeWithText("Rewards").performClick()
        composeTestRule.waitForIdle()

        assertEquals("rewards", selected)
    }

    @Test
    fun clickingAlreadySelectedItem_stillFiresCallback() {
        var selected: String? = null
        setNavBar(items = AttendeeNavItems, selectedId = "home", onItemSelected = { selected = it })

        composeTestRule.onNodeWithText("Home").performClick()
        composeTestRule.waitForIdle()

        assertEquals("home", selected)
    }

    @Test
    fun staffNavBar_clickingFiresStaffId() {
        var selected: String? = null
        setNavBar(items = StaffNavItems, selectedId = "dashboard", onItemSelected = { selected = it })

        composeTestRule.onNodeWithText("Logs").performClick()
        composeTestRule.waitForIdle()

        assertEquals("logs", selected)
    }

    @Test
    fun noInteraction_leavesCallbackUnset() {
        var selected: String? = null
        setNavBar(items = AttendeeNavItems, selectedId = "home", onItemSelected = { selected = it })

        composeTestRule.waitForIdle()

        assertNull(selected)
    }

    @Test
    fun everyItem_meetsMinimumTouchHeight() {
        setNavBar(items = AttendeeNavItems, selectedId = "home")

        listOf("Home", "Events", "Registered", "Rewards", "Profile").forEach { label ->
            composeTestRule.onNode(hasText(label)).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun unknownSelectedId_leavesEveryItemUnselected() {
        setNavBar(items = AttendeeNavItems, selectedId = "does-not-exist")

        listOf("Home", "Events", "Registered", "Rewards", "Profile").forEach { label ->
            composeTestRule.onNode(hasText(label)).assertIsNotSelected()
        }
    }

    private fun roleOf(label: String): Role? =
        composeTestRule.onNode(hasText(label))
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Role)

    private fun setNavBar(
        items: List<NavItem>,
        selectedId: String,
        onItemSelected: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            EventQrTheme {
                EventQrBottomNavBar(
                    items = items,
                    selectedId = selectedId,
                    onItemSelected = onItemSelected,
                )
            }
        }
    }
}
