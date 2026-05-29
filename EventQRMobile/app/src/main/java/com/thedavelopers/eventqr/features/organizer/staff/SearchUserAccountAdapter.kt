package com.thedavelopers.eventqr.features.organizer.staff

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpStaff

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
        holder.bind(items[position], keyOf(items[position]) == selectedKey)
    }

    override fun getItemCount(): Int = items.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.cardSearchUser)
        private val avatarInitial: TextView = itemView.findViewById(R.id.txtUserAvatarInitial)
        private val nameText: TextView = itemView.findViewById(R.id.txtSearchUserName)
        private val emailText: TextView = itemView.findViewById(R.id.txtSearchUserEmail)
        private val selectedIcon: ImageView = itemView.findViewById(R.id.imgSelectedCheck)

        fun bind(item: OrganizerMvpStaff, selected: Boolean) {
            avatarInitial.text = item.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            nameText.text = item.name
            emailText.text = item.email
            card.strokeWidth = if (selected) itemView.resources.getDimensionPixelSize(R.dimen.staff_selected_stroke) else 0
            card.strokeColor = if (selected) Color.parseColor("#4F46E5") else Color.TRANSPARENT
            selectedIcon.visibility = if (selected) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                selectedKey = keyOf(item)
                notifyDataSetChanged()
                onUserSelected(item)
            }
        }
    }

    private fun keyOf(item: OrganizerMvpStaff): String {
        val normalizedEmail = item.email.trim().lowercase()
        return if (normalizedEmail.isNotBlank()) normalizedEmail else item.id
    }
}
