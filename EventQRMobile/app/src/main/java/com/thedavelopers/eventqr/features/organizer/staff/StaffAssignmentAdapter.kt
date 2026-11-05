package com.thedavelopers.eventqr.features.organizer.staff

import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.organizer.OrganizerMvpStaff

/**
 * Staff rows on the 72dp person-row geometry. One trailing element only: an overflow menu holding
 * "Edit permissions" and the destructive "Remove from event" (5.5), so no committing action sits
 * one tap away from a row press.
 */
class StaffAssignmentAdapter(
    private val onRemoveClicked: (OrganizerMvpStaff) -> Unit,
    private val onEditPermissionsClicked: (OrganizerMvpStaff) -> Unit = {},
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
        holder.bind(items[position], position == items.lastIndex)
    }

    override fun getItemCount(): Int = items.size

    inner class StaffViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarInitial: TextView = itemView.findViewById(R.id.txtStaffAvatarInitial)
        private val nameText: TextView = itemView.findViewById(R.id.txtStaffName)
        private val permissionsText: TextView = itemView.findViewById(R.id.txtStaffPermissions)
        private val actionsButton: ImageButton = itemView.findViewById(R.id.btnStaffRowActions)

        fun bind(item: OrganizerMvpStaff, isLast: Boolean) {
            val context = itemView.context
            avatarInitial.text = item.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            nameText.text = item.name
            // A revoked assignment makes its permissions moot, so the state replaces the summary.
            val accessActive = item.accessStatus.equals("Active", ignoreCase = true)
            permissionsText.text = if (accessActive) {
                staffPermissionSummary(context, item)
            } else {
                context.getString(R.string.ui_staff_access_disabled)
            }
            permissionsText.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (accessActive) R.color.ui_text_secondary else R.color.ui_error,
                ),
            )
            actionsButton.contentDescription = context.getString(R.string.ui_staff_row_actions, item.name)
            actionsButton.setOnClickListener { showActions(item) }
            itemView.findViewById<View>(R.id.staffRowDivider).visibility =
                if (isLast) View.GONE else View.VISIBLE
        }

        private fun showActions(item: OrganizerMvpStaff) {
            val context = itemView.context
            val menu = PopupMenu(context, actionsButton)
            menu.menu.add(0, MENU_EDIT_PERMISSIONS, 0, context.getString(R.string.staff_edit_permissions))
            val removeLabel = SpannableString(context.getString(R.string.ui_staff_remove_from_event)).apply {
                setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.ui_error)),
                    0,
                    length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            menu.menu.add(0, MENU_REMOVE, 1, removeLabel)
            menu.setOnMenuItemClickListener { entry ->
                when (entry.itemId) {
                    MENU_EDIT_PERMISSIONS -> onEditPermissionsClicked(item)
                    MENU_REMOVE -> onRemoveClicked(item)
                }
                true
            }
            menu.show()
        }
    }

    private companion object {
        const val MENU_EDIT_PERMISSIONS = 1
        const val MENU_REMOVE = 2
    }
}
