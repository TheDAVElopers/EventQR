package com.thedavelopers.eventqr.features.admin.users

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountStatus
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse
import com.thedavelopers.eventqr.ui.theme.color

enum class AccountAction {
    ENABLE, DISABLE, DELETE
}

class AdminAccountAdapter(
    private val onActionClick: (UserResponse, AccountAction) -> Unit
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
        private val onActionClick: (UserResponse, AccountAction) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val textAvatar: TextView = itemView.findViewById(R.id.textAccountAvatar)
        private val textName: TextView = itemView.findViewById(R.id.textAccountName)
        private val textEmail: TextView = itemView.findViewById(R.id.textAccountEmail)
        private val textRole: TextView = itemView.findViewById(R.id.textAccountRoleBadge)
        private val textStatus: TextView = itemView.findViewById(R.id.textAccountStatusBadge)
        private val actionMenu: View = itemView.findViewById(R.id.actionMenu)

        fun bind(user: UserResponse) {
            textAvatar.text = user.fullName.trim().take(1).uppercase().ifBlank { "U" }
            textName.text = user.fullName
            textEmail.text = user.email
            textRole.text = formatRole(user.role.name)
            textStatus.text = formatStatus(user.status)
            bindRoleStyle(user.role.name)
            bindStatusStyle(user.status)

            actionMenu?.setOnClickListener { showActionMenu(user) }
        }

        private fun showActionMenu(user: UserResponse) {
            val context = itemView.context
            val menu = PopupMenu(context, actionMenu!!)
            menu.menuInflater.inflate(R.menu.admin_account_actions, menu.menu)

            val enableItem = menu.menu.findItem(R.id.action_enable)
            val disableItem = menu.menu.findItem(R.id.action_disable)
            val deleteItem = menu.menu.findItem(R.id.action_delete)

            when (user.status) {
                AccountStatus.ACTIVE -> {
                    enableItem?.isVisible = false
                    disableItem?.isVisible = true
                }
                AccountStatus.INACTIVE, AccountStatus.SUSPENDED -> {
                    enableItem?.isVisible = true
                    disableItem?.isVisible = false
                }
                else -> {
                    enableItem?.isVisible = false
                    disableItem?.isVisible = false
                }
            }

            if (user.status == AccountStatus.ACTIVE) {
                deleteItem?.isVisible = false
            }

            menu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_enable -> onActionClick(user, AccountAction.ENABLE)
                    R.id.action_disable -> onActionClick(user, AccountAction.DISABLE)
                    R.id.action_delete -> onActionClick(user, AccountAction.DELETE)
                    else -> false
                }
                true
            }
            menu.show()
        }

        private fun formatRole(role: String): String {
            return role.lowercase().replace('_', ' ').split(' ').joinToString(" ") { token ->
                token.replaceFirstChar { it.uppercase() }
            }
        }

        private fun formatStatus(status: AccountStatus): String {
            val normalized = status.name.lowercase().replace('_', ' ')
            return normalized.replaceFirstChar { it.uppercase() }
        }

        private fun bindRoleStyle(role: String) {
            val normalized = role.uppercase()
            val background = when {
                normalized.contains("ADMIN") -> R.drawable.bg_admin_role_badge_pink
                normalized.contains("ORGANIZER") -> R.drawable.bg_admin_role_badge_blue
                normalized.contains("STAFF") -> R.drawable.bg_admin_role_badge_green
                else -> R.drawable.bg_admin_role_badge_purple
            }
            textRole.setBackgroundResource(background)
        }

        private fun bindStatusStyle(status: AccountStatus) {
            when (status) {
                AccountStatus.ACTIVE -> {
                    textStatus.setBackgroundResource(R.drawable.bg_admin_status_active_badge)
                    textStatus.setTextColor(itemView.context.color(R.color.on_success_container))
                }
                AccountStatus.PENDING -> {
                    textStatus.setBackgroundResource(R.drawable.bg_admin_status_pending_badge)
                    textStatus.setTextColor(itemView.context.color(R.color.on_warning_container))
                }
                else -> {
                    textStatus.setBackgroundResource(R.drawable.bg_admin_status_inactive_badge)
                    textStatus.setTextColor(itemView.context.color(R.color.on_error_container))
                }
            }
        }
    }
}