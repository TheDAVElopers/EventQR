package com.thedavelopers.eventqr.features.organizer.scanpurposes

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerScanPurposeRequestDto
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.UUID

open class ManageScanPurposesActivity : AppCompatActivity() {
    private val TAG = "ManageScanPurposesActivity"
    private val persistenceTag = "ScanPurposePersistence"
    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var purposeHost: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var scanPurposes: List<OrganizerMvpScanPurpose> = emptyList()
    private var loadRequestSerial: Int = 0
    private var refreshCount: Int = 0

    private val purposeTypes = listOf(
        ScanPurposeType("Event Entry", ScanPurposeCode.ENTRY, "Event"),
        ScanPurposeType("Session Attendance", ScanPurposeCode.ATTENDANCE, "Session"),
        ScanPurposeType("Booth Visit", ScanPurposeCode.BOOTH_VISIT, "Booth"),
        ScanPurposeType("Session Visit", ScanPurposeCode.SESSION_VISIT, "Session"),
        ScanPurposeType("Benefit Claim", ScanPurposeCode.BENEFIT_CLAIM, "Benefit"),
        ScanPurposeType("Reward Redemption", ScanPurposeCode.REWARD_REDEMPTION_SCAN, "Reward"),
        ScanPurposeType("Event Exit", ScanPurposeCode.EXIT, "Event"),
        ScanPurposeType("ID Print", ScanPurposeCode.ID_PRINT, "Event"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        val eventId = intentEventId() ?: return showMissingEventScreen("Scan Purposes")
        selectedEvent = resolveSelectedEvent(repository.getApprovedOrganizerEvents(), eventId) ?: return showMissingEventScreen("Scan Purposes")

        Log.d(TAG, "Loading scan purposes for eventId: $eventId")
        Log.d(persistenceTag, "selectedEventId=$eventId screen=ScanPurposes")

        val shell = organizerRefreshShell(
            title = "Scan Purposes",
            showBack = true,
            topRightLabel = "+ Add",
            onTopRight = { showAddEditDialog() },
            onRefresh = { loadPurposes(showInitialLoading = false) }
        )
        swipeRefresh = shell.swipeRefreshLayout
        purposeHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        shell.content.addView(purposeHost)

        loadPurposes()
    }

    private fun loadPurposes(showInitialLoading: Boolean = true) {
        refreshCount += 1
        val requestSerial = ++loadRequestSerial
        if (showInitialLoading && !swipeRefresh.isRefreshing) {
            purposeHost.removeAllViews()
            purposeHost.addView(loadingState("Loading scan purposes..."))
        }
        MainScope().launch {
            val source = repository.loadScanPurposesForMvp(selectedEvent.id)
            val persistedPurposes = source.data.filter { !it.id.isNullOrBlank() }
            val droppedWithoutId = source.data.size - persistedPurposes.size
            if (!isFinishing && !isDestroyed && requestSerial == loadRequestSerial) {
                scanPurposes = persistedPurposes.toList()
            }
            if (requestSerial != loadRequestSerial) {
                swipeRefresh.isRefreshing = false
                return@launch
            }
            Log.d(
                TAG,
                "eventId=${selectedEvent.id} refreshCount=$refreshCount loadedCount=${source.data.size} persistedCount=${persistedPurposes.size} droppedWithoutId=$droppedWithoutId source=${source.source} message=${source.message}"
            )
            Log.d(
                persistenceTag,
                "eventId=${selectedEvent.id} loadedCount=${source.data.size} persistedCount=${persistedPurposes.size} droppedWithoutId=$droppedWithoutId names=${persistedPurposes.joinToString { it.label }}"
            )
            if (droppedWithoutId > 0) {
                Log.w(
                    persistenceTag,
                    "eventId=${selectedEvent.id} ignoredCount=$droppedWithoutId reason=missingPurposeIdOnLoad"
                )
            }
            swipeRefresh.isRefreshing = false
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
                append(purpose.code?.toDisplayTypeLabel() ?: "Custom Scan")
                append(" · ")
                if (purpose.pointsEnabled && purpose.pointsValue > 0) append("+${purpose.pointsValue} pts · ")
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
            Log.d(TAG, "eventId=${selectedEvent.id} purposeId=$purposeId label=${purpose.label} toggleValue=$enabled")
            Log.d(persistenceTag, "eventId=${selectedEvent.id} toggleRequest id=$purposeId name=${purpose.label} enabled=$enabled")

            val result = repository.enableScanPurposeForMvp(selectedEvent.id, purposeId, enabled)
            when (result) {
                is NetworkResult.Success -> {
                    Log.d(TAG, "eventId=${selectedEvent.id} purposeId=$purposeId toggleApiResult=SUCCESS active=${result.data.enabled}")
                    Log.d(persistenceTag, "eventId=${selectedEvent.id} toggleResponse id=${result.data.scanPurposeId ?: "null"} name=${result.data.title} enabled=${result.data.enabled}")
                    Toast.makeText(this@ManageScanPurposesActivity, "${purpose.label} ${if (enabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                    loadPurposes()
                }
                is NetworkResult.Error -> {
                    Log.w(TAG, "eventId=${selectedEvent.id} purposeId=$purposeId toggleApiResult=ERROR message=${result.message}")
                    Toast.makeText(this@ManageScanPurposesActivity, "Failed to update: ${result.message}", Toast.LENGTH_SHORT).show()
                }
                NetworkResult.Loading -> Log.d(TAG, "eventId=${selectedEvent.id} purposeId=${purpose.id} toggleApiResult=LOADING")
            }
        }
    }

    private fun showAddEditDialog(purpose: OrganizerMvpScanPurpose? = null) {
        val isEdit = purpose != null
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        var selectedType = purposeTypes.firstOrNull { it.code == purpose?.code }
            ?: purpose?.label?.toScanPurposeCode()?.let { inferred -> purposeTypes.firstOrNull { it.code == inferred } }
            ?: purposeTypes.first { it.code == ScanPurposeCode.BOOTH_VISIT }

        val typeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@ManageScanPurposesActivity,
                android.R.layout.simple_spinner_item,
                purposeTypes.map { it.label }
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(purposeTypes.indexOf(selectedType).coerceAtLeast(0), false)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        }
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedType = purposeTypes.getOrElse(position) { selectedType }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val nameInput = EditText(this).apply {
            hint = "Custom name, e.g. Sponsor Booth A"
            setText(purpose?.label.orEmpty())
            setSingleLine(true)
        }
        val descInput = EditText(this).apply {
            hint = "Description, e.g. Track visits for Sponsor Booth A"
            setText(purpose?.description.orEmpty())
            minLines = 2
        }
        val pointsInput = EditText(this).apply {
            hint = "Points awarded"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(purpose?.pointsValue?.takeIf { it > 0 }?.toString() ?: "0")
            setSingleLine(true)
        }
        val duplicateCheck = CheckBox(this).apply {
            text = "Allow duplicate scans"
            isChecked = purpose?.duplicateRule?.lowercase()?.contains("allow") ?: false
        }
        val trackingOnlyCheck = CheckBox(this).apply {
            text = "Tracking only (no points)"
            isChecked = purpose?.trackingOnly ?: ((purpose?.pointsValue ?: 0) <= 0 && purpose?.pointsEnabled != true)
            setOnCheckedChangeListener { _, checked ->
                pointsInput.isEnabled = !checked
                if (checked) pointsInput.setText("0")
            }
        }
        pointsInput.isEnabled = !trackingOnlyCheck.isChecked

        dialogView.addView(text("Scan Type", 14, true))
        dialogView.addView(typeSpinner)
        dialogView.addView(text("Custom Name", 14, true).apply { setPadding(0, dp(12), 0, 0) })
        dialogView.addView(nameInput)
        dialogView.addView(text("Description", 14, true).apply { setPadding(0, dp(12), 0, 0) })
        dialogView.addView(descInput)
        dialogView.addView(text("Points", 14, true).apply { setPadding(0, dp(12), 0, 0) })
        dialogView.addView(pointsInput)
        dialogView.addView(duplicateCheck)
        dialogView.addView(trackingOnlyCheck)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (isEdit) "Edit Scan Purpose" else "Add Scan Purpose")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val description = descInput.text.toString().trim()
                val trackingOnly = trackingOnlyCheck.isChecked
                val points = pointsInput.text.toString().trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
                if (name.isBlank()) {
                    nameInput.error = "Scan purpose name is required"
                    return@setOnClickListener
                }
                val effectivePoints = if (trackingOnly) 0 else points
                val pointsEnabled = !trackingOnly && effectivePoints > 0
                val requestPurpose = (purpose ?: OrganizerMvpScanPurpose(
                    label = "",
                    description = "",
                    enabled = true,
                    duplicateRule = "",
                    trackingOnly = false,
                    pointsEnabled = false,
                    pointsValue = 0,
                    requiredSelectionLabel = "",
                )).copy(
                    label = name,
                    description = description.ifBlank { defaultDescription(name, selectedType) },
                    code = selectedType.code,
                    pointsValue = effectivePoints,
                    pointsEnabled = pointsEnabled,
                    trackingOnly = trackingOnly,
                    duplicateRule = if (duplicateCheck.isChecked) "Allow Duplicates" else "No Duplicates",
                    requiredSelectionLabel = selectedType.selectionLabel,
                )
                savePurpose(requestPurpose)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun savePurpose(purpose: OrganizerMvpScanPurpose) {
        MainScope().launch {
            val existingPurposeId = purpose.id?.takeIf { it.isNotBlank() }
            Log.d(persistenceTag, "eventId=${selectedEvent.id} saveRequest id=${existingPurposeId ?: "null"} name=${purpose.label} code=${purpose.code} enabled=${purpose.enabled} trackingOnly=${purpose.trackingOnly} pointsEnabled=${purpose.pointsEnabled} pointsValue=${purpose.pointsValue}")
            val result = if (existingPurposeId == null) {
                repository.createOrganizerScanPurpose(selectedEvent.id, purpose.toOrganizerRequest())
            } else {
                repository.updateOrganizerScanPurpose(selectedEvent.id, existingPurposeId, purpose.toOrganizerRequest())
            }
            when (result) {
                is NetworkResult.Success -> {
                    Log.d(persistenceTag, "eventId=${selectedEvent.id} saveResponse id=${result.data.scanPurposeId ?: "null"} name=${result.data.title} enabled=${result.data.enabled} code=${result.data.code}")
                    Toast.makeText(this@ManageScanPurposesActivity, "Saved successfully", Toast.LENGTH_SHORT).show()
                    loadPurposes()
                }
                is NetworkResult.Error -> {
                    Log.w(persistenceTag, "eventId=${selectedEvent.id} saveError message=${result.message}")
                    Toast.makeText(this@ManageScanPurposesActivity, "Failed to save: ${result.message}", Toast.LENGTH_SHORT).show()
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun OrganizerMvpScanPurpose.toOrganizerRequest() = OrganizerScanPurposeRequestDto(
        scanPurposeId = id?.let { runCatching { UUID.fromString(it) }.getOrNull() },
        title = label.trim(),
        code = code ?: label.toScanPurposeCode(),
        enabled = enabled,
        trackingOnly = trackingOnly,
        pointsEnabled = pointsEnabled && !trackingOnly && pointsValue > 0,
        pointsValue = if (trackingOnly) 0 else pointsValue.coerceAtLeast(0),
        allowDuplicate = duplicateRule.contains("allow", ignoreCase = true),
        duplicateRuleSummary = duplicateRule,
        requiredSelectionLabel = requiredSelectionLabel.ifBlank { (code ?: label.toScanPurposeCode()).defaultRequiredSelection() },
        description = description.ifBlank { defaultDescription(label, purposeTypes.firstOrNull { it.code == code }) },
    )

    private fun defaultDescription(name: String, type: ScanPurposeType?): String {
        return when (type?.code) {
            ScanPurposeCode.BOOTH_VISIT -> "Track visits for $name."
            ScanPurposeCode.SESSION_VISIT, ScanPurposeCode.ATTENDANCE -> "Record attendance for $name."
            ScanPurposeCode.BENEFIT_CLAIM -> "Validate benefit claims for $name."
            ScanPurposeCode.REWARD_REDEMPTION, ScanPurposeCode.REWARD_REDEMPTION_SCAN -> "Process reward redemption for $name."
            ScanPurposeCode.EXIT -> "Record event exit."
            ScanPurposeCode.ID_PRINT -> "Print or reprint attendee ID."
            else -> "Record scan transaction for $name."
        }
    }

    private fun ScanPurposeCode.toDisplayTypeLabel(): String = when (this) {
        ScanPurposeCode.ENTRY -> "Event Entry"
        ScanPurposeCode.ATTENDANCE -> "Session Attendance"
        ScanPurposeCode.BENEFIT_CLAIM -> "Benefit Claim"
        ScanPurposeCode.BOOTH_VISIT -> "Booth Visit"
        ScanPurposeCode.SESSION_VISIT -> "Session Visit"
        ScanPurposeCode.REWARD_REDEMPTION, ScanPurposeCode.REWARD_REDEMPTION_SCAN -> "Reward Redemption"
        ScanPurposeCode.EXIT -> "Event Exit"
        ScanPurposeCode.ID_PRINT -> "ID Print"
        ScanPurposeCode.REGISTRATION_LOOKUP -> "Registration Lookup"
    }

    private fun ScanPurposeCode.defaultRequiredSelection(): String = when (this) {
        ScanPurposeCode.BOOTH_VISIT -> "Booth"
        ScanPurposeCode.SESSION_VISIT, ScanPurposeCode.ATTENDANCE -> "Session"
        ScanPurposeCode.BENEFIT_CLAIM -> "Benefit"
        ScanPurposeCode.REWARD_REDEMPTION, ScanPurposeCode.REWARD_REDEMPTION_SCAN -> "Reward"
        else -> "Event"
    }

    private fun String.toScanPurposeCode(): ScanPurposeCode = when {
        contains("reprint", ignoreCase = true) -> ScanPurposeCode.REGISTRATION_LOOKUP
        contains("print", ignoreCase = true) -> ScanPurposeCode.ID_PRINT
        contains("attendance", ignoreCase = true) -> ScanPurposeCode.ATTENDANCE
        contains("benefit", ignoreCase = true) -> ScanPurposeCode.BENEFIT_CLAIM
        contains("booth", ignoreCase = true) -> ScanPurposeCode.BOOTH_VISIT
        contains("session", ignoreCase = true) -> ScanPurposeCode.SESSION_VISIT
        contains("reward", ignoreCase = true) -> ScanPurposeCode.REWARD_REDEMPTION_SCAN
        contains("exit", ignoreCase = true) -> ScanPurposeCode.EXIT
        else -> ScanPurposeCode.BOOTH_VISIT
    }

    private data class ScanPurposeType(
        val label: String,
        val code: ScanPurposeCode,
        val selectionLabel: String,
    )
}