package com.thedavelopers.eventqr.ui.icons

import com.thedavelopers.eventqr.ui.components.AdminNavItems
import com.thedavelopers.eventqr.ui.components.AttendeeNavItems
import com.thedavelopers.eventqr.ui.components.OrganizerNavItems
import com.thedavelopers.eventqr.ui.components.StaffNavItems
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * One concept, one icon (`MyFiles/design/05-icon-system.md` §3, §7.6).
 *
 * The `icons` token category checks the drawables; this checks the Kotlin address of them, which is
 * what a screenshot cannot prove: two concepts that render the same picture, or a nav bar that
 * binds the same drawable twice.
 */
class AppIconsTest {

    private val concepts: List<Pair<String, Int>> = AppIcons::class.java.declaredFields
        .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
        .filter { it.name.isNotEmpty() && it.name[0].isUpperCase() }
        .onEach { it.isAccessible = true }
        .map { it.name to it.getInt(null) }

    @Test
    fun everyConceptHasItsOwnDrawable() {
        val duplicates = concepts.groupBy { it.second }.filterValues { it.size > 1 }
        assertEquals("two concepts share one drawable: $duplicates", emptyMap<Int, List<Pair<String, Int>>>(), duplicates)
    }

    @Test
    fun theFamilyIsAddressedOnlyThroughAppIcons() {
        assertEquals("AppIcons exposes 42 concepts", 42, concepts.size)
    }

    @Test
    fun adminRequestsAndLogsAreTwoDifferentPictures() {
        val requests = AdminNavItems.single { it.id == "requests" }.icon
        val logs = AdminNavItems.single { it.id == "logs" }.icon
        assertEquals(AppIcons.Request, requests)
        assertEquals(AppIcons.Logs, logs)
        assertNotEquals("Requests and Logs must not share a shape", requests, logs)
    }

    @Test
    fun everyNavBarBindsDistinctIcons() {
        listOf(
            "attendee" to AttendeeNavItems,
            "staff" to StaffNavItems,
            "admin" to AdminNavItems,
            "organizer" to OrganizerNavItems,
        ).forEach { (role, items) ->
            val icons = items.map { it.icon }
            assertEquals("$role nav binds a drawable twice", icons.size, icons.toSet().size)
        }
    }
}
