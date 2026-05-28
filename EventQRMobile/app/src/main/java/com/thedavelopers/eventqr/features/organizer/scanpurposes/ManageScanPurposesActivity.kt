package com.thedavelopers.eventqr.features.organizer.scanpurposes

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerTransactionRuleDto
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

open class ManageScanPurposesActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var summaryHost: LinearLayout
    private lateinit var rulesHost: LinearLayout
    private lateinit var purposeHost: LinearLayout
    private val purposeInputs = mutableListOf<Pair<OrganizerMvpScanPurpose, LinearLayout>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        val eventId = intentEventId() ?: return showMissingEventScreen("Scan Purposes")
        selectedEvent = resolveSelectedEvent(repository.getApprovedOrganizerEvents(), eventId) ?: return showMissingEventScreen("Scan Purposes")
        val content = organizerShell("Scan Purposes", selectedEvent.title, showBack = true)
        content.addView(card().apply {
            background = rounded(Color.parseColor("#E5E7EB"), 12, Color.parseColor("#C7CAD1"), density = resources.displayMetrics.density)
            addView(text("Configure which scan purposes are available for your event. Active scan purposes will be available to staff during QR scanning.", 14, false))
        })
        summaryHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rulesHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        purposeHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(summaryHost)
        content.addView(rulesHost)
        content.addView(purposeHost)
        content.addView(section("Transaction Rules"))
        listOf(
            "Prevent duplicate entry",
            "Prevent duplicate attendance if configured",
            "Prevent duplicate benefit claim",
            "Prevent duplicate reward claim",
            "Reject wrong event QR",
            "Reject inactive/invalid registration",
            "Reject unauthorized staff scan",
            "Tracking-only purposes log transactions without awarding points",
            "Point-enabled purposes award event-specific points after valid scans",
        ).forEach {
            content.addView(CheckBox(this).apply { text = it; isChecked = true })
        }
        content.addView(primaryButton("Add/Edit scan purpose rule") {
            Toast.makeText(this, "Edit fields directly in each purpose card.", Toast.LENGTH_SHORT).show()
        })
        content.addView(primaryButton("Save configuration") { validateAndSave() })
        content.addView(ghostButton("Reset / Cancel changes") { recreate() })
        content.addView(ghostButton("Configure points") {
            Toast.makeText(this, "Points configuration is handled per scan purpose.", Toast.LENGTH_SHORT).show()
        })
        content.addView(stateCard())
        loadPurposes()
    }

    private fun loadPurposes() {
        summaryHost.removeAllViews()
        purposeHost.removeAllViews()
        purposeInputs.clear()
        purposeHost.addView(loadingState("Loading scan purposes..."))
        MainScope().launch {
            val source = repository.loadScanPurposesForMvp(selectedEvent.id)
            val rulesSource = repository.loadTransactionRulesForMvp(selectedEvent.id)
            renderPurposes(source.data, rulesSource.data)
            source.message?.let {
                Toast.makeText(this@ManageScanPurposesActivity, it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderPurposes(purposes: List<OrganizerMvpScanPurpose>, rules: List<OrganizerTransactionRuleDto>) {
        summaryHost.removeAllViews()
        rulesHost.removeAllViews()
        purposeHost.removeAllViews()
        summaryHost.addView(row().apply {
            addView(summaryCard("Active Purposes", purposes.count { it.enabled }.toString()))
            addView(summaryCard("With Points", purposes.count { it.pointsEnabled }.toString(), SUCCESS))
        })
        if (purposes.isEmpty()) {
            purposeHost.addView(emptyState("No scan purposes configured yet."))
        }
        rulesHost.addView(card().apply {
            addView(text("Transaction Rules", 16, true))
            addView(text("Loaded from the backend for this event.", 12, false, MUTED))
            addView(text("Configured rules: ${rules.size}", 13, true, PRIMARY))
            if (rules.isEmpty()) {
                addView(text("No transaction rules configured yet.", 12, false, MUTED))
            } else {
                rules.forEach { rule ->
                    addView(text(
                        "${rule.scanPurposeId} | active=${rule.active} | duplicate=${rule.allowDuplicate} | staff=${rule.requiresStaffAssignment} | points=${rule.pointsAwarded}",
                        12,
                        false,
                        MUTED,
                    ))
                }
            }
        })
        val header = row()
        header.addView(text("Available Purposes", 14, true, MUTED).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(text("Configure Points", 14, true, PRIMARY))
        purposeHost.addView(header)
        purposes.forEach { purpose ->
            val view = purposeCard(purpose)
            purposeInputs.add(purpose to view)
            purposeHost.addView(view)
        }
    }

    private fun purposeCard(purpose: OrganizerMvpScanPurpose): LinearLayout =
        card().apply {
            val top = row()
            top.addView(text(purpose.label, 16, true).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            top.addView(CheckBox(this@ManageScanPurposesActivity).apply {
                text = if (purpose.enabled) "Enabled" else "Disabled"
                isChecked = purpose.enabled
                setOnCheckedChangeListener { button, checked ->
                    button.text = if (checked) "Enabled" else "Disabled"
                }
            })
            addView(top)
            addView(text(purpose.description, 12, false, MUTED))
            addView(CheckBox(this@ManageScanPurposesActivity).apply {
                text = "Tracking only"
                isChecked = purpose.trackingOnly
            })
            addView(CheckBox(this@ManageScanPurposesActivity).apply {
                text = "Points enabled"
                isChecked = purpose.pointsEnabled
            })
            addView(EditText(this@ManageScanPurposesActivity).apply {
                hint = "Points value"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                setText(purpose.pointsValue.toString())
                background = rounded(Color.WHITE, 8, BORDER, density = resources.displayMetrics.density)
                setPadding(dp(10), 0, dp(10), 0)
            })
            addView(EditText(this@ManageScanPurposesActivity).apply {
                hint = "Duplicate rule"
                setText(purpose.duplicateRule)
                background = rounded(Color.WHITE, 8, BORDER, density = resources.displayMetrics.density)
                setPadding(dp(10), 0, dp(10), 0)
            })
            addView(EditText(this@ManageScanPurposesActivity).apply {
                hint = "Required selection label"
                setText(purpose.requiredSelectionLabel)
                background = rounded(Color.WHITE, 8, BORDER, density = resources.displayMetrics.density)
                setPadding(dp(10), 0, dp(10), 0)
            })
            addView(text("Badges: ${if (purpose.trackingOnly) "Tracking Only" else "Transactions"}${if (purpose.pointsEnabled) " | Points Enabled (${purpose.pointsValue})" else ""}", 12, false, MUTED))
        }

    private fun validateAndSave() {
        val errors = mutableListOf<String>()
        val updated = mutableListOf<OrganizerMvpScanPurpose>()
        purposeInputs.forEach { (purpose, view) ->
            val enabled = (((view.getChildAt(0) as LinearLayout).getChildAt(1)) as CheckBox).isChecked
            val trackingOnly = (view.getChildAt(2) as CheckBox).isChecked
            val pointsEnabled = (view.getChildAt(3) as CheckBox).isChecked
            val points = (view.getChildAt(4) as EditText).text.toString().toIntOrNull()
            val duplicateRule = (view.getChildAt(5) as EditText).text.toString()
            val requiredSelection = (view.getChildAt(6) as EditText).text.toString()
            if (points == null || points < 0) errors.add("${purpose.label}: invalid point value")
            if (trackingOnly && pointsEnabled) errors.add("${purpose.label}: conflicting tracking-only and points rules")
            updated.add(
                purpose.copy(
                    enabled = enabled,
                    trackingOnly = trackingOnly,
                    pointsEnabled = pointsEnabled,
                    pointsValue = points ?: 0,
                    duplicateRule = duplicateRule,
                    requiredSelectionLabel = requiredSelection,
                )
            )
        }
        if (purposeInputs.none { it.first.label == "Entrance Logging" || it.first.label == "Entry" }) {
            errors.add("Missing required scan purpose: Entrance Logging / Entry")
        }
        if (errors.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Validation errors")
                .setMessage(errors.joinToString("\n"))
                .setPositiveButton("OK", null)
                .show()
            return
        }
        MainScope().launch {
            val source = repository.saveScanPurposesForMvp(selectedEvent.id, updated)
            val rulesSource = repository.loadTransactionRulesForMvp(selectedEvent.id)
            source.message?.let {
                Toast.makeText(this@ManageScanPurposesActivity, it, Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this@ManageScanPurposesActivity, "Configuration saved", Toast.LENGTH_SHORT).show()
            renderPurposes(source.data, rulesSource.data)
        }
    }
}
