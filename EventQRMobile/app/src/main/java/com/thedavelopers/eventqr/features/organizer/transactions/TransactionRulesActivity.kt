package com.thedavelopers.eventqr.features.organizer.transactions

import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.dimenPx
import com.thedavelopers.eventqr.ui.theme.dimenFloat
import com.thedavelopers.eventqr.features.organizer.styledText
import com.thedavelopers.eventqr.features.organizer.cardRes
import com.thedavelopers.eventqr.features.organizer.spacerRes
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.organizer.events.DiscardChangesDialog
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerTransactionRuleDto
import com.thedavelopers.eventqr.features.organizer.model.dto.TransactionRuleRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

open class TransactionRulesActivity : AppCompatActivity() {
    private val TAG = "TransactionRulesActivity"
    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var content: LinearLayout

    private var currentRule: OrganizerTransactionRuleDto? = null
    private var purposes: List<OrganizerMvpScanPurpose> = emptyList()
    private var rules: List<OrganizerTransactionRuleDto> = emptyList()
    private var selectedPurposeId: String? = null
    /** Save outcome as (message, success). Rendered above the Save button, never a Toast. */
    private var saveStatus: Pair<String, Boolean>? = null
    private var saveStatusView: android.widget.TextView? = null
    /**
     * The form as the user holds it, and the same rule as loaded: dirty is `draft != baselineRule`.
     * Not named `baseline`: inside a `View.apply { }` block that identifier resolves to
     * `View.getBaseline()` (an Int) and would shadow this one silently.
     */
    private var draft: RuleDraft? = null
    private var baselineRule: RuleDraft? = null
    /** A save owns the screen until it answers: no purpose switch, no back (§6.4). */
    private var saving = false
    /** The live confirm, held so a rotation cannot leak its window (§5.12). */
    private var discardDialog: AlertDialog? = null
    /** The form as the process left it; consumed by the next [renderUI]. */
    private var restoredDraft: RuleDraft? = null
    /** The purpose [restoredDraft] belongs to, so a reload that no longer lists it drops the draft. */
    private var restoredPurposeId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreDraft(savedInstanceState)
        repository = OrganizerRepository(this)
        // Registered before the event guard so every exit from this screen runs one rule.
        onBackPressedDispatcher.addCallback(this, backCallback)
        val eventId = intentEventId() ?: return showMissingEventScreen(getString(R.string.transaction_rules_title))
        showEventsLoadingScreen(getString(R.string.transaction_rules_title))
        lifecycleScope.launch {
            val eventsLoad = repository.loadApprovedOrganizerEvents()
            if (eventsLoad.source == OrganizerMvpDataSource.ERROR) {
                showEventsLoadErrorScreen(getString(R.string.transaction_rules_title), eventsLoad.message) { recreate() }
                return@launch
            }
            selectedEvent = resolveSelectedEvent(eventsLoad.data, eventId)
                ?: run {
                    showMissingEventScreen(getString(R.string.transaction_rules_title))
                    return@launch
                }

            Log.d(TAG, "TransactionRulesActivity started for eventId: $eventId")
            content = organizerShell(getString(R.string.transaction_rules_title), showBack = true)
            loadData()
        }
    }

    override fun onStop() {
        dismissSelectField()
        super.onStop()
    }

    override fun onDestroy() {
        dismissSelectField()
        // A confirm still up when the screen goes would leak its window.
        discardDialog?.dismiss()
        discardDialog = null
        super.onDestroy()
    }

    /**
     * The draft and the selected purpose ride the saved state only - never disk, never the server. A
     * rotation or a killed process comes back to the same form, and the next load supplies the
     * baseline the restored draft is judged against.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val form = draft ?: return
        outState.putBoolean(KEY_ALLOW_DUPLICATE, form.allowDuplicate)
        outState.putBoolean(KEY_REQUIRES_STAFF, form.requiresStaff)
        outState.putInt(KEY_COOLDOWN_MINUTES, form.cooldownMinutes)
        outState.putInt(KEY_MAX_SCANS, form.maxScans)
        outState.putString(KEY_PURPOSE_ID, selectedPurposeId)
    }

    private fun restoreDraft(state: Bundle?) {
        if (state == null || !state.containsKey(KEY_COOLDOWN_MINUTES)) return
        restoredDraft = RuleDraft(
            allowDuplicate = state.getBoolean(KEY_ALLOW_DUPLICATE),
            requiresStaff = state.getBoolean(KEY_REQUIRES_STAFF),
            cooldownMinutes = state.getInt(KEY_COOLDOWN_MINUTES),
            maxScans = state.getInt(KEY_MAX_SCANS),
        )
        // Dropped by applyLoadedData if the reload no longer offers this purpose.
        state.getString(KEY_PURPOSE_ID)?.let {
            selectedPurposeId = it
            restoredPurposeId = it
        }
    }

    private fun loadData() {
        // A save reload rebuilds the tree; an open menu would outlive its anchor.
        dismissSelectField()
        // The form is about to be replaced: a just-saved draft must not make back prompt.
        forgetDraft()
        content.removeAllViews()
        content.addView(loadingState(getString(R.string.transaction_rules_loading)))

        lifecycleScope.launch {
            val purposesLoad = repository.loadScanPurposesForMvp(selectedEvent.id)
            val rulesLoad = repository.loadTransactionRulesForMvp(selectedEvent.id)
            // A failed load is not "no data": rendering defaults here would let Save overwrite the real rule
            // (points reset to 0, duplicate/limit settings lost), so show the error with Retry and no form.
            transactionRulesLoadError(purposesLoad, rulesLoad)?.let { message ->
                renderLoadError(message.ifBlank { getString(R.string.transaction_rules_load_failed) })
                return@launch
            }
            // Every saved purpose can have its own rule; only persisted purposes (with an id) can be edited.
            applyLoadedData(purposesLoad.data, rulesLoad.data)
        }
    }

    /**
     * Renders the form from an already-resolved loads pair. Kept apart from [loadData] so a test can
     * drive the screen without the network; it also owns the default purpose (ENTRY, else the first).
     */
    private fun applyLoadedData(loadedPurposes: List<OrganizerMvpScanPurpose>, loadedRules: List<OrganizerTransactionRuleDto>) {
        purposes = loadedPurposes.filter { !it.id.isNullOrBlank() }
        rules = loadedRules
        Log.d(TAG, "Loaded ${rules.size} transaction rules for ${purposes.size} purposes")

        if (selectedPurposeId == null || purposes.none { it.id == selectedPurposeId }) {
            selectedPurposeId = (purposes.firstOrNull { it.code == ScanPurposeCode.ENTRY } ?: purposes.firstOrNull())?.id
        }
        // The restored draft belongs to the purpose it was typed under: if that purpose is gone, the
        // edits are not this rule's and must not render as (or save onto) the fallback one.
        val restoredFor = restoredPurposeId
        if (restoredFor != null && selectedPurposeId != restoredFor) restoredDraft = null
        restoredPurposeId = null
        renderUI()
    }

    /** Test seam: resolves the event without the events fetch, then renders the given loads. */
    internal fun buildScreen(
        event: OrganizerMvpEvent,
        loadedPurposes: List<OrganizerMvpScanPurpose>,
        loadedRules: List<OrganizerTransactionRuleDto>,
    ) {
        selectedEvent = event
        content = organizerShell(getString(R.string.transaction_rules_title), showBack = true)
        applyLoadedData(loadedPurposes, loadedRules)
    }

    /** Internal for the Robolectric error-state test: the load path itself needs the network. */
    internal fun renderLoadError(message: String) {
        currentRule = null
        // No form, nothing to lose: a stale draft here would make back ask over an error state.
        forgetDraft()
        dismissSelectField()
        content.removeAllViews()
        content.addView(errorState(message) { loadData() })
        content.addView(spacerRes(R.dimen.space_20))
        content.addView(disabledSaveButton())
    }

    private fun disabledSaveButton() = primaryButton(getString(R.string.transaction_rules_save)) { }.apply {
        id = R.id.txr_save_button
        isEnabled = false
        alpha = dimenFloat(R.dimen.alpha_disabled)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimenPx(R.dimen.control_height_54))
    }

    private fun renderUI() {
        // `onSelect` dismisses first, but a render can also start from a reload; close any live menu
        // before the anchor leaves the tree.
        dismissSelectField()
        content.removeAllViews()

        val purposeId = selectedPurposeId
        if (purposes.isEmpty() || purposeId == null) {
            forgetDraft()
            content.addView(emptyState(
                iconRes = AppIcons.Report,
                title = getString(R.string.transaction_rules_no_purposes_title),
                subtext = getString(R.string.transaction_rules_no_purposes_message),
            ))
            content.addView(disabledSaveButton())
            return
        }

        currentRule = rules.find { it.scanPurposeId.toString() == purposeId }
        val rule = currentRule ?: OrganizerTransactionRuleDto(
            eventId = UUID.fromString(selectedEvent.id),
            scanPurposeId = UUID.fromString(purposeId),
        )

        // The rule as loaded is the baseline; the draft starts clean and is compared against it on
        // every switch and back (§6.4). State is held here, not read back from view child indices.
        val loaded = RuleDraft(
            allowDuplicate = rule.allowDuplicate,
            requiresStaff = rule.requiresStaffAssignment,
            cooldownMinutes = rule.duplicateWindowMinutes,
            maxScans = rule.maxUsesPerRegistration,
        )
        baselineRule = loaded
        // A draft the process saved comes back over the freshly loaded rule, so an edit survives
        // rotation and still reads dirty against what the server holds.
        val restored = restoredDraft
        restoredDraft = null
        val form = restored ?: loaded
        draft = form

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(container)

        // Purpose selector: one rule per scan purpose. An exclusive single choice is a select field,
        // never a wrapping chip row (§5.6); the selected purpose's rule stays in memory, so switching
        // re-renders the form without a network reload. Unsaved edits make the switch a discard, so
        // it asks first - the same confirm as back.
        container.addView(cardRes(R.dimen.space_lg).apply {
            addView(selectField(
                fieldId = R.id.txr_purpose_field,
                label = getString(R.string.transaction_rules_purpose_label),
                value = currentPurposeLabel(),
                options = purposes.map { it.label },
                selectedIndex = purposes.indexOfFirst { it.id == purposeId },
            ) { index ->
                val purpose = purposes.getOrNull(index) ?: return@selectField
                val targetId = purpose.id ?: return@selectField
                // A save in flight owns the screen; a switch would reload a rule being written.
                if (saving || targetId == selectedPurposeId) return@selectField
                if (!hasUnsavedEdits()) return@selectField renderPurpose(targetId)
                showDiscardConfirm(
                    body = getString(
                        R.string.transaction_rules_discard_switch_body,
                        currentPurposeLabel(),
                        purpose.label,
                    ),
                ) { switchToPurpose(targetId, purpose.label) }
            })
        })

        // Card 1: Duplicate Prevention
        val card1 = cardRes(R.dimen.space_lg).apply {
            addView(styledText(getString(R.string.transaction_rules_duplicate_prevention), R.dimen.type_subtitle, true).apply { setPadding(0, 0, 0, dimenPx(R.dimen.space_sm)) })
        }

        val allowDuplicateToggle = ruleToggle(
            getString(R.string.transaction_rules_allow_duplicate_title),
            getString(R.string.transaction_rules_allow_duplicate_desc),
            form.allowDuplicate
        ) { draft = draft?.copy(allowDuplicate = it) }
        card1.addView(allowDuplicateToggle)
        card1.addView(divider())

        val requiresStaffToggle = ruleToggle(
            getString(R.string.transaction_rules_require_staff_title),
            getString(R.string.transaction_rules_require_staff_desc),
            form.requiresStaff
        ) { draft = draft?.copy(requiresStaff = it) }
        card1.addView(requiresStaffToggle)
        card1.addView(divider())

        container.addView(card1)

        // Card 2: Scan Limits
        val card2 = cardRes(R.dimen.space_lg).apply {
            addView(styledText(getString(R.string.transaction_rules_scan_limits), R.dimen.type_subtitle, true).apply { setPadding(0, 0, 0, dimenPx(R.dimen.space_sm)) })
        }

        val cooldownInput = labeledInput(
            getString(R.string.transaction_rules_cooldown_label),
            form.cooldownMinutes.toString(),
            hint = "60",
            inputType = InputType.TYPE_CLASS_NUMBER
        ) { draft = draft?.copy(cooldownMinutes = it.toIntOrNull() ?: 0) }
        card2.addView(cooldownInput)
        card2.addView(styledText(getString(R.string.transaction_rules_cooldown_helper), R.dimen.type_caption, false, orgColors.muted).apply {
            setPadding(0, dimenPx(R.dimen.space_6), 0, dimenPx(R.dimen.space_md))
        })

        val maxScansInput = labeledInput(
            getString(R.string.transaction_rules_max_scans_label),
            form.maxScans.toString(),
            hint = "10",
            inputType = InputType.TYPE_CLASS_NUMBER
        ) { draft = draft?.copy(maxScans = it.toIntOrNull() ?: 1) }
        card2.addView(maxScansInput)
        card2.addView(styledText(getString(R.string.transaction_rules_max_scans_helper), R.dimen.type_caption, false, orgColors.muted).apply {
            setPadding(0, dimenPx(R.dimen.space_6), 0, 0)
        })

        container.addView(card2)

        content.addView(spacerRes(R.dimen.space_20))

        saveStatusView = styledText(
            saveStatus?.first.orEmpty(),
            R.dimen.ui_type_body,
            false,
            color(if (saveStatus?.second == true) R.color.ui_success else R.color.ui_error),
        ).apply {
            id = R.id.txr_save_status
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = if (saveStatus == null) android.view.View.GONE else android.view.View.VISIBLE
            setPadding(0, 0, 0, dimenPx(R.dimen.space_sm))
        }
        content.addView(saveStatusView)

        content.addView(primaryButton(getString(R.string.transaction_rules_save)) {
            draft?.let { saveRules(it) }
        }.apply {
            id = R.id.txr_save_button
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimenPx(R.dimen.ui_button_height))
        })
    }

    /** The purpose's label, which is also the select field's value. */
    private fun currentPurposeLabel(): String =
        purposes.firstOrNull { it.id == selectedPurposeId }?.label ?: purposes.firstOrNull()?.label.orEmpty()

    /** Repaints for another purpose from the rules already in memory: the one place that switches. */
    private fun renderPurpose(purposeId: String) {
        selectedPurposeId = purposeId
        saveStatus = null
        renderUI()
    }

    /**
     * The switch after a confirmed discard. The field is looked up on the activity, never on the card
     * receiver the confirm was built from: the re-render detaches that card, so a lookup there would
     * focus a dead view and announce from nowhere.
     */
    private fun switchToPurpose(purposeId: String, label: String) {
        renderPurpose(purposeId)
        findViewById<View>(R.id.txr_purpose_field)?.apply {
            requestFocus()
            announceForAccessibility(getString(R.string.transaction_rules_showing_purpose, label))
        }
    }

    /**
     * One confirm at a time, held so a rotation cannot leak its window (§5.12). [onDiscard] runs
     * after the dialog dismisses.
     */
    private fun showDiscardConfirm(body: String, onDiscard: () -> Unit) {
        discardDialog?.dismiss()
        val dialog = DiscardChangesDialog.create(this, onDiscard = onDiscard, body = body)
        dialog.setOnDismissListener { if (discardDialog === dialog) discardDialog = null }
        discardDialog = dialog
        dialog.show()
    }

    /** The form is gone or about to be replaced, so nothing is dirty. */
    private fun forgetDraft() {
        draft = null
        baselineRule = null
    }

    /**
     * No form built yet (loading, error, no purposes) means nothing to lose. Not named `isDirty()`:
     * under a `View.apply { }` block that name resolves against the implicit View receiver instead of
     * this screen, so a dirty check must not share it.
     */
    private fun hasUnsavedEdits(): Boolean {
        val base = baselineRule ?: return false
        val edits = draft ?: return false
        return isRuleDirty(base, edits)
    }

    private fun saveRules(edits: RuleDraft) {
        val purposeId = selectedPurposeId
        if (purposeId == null) {
            showSaveStatus(getString(R.string.transaction_rules_no_purposes_message), success = false)
            return
        }

        val request = buildTransactionRuleRequest(
            purposeId = UUID.fromString(purposeId),
            existing = currentRule,
            allowDuplicate = edits.allowDuplicate,
            requiresStaff = edits.requiresStaff,
            cooldown = edits.cooldownMinutes,
            maxScans = edits.maxScans,
        )

        Log.d(TAG, "Saving rules for event ${selectedEvent.id}")
        saving = true

        lifecycleScope.launch {
            try {
                val result = withContext(NonCancellable) { submitRule(request) }.also { ensureActive() }
                Log.d(TAG, "Save result: ${result.source}")

                if (result.source == OrganizerMvpDataSource.BACKEND) {
                    // Reported through the live view as well, so the outcome survives a reload that
                    // answers late or not at all.
                    showSaveStatus(getString(R.string.transaction_rules_rules_saved_successfully), success = true)
                    // What the server accepted is the new baseline, so a back press during the reload
                    // that follows cannot prompt to discard work that is already saved. The reload
                    // rebuilds both from the saved rule anyway.
                    baselineRule = edits
                    reloadAfterSave()
                } else {
                    // A failed save keeps the edits and re-renders nothing: the form stays dirty.
                    showSaveStatus(getString(R.string.transaction_rules_failed_save, result.message), success = false)
                }
            } finally {
                saving = false
            }
        }
    }

    /**
     * Test seam: [saveRules] reaches the network here, so a test can answer a save (or hold one
     * open) without one - the same shape `ManageScanPurposesActivity.savePurpose` uses.
     */
    internal open suspend fun submitRule(request: TransactionRuleRequest): OrganizerMvpLoad<OrganizerTransactionRuleDto> =
        repository.saveTransactionRuleForMvp(selectedEvent.id, request)

    /**
     * Test seam: the post-save reload is the network path, the same limit [submitRule] removes. A
     * test that answers the save supplies its own re-render.
     */
    internal open fun reloadAfterSave() = loadData()

    /** Inline outcome above Save: no Toast, announced once by the polite live region. */
    private fun showSaveStatus(message: String, success: Boolean) {
        saveStatus = message to success
        saveStatusView?.apply {
            text = message
            setTextColor(color(if (success) R.color.ui_success else R.color.ui_error))
            visibility = android.view.View.VISIBLE
        }
    }

    // ---------------------------------------------------------------- back

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // A save in flight owns the screen: back waits for its answer.
            if (saving) return
            if (!hasUnsavedEdits()) {
                finish()
                return
            }
            showDiscardConfirm(
                body = getString(R.string.transaction_rules_discard_leave_body, currentPurposeLabel()),
            ) { finish() }
        }
    }

    private fun divider() = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimenPx(R.dimen.stroke_hairline)).apply {
            setMargins(0, dimenPx(R.dimen.space_xs), 0, dimenPx(R.dimen.space_xs))
        }
        setBackgroundColor(color(R.color.surface_muted))
    }

    private companion object {
        const val KEY_ALLOW_DUPLICATE = "transaction_rules_draft_allow_duplicate"
        const val KEY_REQUIRES_STAFF = "transaction_rules_draft_requires_staff"
        const val KEY_COOLDOWN_MINUTES = "transaction_rules_draft_cooldown_minutes"
        const val KEY_MAX_SCANS = "transaction_rules_draft_max_scans"
        const val KEY_PURPOSE_ID = "transaction_rules_draft_purpose_id"
    }
}

/**
 * The form's state, compared as a value: typing a number and reverting it leaves the form clean,
 * so no confirm is owed for an edit that no longer exists.
 */
internal data class RuleDraft(
    val allowDuplicate: Boolean,
    val requiresStaff: Boolean,
    val cooldownMinutes: Int,
    val maxScans: Int,
)

/** §6.4: the form is dirty when the draft differs from the rule it was loaded from. */
internal fun isRuleDirty(baseline: RuleDraft, draft: RuleDraft): Boolean = baseline != draft

/**
 * The message to show when either load failed (blank when the server gave none), or null when both
 * came from the backend and the form may be rendered and saved.
 */
internal fun transactionRulesLoadError(vararg loads: OrganizerMvpLoad<*>): String? =
    loads.firstOrNull { it.source == OrganizerMvpDataSource.ERROR }?.let { it.message.orEmpty() }

/** Keeps the saved rule's `active` and points; a purpose with no rule yet starts active. */
internal fun buildTransactionRuleRequest(
    purposeId: UUID,
    existing: OrganizerTransactionRuleDto?,
    allowDuplicate: Boolean,
    requiresStaff: Boolean,
    cooldown: Int,
    maxScans: Int,
): TransactionRuleRequest = TransactionRuleRequest(
    scanPurposeId = purposeId,
    active = existing?.active ?: true,
    allowDuplicate = allowDuplicate,
    duplicateWindowMinutes = cooldown,
    maxUsesPerRegistration = maxScans,
    requiresStaffAssignment = requiresStaff,
    pointsAwarded = existing?.pointsAwarded ?: 0,
)
