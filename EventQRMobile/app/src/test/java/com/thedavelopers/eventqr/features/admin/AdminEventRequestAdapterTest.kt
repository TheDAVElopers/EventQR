package com.thedavelopers.eventqr.features.admin

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Request row binding (§6.5): one TalkBack node in reading order with a named click action, no child
 * read twice, and the badge leaving the row's end for the text column above fontScale 1.3.
 *
 * `2026-10-12T01:41:00Z` is 12 Oct 2026 in the event zone (Asia/Manila).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AdminEventRequestAdapterTest {

    private val context: Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_EventQR)

    private fun item() = EventRequestResponse(
        eventRequestId = UUID.randomUUID(),
        requesterUserId = UUID.randomUUID(),
        eventName = "DevFest Manila",
        requesterName = "Maria Santos",
        status = EventRequestStatus.PENDING,
        createdAt = Instant.parse("2026-10-12T01:41:00Z"),
    )

    private fun bind(item: EventRequestResponse, onTap: (EventRequestResponse) -> Unit = {}): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_admin_event_request, FrameLayout(context), false)
        AdminEventRequestAdapter.AdminEventRequestViewHolder(view).bind(item, onTap)
        return view
    }

    @Test
    fun bindsOneNodeInReadingOrder() {
        val row = bind(item()).findViewById<View>(R.id.requestRow)
        assertEquals(
            "DevFest Manila. Pending review. Maria Santos. Submitted 12\u00A0Oct\u00A02026.",
            row.contentDescription,
        )
    }

    @Test
    fun everyChildIsHiddenFromTalkBack() {
        val view = bind(item())
        val leaves = listOf(
            R.id.textRequestAvatar,
            R.id.textRequestTitle,
            R.id.textRequestRequester,
            R.id.textRequestSubmitted,
            R.id.textRequestStatus,
        )
        for (id in leaves) {
            assertEquals(
                "child $id must not be its own node",
                View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                view.findViewById<View>(id).importantForAccessibility,
            )
        }
    }

    @Test
    fun theClickIsNamedAsItsOutcome() {
        val row = bind(item()).findViewById<View>(R.id.requestRow)
        val info = AccessibilityNodeInfo.obtain()
        row.onInitializeAccessibilityNodeInfo(info)
        assertEquals(
            "Open request",
            info.actionList.first { it.id == AccessibilityNodeInfo.ACTION_CLICK }.label,
        )
    }

    @Test
    fun tapReportsTheWholeRequest() {
        val request = item()
        var tapped: EventRequestResponse? = null
        bind(request) { tapped = it }.findViewById<View>(R.id.requestRow).performClick()
        assertEquals(request, tapped)
    }

    @Test
    fun badgeTrailsTheTextColumnAtTheDefaultScale() {
        val view = bind(item())
        assertEquals(
            view.findViewById<View>(R.id.requestRow),
            view.findViewById<View>(R.id.textRequestStatus).parent,
        )
    }

    @Test
    @Config(fontScale = 2.0f)
    fun badgeStacksUnderTheMetaLinesAboveOnePointThree() {
        val view = bind(item())
        val badge = view.findViewById<View>(R.id.textRequestStatus)
        assertEquals(view.findViewById<View>(R.id.textRequestColumn), badge.parent)
        assertEquals(Gravity.START, (badge.layoutParams as LinearLayout.LayoutParams).gravity)
    }
}
