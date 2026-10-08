package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.util.UiStrings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RegistrationErrorsTest {

    private val strings = UiStrings(ApplicationProvider.getApplicationContext())

    @Test
    fun ownEmailForbidden_mapsToFriendlyMessage() {
        assertEquals(
            "Registration must use your own account email.",
            toFriendlyRegistrationError("You can only register using your own account email", strings),
        )
        assertEquals(
            "custom",
            toFriendlyRegistrationError("You can only register using your own account email", strings, "custom"),
        )
    }

    @Test
    fun genericForbidden_stillPermissionMessage() {
        assertEquals("You do not have permission to register for this event.", toFriendlyRegistrationError("Forbidden", strings))
    }

    @Test
    fun existingMappingsUnchanged() {
        assertEquals("You are already registered for this event.", toFriendlyRegistrationError("Duplicate registration", strings))
        assertEquals("Registration failed. Please try again.", toFriendlyRegistrationError("boom", strings))
    }
}
