package com.thedavelopers.eventqr.features.organizer.staff

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpStaff

/**
 * Account rows on the shared geometry. Selection is a fill + one badge (§5.4), not a card stroke.
 */
class SearchUserAccountAdapter(
    private val onUserSelected: (OrganizerMvpStaff) -> Unit,
) : RecyclerView.Adapter<SearchUserAccountAdapter.UserViewHolder>() {
    private val items = mutableListOf<OrganizerMvpStaff>()
    private var selectedKey: String? = null

    fun submitItems(newItems: List<OrganizerMvpStaff>) {
        items.clear()
        items.addAll(newItems)
        if (selectedKey != null && items.none { keyOf(it) == selectedKey }) {
            selectedKey = null
        }
        notifyDataSetChanged()
    }

    fun setSelected(item: OrganizerMvpStaff?) {
        selectedKey = item?.let { keyOf(it) }
        notifyDataSetChanged()
    }

    fun selectedUser(): OrganizerMvpStaff? = items.firstOrNull { keyOf(it) == selectedKey }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_user_account, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(items[position], keyOf(items[position]) == selectedKey, position == items.lastIndex)
    }

    override fun getItemCount(): Int = items.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: View = itemView.findViewById(R.id.userRowContainer)
        private val avatarInitial: TextView = itemView.findViewById(R.id.txtUserAvatarInitial)
        private val nameText: TextView = itemView.findViewById(R.id.txtSearchUserName)
        private val emailText: TextView = itemView.findViewById(R.id.txtSearchUserEmail)
        private val selectedBadge: TextView = itemView.findViewById(R.id.txtUserSelected)

        fun bind(item: OrganizerMvpStaff, selected: Boolean, isLast: Boolean) {
            avatarInitial.text = item.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            nameText.text = item.name
            emailText.text = item.email
            selectedBadge.visibility = if (selected) View.VISIBLE else View.GONE
            // The row's fill carries selection; state_selected also drives the pressed colour.
            container.isSelected = selected

            container.setOnClickListener {
                selectedKey = keyOf(item)
                notifyDataSetChanged()
                onUserSelected(item)
            }
            itemView.findViewById<View>(R.id.userRowDivider).visibility =
                if (isLast) View.GONE else View.VISIBLE
        }
    }

    private fun keyOf(item: OrganizerMvpStaff): String {
        val normalizedEmail = item.email.trim().lowercase()
        return if (normalizedEmail.isNotBlank()) normalizedEmail else item.id
    }
}
