package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.dashboard.DashboardActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Item 6: tapping a bottom-nav tab must not finish the current screen, otherwise Back from a
 * non-home tab exits the app instead of walking the tab stack. Only Home is a root and clears
 * the tabs above it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class AttendeeTabNavigationTest {

    private fun start(destination: Class<out AppCompatActivity>): Intent {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().get()
        activity.openAttendeeDestination(destination)

        assertFalse(
            "${destination.simpleName} must leave the caller on the back stack",
            activity.isFinishing,
        )
        return shadowOf(activity).nextStartedActivity
    }

    @Test
    fun nonHomeTab_isPushedWithoutClearingTheStack() {
        val intent = start(AttendeeEventsActivity::class.java)

        assertEquals(AttendeeEventsActivity::class.java.name, intent.component?.className)
        assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
        assertEquals(
            "a tab already on the stack must be reused, not stacked again",
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            intent.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
        )
    }

    /**
     * The flags a round trip uses: Events -> Registered -> Events must surface the existing Events
     * instance (REORDER_TO_FRONT) instead of pushing a duplicate, which would cost Back one extra
     * press per round trip.
     */
    @Test
    fun crossTabPingPong_reusesTheExistingTabInstance() {
        listOf(
            AttendeeEventsActivity::class.java,
            RegisteredEventsActivity::class.java,
            AttendeeEventsActivity::class.java,
        ).forEach { destination ->
            val intent = start(destination)

            assertTrue(
                "${destination.simpleName} must surface the tab that is already on the stack",
                intent.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT != 0,
            )
            assertFalse(
                "${destination.simpleName} must not clear the tabs above it",
                intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0,
            )
        }
    }

    @Test
    fun homeTab_clearsBackToTheRoot() {
        val intent = start(DashboardActivity::class.java)

        assertEquals(DashboardActivity::class.java.name, intent.component?.className)
        assertEquals(
            Intent.FLAG_ACTIVITY_CLEAR_TOP,
            intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
    }
}
