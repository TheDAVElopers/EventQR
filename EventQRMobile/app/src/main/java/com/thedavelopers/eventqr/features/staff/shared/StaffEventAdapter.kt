package com.thedavelopers.eventqr.features.staff

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse

class StaffEventAdapter(private val onEventClick: (StaffAssignedEventResponse) -> Unit) : RecyclerView.Adapter<StaffEventAdapter.ViewHolder>() {
    private val items = mutableListOf<StaffAssignedEventResponse>()

    fun submitItems(newItems: List<StaffAssignedEventResponse>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_staff_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.titleView.text = item.title
        holder.timeView.text = DateFormatters.formatInstant(item.eventStartAt)
        holder.itemView.setOnClickListener { onEventClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleView: TextView = itemView.findViewById(R.id.txtEventTitle)
        val timeView: TextView = itemView.findViewById(R.id.txtEventTime)
    }
}
