package com.thedavelopers.eventqr.core.util

import com.thedavelopers.eventqr.core.api.dto.AccountRole
import android.content.Context
import com.thedavelopers.eventqr.R

object RoleMapper {
    /**
     * Normalizes a role string from the backend into a consistent uppercase format.
     * Maps 'USER' variations to 'ATTENDEE'.
     */
    fun normalizeRole(role: String?): String {
        if (role.isNullOrBlank()) return ""
        
        val upper = role.trim().uppercase()
        return when (upper) {
            "USER", "ATTENDEE" -> AccountRole.ATTENDEE.name
            "STAFF" -> AccountRole.STAFF.name
            "ORGANIZER" -> AccountRole.ORGANIZER.name
            "ADMIN" -> AccountRole.ADMIN.name
            "SUPER_ADMIN", "SUPERADMIN" -> AccountRole.SUPER_ADMIN.name
            else -> upper // Return as-is if unknown, let the router handle it
        }
    }

    /**
     * Privilege ranks for floor checks. Explicit values, never enum declaration order
     * (declaration order would rank STAFF above ORGANIZER, which is backwards).
     * 'USER' normalizes to ATTENDEE, so it shares rank 0 and is never a distinct tier.
     */
    private val RANK: Map<String, Int> = mapOf(
        AccountRole.ATTENDEE.name to 0,
        AccountRole.STAFF.name to 1,
        AccountRole.ORGANIZER.name to 2,
        AccountRole.ADMIN.name to 3,
        AccountRole.SUPER_ADMIN.name to 4,
    )

    /** Returns the privilege rank of [role], or -1 for null/blank/unknown roles. */
    fun rankOf(role: String?): Int = RANK[normalizeRole(role)] ?: -1

    /** True when [role] is known and ranks at least as high as [min]. Unknown roles fail closed. */
    fun isAtLeast(role: String?, min: AccountRole): Boolean {
        val r = rankOf(role)
        val m = rankOf(min.name)
        return r >= 0 && m >= 0 && r >= m
    }

    /**
     * Maps a normalized role string to a displayable name.
     */
    fun getDisplayNameRes(role: String?): Int? = when (normalizeRole(role)) {
            AccountRole.ATTENDEE.name -> R.string.common_attendee
            AccountRole.STAFF.name -> R.string.staff_dashboard_staff
            AccountRole.ORGANIZER.name -> R.string.organizer_dashboard_organizer
            AccountRole.ADMIN.name -> R.string.role_administrator
            AccountRole.SUPER_ADMIN.name -> R.string.role_super_admin
        else -> null
    }

    fun getDisplayName(context: Context, role: String?): String =
        getDisplayNameRes(role)?.let { context.getString(it) } ?: normalizeRole(role).lowercase().capitalize()
}
