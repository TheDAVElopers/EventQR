package com.thedavelopers.eventqr.features.admin.logs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.admin.auditTargetText
import com.thedavelopers.eventqr.features.audit.AuditActionCopy
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
        private val context = itemView.context
        private val textTime: TextView = itemView.findViewById(R.id.textAuditTime)
        private val textTimeInline: TextView = itemView.findViewById(R.id.textAuditTimeInline)
        private val textAction: TextView = itemView.findViewById(R.id.textAuditAction)
        private val textActor: TextView = itemView.findViewById(R.id.textAuditActor)
        private val textTarget: TextView = itemView.findViewById(R.id.textAuditTarget)
        private val metaDot: View = itemView.findViewById(R.id.auditMetaDot)

        fun bind(item: AuditLogResponse) {
            val time = DateFormatters.auditTime(item.timestamp)
            val action = AuditActionCopy.copyFor(context, item.action)
            val actor = item.performedByFullName?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.audit_actor_fallback)
            val target = auditTargetText(item)

            // §6.5 reflow: at fontScale > 1.3 a 96dp column cannot hold `9:41 AM` beside a two-line
            // stack on a 360dp screen, so the time moves into the meta line.
            val reflowed = context.resources.configuration.fontScale > REFLOW_FONT_SCALE
            textTime.visibility = if (reflowed) View.GONE else View.VISIBLE
            textTime.text = time
            textTimeInline.visibility = if (reflowed) View.VISIBLE else View.GONE
            textTimeInline.text = time

            textAction.text = action
            textActor.text = context.getString(R.string.ui_audit_meta_by, actor)
            textTarget.text = target
            textTarget.visibility = if (target == null) View.GONE else View.VISIBLE
            metaDot.visibility = textTarget.visibility

            itemView.contentDescription = if (target == null) {
                context.getString(R.string.ui_audit_row_description_no_target, time, action, actor)
            } else {
                context.getString(R.string.ui_audit_row_description, time, action, actor, target)
            }
        }
    }

    private companion object {
        /** §6.5: above this font scale the leading time column reflows into the meta line. */
        const val REFLOW_FONT_SCALE = 1.3f
    }
}
