package com.thedavelopers.eventqr.features.admin.logs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.audit.model.dto.AuditLogResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * Audit row binding (§6.5): the leading time column at the default font scale, the meta-line reflow
 * above 1.3x, and the single TalkBack sentence for the whole row.
 *
 * The timestamp is `2026-10-12T01:41:00Z`, i.e. 9:41 AM in the audit zone (Asia/Manila).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AdminAuditLogAdapterTest {

    private val context: Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_EventQR)

    private fun item(target: String? = "DevFest Manila") = AuditLogResponse(
        auditLogId = UUID.randomUUID(),
        action = "EVENT_REQUEST_APPROVED",
        details = "server free text that must never reach a row",
        performedByUserId = UUID.randomUUID(),
        performedByFullName = "Maria Santos",
        targetUserFullName = target,
        timestamp = Instant.parse("2026-10-12T01:41:00Z"),
    )

    private fun bind(item: AuditLogResponse): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_admin_audit_log, FrameLayout(context), false)
        AdminAuditLogAdapter.AdminAuditLogViewHolder(view).bind(item)
        return view
    }

    @Test
    fun bindsTimeActionActorAndTargetInOneNode() {
        val view = bind(item())

        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.textAuditTime).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.textAuditTimeInline).visibility)
        assertEquals("9:41 AM", view.findViewById<TextView>(R.id.textAuditTime).text)
        assertEquals("Approved an event request", view.findViewById<TextView>(R.id.textAuditAction).text)
        assertEquals("by Maria Santos", view.findViewById<TextView>(R.id.textAuditActor).text)
        assertEquals("DevFest Manila", view.findViewById<TextView>(R.id.textAuditTarget).text)
        assertEquals(
            "9:41 AM. Approved an event request by Maria Santos, DevFest Manila.",
            view.contentDescription,
        )
    }

    @Test
    @Config(fontScale = 2.0f)
    fun reflowsTheTimeIntoTheMetaLineAboveOnePointThree() {
        val view = bind(item())

        assertEquals(View.GONE, view.findViewById<View>(R.id.textAuditTime).visibility)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.textAuditTimeInline).visibility)
        assertEquals("9:41 AM", view.findViewById<TextView>(R.id.textAuditTimeInline).text)
        assertEquals(
            "9:41 AM. Approved an event request by Maria Santos, DevFest Manila.",
            view.contentDescription,
        )
    }

    @Test
    fun blankTargetDropsTheDotAndTheTargetFromTheDescription() {
        val view = bind(item(target = null))

        assertEquals(View.GONE, view.findViewById<View>(R.id.auditMetaDot).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.textAuditTarget).visibility)
        assertEquals("9:41 AM. Approved an event request by Maria Santos.", view.contentDescription)
    }

    @Test
    fun submitNotifiesOnlyWhatChangedInsideOneLoad() {
        val adapter = AdminAuditLogAdapter()
        val events = mutableListOf<String>()
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                events += "changed"
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                events += "inserted($positionStart,$itemCount)"
            }
        })

        val page = listOf(item(), item())
        val nextPage = page + item()
        adapter.submitItems(page, loadId = 1) // first load for this id: the set is replaced
        adapter.submitItems(nextPage, loadId = 1) // next page: append the tail only
        adapter.submitItems(nextPage, loadId = 1) // identical: nothing to notify
        adapter.submitItems(page, loadId = 2) // new load: full refresh
        adapter.submitItems(listOf(item(), item(), item()), loadId = 2) // grows, but a different set

        assertEquals(listOf("changed", "inserted(2,1)", "changed", "changed"), events)
    }
}
