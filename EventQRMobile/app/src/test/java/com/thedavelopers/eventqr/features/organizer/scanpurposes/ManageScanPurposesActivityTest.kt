package com.thedavelopers.eventqr.features.organizer.scanpurposes

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpScanPurpose
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerScanPurposeRequestDto
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * The purpose dialog has no duplicate or tracking checkboxes: duplicate prevention lives on the
 * Transaction Rules screen, and Points = 0 is the one way to make a purpose tracking-only.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class ManageScanPurposesActivityTest {

    private fun purpose(
        label: String = "Booth visit",
        points: Int = 0,
        trackingOnly: Boolean = points == 0,
        allowDuplicate: Boolean = false,
    ) = OrganizerMvpScanPurpose(
        label = label,
        description = "Track booth visits",
        enabled = true,
        duplicateRule = if (allowDuplicate) "Duplicates allowed" else "Reject invalid duplicate scans",
        trackingOnly = trackingOnly,
        pointsEnabled = points > 0,
        pointsValue = points,
        requiredSelectionLabel = "Booth",
        id = UUID.randomUUID().toString(),
        code = ScanPurposeCode.BOOTH_VISIT,
        allowDuplicate = allowDuplicate,
    )

    /** No event id in the intent: onCreate stops at the missing-event state, so no network is touched. */
    private fun screen(): ManageScanPurposesActivity =
        Robolectric.buildActivity(ManageScanPurposesActivity::class.java)
            .create().start().resume().visible().get()

    private fun openDialog(
        activity: ManageScanPurposesActivity,
        purpose: OrganizerMvpScanPurpose?,
    ): Dialog {
        activity.showAddEditDialog(purpose)
        return requireNotNull(ShadowDialog.getLatestDialog()) { "dialog did not open" }
    }

    private fun View.checkBoxes(): List<CheckBox> {
        val found = mutableListOf<CheckBox>()
        if (this is CheckBox) found.add(this)
        if (this is ViewGroup) {
            for (i in 0 until childCount) found.addAll(getChildAt(i).checkBoxes())
        }
        return found
    }

    private fun View.allTexts(): List<String> {
        val found = mutableListOf<String>()
        if (this is TextView) found.add(text.toString())
        if (this is ViewGroup) {
            for (i in 0 until childCount) found.addAll(getChildAt(i).allTexts())
        }
        return found
    }

    private fun Dialog.content(): View = requireNotNull(window).decorView

    @Test
    fun createDialogHasNoDuplicateOrTrackingCheckbox() {
        val activity = screen()
        val dialog = openDialog(activity, null)
        val texts = dialog.content().allTexts()

        assertTrue("no checkbox of any kind", dialog.content().checkBoxes().isEmpty())
        assertFalse(texts.any { it.contains("duplicate", ignoreCase = true) })
        assertFalse(texts.any { it.contains("Tracking only", ignoreCase = true) })
        // The rest of the form is untouched.
        assertNotNull(dialog.findViewById<View>(R.id.msp_type_spinner))
        assertNotNull(dialog.findViewById<View>(R.id.msp_name_input))
        assertNotNull(dialog.findViewById<View>(R.id.msp_desc_input))
        assertEquals(
            activity.getString(R.string.manage_scan_purposes_save),
            dialog.findViewById<TextView>(R.id.dialogPositive).text.toString(),
        )
    }

    @Test
    fun pointsIsEditableAndStartsAtZeroForANewPurpose() {
        val points = openDialog(screen(), null).findViewById<EditText>(R.id.msp_points_input)

        assertTrue("points is always editable now", points.isEnabled)
        assertEquals("0", points.text.toString())
    }

    @Test
    fun editDialogForATrackingOnlyPurposeShowsZeroEditablePoints() {
        val activity = screen()
        val dialog = openDialog(activity, purpose(points = 0, trackingOnly = true))
        val points = dialog.findViewById<EditText>(R.id.msp_points_input)

        assertTrue(dialog.content().checkBoxes().isEmpty())
        assertTrue(points.isEnabled)
        assertEquals("0", points.text.toString())
        // Delete still lives in the edit dialog.
        assertTrue(dialog.content().allTexts().contains(activity.getString(R.string.admin_account_management_delete)))
    }

    @Test
    fun editDialogForAPointsPurposeShowsItsPoints() {
        val points = openDialog(screen(), purpose(points = 25)).findViewById<EditText>(R.id.msp_points_input)

        assertTrue(points.isEnabled)
        assertEquals("25", points.text.toString())
    }

    @Test
    fun zeroPointsIsSentAsTrackingOnlyExactlyLikeTheOldCheckedBox() {
        val activity = screen()
        val draft = activity.draftScanPurpose(null, "Booth visit", "", ScanPurposeCode.BOOTH_VISIT, "Booth", 0)
        val request = with(activity) { draft.toOrganizerRequest() }

        assertTrue(draft.trackingOnly)
        assertFalse(draft.pointsEnabled)
        assertEquals(0, draft.pointsValue)
        assertTrue(request.trackingOnly)
        assertFalse(request.pointsEnabled)
        assertEquals(0, request.pointsValue)
    }

    @Test
    fun positivePointsIsNotTrackingOnly() {
        val activity = screen()
        val draft = activity.draftScanPurpose(null, "Booth visit", "", ScanPurposeCode.BOOTH_VISIT, "Booth", 25)
        val request = with(activity) { draft.toOrganizerRequest() }

        assertFalse(draft.trackingOnly)
        assertTrue(draft.pointsEnabled)
        assertEquals(25, draft.pointsValue)
        assertFalse(request.trackingOnly)
        assertTrue(request.pointsEnabled)
        assertEquals(25, request.pointsValue)
    }

    @Test
    fun loweringAPointsPurposeToZeroMakesItTrackingOnly() {
        val activity = screen()
        val existing = purpose(points = 25)
        val draft = activity.draftScanPurpose(existing, existing.label, existing.description, ScanPurposeCode.BOOTH_VISIT, "Booth", 0)
        val request = with(activity) { draft.toOrganizerRequest() }

        assertTrue(draft.trackingOnly)
        assertFalse(draft.pointsEnabled)
        assertEquals(0, draft.pointsValue)
        assertEquals(0, request.pointsValue)
        assertTrue(request.trackingOnly)
        assertFalse(request.pointsEnabled)
    }

    @Test
    fun savingATrackingOnlyPurposeUnchangedKeepsItTrackingOnly() {
        val activity = screen()
        val existing = purpose(points = 0, trackingOnly = true)
        val request = with(activity) {
            activity.draftScanPurpose(existing, existing.label, existing.description, ScanPurposeCode.BOOTH_VISIT, "Booth", 0)
                .toOrganizerRequest()
        }

        assertTrue(request.trackingOnly)
        assertEquals(0, request.pointsValue)
        assertEquals(existing.id, request.scanPurposeId?.toString())
    }

    @Test
    fun editingKeepsDuplicatesAllowedWhenTheRulesScreenAllowedThem() {
        val activity = screen()
        val existing = purpose(points = 10, allowDuplicate = true)
        val request = with(activity) {
            activity.draftScanPurpose(existing, existing.label, existing.description, ScanPurposeCode.BOOTH_VISIT, "Booth", 10)
                .toOrganizerRequest()
        }

        assertTrue("a purpose with duplicates allowed must stay allowed", request.allowDuplicate)
    }

    @Test
    fun editingKeepsDuplicatesBlockedWhenTheRulesScreenBlockedThem() {
        val activity = screen()
        val existing = purpose(points = 10, allowDuplicate = false)
        val request = with(activity) {
            activity.draftScanPurpose(existing, existing.label, existing.description, ScanPurposeCode.BOOTH_VISIT, "Booth", 10)
                .toOrganizerRequest()
        }

        assertFalse(request.allowDuplicate)
    }

    @Test
    fun aNewPurposeUsesTheRulesScreenDefaultOfNoDuplicates() {
        val activity = screen()
        val request = with(activity) {
            activity.draftScanPurpose(null, "Booth visit", "", ScanPurposeCode.BOOTH_VISIT, "Booth", 0)
                .toOrganizerRequest()
        }

        assertFalse("new purposes default to no duplicates", request.allowDuplicate)
    }

    @Test
    fun changingTheScanTypeDoesNotChangeTheStoredDuplicateSetting() {
        val activity = screen()
        val existing = purpose(points = 10, allowDuplicate = true)
        val request = with(activity) {
            activity.draftScanPurpose(existing, existing.label, existing.description, ScanPurposeCode.REWARD_REDEMPTION_SCAN, "Reward", 10)
                .toOrganizerRequest()
        }

        assertEquals(ScanPurposeCode.REWARD_REDEMPTION_SCAN, request.code)
        assertTrue(request.allowDuplicate)
    }

    /** Proves the dialog wiring, not just the mapper: what the user types is what gets saved. */
    @Test
    fun saveButtonSendsTheTypedPointsAndTheDerivedTrackingFlag() {
        val activity = Robolectric.buildActivity(CapturingActivity::class.java)
            .create().start().resume().visible().get()

        fun saveWith(points: String): OrganizerScanPurposeRequestDto {
            activity.saved = null
            activity.showAddEditDialog(null)
            val dialog = requireNotNull(ShadowDialog.getLatestDialog()) { "dialog did not open" }
            dialog.findViewById<EditText>(R.id.msp_name_input).setText("Booth visit")
            dialog.findViewById<EditText>(R.id.msp_points_input).setText(points)
            dialog.findViewById<View>(R.id.dialogPositive).performClick()
            val draft = requireNotNull(activity.saved) { "Save did not reach savePurpose" }
            return with(activity) { draft.toOrganizerRequest() }
        }

        val tracked = saveWith("0")
        assertEquals(0, tracked.pointsValue)
        assertTrue(tracked.trackingOnly)
        assertFalse(tracked.pointsEnabled)

        val scored = saveWith("7")
        assertEquals(7, scored.pointsValue)
        assertFalse(scored.trackingOnly)
        assertTrue(scored.pointsEnabled)
    }

    /** Test seam: captures what the dialog hands to the save path instead of calling the API. */
    private class CapturingActivity : ManageScanPurposesActivity() {
        var saved: OrganizerMvpScanPurpose? = null

        override fun savePurpose(purpose: OrganizerMvpScanPurpose) {
            saved = purpose
        }
    }
}
