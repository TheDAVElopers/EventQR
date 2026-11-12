package com.thedavelopers.eventqr.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Width is the 360dp design target and graphics mode is NATIVE so text measurement is real: the
 * label guard measures glyphs, and the legacy Robolectric font reports "Dashboard" at ~43dp instead
 * of 55dp, which would let the rule pass in the wrong state.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventQrBottomNavBarComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun attendeeNavItems_exposeExpectedIdsAndLabels() {
        assertEquals(
            listOf("home", "events", "rewards", "profile"),
            AttendeeNavItems.map { it.id },
        )
        assertEquals(
            listOf("Home", "Events", "Rewards", "Profile"),
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

        listOf("Home", "Events", "Rewards", "Profile").forEach { label ->
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

        listOf("Home", "Events", "Rewards", "Profile").forEach { label ->
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

        listOf("Home", "Events", "Rewards", "Profile").forEach { label ->
            composeTestRule.onNode(hasText(label)).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun unknownSelectedId_leavesEveryItemUnselected() {
        setNavBar(items = AttendeeNavItems, selectedId = "does-not-exist")

        listOf("Home", "Events", "Rewards", "Profile").forEach { label ->
            composeTestRule.onNode(hasText(label)).assertIsNotSelected()
        }
    }

    @Test
    fun width360_fontScaleOne_showsLabels() {
        setNavBar(items = OrganizerNavItems, selectedId = "dashboard", fontScale = 1f)

        OrganizerNavItems.forEach { item ->
            composeTestRule.onNodeWithText(item.label).assertIsDisplayed()
        }
    }

    @Test
    fun width360_fontScaleOnePointThree_dropsLabelsButKeepsTheItemUsable() {
        var selected: String? = null
        setNavBar(
            items = OrganizerNavItems,
            selectedId = "dashboard",
            onItemSelected = { selected = it },
            fontScale = 1.3f,
        )

        // One long label drops every label: icon-only, never a half-word ("Dashboa").
        composeTestRule.onAllNodesWithText("Dashboard").assertCountEquals(0)
        OrganizerNavItems.forEach { item ->
            val node = composeTestRule.onNodeWithContentDescription(item.label)
            node.assertIsDisplayed()
            assertEquals(
                Role.Tab,
                node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role),
            )
            node.assertHeightIsAtLeast(48.dp)
        }

        composeTestRule.onNodeWithContentDescription("Reports").performClick()
        composeTestRule.waitForIdle()

        assertEquals("reports", selected)

        // The selected item stays clickable too: icon-only must not turn a tab into a label.
        composeTestRule.onNodeWithContentDescription("Dashboard").performClick()
        composeTestRule.waitForIdle()

        assertEquals("dashboard", selected)
    }

    @Test
    fun unboundedWidth_hidesLabels() {
        // A horizontally scrolling parent gives an infinite max width: every slot is "infinite",
        // so the measured guard must fall back to the icon-only state instead of showing labels.
        composeTestRule.setContent {
            EventQrTheme {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    EventQrBottomNavBar(
                        items = OrganizerNavItems,
                        selectedId = "dashboard",
                        onItemSelected = {},
                    )
                }
            }
        }

        composeTestRule.onAllNodesWithText("Dashboard").assertCountEquals(0)
        OrganizerNavItems.forEach { item ->
            composeTestRule.onNodeWithContentDescription(item.label).assertExists()
        }
    }

    @Test
    fun fontScaleTwo_hidesLabels_andKeepsThemAsContentDescriptions() {
        setNavBar(items = OrganizerNavItems, selectedId = "dashboard", fontScale = 2f)

        OrganizerNavItems.forEach { item ->
            composeTestRule.onNodeWithContentDescription(item.label).assertExists()
        }
        composeTestRule.onAllNodesWithText("Dashboard").assertCountEquals(0)
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
        fontScale: Float = 1f,
    ) {
        composeTestRule.setContent {
            EventQrTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                ) {
                    EventQrBottomNavBar(
                        items = items,
                        selectedId = selectedId,
                        onItemSelected = onItemSelected,
                    )
                }
            }
        }
    }
}
