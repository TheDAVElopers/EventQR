package com.thedavelopers.eventqr.features.audit

import android.content.Context
import androidx.annotation.StringRes
import com.thedavelopers.eventqr.R

/**
 * Verb-first row copy for the audit log (§6.5). A data-layer map, not adapter logic: eight codes,
 * each with the verb phrase that says what happened. An unmapped code falls back to its
 * sentence-cased form (`EVENT_REQUEST_PENDING` -> `Event request pending`) so a new backend action
 * never renders blank until it is given a verb.
 *
 * `EVENT_REQUEST_PENDING` is deliberately absent: request creation writes no audit entry.
 */
object AuditActionCopy {

    private val byCode: Map<String, Int> = mapOf(
        "EVENT_REQUEST_APPROVED" to R.string.ui_audit_action_event_request_approved,
        "EVENT_REQUEST_REJECTED" to R.string.ui_audit_action_event_request_rejected,
        "ACCOUNT_ENABLED" to R.string.ui_audit_action_account_enabled,
        "ACCOUNT_DISABLED" to R.string.ui_audit_action_account_disabled,
        "ACCOUNT_UPDATED" to R.string.ui_audit_action_account_updated,
        "ACCOUNT_STATUS_UPDATED" to R.string.ui_audit_action_account_status_updated,
        "ACCOUNT_ROLE_UPDATED" to R.string.ui_audit_action_account_role_updated,
        "ACCOUNT_DELETED" to R.string.ui_audit_action_account_deleted,
    )

    /** String resource for a mapped code, or null when the code has no verb phrase yet. */
    @StringRes
    fun resFor(code: String): Int? = byCode[code]

    /** The row copy for [code]: the mapped phrase, else the sentence-cased code. */
    fun copyFor(context: Context, code: String): String =
        resFor(code)?.let(context::getString) ?: sentenceCase(code)

    /** `EVENT_REQUEST_PENDING` -> `Event request pending`; sentence case, never Title Case. */
    fun sentenceCase(code: String): String {
        val words = code.trim().lowercase().split('_', ' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return code
        return words.joinToString(" ").replaceFirstChar { it.uppercase() }
    }
}
