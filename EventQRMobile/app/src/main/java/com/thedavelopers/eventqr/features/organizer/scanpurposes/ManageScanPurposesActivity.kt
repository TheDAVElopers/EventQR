package com.thedavelopers.eventqr.features.organizer.scanpurposes

import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.dimenPx
import com.thedavelopers.eventqr.features.organizer.styledText
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.ScanPurposeCode
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerScanPurposeRequestDto
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

open class ManageScanPurposesActivity : AppCompatActivity() {
    private val TAG = "ManageScanPurposesActivity"
    private val persistenceTag = "ScanPurposePersistence"
    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var purposeHost: LinearLayout
    private lateinit var statusLine: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var scanPurposes: List<OrganizerMvpScanPurpose> = emptyList()
    private var loadRequestSerial: Int = 0
    private var refreshCount: Int = 0

    private val purposeTypes by lazy { listOf(
        ScanPurposeType(getString(R.string.scan_purpose_type_event_entry), ScanPurposeCode.ENTRY, "Event"),
        ScanPurposeType(getString(R.string.scan_purpose_type_session_attendance), ScanPurposeCode.ATTENDANCE, "Session"),
        ScanPurposeType(getString(R.string.scan_purpose_type_booth_visit), ScanPurposeCode.BOOTH_VISIT, "Booth"),
        ScanPurposeType(getString(R.string.scan_purpose_type_session_visit), ScanPurposeCode.SESSION_VISIT, "Session"),
        ScanPurposeType(getString(R.string.scan_purpose_type_benefit_claim), ScanPurposeCode.BENEFIT_CLAIM, "Benefit"),
        ScanPurposeType(getString(R.string.scan_purpose_type_reward_redemption), ScanPurposeCode.REWARD_REDEMPTION_SCAN, "Reward"),
        ScanPurposeType(getString(R.string.scan_purpose_type_event_exit), ScanPurposeCode.EXIT, "Event"),
        ScanPurposeType(getString(R.string.scan_purpose_type_id_print), ScanPurposeCode.ID_PRINT, "Event"),
    ) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        val eventId = intentEventId() ?: return showMissingEventScreen(getString(R.string.manage_scan_purposes_title))
        showEventsLoadingScreen(getString(R.string.manage_scan_purposes_title))
        lifecycleScope.launch {
            val eventsLoad = repository.loadApprovedOrganizerEvents()
            if (eventsLoad.source == OrganizerMvpDataSource.ERROR) {
                showEventsLoadErrorScreen(getString(R.string.manage_scan_purposes_title), eventsLoad.message) { recreate() }
                return@launch
            }
            val selectableEvents = eventsLoad.data
            selectedEvent = resolveSelectedEvent(selectableEvents, eventId) ?: run {
                showMissingEventScreen(getString(R.string.manage_scan_purposes_title))
                return@launch
            }
            Log.d(TAG, "Loading scan purposes for eventId: $eventId")
            Log.d(persistenceTag, "selectedEventId=$eventId screen=ScanPurposes")

            val shell = organizerRefreshShell(
                title = getString(R.string.manage_scan_purposes_title),
                showBack = true,
                topRightLabel = getString(R.string.manage_scan_purposes_add_action),
                onTopRight = { showAddEditDialog() },
                onRefresh = { loadPurposes(showInitialLoading = false) }
            )
            swipeRefresh = shell.swipeRefreshLayout
            // One inline status line above the list: every failure lands here, never in a Toast.
            statusLine = styledText("", R.dimen.ui_type_body_s, false, color(R.color.ui_error)).apply {
                visibility = View.GONE
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                setPadding(dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_sm), dimenPx(R.dimen.space_lg), dimenPx(R.dimen.space_sm))
            }
            shell.content.addView(statusLine)
            purposeHost = LinearLayout(this@ManageScanPurposesActivity).apply {
                id = com.thedavelopers.eventqr.R.id.msp_purpose_host
                orientation = LinearLayout.VERTICAL
            }
            shell.content.addView(purposeHost)

            loadPurposes()
        }
    }

    /**
     * Coming back from Transaction Rules can change `allowDuplicate`; re-read the snapshot so an edit
     * here never writes a stale duplicate setting (the PATCH rewrites the rule row).
     */
    override fun onResume() {
        super.onResume()
        if (::selectedEvent.isInitialized && ::purposeHost.isInitialized) {
            loadPurposes(showInitialLoading = false)
        }
    }

    private fun showStatus(message: CharSequence?) {
        if (message.isNullOrBlank()) return
        statusLine.text = message
        statusLine.visibility = View.VISIBLE
        // A row action can fail while the list is scrolled down; the line is above the list.
        var parent = statusLine.parent
        while (parent != null && parent !is ScrollView) parent = parent.parent
        (parent as? ScrollView)?.scrollTo(0, 0)
    }

    private fun loadPurposes(showInitialLoading: Boolean = true) {
        refreshCount += 1
        val requestSerial = ++loadRequestSerial
        if (showInitialLoading && !swipeRefresh.isRefreshing) {
            statusLine.visibility = View.GONE
            purposeHost.removeAllViews()
            purposeHost.addView(loadingState(getString(R.string.manage_scan_purposes_loading)))
        }
        lifecycleScope.launch {
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
                "eventId=${selectedEvent.id} refreshCount=$refreshCount loadedCount=${source.data.size} persistedCount=${persistedPurposes.size} droppedWithoutId=$droppedWithoutId source=${source.source}"
            )
            Log.d(
                persistenceTag,
                "eventId=${selectedEvent.id} loadedCount=${source.data.size} persistedCount=${persistedPurposes.size} droppedWithoutId=$droppedWithoutId"
            )
            if (droppedWithoutId > 0) {
                Log.w(
                    persistenceTag,
                    "eventId=${selectedEvent.id} ignoredCount=$droppedWithoutId reason=missingPurposeIdOnLoad"
                )
            }
            swipeRefresh.isRefreshing = false
            if (source.source == OrganizerMvpDataSource.ERROR) {
                // The failure owns the slot: an empty state would claim "no purposes" for a list
                // that was never read. Show the error line only.
                purposeHost.removeAllViews()
                source.message?.let { showStatus(it) }
                return@launch
            }
            renderPurposes(scanPurposes)
            source.message?.let {
                showStatus(it)
            }
        }
    }

    private fun renderPurposes(purposes: List<OrganizerMvpScanPurpose>) {
        purposeHost.removeAllViews()
        if (purposes.isEmpty()) {
            purposeHost.addView(emptyState(
                iconRes = AppIcons.Scan,
                title = getString(R.string.manage_scan_purposes_empty_title),
                subtext = getString(R.string.manage_scan_purposes_empty_subtext),
                actionLabel = getString(R.string.manage_scan_purposes_add_action),
                onAction = { showAddEditDialog() },
            ))
            return
        }

        purposes.forEach { purpose ->
            val subtitle = buildString {
                append(purpose.code?.toDisplayTypeLabel() ?: getString(R.string.manage_scan_purposes_custom_scan))
                append(" · ")
                if (purpose.pointsEnabled && purpose.pointsValue > 0) append("+").append(getString(R.string.common_points_short, purpose.pointsValue)); append(" · ")
                append(getString(if (purpose.allowDuplicate) R.string.manage_scan_purposes_allows_duplicates else R.string.manage_scan_purposes_no_duplicates))
            }

            val purposeCard = purposeCard(
                title = purpose.label,
                subtitle = subtitle,
                enabled = purpose.enabled,
                onToggle = { _ -> Unit }
            ).apply {
                setOnClickListener { showAddEditDialog(purpose) }
            }
            // The row owns its hierarchy; look the switch up by id instead of by child index.
            val toggleSwitch = purposeCard.findViewById<androidx.appcompat.widget.SwitchCompat>(
                com.thedavelopers.eventqr.R.id.switchToggle,
            )
            toggleSwitch?.id = com.thedavelopers.eventqr.R.id.msp_purpose_toggle
            toggleSwitch?.setOnCheckedChangeListener { _, checked ->
                togglePurpose(purpose, checked, toggleSwitch)
            }
            purposeHost.addView(purposeCard)
        }
    }

    private fun togglePurpose(
        purpose: OrganizerMvpScanPurpose,
        enabled: Boolean,
        toggle: androidx.appcompat.widget.SwitchCompat? = null,
    ) {
        lifecycleScope.launch {
            val purposeId = purpose.id?.takeIf { it.isNotBlank() }
            if (purposeId == null) {
                Log.w(persistenceTag, "eventId=${selectedEvent.id} toggleSkipped reason=missingPurposeId")
                showStatus(this@ManageScanPurposesActivity.getString(R.string.manage_scan_purposes_unable_to_update_unsaved_scan_purpos))
                return@launch
            }
            Log.d(TAG, "eventId=${selectedEvent.id} purposeId=$purposeId toggleValue=$enabled")
            Log.d(persistenceTag, "eventId=${selectedEvent.id} toggleRequest id=$purposeId enabled=$enabled")

            val result = withContext(NonCancellable) { repository.enableScanPurposeForMvp(selectedEvent.id, purposeId, enabled) }.also { ensureActive() }
            when (result) {
                is NetworkResult.Success -> {
                    Log.d(TAG, "eventId=${selectedEvent.id} purposeId=$purposeId toggleApiResult=SUCCESS active=${result.data.enabled}")
                    Log.d(persistenceTag, "eventId=${selectedEvent.id} toggleResponse id=${result.data.scanPurposeId ?: "null"} enabled=${result.data.enabled}")
                    Toast.makeText(this@ManageScanPurposesActivity, "${purpose.label} ${getString(if (enabled) R.string.manage_scan_purposes_enabled else R.string.manage_scan_purposes_disabled)}", Toast.LENGTH_SHORT).show()
                    loadPurposes()
                }
                is NetworkResult.Error -> {
                    Log.w(TAG, "eventId=${selectedEvent.id} purposeId=$purposeId toggleApiResult=ERROR")
                    toggle?.setOnCheckedChangeListener(null)
                    toggle?.isChecked = purpose.enabled
                    toggle?.setOnCheckedChangeListener { _, checked ->
                        togglePurpose(purpose, checked, toggle)
                    }
                    showStatus(getString(R.string.manage_scan_purposes_failed_update, result.message))
                }
                NetworkResult.Loading -> Log.d(TAG, "eventId=${selectedEvent.id} purposeId=${purpose.id} toggleApiResult=LOADING")
            }
        }
    }

    internal fun showAddEditDialog(purpose: OrganizerMvpScanPurpose? = null) {
        val isEdit = purpose != null
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        var selectedType = purposeTypes.firstOrNull { it.code == purpose?.code }
            ?: purpose?.label?.toScanPurposeCode()?.let { inferred -> purposeTypes.firstOrNull { it.code == inferred } }
            ?: purposeTypes.first { it.code == ScanPurposeCode.BOOTH_VISIT }

        val typeSpinner = Spinner(this).apply {
            id = com.thedavelopers.eventqr.R.id.msp_type_spinner
            adapter = ArrayAdapter(
                this@ManageScanPurposesActivity,
                android.R.layout.simple_spinner_item,
                purposeTypes.map { it.label }
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(purposeTypes.indexOf(selectedType).coerceAtLeast(0), false)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dimenPx(R.dimen.touch_target_min))
        }
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedType = purposeTypes.getOrElse(position) { selectedType }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val nameInput = EditText(this).apply {
            id = com.thedavelopers.eventqr.R.id.msp_name_input
            hint = getString(R.string.manage_scan_purposes_hint_name)
            setText(purpose?.label.orEmpty())
            setSingleLine(true)
        }
        val descInput = EditText(this).apply {
            id = com.thedavelopers.eventqr.R.id.msp_desc_input
            hint = getString(R.string.manage_scan_purposes_hint_description)
            setText(purpose?.description.orEmpty())
            minLines = 2
        }
        val pointsInput = EditText(this).apply {
            id = com.thedavelopers.eventqr.R.id.msp_points_input
            hint = getString(R.string.manage_scan_purposes_hint_points)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(purpose?.pointsValue?.takeIf { it > 0 }?.toString() ?: "0")
            setSingleLine(true)
        }

        dialogView.addView(styledText(getString(R.string.manage_scan_purposes_label_scan_type), R.dimen.type_body_medium, true))
        dialogView.addView(typeSpinner)
        dialogView.addView(styledText(getString(R.string.manage_scan_purposes_label_custom_name), R.dimen.type_body_medium, true).apply { setPadding(0, dimenPx(R.dimen.space_md), 0, 0) })
        dialogView.addView(nameInput)
        dialogView.addView(styledText(getString(R.string.common_description), R.dimen.type_body_medium, true).apply { setPadding(0, dimenPx(R.dimen.space_md), 0, 0) })
        dialogView.addView(descInput)
        dialogView.addView(styledText(getString(R.string.manage_scan_purposes_label_points), R.dimen.type_body_medium, true).apply { setPadding(0, dimenPx(R.dimen.space_md), 0, 0) })
        dialogView.addView(pointsInput)

        // §5.12: one dialog base, title / form / right-aligned text actions. Delete stays reachable from
        // the edit dialog (it has no other home) but is pushed to the start and tinted destructive.
        val root = layoutInflater.inflate(R.layout.dialog_base, null, false) as LinearLayout
        root.findViewById<TextView>(R.id.dialogTitle).setText(
            if (isEdit) R.string.manage_scan_purposes_edit_scan_purpose else R.string.manage_scan_purposes_add_scan_purpose,
        )
        root.findViewById<View>(R.id.dialogBody).visibility = View.GONE
        val actions = root.findViewById<LinearLayout>(R.id.dialogActions)
        root.addView(dialogView, root.indexOfChild(actions))
        val dialog = AlertDialog.Builder(this).setView(root).create()

        val positive = root.findViewById<Button>(R.id.dialogPositive)
        positive.setText(R.string.manage_scan_purposes_save)
        positive.alpha = 1f
        positive.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val description = descInput.text.toString().trim()
            val points = pointsInput.text.toString().trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
            if (name.isBlank()) {
                nameInput.error = getString(R.string.manage_scan_purposes_scan_purpose_name_is_required)
                nameInput.requestFocus()
                return@setOnClickListener
            }
            savePurpose(draftScanPurpose(purpose, name, description, selectedType.code, selectedType.selectionLabel, points))
            dialog.dismiss()
        }

        val negative = root.findViewById<Button>(R.id.dialogNegative)
        negative.setText(R.string.request_event_cancel)
        negative.setOnClickListener { dialog.dismiss() }

        if (isEdit && purpose != null) {
            actions.addView(
                Button(this).apply {
                    setText(R.string.admin_account_management_delete)
                    setTextColor(color(R.color.ui_error))
                    setBackgroundResource(R.drawable.bg_ui_button_ghost)
                    minHeight = dimenPx(R.dimen.ui_button_height_inline)
                    setPadding(dimenPx(R.dimen.space_lg), 0, dimenPx(R.dimen.space_lg), 0)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { marginEnd = dimenPx(R.dimen.space_8) }
                    setOnClickListener {
                        dialog.dismiss()
                        confirmDeletePurpose(purpose)
                    }
                },
                0,
            )
        }

        dialog.show()
    }

    /**
     * Dialog result -> model. Points is the only input for tracking-only: 0 means tracking-only, any
     * positive value awards points, so `trackingOnly` and `pointsEnabled` always match the stored rule.
     * Duplicate settings belong to the Transaction Rules screen and are carried over untouched.
     */
    internal fun draftScanPurpose(
        existing: OrganizerMvpScanPurpose?,
        name: String,
        description: String,
        code: ScanPurposeCode,
        selectionLabel: String,
        points: Int,
    ): OrganizerMvpScanPurpose {
        val trackingOnly = points == 0
        val base = existing ?: OrganizerMvpScanPurpose(
            label = "",
            description = "",
            enabled = true,
            duplicateRule = "",
            trackingOnly = true,
            pointsEnabled = false,
            pointsValue = 0,
            requiredSelectionLabel = "",
        )
        return base.copy(
            label = name,
            description = description.ifBlank { defaultDescription(name, purposeTypes.firstOrNull { it.code == code }) },
            code = code,
            pointsValue = if (trackingOnly) 0 else points,
            pointsEnabled = !trackingOnly,
            trackingOnly = trackingOnly,
            requiredSelectionLabel = selectionLabel,
        )
    }

    internal open fun savePurpose(purpose: OrganizerMvpScanPurpose) {
        lifecycleScope.launch {
            val existingPurposeId = purpose.id?.takeIf { it.isNotBlank() }
            Log.d(persistenceTag, "eventId=${selectedEvent.id} saveRequest id=${existingPurposeId ?: "null"} code=${purpose.code} enabled=${purpose.enabled} trackingOnly=${purpose.trackingOnly} pointsEnabled=${purpose.pointsEnabled} pointsValue=${purpose.pointsValue}")
            val request = purpose.toOrganizerRequest()
            val result = withContext(NonCancellable) {
                if (existingPurposeId == null) {
                    repository.createOrganizerScanPurpose(selectedEvent.id, request)
                } else {
                    repository.updateOrganizerScanPurpose(selectedEvent.id, existingPurposeId, request)
                }
            }.also { ensureActive() }
            when (result) {
                is NetworkResult.Success -> {
                    Log.d(persistenceTag, "eventId=${selectedEvent.id} saveResponse id=${result.data.scanPurposeId ?: "null"} enabled=${result.data.enabled} code=${result.data.code}")
                    Toast.makeText(this@ManageScanPurposesActivity, this@ManageScanPurposesActivity.getString(R.string.manage_scan_purposes_saved_successfully), Toast.LENGTH_SHORT).show()
                    loadPurposes()
                }
                is NetworkResult.Error -> {
                    Log.w(persistenceTag, "eventId=${selectedEvent.id} saveError")
                    showStatus(getString(R.string.manage_scan_purposes_failed_save, result.message))
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun confirmDeletePurpose(purpose: OrganizerMvpScanPurpose) {
        val purposeId = purpose.id?.takeIf { it.isNotBlank() }
        if (purposeId == null) {
            showStatus(this.getString(R.string.manage_scan_purposes_unable_to_delete_unsaved_scan_purpos))
            return
        }

        // §5.12: `dialog_base` with the verb as the destructive commit.
        val root = layoutInflater.inflate(R.layout.dialog_base, null, false)
        val dialog = AlertDialog.Builder(this).setView(root).create()
        root.findViewById<TextView>(R.id.dialogTitle).setText(R.string.manage_scan_purposes_delete_scan_purpose)
        root.findViewById<TextView>(R.id.dialogBody).text = getString(R.string.manage_scan_purposes_delete_message, purpose.label)
        root.findViewById<Button>(R.id.dialogNegative).apply {
            setText(R.string.request_event_cancel)
            setOnClickListener { dialog.dismiss() }
        }
        root.findViewById<Button>(R.id.dialogPositive).apply {
            setText(R.string.admin_account_management_delete)
            setBackgroundResource(R.drawable.bg_ui_button_destructive_commit)
            setTextColor(ContextCompat.getColor(context, R.color.ui_on_primary))
            setOnClickListener {
                dialog.dismiss()
                deletePurposeIfUnused(purposeId, purpose.label)
            }
        }
        dialog.show()
    }

    private fun deletePurposeIfUnused(purposeId: String, purposeName: String) {
        lifecycleScope.launch {
            val transactionResult = repository.fetchOrganizerTransactions(selectedEvent.id)
            val usageCount = when (transactionResult) {
                is NetworkResult.Success -> transactionResult.data.count { it.scanPurposeId?.toString() == purposeId }
                is NetworkResult.Error -> {
                    showStatus(this@ManageScanPurposesActivity.getString(R.string.manage_scan_purposes_unable_to_verify_transaction_usage_t))
                    return@launch
                }
                NetworkResult.Loading -> 0
            }

            if (usageCount > 0) {
                showStatus(getString(R.string.manage_scan_purposes_in_use, purposeName, usageCount))
                return@launch
            }

            when (val deleteResult = withContext(NonCancellable) { repository.deleteScanPurposeForMvp(selectedEvent.id, purposeId) }.also { ensureActive() }) {
                is NetworkResult.Success -> {
                    Toast.makeText(this@ManageScanPurposesActivity, this@ManageScanPurposesActivity.getString(R.string.manage_scan_purposes_scan_purpose_deleted), Toast.LENGTH_SHORT).show()
                    loadPurposes()
                }
                is NetworkResult.Error -> {
                    showStatus(getString(R.string.manage_scan_purposes_failed_delete, deleteResult.message))
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    internal fun OrganizerMvpScanPurpose.toOrganizerRequest() = OrganizerScanPurposeRequestDto(
        scanPurposeId = id?.let { runCatching { UUID.fromString(it) }.getOrNull() },
        title = label.trim(),
        code = code ?: label.toScanPurposeCode(),
        enabled = enabled,
        trackingOnly = trackingOnly,
        pointsEnabled = pointsEnabled && !trackingOnly && pointsValue > 0,
        pointsValue = if (trackingOnly) 0 else pointsValue.coerceAtLeast(0),
        allowDuplicate = allowDuplicate,
        duplicateRuleSummary = duplicateRule,
        requiredSelectionLabel = requiredSelectionLabel.ifBlank { (code ?: label.toScanPurposeCode()).defaultRequiredSelection() },
        description = description.ifBlank { defaultDescription(label, purposeTypes.firstOrNull { it.code == code }) },
    )

    private fun defaultDescription(name: String, type: ScanPurposeType?): String {
        return when (type?.code) {
            ScanPurposeCode.BOOTH_VISIT -> getString(R.string.manage_scan_purposes_desc_track, name)
            ScanPurposeCode.SESSION_VISIT, ScanPurposeCode.ATTENDANCE -> getString(R.string.manage_scan_purposes_desc_attend, name)
            ScanPurposeCode.BENEFIT_CLAIM -> getString(R.string.manage_scan_purposes_desc_benefit, name)
            ScanPurposeCode.REWARD_REDEMPTION, ScanPurposeCode.REWARD_REDEMPTION_SCAN -> getString(R.string.manage_scan_purposes_desc_reward, name)
            ScanPurposeCode.EXIT -> getString(R.string.manage_scan_purposes_desc_exit)
            ScanPurposeCode.ID_PRINT -> getString(R.string.manage_scan_purposes_desc_id_print)
            else -> getString(R.string.manage_scan_purposes_desc_default, name)
        }
    }

    private fun ScanPurposeCode.toDisplayTypeLabel(): String = when (this) {
        ScanPurposeCode.ENTRY -> getString(R.string.scan_purpose_type_event_entry)
        ScanPurposeCode.ATTENDANCE -> getString(R.string.scan_purpose_type_session_attendance)
        ScanPurposeCode.BENEFIT_CLAIM -> getString(R.string.scan_purpose_type_benefit_claim)
        ScanPurposeCode.BOOTH_VISIT -> getString(R.string.scan_purpose_type_booth_visit)
        ScanPurposeCode.SESSION_VISIT -> getString(R.string.scan_purpose_type_session_visit)
        ScanPurposeCode.REWARD_REDEMPTION, ScanPurposeCode.REWARD_REDEMPTION_SCAN -> getString(R.string.scan_purpose_type_reward_redemption)
        ScanPurposeCode.EXIT -> getString(R.string.scan_purpose_type_event_exit)
        ScanPurposeCode.ID_PRINT -> getString(R.string.scan_purpose_type_id_print)
        ScanPurposeCode.REGISTRATION_LOOKUP -> getString(R.string.scan_purpose_type_registration_lookup)
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