package com.thedavelopers.eventqr.core.util

import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import android.content.Context

/**
 * Single source of truth for the portal-switcher feature.
 *
 * Every user always has the Attendee Portal. Users additionally hold exactly the portal
 * matching the single role assigned to their account. Roles are NOT cumulative: an ADMIN
 * only sees Admin Portal (+ Attendee Portal), a SUPER_ADMIN only sees
 * Super Admin Portal (+ Attendee Portal), a STAFF only sees Staff Portal (+ Attendee Portal),
 * and a plain attendee sees only the Attendee Portal (the switcher stays hidden).
 */
object PortalSwitcher {
    const val PORTAL_ATTENDEE = "Attendee Portal"
    const val PORTAL_STAFF = "Staff Portal"
    const val PORTAL_ORGANIZER = "Organizer Portal"
    const val PORTAL_ADMIN = "Admin Portal"
    const val PORTAL_SUPER_ADMIN = "Super Admin Portal"

    /**
     * The portals a user with the given [normalizedRole] (see [RoleMapper.normalizeRole])
     * may switch to. Any role that is not one of the elevated roles yields the Attendee
     * portal only, so off-app or unexpected role values can never leak elevated portals.
     */
    fun portalsForRole(normalizedRole: String?): List<String> = buildList {
        add(PORTAL_ATTENDEE)
        when (normalizedRole) {
            AccountRole.STAFF.name -> add(PORTAL_STAFF)
            AccountRole.ORGANIZER.name -> add(PORTAL_ORGANIZER)
            AccountRole.ADMIN.name -> add(PORTAL_ADMIN)
            AccountRole.SUPER_ADMIN.name -> add(PORTAL_SUPER_ADMIN)
        }
    }

    /** Row icon for a portal option. */
    fun iconRes(portal: String): Int = when (portal) {
        PORTAL_ATTENDEE -> R.drawable.ic_nav_profile
        PORTAL_STAFF -> R.drawable.ic_qr_scan
        PORTAL_ORGANIZER -> R.drawable.ic_calendar
        PORTAL_ADMIN, PORTAL_SUPER_ADMIN -> R.drawable.ic_group
        else -> R.drawable.ic_nav_home
    }

    /** Localized portal name; the PORTAL_* constants stay stable identifiers used for comparisons. */
    fun title(context: Context, portal: String): String = when (portal) {
        PORTAL_ATTENDEE -> context.getString(R.string.item_portal_option_attendee_portal)
        PORTAL_STAFF -> context.getString(R.string.portal_staff_portal)
        PORTAL_ORGANIZER -> context.getString(R.string.organizer_dashboard_organizer_portal)
        PORTAL_ADMIN -> context.getString(R.string.admin_dashboard_admin_portal)
        PORTAL_SUPER_ADMIN -> context.getString(R.string.portal_super_admin_portal)
        else -> portal
    }

    /** Row subtitle for a portal option. */
    fun subtitle(context: Context, portal: String): String = when (portal) {
        PORTAL_ATTENDEE -> context.getString(R.string.item_portal_option_events_rewards_and_your_profile)
        PORTAL_STAFF -> context.getString(R.string.portal_scan_qr_codes_and_manage_entries)
        PORTAL_ORGANIZER -> context.getString(R.string.portal_manage_your_events_and_attendees)
        PORTAL_ADMIN -> context.getString(R.string.portal_platform_administration_and_oversigh)
        PORTAL_SUPER_ADMIN -> context.getString(R.string.portal_full_platform_administration_and_con)
        else -> context.getString(R.string.portal_open_portal)
    }
}