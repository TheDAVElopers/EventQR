package com.thedavelopers.eventqr.features.admin.users

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountStatus
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse

enum class AccountAction {
    ENABLE, DISABLE, DELETE
}

/**
 * Account rows (§5.5). One badge for the account status, chosen from the shared tone table
 * (§5.4); the role is plain text in the meta line. No per-status drawables and no per-role
 * colour tiles - role is not a state.
 */
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
            textStatus.setTextAppearance(toneFor(user.status))
            actionMenu.setOnClickListener { showActionMenu(user) }
        }

        private fun showActionMenu(user: UserResponse) {
            val context = itemView.context
            val menu = PopupMenu(context, actionMenu)
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

        /** `Active` confirms nothing on this screen, so it is success; every other state is neutral. */
        private fun toneFor(status: AccountStatus): Int = when (status) {
            AccountStatus.PENDING -> R.style.Widget_EventQR_Ui_StatusBadge_Warning
            AccountStatus.INACTIVE, AccountStatus.SUSPENDED -> R.style.Widget_EventQR_Ui_StatusBadge_Error
            else -> R.style.Widget_EventQR_Ui_StatusBadge_Success
        }
    }
}
