package com.thedavelopers.eventqr.features.admin.logs

import android.view.View
import android.widget.TextView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.admin.auditTargetText
import com.thedavelopers.eventqr.features.audit.AuditActionCopy
import com.thedavelopers.eventqr.features.audit.model.dto.AuditLogResponse

/**
 * Binds one `item_admin_audit_log.xml` row (§6.5 v1.6). Shared by the audit list and the admin
 * dashboard's read-only activity preview, so the row copy, the reflow rule and the TalkBack
 * sentence cannot drift between the two.
 */
internal object AuditLogRowBinder {

    /** §6.5: above this font scale the leading time column reflows into the meta line. */
    private const val REFLOW_FONT_SCALE = 1.3f

    fun bind(itemView: View, item: AuditLogResponse) {
        val context = itemView.context
        val textTime = itemView.findViewById<TextView>(R.id.textAuditTime)
        val textTimeInline = itemView.findViewById<TextView>(R.id.textAuditTimeInline)
        val textAction = itemView.findViewById<TextView>(R.id.textAuditAction)
        val textActor = itemView.findViewById<TextView>(R.id.textAuditActor)
        val textTarget = itemView.findViewById<TextView>(R.id.textAuditTarget)
        val metaDot = itemView.findViewById<View>(R.id.auditMetaDot)

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
