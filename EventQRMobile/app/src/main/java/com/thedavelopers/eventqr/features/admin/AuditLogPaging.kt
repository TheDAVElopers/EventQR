package com.thedavelopers.eventqr.features.admin

import androidx.annotation.StringRes
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.PageResponse
import com.thedavelopers.eventqr.features.audit.model.dto.AuditLogResponse

/**
 * The audit-log endpoint may answer with a bare array (legacy) or a Spring `Page` object. Both are
 * normalised to a [PageResponse]; a bare array is one complete (last) page.
 */
internal fun parseAuditLogPage(json: JsonElement?, gson: Gson): PageResponse<AuditLogResponse> {
    if (json == null || json.isJsonNull) return PageResponse()
    if (json.isJsonArray) {
        val items = json.asJsonArray.map { gson.fromJson(it, AuditLogResponse::class.java) }
        return PageResponse(content = items, totalElements = items.size.toLong(), totalPages = 1, last = true, empty = items.isEmpty())
    }
    val type = com.google.gson.reflect.TypeToken.getParameterized(PageResponse::class.java, AuditLogResponse::class.java).type
    return gson.fromJson(json, type)
}

/** Audit actions the backend really emits: ACCOUNT_* and EVENT_REQUEST_*. Anything else lands in All only. */
enum class AuditCategory(@StringRes val labelRes: Int) {
    ALL(R.string.audit_filter_all),
    EVENT_REQUEST(R.string.audit_filter_event_requests),
    ACCOUNT(R.string.audit_filter_account),
}

/** Server-side `actionPrefix` for a category (null = all actions). */
fun AuditCategory.actionPrefix(): String? = when (this) {
    AuditCategory.ALL -> null
    AuditCategory.EVENT_REQUEST -> "EVENT_REQUEST_"
    AuditCategory.ACCOUNT -> "ACCOUNT_"
}

/** Target column text: target user's name, else details, else null (caller shows the placeholder). */
fun auditTargetText(item: AuditLogResponse): String? =
    item.targetUserFullName?.takeIf { it.isNotBlank() } ?: item.details?.takeIf { it.isNotBlank() }
