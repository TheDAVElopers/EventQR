package com.thedavelopers.eventqr.features.organizer.scanpurposes

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerTransactionRuleDto
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

open class ManageScanPurposesActivity : AppCompatActivity() {
    private val TAG = "ManageScanPurposesActivity"
    private val persistenceTag = "ScanPurposePersistence"
    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var purposeHost: LinearLayout
    private var scanPurposes: List<OrganizerMvpScanPurpose> = emptyList()
    private var loadRequestSerial: Int = 0
    private var refreshCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        val eventId = intentEventId() ?: return showMissingEventScreen("Scan Purposes")
        selectedEvent = resolveSelectedEvent(repository.getApprovedOrganizerEvents(), eventId) ?: return showMissingEventScreen("Scan Purposes")
        
        Log.d(TAG, "Loading scan purposes for eventId: $eventId")
        Log.d(persistenceTag, "selectedEventId=$eventId screen=ScanPurposes")
        
        val content = organizerShell(
            title = "Scan Purposes",
            showBack = true,
            topRightLabel = "+ Add",
            onTopRight = { showAddEditDialog() }
        )
        
        purposeHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(purposeHost)
        
        loadPurposes()
    }

    private fun loadPurposes() {
        refreshCount += 1
        val requestSerial = ++loadRequestSerial
        purposeHost.removeAllViews()
        purposeHost.addView(loadingState("Loading scan purposes..."))
        MainScope().launch {
            val source = repository.loadScanPurposesForMvp(selectedEvent.id)
            if (!isFinishing && !isDestroyed && requestSerial == loadRequestSerial) {
                scanPurposes = source.data.toList()
            }
            if (requestSerial != loadRequestSerial) return@launch
            Log.d(
                TAG,
                "eventId=${selectedEvent.id} refreshCount=$refreshCount loadedCount=${source.data.size} source=${source.source} message=${source.message}"
            )
            Log.d(
                persistenceTag,
                "eventId=${selectedEvent.id} loadedCount=${source.data.size} names=${source.data.joinToString { it.label }}"
            )
            renderPurposes(scanPurposes)
            source.message?.let {
                Toast.makeText(this@ManageScanPurposesActivity, it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderPurposes(purposes: List<OrganizerMvpScanPurpose>) {
        purposeHost.removeAllViews()
        if (purposes.isEmpty()) {
            purposeHost.addView(emptyState("No scan purposes configured yet. Use '+ Add' to create one."))
            return
        }
        
        purposes.forEach { purpose ->
            val subtitle = buildString {
                if (purpose.pointsEnabled) append("+${purpose.pointsValue} pts · ")
                append(if (purpose.duplicateRule.lowercase().contains("allow")) "Allows duplicates" else "No duplicates")
            }
            
            purposeHost.addView(purposeCard(
                title = purpose.label,
                subtitle = subtitle,
                enabled = purpose.enabled,
                onToggle = { isChecked ->
                    togglePurpose(purpose, isChecked)
                }
            ).apply {
                setOnClickListener { showAddEditDialog(purpose) }
            })
        }
    }

    private fun togglePurpose(purpose: OrganizerMvpScanPurpose, enabled: Boolean) {
        MainScope().launch {
            val purposeId = purpose.id?.takeIf { it.isNotBlank() }
            if (purposeId == null) {
                Log.w(persistenceTag, "eventId=${selectedEvent.id} toggleSkipped reason=missingPurposeId name=${purpose.label}")
                Toast.makeText(this@ManageScanPurposesActivity, "Unable to update unsaved scan purpose.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            Log.d(
                TAG,
                "eventId=${selectedEvent.id} purposeId=$purposeId label=${purpose.label} toggleValue=$enabled"
            )
            Log.d(
                persistenceTag,
                "eventId=${selectedEvent.id} toggleRequest id=$purposeId name=${purpose.label} enabled=$enabled"
            )

            val result = repository.enableScanPurposeForMvp(selectedEvent.id, purposeId, enabled)
            when (result) {
                is NetworkResult.Success -> {
                    Log.d(
                        TAG,
                        "eventId=${selectedEvent.id} purposeId=$purposeId toggleApiResult=SUCCESS active=${result.data.enabled}"
                    )
                    Log.d(
                        persistenceTag,
                        "eventId=${selectedEvent.id} toggleResponse id=${result.data.scanPurposeId ?: "null"} name=${result.data.title} enabled=${result.data.enabled}"
                    )
                    Toast.makeText(
                        this@ManageScanPurposesActivity,
                        "${purpose.label} ${if (enabled) "enabled" else "disabled"}",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadPurposes()
                }
                is NetworkResult.Error -> {
                    Log.w(
                        TAG,
                        "eventId=${selectedEvent.id} purposeId=$purposeId toggleApiResult=ERROR message=${result.message}"
                    )
                    Toast.makeText(
                        this@ManageScanPurposesActivity,
                        "Failed to update: ${result.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                NetworkResult.Loading -> {
                    Log.d(
                        TAG,
                        "eventId=${selectedEvent.id} purposeId=${purpose.id} toggleApiResult=LOADING"
                    )
                }
            }
        }
    }

    private fun showAddEditDialog(purpose: OrganizerMvpScanPurpose? = null) {
        val isEdit = purpose != null
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val nameInput = EditText(this).apply {
            hint = "Purpose Name (e.g. Session Attendance)"
            setText(purpose?.label ?: "")
        }
        val descInput = EditText(this).apply {
            hint = "Description"
            setText(purpose?.description ?: "")
        }
        val pointsInput = EditText(this).apply {
            hint = "Points awarded"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(purpose?.pointsValue?.toString() ?: "0")
        }
        val duplicateCheck = CheckBox(this).apply {
            text = "Allow duplicate scans"
            isChecked = purpose?.duplicateRule?.lowercase()?.contains("allow") ?: false
        }
        val trackingOnlyCheck = CheckBox(this).apply {
            text = "Tracking only (no points)"
            isChecked = purpose?.trackingOnly ?: false
        }

        dialogView.addView(text("Name", 14, true))
        dialogView.addView(nameInput)
        dialogView.addView(text("Description", 14, true).apply { setPadding(0, dp(12), 0, 0) })
        dialogView.addView(descInput)
        dialogView.addView(text("Points", 14, true).apply { setPadding(0, dp(12), 0, 0) })
        dialogView.addView(pointsInput)
        dialogView.addView(duplicateCheck)
        dialogView.addView(trackingOnlyCheck)

        AlertDialog.Builder(this)
            .setTitle(if (isEdit) "Edit Scan Purpose" else "Add Scan Purpose")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newPurpose = (purpose ?: OrganizerMvpScanPurpose(
                    label = "",
                    description = "",
                    enabled = true,
                    duplicateRule = "",
                    trackingOnly = false,
                    pointsEnabled = true,
                    pointsValue = 0,
                    requiredSelectionLabel = ""
                )).copy(
                    label = nameInput.text.toString(),
                    description = descInput.text.toString(),
                    pointsValue = pointsInput.text.toString().toIntOrNull() ?: 0,
                    pointsEnabled = !trackingOnlyCheck.isChecked,
                    trackingOnly = trackingOnlyCheck.isChecked,
                    duplicateRule = if (duplicateCheck.isChecked) "Allow Duplicates" else "No Duplicates"
                )
                savePurpose(newPurpose)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savePurpose(purpose: OrganizerMvpScanPurpose) {
        MainScope().launch {
            val existingPurposeId = purpose.id?.takeIf { it.isNotBlank() }
            Log.d(
                persistenceTag,
                "eventId=${selectedEvent.id} saveRequest id=${existingPurposeId ?: "null"} name=${purpose.label} enabled=${purpose.enabled} trackingOnly=${purpose.trackingOnly} pointsEnabled=${purpose.pointsEnabled} pointsValue=${purpose.pointsValue}"
            )
            val result = if (existingPurposeId == null) {
                repository.createOrganizerScanPurpose(selectedEvent.id, purpose.toOrganizerRequest())
            } else {
                repository.updateOrganizerScanPurpose(selectedEvent.id, existingPurposeId, purpose.toOrganizerRequest())
            }
            when (result) {
                is NetworkResult.Success -> {
                    Log.d(
                        persistenceTag,
                        "eventId=${selectedEvent.id} saveResponse id=${result.data.scanPurposeId ?: "null"} name=${result.data.title} enabled=${result.data.enabled} code=${result.data.code}"
                    )
                    Toast.makeText(this@ManageScanPurposesActivity, "Saved successfully", Toast.LENGTH_SHORT).show()
                    loadPurposes()
                }
                is NetworkResult.Error -> {
                    Log.w(
                        persistenceTag,
                        "eventId=${selectedEvent.id} saveError message=${result.message}"
                    )
                    Toast.makeText(this@ManageScanPurposesActivity, "Failed to save: ${result.message}", Toast.LENGTH_SHORT).show()
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun OrganizerMvpScanPurpose.toOrganizerRequest() = com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerScanPurposeRequestDto(
        scanPurposeId = id?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() },
        title = label,
        code = code ?: label.toScanPurposeCode(),
        enabled = enabled,
        trackingOnly = trackingOnly,
        pointsEnabled = pointsEnabled,
        pointsValue = pointsValue,
        allowDuplicate = duplicateRule.contains("allow", ignoreCase = true),
        duplicateRuleSummary = duplicateRule,
        requiredSelectionLabel = requiredSelectionLabel,
        description = description,
    )

    private fun String.toScanPurposeCode(): com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode = when {
        contains("reprint", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.REGISTRATION_LOOKUP
        contains("print", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ID_PRINT
        contains("attendance", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ATTENDANCE
        contains("benefit", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.BENEFIT_CLAIM
        contains("booth", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.BOOTH_VISIT
        contains("session", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.SESSION_VISIT
        contains("reward", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.REWARD_REDEMPTION
        contains("exit", ignoreCase = true) -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.EXIT
        else -> com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode.ENTRY
    }
}
