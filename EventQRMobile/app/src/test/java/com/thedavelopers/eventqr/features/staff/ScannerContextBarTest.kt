package com.thedavelopers.eventqr.features.staff

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import java.util.UUID

/** §5.13 context field: the purpose must bind, and the no-event state must not offer a dead command. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ScannerContextBarTest {

    @Before
    fun seedStaffSession() {
        SessionManager(ApplicationProvider.getApplicationContext()).apply {
            clearSession()
            saveRole(AccountRole.STAFF)
        }
    }

    private fun event() = EventSpinnerOption(id = "e1", label = "DevFest Manila 2026", canScan = true)

    private fun entryPurpose() = ScanPurposeResponse(
        scanPurposeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        eventId = UUID.fromString("00000000-0000-0000-0000-0000000000e1"),
        name = "Entry",
        code = ScanPurposeCode.ENTRY,
        active = true,
        trackingOnly = false,
        description = "Grants entry to the venue.",
    )

    @Test
    fun selectedEventAndPurposeBind_toTitleAndPurposeLine() {
        val activity = Robolectric.buildActivity(ScannerActivity::class.java).create().get()
        activity.showEvents(listOf(event()))
        activity.showPurposes(listOf(entryPurpose()))

        val purposeLabel = activity.getString(R.string.scan_purpose_type_event_entry)
        val purpose = activity.findViewById<TextView>(R.id.scanContextPurpose)
        assertEquals(purposeLabel, purpose.text.toString())
        assertEquals(View.VISIBLE, purpose.visibility)
        val description = activity.findViewById<View>(R.id.scanContext).contentDescription
        assertNotNull(description)
        assertTrue(description.toString().contains("DevFest Manila 2026"))
        assertTrue(description.toString().contains(purposeLabel))
        assertTrue(
            activity.findViewById<View>(R.id.scanPanel).contentDescription.toString()
                .contains("Scanning for DevFest Manila 2026, purpose $purposeLabel"),
        )
    }

    @Test
    fun noActiveEvent_showsEmptyCopy_andDropsCaretAndTouch() {
        val activity = Robolectric.buildActivity(ScannerActivity::class.java).create().get()
        activity.showEvents(emptyList())

        assertEquals(
            activity.getString(R.string.ui_staff_no_active_events),
            activity.findViewById<TextView>(R.id.scanContextEvent).text.toString(),
        )
        assertEquals(View.GONE, activity.findViewById<TextView>(R.id.scanContextPurpose).visibility)
        assertEquals(View.GONE, activity.findViewById<ImageView>(R.id.scanContextCaret).visibility)
        val bar = activity.findViewById<View>(R.id.scanContext)
        assertFalse(bar.isClickable)
        assertFalse(bar.isFocusable)
        assertEquals(
            activity.getString(R.string.ui_scan_panel_description),
            activity.findViewById<View>(R.id.scanPanel).contentDescription.toString(),
        )
    }
}
