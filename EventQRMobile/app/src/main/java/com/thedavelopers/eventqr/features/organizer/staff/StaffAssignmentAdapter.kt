package com.thedavelopers.eventqr.features.organizer.staff

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpStaff

class StaffAssignmentAdapter(
    private val onRemoveClicked: (OrganizerMvpStaff) -> Unit,
) : RecyclerView.Adapter<StaffAssignmentAdapter.StaffViewHolder>() {
    private val items = mutableListOf<OrganizerMvpStaff>()

    fun submitItems(newItems: List<OrganizerMvpStaff>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StaffViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_staff_assignment, parent, false)
        return StaffViewHolder(view)
    }

    override fun onBindViewHolder(holder: StaffViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class StaffViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarInitial: TextView = itemView.findViewById(R.id.txtStaffAvatarInitial)
        private val nameText: TextView = itemView.findViewById(R.id.txtStaffName)
        private val emailText: TextView = itemView.findViewById(R.id.txtStaffEmail)
        private val removeText: TextView = itemView.findViewById(R.id.txtRemoveStaff)

        fun bind(item: OrganizerMvpStaff) {
            avatarInitial.text = item.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            nameText.text = item.name
            emailText.text = item.email
            removeText.setOnClickListener { onRemoveClicked(item) }
        }
    }
}
