package com.thedavelopers.eventqr.features.admin

import androidx.annotation.StringRes
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.util.RoleMapper

/**
 * The one role-to-label map for the admin screens' compact pass-head eyebrow (Requests, Accounts,
 * Logs). A SUPER_ADMIN must never collapse into `ADMIN`, the same collapse that
 * `PortalSwitcher.shortRoleName` already guards on the portal tabs.
 *
 * The strings stay sentence case because the eyebrow renders through
 * `TextAppearance.EventQR.Type.Overline`, which is all caps: "Super admin" prints as SUPER ADMIN.
 */
object AdminRoleLabels {

    /** Eyebrow label for an admin screen. Anything that is not SUPER_ADMIN reads as admin. */
    @StringRes
    fun eyebrowRes(role: String?): Int =
        if (RoleMapper.normalizeRole(role) == AccountRole.SUPER_ADMIN.name) {
            R.string.ui_admin_role_super_admin
        } else {
            R.string.ui_admin_role_admin
        }
}
