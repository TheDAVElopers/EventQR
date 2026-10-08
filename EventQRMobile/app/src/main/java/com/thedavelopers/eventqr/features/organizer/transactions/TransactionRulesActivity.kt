package com.thedavelopers.eventqr.features.organizer.transactions

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerTransactionRuleDto
import com.thedavelopers.eventqr.features.organizer.model.dto.TransactionRuleRequest
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        val eventId = intentEventId() ?: return showMissingEventScreen("Transaction Rules")
        lifecycleScope.launch {
            selectedEvent = resolveSelectedEvent(repository.getApprovedOrganizerEvents(), eventId)
                ?: run {
                    showMissingEventScreen("Transaction Rules")
                    return@launch
                }

            Log.d(TAG, "TransactionRulesActivity started for eventId: $eventId")
            content = organizerShell("Transaction Rules", showBack = true)
            loadData()
        }
    }

    private fun loadData() {
        content.removeAllViews()
        content.addView(loadingState("Loading transaction rules..."))

        MainScope().launch {
            // Every saved purpose can have its own rule; only persisted purposes (with an id) can be edited.
            purposes = repository.loadScanPurposesForMvp(selectedEvent.id).data.filter { !it.id.isNullOrBlank() }
            rules = repository.loadTransactionRulesForMvp(selectedEvent.id).data
            Log.d(TAG, "Loaded ${rules.size} transaction rules for ${purposes.size} purposes")

            if (selectedPurposeId == null || purposes.none { it.id == selectedPurposeId }) {
                selectedPurposeId = (purposes.firstOrNull { it.code == ScanPurposeCode.ENTRY } ?: purposes.firstOrNull())?.id
            }
            renderUI()
        }
    }

    private fun renderUI() {
        content.removeAllViews()

        val purposeId = selectedPurposeId
        if (purposes.isEmpty() || purposeId == null) {
            content.addView(emptyState(
                iconRes = R.drawable.ic_organizer_reports,
                title = getString(R.string.transaction_rules_no_purposes_title),
                subtext = getString(R.string.transaction_rules_no_purposes_message),
            ))
            content.addView(primaryButton(getString(R.string.transaction_rules_save)) { }.apply {
                id = R.id.txr_save_button
                isEnabled = false
                alpha = 0.5f
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
            })
            return
        }

        currentRule = rules.find { it.scanPurposeId.toString() == purposeId }
        val rule = currentRule ?: OrganizerTransactionRuleDto(
            eventId = UUID.fromString(selectedEvent.id),
            scanPurposeId = UUID.fromString(purposeId),
        )

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(container)

        // Purpose selector: one rule per scan purpose.
        container.addView(card(16).apply {
            addView(text(getString(R.string.transaction_rules_purpose_label), 14, true).apply { setPadding(0, 0, 0, dp(8)) })
            addView(android.widget.HorizontalScrollView(this@TransactionRulesActivity).apply {
                isHorizontalScrollBarEnabled = false
                addView(row().apply {
                    purposes.forEach { purpose ->
                        addView(chip(purpose.label, purpose.id == purposeId).apply {
                            setOnClickListener {
                                selectedPurposeId = purpose.id
                                renderUI()
                            }
                        })
                    }
                })
            })
        })

        // Card 1: Duplicate Prevention
        val card1 = card(16).apply {
            addView(text("Duplicate Prevention", 17, true).apply { setPadding(0, 0, 0, dp(8)) })
        }

        val allowDuplicateToggle = ruleToggle(
            getString(R.string.transaction_rules_allow_duplicate_title),
            getString(R.string.transaction_rules_allow_duplicate_desc),
            rule.allowDuplicate
        ) { }
        card1.addView(allowDuplicateToggle)
        card1.addView(divider())

        val requiresStaffToggle = ruleToggle(
            getString(R.string.transaction_rules_require_staff_title),
            getString(R.string.transaction_rules_require_staff_desc),
            rule.requiresStaffAssignment
        ) { }
        card1.addView(requiresStaffToggle)
        card1.addView(divider())

        container.addView(card1)

        // Card 2: Scan Limits
        val card2 = card(16).apply {
            addView(text("Scan Limits", 17, true).apply { setPadding(0, 0, 0, dp(8)) })
        }

        val cooldownInput = labeledInput(
            "Duplicate Cooldown (minutes)",
            rule.duplicateWindowMinutes.toString(),
            hint = "60",
            inputType = InputType.TYPE_CLASS_NUMBER
        ) { }
        card2.addView(cooldownInput)

        val maxScansInput = labeledInput(
            getString(R.string.transaction_rules_max_scans_label),
            rule.maxUsesPerRegistration.toString(),
            hint = "10",
            inputType = InputType.TYPE_CLASS_NUMBER
        ) { }
        card2.addView(maxScansInput)
        card2.addView(text(getString(R.string.transaction_rules_max_scans_helper), 12, false, MUTED).apply {
            setPadding(0, dp(6), 0, 0)
        })

        container.addView(card2)

        content.addView(spacer(20))

        content.addView(primaryButton(getString(R.string.transaction_rules_save)) {
            val allowDuplicate = (allowDuplicateToggle.getChildAt(0) as LinearLayout).let {
                (it.getChildAt(1) as androidx.appcompat.widget.SwitchCompat).isChecked
            }
            val requiresStaff = (requiresStaffToggle.getChildAt(0) as LinearLayout).let {
                (it.getChildAt(1) as androidx.appcompat.widget.SwitchCompat).isChecked
            }
            val cooldown = (cooldownInput.getChildAt(1) as android.widget.EditText).text.toString().toIntOrNull() ?: 0
            val maxScans = (maxScansInput.getChildAt(1) as android.widget.EditText).text.toString().toIntOrNull() ?: 1

            saveRules(allowDuplicate, requiresStaff, cooldown, maxScans)
        }.apply {
            id = R.id.txr_save_button
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
        })
    }

    private fun saveRules(allowDuplicate: Boolean, requiresStaff: Boolean, cooldown: Int, maxScans: Int) {
        val purposeId = selectedPurposeId
        if (purposeId == null) {
            Toast.makeText(this, getString(R.string.transaction_rules_no_purposes_message), Toast.LENGTH_LONG).show()
            return
        }

        val request = buildTransactionRuleRequest(
            purposeId = UUID.fromString(purposeId),
            existing = currentRule,
            allowDuplicate = allowDuplicate,
            requiresStaff = requiresStaff,
            cooldown = cooldown,
            maxScans = maxScans,
        )

        Log.d(TAG, "Saving rules for event ${selectedEvent.id}: $request")

        MainScope().launch {
            val result = repository.saveTransactionRuleForMvp(selectedEvent.id, request)
            Log.d(TAG, "Save result: ${result.source}, message: ${result.message}")

            if (result.source == OrganizerMvpDataSource.BACKEND) {
                Toast.makeText(this@TransactionRulesActivity, this@TransactionRulesActivity.getString(R.string.transaction_rules_rules_saved_successfully), Toast.LENGTH_SHORT).show()
                loadData()
            } else {
                Toast.makeText(this@TransactionRulesActivity, "Failed to save: ${result.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun divider() = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            setMargins(0, dp(4), 0, dp(4))
        }
        setBackgroundColor(Color.parseColor("#F3F4F6"))
    }
}

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
