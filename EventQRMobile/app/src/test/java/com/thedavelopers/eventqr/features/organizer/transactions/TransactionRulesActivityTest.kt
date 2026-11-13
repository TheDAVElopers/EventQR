package com.thedavelopers.eventqr.features.organizer.transactions

import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.auth.AuthStripTestKit
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpDataSource
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpEvent
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpLoad
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpScanPurpose
import com.thedavelopers.eventqr.features.organizer.allTexts
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerTransactionRuleDto
import com.thedavelopers.eventqr.features.organizer.model.dto.TransactionRuleRequest
import java.util.UUID
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * The scan-purpose control is a select field (plan §5.2), not the old wrapping chip row: one visible
 * value, an anchored menu, and a selection change that re-renders the in-memory rule with no reload.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class TransactionRulesActivityTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val entryId = UUID.randomUUID().toString()
    private val boothId = UUID.randomUUID().toString()

    private fun purpose(label: String, id: String, code: ScanPurposeCode? = null) = OrganizerMvpScanPurpose(
        label = label,
        description = "",
        enabled = true,
        duplicateRule = "",
        trackingOnly = false,
        pointsEnabled = false,
        pointsValue = 0,
        requiredSelectionLabel = "",
        id = id,
        code = code,
    )

    private fun rule(purposeId: String, cooldown: Int, maxScans: Int) = OrganizerTransactionRuleDto(
        eventId = UUID.randomUUID(),
        scanPurposeId = UUID.fromString(purposeId),
        duplicateWindowMinutes = cooldown,
        maxUsesPerRegistration = maxScans,
    )

    private fun event() = OrganizerMvpEvent(
        id = UUID.randomUUID().toString(), title = "Fest", organizerName = "Org", dateTime = "-",
        shortDate = "-", venue = "Hall", status = "Approved", submittedDate = "-", adminRemarks = "",
        additionalOrganizers = emptyList(), registeredCount = 0, enteredCount = 0, attendedCount = 0,
        exitedCount = 0, noShowCount = 0, totalTransactions = 0, successfulScans = 0, rejectedScans = 0,
        benefitClaims = 0, boothSessionVisits = 0, rewardRedemptions = 0, totalPointsAwarded = 0,
        idTemplateStatus = "-", rewardsStatus = "Enabled", staffCount = 0, scanPurposesCount = 0,
    )

    /** The ENTRY purpose is second in the list: the default must still be ENTRY, not the first. */
    private fun screen(): TransactionRulesActivity {
        // No event id in the intent: onCreate stops at the missing-event state, so no repository call
        // is ever launched and the whole test runs without a network path. `visible()` attaches the
        // decor, which the dismiss-focus guard (`isAttachedToWindow`) needs.
        val activity = Robolectric.buildActivity(TransactionRulesActivity::class.java)
            .create().start().resume().visible().get()
        activity.buildScreen(
            event = event(),
            loadedPurposes = listOf(purpose("Booth", boothId), purpose("Entry", entryId, ScanPurposeCode.ENTRY)),
            loadedRules = listOf(rule(entryId, cooldown = 11, maxScans = 3), rule(boothId, cooldown = 22, maxScans = 7)),
        )
        return activity
    }

    private fun field(activity: TransactionRulesActivity): View =
        activity.findViewById(R.id.txr_purpose_field)

    private fun View.findText(label: String): TextView? {
        if (this is TextView && text.toString() == label) return this
        if (this is ViewGroup) {
            for (i in 0 until childCount) getChildAt(i).findText(label)?.let { return it }
        }
        return null
    }

    /** The row whose own label is [label]; the menu rows are the clickable group holding one text. */
    private fun View.findMenuRow(label: String): View? {
        if (this is ViewGroup) {
            if (isClickable && (getChildAt(0) as? TextView)?.text?.toString() == label) return this
            for (i in 0 until childCount) getChildAt(i).findMenuRow(label)?.let { return it }
        }
        return null
    }

    /** Robolectric never lays out a detached view, and the menu needs the field's real width. */
    private fun openMenu(activity: TransactionRulesActivity): PopupWindow {
        val field = field(activity)
        field.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        field.layout(0, 0, field.measuredWidth, field.measuredHeight)
        field.performClick()
        return requireNotNull(field.getTag(R.id.select_field_menu) as? PopupWindow) { "menu did not open" }
    }

    @Test
    fun fieldShowsTheEntryPurposeAndItsRule() {
        val activity = screen()
        val texts = activity.window.decorView.allTexts()

        assertTrue("the label above the field", texts.contains(activity.getString(R.string.transaction_rules_purpose_label)))
        assertEquals("Entry", field(activity).findText("Entry")?.text?.toString())
        // The rule comes from memory: ENTRY's cooldown is 11, Booth's 22.
        assertTrue(texts.contains("11"))
        assertFalse(texts.contains("22"))
    }

    @Test
    fun labelIsTheFieldsAccessibleNameAndTheFieldIsADropdownList() {
        val activity = screen()
        val label = activity.window.decorView.findText(activity.getString(R.string.transaction_rules_purpose_label))

        assertNotNull(label)
        assertEquals(R.id.txr_purpose_field, label!!.labelFor)
        // The label renders the FieldLabel ramp: 11sp, semibold, 0.16 tracking, text_secondary.
        assertEquals(context.resources.getDimension(R.dimen.ui_type_overline), label.textSize, 0f)
        assertEquals(
            ContextCompat.getColor(context, R.color.ui_text_secondary),
            label.currentTextColor,
        )
        assertEquals("Entry", ViewCompat.getStateDescription(field(activity)))

        val info = AccessibilityNodeInfo.obtain()
        field(activity).onInitializeAccessibilityNodeInfo(info)
        assertEquals(
            activity.getString(R.string.ui_organizer_select_role),
            AccessibilityNodeInfoCompat.wrap(info).roleDescription?.toString(),
        )
        assertEquals("Entry", info.stateDescription?.toString())
        assertTrue(info.actionList.any { it.id == AccessibilityNodeInfo.ACTION_EXPAND })
        assertTrue(info.actionList.any { it.id == AccessibilityNodeInfo.ACTION_COLLAPSE })
    }

    @Test
    fun menuIsAnAnchoredOverlayWithOneButtonRowPerPurposeAndTheCurrentRowSelected() {
        val activity = screen()
        val popup = openMenu(activity)

        assertTrue(popup.isShowing)
        assertTrue("Back closes it", popup.isFocusable)
        assertNotNull(popup.contentView.background)
        val rows = listOf("Booth", "Entry").map { label ->
            requireNotNull(popup.contentView.findMenuRow(label)) { "missing row $label" }
        }
        assertEquals(context.resources.getDimensionPixelSize(R.dimen.ui_touch_min), rows[0].minimumHeight)
        assertFalse(rows[0].isSelected)
        assertTrue(rows[1].isSelected)
        val info = AccessibilityNodeInfo.obtain()
        rows[1].onInitializeAccessibilityNodeInfo(info)
        assertEquals(android.widget.Button::class.java.name, info.className.toString())
        assertTrue(info.isSelected)
    }

    @Test
    fun selectingAnotherPurposeRendersThatPurposeRuleAndDismissesTheMenu() {
        val activity = screen()
        val popup = openMenu(activity)

        popup.contentView.findMenuRow("Booth")!!.performClick()

        assertFalse("the menu must close before the screen re-renders", popup.isShowing)
        val texts = activity.window.decorView.allTexts()
        assertEquals("Booth", field(activity).findText("Booth")?.text?.toString())
        assertTrue("Booth's cooldown", texts.contains("22"))
        assertTrue("Booth's scan limit", texts.contains("7"))
        assertFalse("ENTRY's rule is gone", texts.contains("11"))
    }

    @Test
    fun selectingTheCurrentPurposeChangesNothing() {
        val activity = screen()
        val before = field(activity)
        val popup = openMenu(activity)

        popup.contentView.findMenuRow("Entry")!!.performClick()

        assertFalse(popup.isShowing)
        // No re-render happened: the very same field instance is still on screen.
        assertSame(before, field(activity))
        assertNull(field(activity).getTag(R.id.select_field_menu))
    }

    @Test
    fun dismissingTheMenuKeepsTheSelectionAndReturnsFocus() {
        val activity = screen()
        val before = field(activity)
        val popup = openMenu(activity)
        // A focusable popup is what lets Back dismiss it instead of reaching the activity.
        assertTrue("Back closes the menu", popup.isFocusable)

        // Robolectric attaches no key dispatcher to the popup window, so this exercises the same
        // dismiss contract Back triggers on device.
        popup.dismiss()

        assertFalse(popup.isShowing)
        assertSame("no re-render on dismiss", before, field(activity))
        assertEquals("Entry", field(activity).findText("Entry")?.text?.toString())
        assertTrue("dismiss returns focus to the field", field(activity).isFocused)
        assertNull(field(activity).getTag(R.id.select_field_menu))
        assertNull(activity.window.decorView.getTag(R.id.select_field_menu))
    }

    @Test
    fun stoppingTheScreenClosesTheMenuWithoutReturningFocus() {
        val controller = Robolectric.buildActivity(TransactionRulesActivity::class.java)
            .create().start().resume().visible()
        val activity = controller.get()
        activity.buildScreen(
            event = event(),
            loadedPurposes = listOf(purpose("Booth", boothId), purpose("Entry", entryId, ScanPurposeCode.ENTRY)),
            loadedRules = listOf(rule(entryId, cooldown = 11, maxScans = 3)),
        )
        val field = field(activity)
        field.clearFocus()
        openMenu(activity)
        assertNotNull(activity.window.decorView.getTag(R.id.select_field_menu))

        controller.stop()

        assertNull("a programmatic close clears the activity's popup", activity.window.decorView.getTag(R.id.select_field_menu))
        assertFalse("leaving the screen must not paint a focus ring", field.isFocused)
    }

    @Test
    fun selectionSurvivesAReloadThatStillContainsThePurpose() {
        val activity = screen()
        openMenu(activity).contentView.findMenuRow("Booth")!!.performClick()
        assertEquals("Booth", field(activity).findText("Booth")?.text?.toString())

        // A reduced reload (no ENTRY) that still holds Booth: the selection must not fall back.
        activity.buildScreen(
            event = event(),
            loadedPurposes = listOf(purpose("Booth", boothId)),
            loadedRules = listOf(rule(boothId, cooldown = 22, maxScans = 7)),
        )

        assertEquals("Booth", field(activity).findText("Booth")?.text?.toString())
        assertTrue(activity.window.decorView.allTexts().contains("22"))
    }

    @Test
    fun purposesWithoutAPersistedIdAreNotOffered() {
        val activity = Robolectric.buildActivity(TransactionRulesActivity::class.java).create().get()
        activity.buildScreen(
            event = event(),
            loadedPurposes = listOf(
                purpose("Entry", entryId, ScanPurposeCode.ENTRY),
                purpose("Ghost", ""),
            ),
            loadedRules = emptyList(),
        )

        val popup = openMenu(activity)
        assertNotNull("the persisted purpose is listed", popup.contentView.findMenuRow("Entry"))
        assertNull("a purpose without an id cannot be edited", popup.contentView.findMenuRow("Ghost"))
    }

    @Test
    fun emptyAndErrorStatesRenderNoField() {
        val empty = Robolectric.buildActivity(TransactionRulesActivity::class.java).create().get()
        empty.buildScreen(event(), emptyList(), emptyList())
        assertNull(empty.findViewById<View>(R.id.txr_purpose_field))
        assertNotNull(empty.findViewById<View>(R.id.txr_save_button))

        val failed = Robolectric.buildActivity(TransactionRulesActivity::class.java).create().get()
        failed.buildScreen(event(), listOf(purpose("Entry", entryId)), emptyList())
        failed.renderLoadError("Couldn't load transaction rules")
        assertNull(failed.findViewById<View>(R.id.txr_purpose_field))
        assertNotNull(failed.findViewById<View>(R.id.txr_save_button))
    }

    // ------------------------------------------------------------ unsaved edits (§6.4)

    /** Same fixture, a subclass that answers the save so the guard can be pinned without a network. */
    private fun <T : TransactionRulesActivity> screenOf(clazz: Class<T>): T {
        val activity = Robolectric.buildActivity(clazz).create().start().resume().visible().get()
        activity.buildScreen(
            event = event(),
            loadedPurposes = listOf(purpose("Booth", boothId), purpose("Entry", entryId, ScanPurposeCode.ENTRY)),
            loadedRules = listOf(rule(entryId, cooldown = 11, maxScans = 3), rule(boothId, cooldown = 22, maxScans = 7)),
        )
        return activity
    }

    private fun latestDialog(): AlertDialog? = ShadowDialog.getLatestDialog() as? AlertDialog

    private fun View.findSwitch(): SwitchCompat? {
        if (this is SwitchCompat) return this
        if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findSwitch()?.let { return it }
        return null
    }

    /** The first toggle is `Allow duplicate scans`: flipping it is the cheapest unsaved edit. */
    private fun allowDuplicate(activity: TransactionRulesActivity): SwitchCompat =
        requireNotNull(activity.window.decorView.findSwitch()) { "no rule toggle on screen" }

    private fun body(dialog: AlertDialog): String =
        dialog.findViewById<TextView>(R.id.dialogBody)!!.text.toString()

    @Test
    fun switchingWithNoEditsNeedsNoConfirm() {
        val activity = screen()

        openMenu(activity).contentView.findMenuRow("Booth")!!.performClick()

        assertNull("a clean form switches without asking", latestDialog())
        assertEquals("Booth", field(activity).findText("Booth")?.text?.toString())
    }

    @Test
    fun switchingWithEditsAsksAndKeepEditingKeepsBoth() {
        val activity = screen()
        allowDuplicate(activity).performClick()
        val before = field(activity)

        openMenu(activity).contentView.findMenuRow("Booth")!!.performClick()

        val dialog = requireNotNull(latestDialog()) { "a dirty switch must ask" }
        assertEquals(
            activity.getString(R.string.ui_organizer_edit_discard_title),
            dialog.findViewById<TextView>(R.id.dialogTitle)!!.text.toString(),
        )
        assertEquals(
            activity.getString(R.string.transaction_rules_discard_switch_body, "Entry", "Booth"),
            body(dialog),
        )

        dialog.findViewById<Button>(R.id.dialogNegative)!!.performClick()

        assertFalse(dialog.isShowing)
        assertSame("Keep editing must not re-render", before, field(activity))
        assertEquals("Entry", field(activity).findText("Entry")?.text?.toString())
        assertTrue("the edit the user kept", allowDuplicate(activity).isChecked)
    }

    @Test
    fun discardingSwitchesAndResetsTheFormToTheTargetRule() {
        AuthStripTestKit.enableAccessibility()
        val activity = screen()
        allowDuplicate(activity).performClick()

        openMenu(activity).contentView.findMenuRow("Booth")!!.performClick()
        requireNotNull(latestDialog()).findViewById<Button>(R.id.dialogPositive)!!.performClick()

        val texts = activity.window.decorView.allTexts()
        assertEquals("Booth", field(activity).findText("Booth")?.text?.toString())
        assertTrue("Booth's cooldown", texts.contains("22"))
        assertTrue("Booth's scan limit", texts.contains("7"))
        assertFalse("the discarded toggle is gone", allowDuplicate(activity).isChecked)
        // The re-render replaces the field the organizer was on, so focus and a spoken confirmation
        // have to land on the new one - the card that raised the dialog is detached by now.
        assertTrue("the re-rendered field takes focus", field(activity).isFocused)
        assertEquals(
            listOf(activity.getString(R.string.transaction_rules_showing_purpose, "Booth")),
            AuthStripTestKit.announcements(),
        )
    }

    /** A scrim tap or Back cancels the confirm: same purpose, same edits, no re-render. */
    @Test
    fun cancellingTheConfirmKeepsThePurposeAndTheEdits() {
        val activity = screen()
        allowDuplicate(activity).performClick()
        val before = field(activity)

        openMenu(activity).contentView.findMenuRow("Booth")!!.performClick()
        val dialog = requireNotNull(latestDialog()) { "a dirty switch must ask" }
        // Both the scrim and Back route through `cancel()` on a cancelable AlertDialog.
        dialog.cancel()

        assertFalse(dialog.isShowing)
        assertSame("no re-render happened", before, field(activity))
        assertEquals("Entry", field(activity).findText("Entry")?.text?.toString())
        assertTrue("the edit survived", allowDuplicate(activity).isChecked)

        // Still dirty, so the next attempt asks again - a fresh dialog, not the cancelled one.
        openMenu(activity).contentView.findMenuRow("Booth")!!.performClick()
        val second = requireNotNull(latestDialog())
        assertNotSame(dialog, second)
        assertTrue(second.isShowing)
    }

    /** A successful save becomes the new baseline: clean, so switch and back owe no confirm. */
    @Test
    fun aSuccessfulSaveLeavesTheFormClean() {
        val activity = screenOf(SuccessSaveActivity::class.java)
        allowDuplicate(activity).performClick()

        activity.findViewById<View>(R.id.txr_save_button).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "the save is reported inline",
            View.VISIBLE,
            activity.findViewById<TextView>(R.id.txr_save_status).visibility,
        )

        openMenu(activity).contentView.findMenuRow("Booth")!!.performClick()
        assertNull("a saved form owes no confirm on a switch", latestDialog())
        assertEquals("Booth", field(activity).findText("Booth")?.text?.toString())

        activity.onBackPressedDispatcher.onBackPressed()
        assertNull("nor on back", latestDialog())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun editingThenRevertingLeavesTheFormClean() {
        val activity = screen()
        allowDuplicate(activity).performClick()
        allowDuplicate(activity).performClick()

        openMenu(activity).contentView.findMenuRow("Booth")!!.performClick()

        assertNull("a reverted edit owes no confirm", latestDialog())
        assertEquals("Booth", field(activity).findText("Booth")?.text?.toString())
    }

    @Test
    fun dirtyBackAsksAndOnlyDiscardLeaves() {
        val activity = screen()
        allowDuplicate(activity).performClick()

        activity.onBackPressedDispatcher.onBackPressed()

        val dialog = requireNotNull(latestDialog()) { "a dirty back must ask" }
        assertEquals(
            activity.getString(R.string.transaction_rules_discard_leave_body, "Entry"),
            body(dialog),
        )
        assertFalse(activity.isFinishing)

        dialog.findViewById<Button>(R.id.dialogNegative)!!.performClick()
        assertFalse("Keep editing stays on the screen", activity.isFinishing)

        activity.onBackPressedDispatcher.onBackPressed()
        requireNotNull(latestDialog()).findViewById<Button>(R.id.dialogPositive)!!.performClick()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun cleanBackLeavesWithNoConfirm() {
        val activity = screen()

        activity.onBackPressedDispatcher.onBackPressed()

        assertTrue(activity.isFinishing)
        assertNull(latestDialog())
    }

    @Test
    fun aFailedSaveKeepsTheEditsAndStaysDirty() {
        val activity = screenOf(FailingSaveActivity::class.java)
        allowDuplicate(activity).performClick()

        activity.findViewById<View>(R.id.txr_save_button).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val status = activity.findViewById<TextView>(R.id.txr_save_status)
        assertEquals(View.VISIBLE, status.visibility)
        assertTrue("the message is reported inline", status.text.toString().contains("No connection"))
        assertTrue("a failed save keeps the edit", allowDuplicate(activity).isChecked)

        openMenu(activity).contentView.findMenuRow("Booth")!!.performClick()
        assertNotNull("the form is still unsaved, so the switch must ask", latestDialog())
    }

    @Test
    fun aSaveInFlightIgnoresBackAndThePurposeSwitch() {
        val activity = screenOf(HangingSaveActivity::class.java)
        allowDuplicate(activity).performClick()

        activity.findViewById<View>(R.id.txr_save_button).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("the save is still open", activity.submitting)

        activity.onBackPressedDispatcher.onBackPressed()
        assertFalse("back waits for the save", activity.isFinishing)
        assertNull(latestDialog())

        val before = field(activity)
        openMenu(activity).contentView.findMenuRow("Booth")!!.performClick()
        assertSame("a switch waits for the save", before, field(activity))
        assertNull(latestDialog())
        assertEquals("Entry", field(activity).findText("Entry")?.text?.toString())
    }

    /** Value equality: an edit that was undone is not an unsaved change. */
    @Test
    fun isRuleDirtyComparesTheDraftWithItsBaselineAsAValue() {
        val loaded = RuleDraft(allowDuplicate = false, requiresStaff = false, cooldownMinutes = 11, maxScans = 3)

        assertFalse(isRuleDirty(loaded, loaded.copy()))
        assertTrue(isRuleDirty(loaded, loaded.copy(allowDuplicate = true)))
        assertTrue(isRuleDirty(loaded, loaded.copy(requiresStaff = true)))
        assertTrue(isRuleDirty(loaded, loaded.copy(cooldownMinutes = 12)))
        assertTrue(isRuleDirty(loaded, loaded.copy(maxScans = 4)))
    }

    // ------------------------------------------------------------ rotation / process death

    @Test
    fun anEditSurvivesRecreateAndStillReadsDirty() {
        val controller = Robolectric.buildActivity(TransactionRulesActivity::class.java)
            .create().start().resume().visible()

        fun render() = controller.get().buildScreen(
            event = event(),
            loadedPurposes = listOf(purpose("Booth", boothId), purpose("Entry", entryId, ScanPurposeCode.ENTRY)),
            loadedRules = listOf(rule(entryId, cooldown = 11, maxScans = 3), rule(boothId, cooldown = 22, maxScans = 7)),
        )
        render()
        allowDuplicate(controller.get()).performClick()

        controller.recreate()
        render()

        val recreated = controller.get()
        assertTrue("the edit must survive the recreation", allowDuplicate(recreated).isChecked)

        recreated.onBackPressedDispatcher.onBackPressed()
        assertNotNull("a restored draft that differs from the loaded rule is dirty", latestDialog())
        assertFalse("the confirm keeps the screen up", recreated.isFinishing)
    }

    @Test
    fun aCleanFormRecreatesClean() {
        val controller = Robolectric.buildActivity(TransactionRulesActivity::class.java)
            .create().start().resume().visible()

        fun render() = controller.get().buildScreen(
            event = event(),
            loadedPurposes = listOf(purpose("Entry", entryId, ScanPurposeCode.ENTRY)),
            loadedRules = listOf(rule(entryId, cooldown = 11, maxScans = 3)),
        )
        render()

        controller.recreate()
        render()

        val recreated = controller.get()
        recreated.onBackPressedDispatcher.onBackPressed()
        assertNull("a form with no edits owes no confirm after a recreate", latestDialog())
        assertTrue(recreated.isFinishing)
    }

    @Test
    fun aRestoredDraftIsDroppedWhenItsPurposeIsGone() {
        val controller = Robolectric.buildActivity(TransactionRulesActivity::class.java)
            .create().start().resume().visible()

        // The draft is typed under ENTRY, which is the screen's default selection.
        controller.get().buildScreen(
            event = event(),
            loadedPurposes = listOf(purpose("Entry", entryId, ScanPurposeCode.ENTRY)),
            loadedRules = listOf(rule(entryId, cooldown = 11, maxScans = 3)),
        )
        allowDuplicate(controller.get()).performClick()

        // The reload no longer lists ENTRY, so the screen falls back to BOOTH: the draft is not
        // that purpose's and must not render as (or save onto) it.
        controller.recreate()
        controller.get().buildScreen(
            event = event(),
            loadedPurposes = listOf(purpose("Booth", boothId)),
            loadedRules = listOf(rule(boothId, cooldown = 22, maxScans = 7)),
        )

        val recreated = controller.get()
        assertFalse("another purpose's draft must not carry over", allowDuplicate(recreated).isChecked)
        recreated.onBackPressedDispatcher.onBackPressed()
        assertNull("and it owes no discard confirm", latestDialog())
        assertTrue("a clean fallback form just closes", recreated.isFinishing)
    }

    /** Answers the save with a failure: the network path is the only thing stubbed. */
    private class FailingSaveActivity : TransactionRulesActivity() {
        override suspend fun submitRule(request: TransactionRuleRequest) = OrganizerMvpLoad(
            OrganizerTransactionRuleDto(eventId = UUID.randomUUID(), scanPurposeId = request.scanPurposeId),
            OrganizerMvpDataSource.ERROR,
            "No connection",
        )
    }

    /**
     * Answers the save with success and skips the reload that follows it, which is the same network
     * path: the baseline the screen sets from the answer is what this test pins.
     */
    private class SuccessSaveActivity : TransactionRulesActivity() {
        override suspend fun submitRule(request: TransactionRuleRequest) = OrganizerMvpLoad(
            OrganizerTransactionRuleDto(eventId = UUID.randomUUID(), scanPurposeId = request.scanPurposeId),
            OrganizerMvpDataSource.BACKEND,
            null,
        )

        override fun reloadAfterSave() = Unit
    }

    /** Holds the save open, which is what an unanswered request looks like to the screen. */
    private class HangingSaveActivity : TransactionRulesActivity() {
        var submitting = false

        override suspend fun submitRule(request: TransactionRuleRequest): OrganizerMvpLoad<OrganizerTransactionRuleDto> {
            submitting = true
            return suspendCancellableCoroutine { }
        }
    }
}
