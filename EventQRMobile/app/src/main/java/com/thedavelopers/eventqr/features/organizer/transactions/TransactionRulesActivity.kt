package com.thedavelopers.eventqr.features.organizer.transactions

import android.os.Bundle
import android.text.InputType
import android.graphics.Color
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.features.organizer.*
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerTransactionRuleDto
import com.thedavelopers.eventqr.features.organizer.model.dto.TransactionRuleRequest
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

open class TransactionRulesActivity : AppCompatActivity() {
    private lateinit var repository: OrganizerRepository
    private lateinit var selectedEvent: OrganizerMvpEvent
    private lateinit var host: LinearLayout
    private var purposeLabels: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OrganizerRepository(this)
        val eventId = intentEventId() ?: return showMissingEventScreen("Transaction Rules")
        selectedEvent = resolveSelectedEvent(repository.getApprovedOrganizerEvents(), eventId) ?: return showMissingEventScreen("Transaction Rules")
        val content = organizerShell("Transaction Rules", selectedEvent.title, showBack = true)
        content.addView(card().apply {
            addView(text("Event", 12, false, MUTED))
            addView(text(selectedEvent.title, 17, true))
            addView(text("${selectedEvent.shortDate} | ${selectedEvent.venue}", 12, false, MUTED))
        })
        host = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(host)
        loadRules()
    }

    private fun loadRules() {
        host.removeAllViews()
        host.addView(loadingState("Loading transaction rules..."))
        MainScope().launch {
            val purposes = repository.loadScanPurposesForMvp(selectedEvent.id)
            purposeLabels = purposes.data.mapNotNull { purpose ->
                val id = purpose.id ?: return@mapNotNull null
                id to purpose.label
            }.toMap()
            val rules = repository.loadTransactionRulesForMvp(selectedEvent.id)
            renderRules(rules)
        }
    }

    private fun renderRules(source: OrganizerMvpLoad<List<OrganizerTransactionRuleDto>>) {
        host.removeAllViews()
        dataSourceBanner(source)?.let { host.addView(it) }
        if (source.data.isEmpty()) {
            host.addView(emptyState("No transaction rules configured yet.", "Open Scan Purposes") {
                openOrganizerPage(ManageScanPurposesActivity::class.java, selectedEvent.id, selectedEvent.title)
            })
            return
        }
        source.data.forEach { host.addView(ruleCard(it)) }
    }

    private fun ruleCard(rule: OrganizerTransactionRuleDto): LinearLayout =
        card().apply {
            addView(text(purposeLabels[rule.scanPurposeId.toString()] ?: "Scan purpose ${rule.scanPurposeId.toString().take(8)}", 16, true))
            addView(text("Rule ID: ${rule.id ?: "Not saved"}", 12, false, MUTED))
            val active = CheckBox(this@TransactionRulesActivity).apply {
                text = "Active"
                isChecked = rule.active
            }
            val allowDuplicate = CheckBox(this@TransactionRulesActivity).apply {
                text = "Allow duplicate scans"
                isChecked = rule.allowDuplicate
            }
            val requiresStaff = CheckBox(this@TransactionRulesActivity).apply {
                text = "Requires staff assignment"
                isChecked = rule.requiresStaffAssignment
            }
            val duplicateWindow = numericInput("Duplicate window minutes", rule.duplicateWindowMinutes)
            val maxUses = numericInput("Max uses per registration", rule.maxUsesPerRegistration)
            val points = numericInput("Points awarded", rule.pointsAwarded)
            addView(active)
            addView(allowDuplicate)
            addView(requiresStaff)
            addView(duplicateWindow)
            addView(maxUses)
            addView(points)
            addView(primaryButton("Save rule") {
                val request = TransactionRuleRequest(
                    scanPurposeId = rule.scanPurposeId,
                    active = active.isChecked,
                    allowDuplicate = allowDuplicate.isChecked,
                    duplicateWindowMinutes = duplicateWindow.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 0,
                    maxUsesPerRegistration = maxUses.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    requiresStaffAssignment = requiresStaff.isChecked,
                    pointsAwarded = points.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 0,
                )
                MainScope().launch {
                    val result = repository.saveTransactionRuleForMvp(selectedEvent.id, request)
                    result.message?.let {
                        Toast.makeText(this@TransactionRulesActivity, it, Toast.LENGTH_SHORT).show()
                    } ?: Toast.makeText(this@TransactionRulesActivity, "Rule saved", Toast.LENGTH_SHORT).show()
                    if (result.source == OrganizerMvpDataSource.BACKEND) loadRules()
                }
            })
        }

    private fun numericInput(hintText: String, value: Int): EditText =
        EditText(this).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value.toString())
            background = rounded(Color.WHITE, 8, BORDER, density = resources.displayMetrics.density)
            setPadding(dp(10), 0, dp(10), 0)
        }
}
