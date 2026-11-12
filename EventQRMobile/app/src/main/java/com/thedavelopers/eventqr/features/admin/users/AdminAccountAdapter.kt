package com.thedavelopers.eventqr.features.admin.users

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.AccountStatus
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse
import com.thedavelopers.eventqr.ui.components.ListRowBinder

enum class AccountAction {
    DELETE
}

/**
 * Account rows (§5.5). One badge for the account status, chosen from the shared tone table
 * (§5.4); the role is not shown because role is not a state. No per-status drawables and no
 * per-role colour tiles. The overflow holds one action, Delete.
 */
class AdminAccountAdapter(
    private val onActionClick: (UserResponse) -> Unit
) : RecyclerView.Adapter<AdminAccountAdapter.AdminAccountViewHolder>() {

    private val items = mutableListOf<UserResponse>()

    fun submitItems(users: List<UserResponse>) {
        items.clear()
        items.addAll(users)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminAccountViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_account, parent, false)
        return AdminAccountViewHolder(view, onActionClick)
    }

    override fun onBindViewHolder(holder: AdminAccountViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class AdminAccountViewHolder(
        itemView: View,
        private val onActionClick: (UserResponse) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val textAvatar: TextView = itemView.findViewById(R.id.textAccountAvatar)
        private val textName: TextView = itemView.findViewById(R.id.textAccountName)
        private val textEmail: TextView = itemView.findViewById(R.id.textAccountEmail)
        private val textStatus: TextView = itemView.findViewById(R.id.textAccountStatusBadge)
        private val actionMenu: View = itemView.findViewById(R.id.actionMenu)

        fun bind(user: UserResponse) {
            textAvatar.text = user.fullName.trim().take(1).uppercase().ifBlank { "U" }
            textName.text = user.fullName
            // Role is not a state, so it stays meta text - but it must be visible before delete.
            textEmail.text = itemView.context.getString(
                R.string.ui_admin_accounts_meta,
                user.email,
                roleLabel(user.role),
            )
            textStatus.text = statusLabel(user.status)
            ListRowBinder.applyBadgeTone(textStatus, toneFor(user.status))
            // Delete is irreversible and the server rejects both cases; never offer it here.
            val canDelete = user.status != AccountStatus.ACTIVE && user.role != AccountRole.SUPER_ADMIN
            actionMenu.visibility = if (canDelete) View.VISIBLE else View.GONE
            if (!canDelete) {
                actionMenu.setOnClickListener(null)
                return
            }
            // The row announces whose menu this is; a bare "More" is ambiguous in a long list.
            actionMenu.contentDescription =
                itemView.context.getString(R.string.ui_admin_accounts_actions_for, user.fullName)
            actionMenu.setOnClickListener { showActionMenu(user) }
        }

        private fun showActionMenu(user: UserResponse) {
            val menu = PopupMenu(itemView.context, actionMenu)
            menu.menuInflater.inflate(R.menu.admin_account_actions, menu.menu)
            menu.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_delete) {
                    onActionClick(user)
                    true
                } else {
                    false
                }
            }
            menu.show()
        }

        private fun roleLabel(role: AccountRole): String = itemView.context.getString(
            when (role) {
                AccountRole.ATTENDEE -> R.string.ui_admin_role_attendee
                AccountRole.STAFF -> R.string.ui_admin_role_staff
                AccountRole.ORGANIZER -> R.string.ui_admin_role_organizer
                AccountRole.ADMIN -> R.string.ui_admin_role_admin
                AccountRole.SUPER_ADMIN -> R.string.ui_admin_role_super_admin
            },
        )

        private fun statusLabel(status: AccountStatus): String = itemView.context.getString(
            when (status) {
                AccountStatus.ACTIVE -> R.string.ui_admin_accounts_status_active
                AccountStatus.INACTIVE -> R.string.ui_admin_accounts_status_inactive
                AccountStatus.SUSPENDED -> R.string.ui_admin_accounts_status_suspended
                AccountStatus.PENDING -> R.string.ui_admin_accounts_status_pending
                else -> R.string.ui_admin_accounts_status_unknown
            },
        )

        /**
         * `Active` confirms nothing on this screen, so it is the only success tone. Suspended is
         * the error state; inactive/pending and an unmapped status stay neutral - never success.
         */
        private fun toneFor(status: AccountStatus): Int = when (status) {
            AccountStatus.ACTIVE -> R.style.Widget_EventQR_Ui_StatusBadge_Success
            AccountStatus.SUSPENDED -> R.style.Widget_EventQR_Ui_StatusBadge_Error
            AccountStatus.INACTIVE, AccountStatus.PENDING -> R.style.Widget_EventQR_Ui_StatusBadge_Neutral
            else -> R.style.Widget_EventQR_Ui_StatusBadge_Neutral
        }
    }
}
