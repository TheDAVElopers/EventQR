package com.thedavelopers.eventqr.features.admin

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.ui.components.EventRequestHolder

class AdminEventRequestAdapter(
    private val onTap: (EventRequestResponse) -> Unit,
) : RecyclerView.Adapter<AdminEventRequestAdapter.AdminEventRequestViewHolder>() {

    private val rows = mutableListOf<EventRequestResponse>()

    fun submit(items: List<EventRequestResponse>) {
        rows.clear()
        rows.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminEventRequestViewHolder {
        return AdminEventRequestViewHolder(EventRequestHolder(parent.context))
    }

    override fun onBindViewHolder(holder: AdminEventRequestViewHolder, position: Int) {
        holder.bind(rows[position], onTap)
    }

    override fun getItemCount(): Int = rows.size

    class AdminEventRequestViewHolder(
        private val holder: EventRequestHolder,
    ) : RecyclerView.ViewHolder(holder.view) {

        fun bind(
            request: EventRequestResponse,
            onTap: (EventRequestResponse) -> Unit,
        ) {
            holder.onClick = onTap
            holder.update(request)
        }
    }
}
