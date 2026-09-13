package com.thedavelopers.eventqr.features.organizer.transactions

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    private var entryPurposeId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        val eventId = intentEventId() ?: return showMissingEventScreen("Transaction Rules")
        selectedEvent = resolveSelectedEvent(repository.getApprovedOrganizerEvents(), eventId) ?: return showMissingEventScreen("Transaction Rules")
        
        Log.d(TAG, "TransactionRulesActivity started for eventId: $eventId")
        
        content = organizerShell("Transaction Rules", showBack = true)
        loadData()
    }

    private fun loadData() {
        content.removeAllViews()
        content.addView(loadingState("Loading transaction rules..."))
        
        MainScope().launch {
            // 1. Load scan purposes to find ENTRY
            val purposes = repository.loadScanPurposesForMvp(selectedEvent.id)
            val entryPurpose = purposes.data.find { it.code == com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ENTRY }
                ?: purposes.data.firstOrNull()
            
            entryPurposeId = entryPurpose?.id
            
            // 2. Load transaction rules
            val rules = repository.loadTransactionRulesForMvp(selectedEvent.id)
            Log.d(TAG, "Loaded ${rules.data.size} transaction rules. EntryPurposeId: $entryPurposeId")
            
            currentRule = rules.data.find { it.scanPurposeId.toString() == entryPurposeId }
                ?: rules.data.firstOrNull()
            
            renderUI()
        }
    }

    private fun renderUI() {
        content.removeAllViews()
        
        val rule = currentRule ?: OrganizerTransactionRuleDto(
            eventId = UUID.fromString(selectedEvent.id),
            scanPurposeId = UUID.fromString(entryPurposeId ?: UUID.randomUUID().toString())
        )
        
        // Use mutable values to collect final state on save button click
        // To simplify, we'll just read from the UI elements or use a temporary state object.
        // For this implementation, we'll recreate the request from the latest UI values.
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(container)

        // Card 1: Duplicate Prevention
        val card1 = card(16).apply {
            addView(text("Duplicate Prevention", 17, true).apply { setPadding(0, 0, 0, dp(8)) })
        }
        
        val allowDuplicateToggle = ruleToggle(
            "Allow Duplicate Entry Scans",
            "Allow attendee to check in more than once",
            rule.allowDuplicate
        ) { }
        card1.addView(allowDuplicateToggle)
        card1.addView(divider())
        
        val requiresStaffToggle = ruleToggle(
            "Require Verification Before Entry",
            "Staff must verify QR before logging entry",
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
            "Max Scans Per Day (per attendee)",
            rule.maxUsesPerRegistration.toString(),
            hint = "10",
            inputType = InputType.TYPE_CLASS_NUMBER
        ) { }
        card2.addView(maxScansInput)
        
        container.addView(card2)

        content.addView(spacer(20))
        
        content.addView(primaryButton("Save Rules") {
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
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
        })
    }

    private fun saveRules(allowDuplicate: Boolean, requiresStaff: Boolean, cooldown: Int, maxScans: Int) {
        val purposeId = entryPurposeId ?: return
        
        val request = TransactionRuleRequest(
            scanPurposeId = UUID.fromString(purposeId),
            active = true,
            allowDuplicate = allowDuplicate,
            duplicateWindowMinutes = cooldown,
            maxUsesPerRegistration = maxScans,
            requiresStaffAssignment = requiresStaff,
            pointsAwarded = currentRule?.pointsAwarded ?: 0
        )
        
        Log.d(TAG, "Saving rules for event ${selectedEvent.id}: $request")
        
        MainScope().launch {
            val result = repository.saveTransactionRuleForMvp(selectedEvent.id, request)
            Log.d(TAG, "Save result: ${result.source}, message: ${result.message}")
            
            if (result.source == OrganizerMvpDataSource.BACKEND) {
                Toast.makeText(this@TransactionRulesActivity, "Rules saved successfully", Toast.LENGTH_SHORT).show()
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

