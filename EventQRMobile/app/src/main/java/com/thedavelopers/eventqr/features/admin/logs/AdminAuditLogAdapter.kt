package com.thedavelopers.eventqr.features.admin.logs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.audit.model.dto.AuditLogResponse

/**
 * Audit rows (§6.5 v1.6). Action-led, two-line, no card, no per-type icon or tint: the verb is the
 * only differentiator. The row carries no copy logic and no formatter - both live in the data layer.
 *
 * TalkBack: one node per row (`9:41 AM. Approved an event request by Maria Santos, DevFest Manila.`).
 */
class AdminAuditLogAdapter : RecyclerView.Adapter<AdminAuditLogAdapter.AdminAuditLogViewHolder>() {
    private val rows = mutableListOf<AuditLogResponse>()

    /** The load that produced [rows]; a different id means the set is replaced, not appended to. */
    private var loadId: Int? = null

    /**
     * Replaces the row set for one load. A new [loadId] (first load, filter change, retry) always
     * performs a full refresh; inside one load paging only appends, so the common case notifies
     * just the inserted range instead of rebinding every accumulated row. Identical input notifies
     * nothing. Rows that merely share a prefix under a different [loadId] are never appended to.
     */
    fun submitItems(items: List<AuditLogResponse>, loadId: Int) {
        val sameLoad = this.loadId == loadId
        this.loadId = loadId
        val appended = sameLoad && items.size >= rows.size && rows.indices.all { rows[it] == items[it] }
        val previousSize = rows.size
        rows.clear()
        rows.addAll(items)
        when {
            !appended -> notifyDataSetChanged()
            items.size > previousSize -> notifyItemRangeInserted(previousSize, items.size - previousSize)
            // Identical set inside one load: nothing changed, so nothing to notify.
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminAuditLogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_audit_log, parent, false)
        return AdminAuditLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: AdminAuditLogViewHolder, position: Int) {
        holder.bind(rows[position])
    }

    override fun getItemCount(): Int = rows.size

    class AdminAuditLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(item: AuditLogResponse) {
            AuditLogRowBinder.bind(itemView, item)
        }
    }
}
