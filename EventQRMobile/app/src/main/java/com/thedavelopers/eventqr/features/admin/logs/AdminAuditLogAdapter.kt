package com.thedavelopers.eventqr.features.admin.logs

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.admin.auditTargetText
import com.thedavelopers.eventqr.features.audit.model.dto.AuditLogResponse
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AdminAuditLogAdapter : RecyclerView.Adapter<AdminAuditLogAdapter.AdminAuditLogViewHolder>() {
    private val rows = mutableListOf<AuditLogResponse>()
    private val timestampFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.of("Asia/Manila"))

    fun submitItems(items: List<AuditLogResponse>) {
        rows.clear()
        rows.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminAuditLogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_audit_log, parent, false)
        return AdminAuditLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: AdminAuditLogViewHolder, position: Int) {
        holder.bind(rows[position], timestampFormatter)
    }

    override fun getItemCount(): Int = rows.size

    class AdminAuditLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textActionTitle: TextView = itemView.findViewById(R.id.textAuditActionTitle)
        private val textAuditActorTarget: TextView = itemView.findViewById(R.id.textAuditActorTarget)
        private val textAuditTimestamp: TextView = itemView.findViewById(R.id.textAuditTimestamp)
        private val iconTile: View = itemView.findViewById(R.id.auditIconTile)
        private val icon: ImageView = itemView.findViewById(R.id.imgAuditIcon)

        fun bind(item: AuditLogResponse, formatter: DateTimeFormatter) {
            val action = item.action.ifBlank { "AUDIT_ENTRY" }
            textActionTitle.text = action.toFriendlyTitle()
            val actor = item.performedByFullName?.takeIf { it.isNotBlank() } ?: itemView.context.getString(R.string.audit_actor_fallback)
            val target = auditTargetText(item) ?: itemView.context.getString(R.string.audit_target_fallback)
            textAuditActorTarget.text = itemView.context.getString(R.string.audit_actor_target, actor, target)
            textAuditTimestamp.text = formatter.format(item.timestamp)
            bindIconStyle(action)
        }

        private fun bindIconStyle(action: String) {
            val normalized = action.lowercase()
            val background: Int
            val tint: Int
            when {
                normalized.contains("approve") || normalized.contains("reject") || normalized.contains("request") -> {
                    background = R.drawable.bg_admin_role_badge_green
                    tint = 0xFF10B981.toInt()
                }
                normalized.contains("disable") || normalized.contains("delete") -> {
                    background = R.drawable.bg_admin_role_badge_pink
                    tint = 0xFFEF4444.toInt()
                }
                normalized.contains("account") || normalized.contains("role") || normalized.contains("user") -> {
                    background = R.drawable.bg_admin_role_badge_blue
                    tint = 0xFF4F46E5.toInt()
                }
                else -> {
                    background = R.drawable.bg_admin_role_badge_purple
                    tint = 0xFF6D3FF2.toInt()
                }
            }
            iconTile.setBackgroundResource(background)
            icon.imageTintList = ColorStateList.valueOf(tint)
        }

        private fun String.toFriendlyTitle(): String {
            return trim().lowercase().split('_', ' ').filter { it.isNotBlank() }.joinToString(" ") { token ->
                token.replaceFirstChar { it.uppercase() }
            }
        }
    }
}