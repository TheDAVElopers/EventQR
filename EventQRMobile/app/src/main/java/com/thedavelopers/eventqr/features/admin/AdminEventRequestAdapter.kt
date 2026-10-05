package com.thedavelopers.eventqr.features.admin

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.ui.components.EventRequestCard
import com.thedavelopers.eventqr.ui.theme.EventQrTheme

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
        val composeView = ComposeView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        return AdminEventRequestViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: AdminEventRequestViewHolder, position: Int) {
        holder.bind(rows[position], onTap)
    }

    override fun getItemCount(): Int = rows.size

    class AdminEventRequestViewHolder(private val composeView: ComposeView) : RecyclerView.ViewHolder(composeView) {
        fun bind(
            request: EventRequestResponse,
            onTap: (EventRequestResponse) -> Unit,
        ) {
            composeView.setContent {
                EventQrTheme {
                    EventRequestCard(
                        request = request,
                        onClick = { onTap(request) },
                    )
                }
            }
        }
    }
}