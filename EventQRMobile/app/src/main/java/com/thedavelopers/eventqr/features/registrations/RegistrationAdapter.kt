package com.thedavelopers.eventqr.features.registrations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import java.util.UUID

class RegistrationAdapter(
    private val onClick: ((RegistrationResponse) -> Unit)? = null,
    private val onSelectionChanged: ((Int) -> Unit)? = null,
) : RecyclerView.Adapter<RegistrationAdapter.ViewHolder>() {

    private val items = mutableListOf<RegistrationResponse>()
    private var selectionMode = false
    private val selectedIds = mutableSetOf<UUID>()
    // Selected registrations are remembered here so a selection survives paging and search changes
    // (the visible list is only one page / one query of the full result set).
    private val selectedItems = LinkedHashMap<UUID, RegistrationResponse>()

    fun submitItems(newItems: List<RegistrationResponse>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    /** Selects every selectable (QR-issued) registration in [all], including ones not currently shown. */
    fun selectAll(all: List<RegistrationResponse>) {
        all.filter { it.qrCredentialId != null }.forEach {
            selectedIds.add(it.attendeeUserId)
            selectedItems[it.attendeeUserId] = it
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) clearSelectionInternal(notify = true)
        notifyDataSetChanged()
    }

    fun isSelectionMode(): Boolean = selectionMode

    fun isSelected(registration: RegistrationResponse): Boolean = registration.attendeeUserId in selectedIds

    fun getSelectedItems(): List<RegistrationResponse> = selectedItems.values.toList()

    private fun select(registration: RegistrationResponse, selected: Boolean) {
        if (selected) {
            selectedIds.add(registration.attendeeUserId)
            selectedItems[registration.attendeeUserId] = registration
        } else {
            selectedIds.remove(registration.attendeeUserId)
            selectedItems.remove(registration.attendeeUserId)
        }
    }

    fun toggleSelection(registration: RegistrationResponse) {
        if (!selectionMode) return
        select(registration, registration.attendeeUserId !in selectedIds)
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun toggleSelectAll() {
        selectAll(items)
    }

    /** True when every selectable row currently shown is selected. */
    fun isAllSelected(): Boolean {
        val selectable = items.filter { it.qrCredentialId != null }
        return selectable.isNotEmpty() && selectable.all { it.attendeeUserId in selectedIds }
    }

    fun clearSelection() {
        clearSelectionInternal(notify = true)
    }

    private fun clearSelectionInternal(notify: Boolean) {
        if (selectedIds.isEmpty()) return
        selectedIds.clear()
        selectedItems.clear()
        if (notify) {
            notifyDataSetChanged()
            onSelectionChanged?.invoke(0)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_registration, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkView: CheckBox = itemView.findViewById(R.id.chkRegistrationSelect)
        private val avatarView: TextView = itemView.findViewById(R.id.txtRegistrationAvatar)
        private val titleView: TextView = itemView.findViewById(R.id.txtRegistrationTitle)
        private val detailView: TextView = itemView.findViewById(R.id.txtRegistrationDetails)
        private val statusView: TextView = itemView.findViewById(R.id.txtRegistrationStatus)
        private val pointsView: TextView = itemView.findViewById(R.id.txtRegistrationPoints)

        fun bind(item: RegistrationResponse) {
            val name = item.attendeeName.ifBlank { "Attendee" }
            avatarView.text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "A"
            titleView.text = name
            detailView.text = item.attendeeEmail.ifBlank { "No email provided" }
            pointsView.text = "${item.pointsEarned} pts"
            RegistrationStatusBadgeStyler.bind(statusView, item.status)

            checkView.visibility = if (selectionMode) View.VISIBLE else View.GONE
            checkView.isChecked = isSelected(item)
            checkView.isEnabled = item.qrCredentialId != null

            (itemView as? androidx.cardview.widget.CardView)?.setCardBackgroundColor(
                if (selectionMode && isSelected(item)) 0xFFE0E7FF.toInt() else android.graphics.Color.WHITE
            )

            itemView.setOnClickListener {
                if (selectionMode) {
                    toggleSelection(item)
                } else {
                    onClick?.invoke(item)
                }
            }
            checkView.setOnClickListener {
                select(item, checkView.isChecked)
                (itemView as? androidx.cardview.widget.CardView)?.setCardBackgroundColor(
                    if (isSelected(item)) 0xFFE0E7FF.toInt() else android.graphics.Color.WHITE
                )
                onSelectionChanged?.invoke(selectedIds.size)
            }
        }
    }
}
