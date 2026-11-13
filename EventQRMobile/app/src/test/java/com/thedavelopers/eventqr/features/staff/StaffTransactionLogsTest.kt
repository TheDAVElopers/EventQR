package com.thedavelopers.eventqr.features.staff

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.api.dto.TransactionType
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * "My scans" (§6.3): a content-sized stats band, a list that owns the rest of the screen, the
 * prompt state while no event is selected, and the no-scans state once one is. The band geometry
 * is pinned here because a band that grows past its `minHeight` is what pushed the list off screen.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class StaffTransactionLogsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        SessionManager(context).apply { clearSession(); saveRole(AccountRole.STAFF) }
    }

    // ---- geometry

    private fun themed(): Context = ContextThemeWrapper(context, R.style.Theme_EventQR)

    private fun screen(heightDp: Int = 800, beforeMeasure: (View) -> Unit = {}): View {
        val root = LayoutInflater.from(themed()).inflate(R.layout.activity_staff_transaction_logs, null, false)
        // The nav is a ComposeView and needs a window recomposer; it is not part of the body geometry.
        root.findViewById<View>(R.id.composeBottomNav).visibility = View.GONE
        beforeMeasure(root)
        val density = context.resources.displayMetrics.density
        val width = (360 * density).toInt()
        val height = (heightDp * density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
        return root
    }

    @Test
    fun statsBand_isContentSized_andTheListTakesTheRest() {
        val root = screen()
        val band = root.findViewById<LinearLayout>(R.id.staffTransactionsStats)
        val bandParams = band.layoutParams as LinearLayout.LayoutParams
        assertEquals(
            "the stats band must size to its content, never to the screen",
            ViewGroup.LayoutParams.WRAP_CONTENT,
            bandParams.height,
        )
        assertEquals("the stats band must not take weight", 0f, bandParams.weight, 0f)

        // The root defect: a match_parent cell in a wrap_content band measures its AT_MOST spec,
        // so the band grew to the screen and pushed the list out.
        for (index in 0 until band.childCount) {
            val child = band.getChildAt(index)
            if (child is LinearLayout) {
                assertEquals(
                    "cell $index must size to its content, never to the screen",
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (child.layoutParams as LinearLayout.LayoutParams).height,
                )
            } else {
                assertEquals(
                    "rule $index must run the band's full height",
                    band.height,
                    child.height,
                )
            }
        }

        val host = root.findViewById<View>(R.id.swipeRefreshStaffTransactions)
        val hostParams = host.layoutParams as LinearLayout.LayoutParams
        assertEquals("the list host takes the remaining space", 0, hostParams.height)
        assertEquals("the list host is the one weighted child", 1f, hostParams.weight, 0f)

        val floor = context.resources.getDimensionPixelSize(R.dimen.ui_metric_row_height)
        assertTrue("band was ${band.height}px, below the 56dp floor", band.height >= floor)
        assertTrue(
            "band was ${band.height}px of ${root.height}px: it swallowed the screen",
            band.height < root.height / 3,
        )
        assertTrue(
            "list area was ${host.height}px of ${root.height}px",
            host.height > root.height / 2,
        )
        assertTrue("the band sits above the list", band.bottom <= host.top)
    }

    @Test
    @Config(fontScale = 2.0f)
    fun statsBand_growsAtFontScale2_andTheListStaysReachable() {
        val values = listOf(R.id.txtTotalScans, R.id.txtSuccessfulScans, R.id.txtRejectedScans)
        // A real count, not the "--" placeholder: four digits at 2.0 in a third of the width is
        // what a single line clips.
        val root = screen { view -> values.forEach { view.findViewById<TextView>(it).text = "1234" } }
        val band = root.findViewById<LinearLayout>(R.id.staffTransactionsStats)
        val floor = context.resources.getDimensionPixelSize(R.dimen.ui_metric_row_height)

        assertTrue(
            "band was ${band.height}px at fontScale 2.0: the labels must grow it past the 56dp floor",
            band.height > floor,
        )
        assertTrue(
            "band was ${band.height}px of ${root.height}px at fontScale 2.0",
            band.height < root.height / 3,
        )
        assertTrue(
            "the list must stay reachable at fontScale 2.0",
            root.findViewById<View>(R.id.swipeRefreshStaffTransactions).height > root.height / 3,
        )

        for (id in values) {
            val value = root.findViewById<TextView>(id)
            val layout = value.layout
            assertNotNull("value ${Integer.toHexString(id)} never laid out", layout)
            assertEquals(
                "value ${Integer.toHexString(id)} must not cap its lines: a count wraps, it never clips",
                Int.MAX_VALUE,
                value.maxLines,
            )
            for (line in 0 until (layout?.lineCount ?: 0)) {
                assertTrue(
                    "value line $line is ${layout?.getLineWidth(line)}px over ${value.width}px",
                    (layout?.getLineWidth(line) ?: 0f) <= value.width + 1f,
                )
            }
            assertTrue(
                "value ${Integer.toHexString(id)} is ${value.height}px over a ${layout?.height}px layout",
                value.height >= (layout?.height ?: 0),
            )
        }
    }

    // ---- states

    @Test
    fun noEventSelected_showsThePrompt_andGreysThePurposeFilter() {
        val activity = Robolectric.buildActivity(StaffTransactionsActivity::class.java).create().get()

        activity.showPromptState()

        val empty = activity.findViewById<EventQrEmptyState>(R.id.txtStaffTransactionsEmptyState)
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals(context.getString(R.string.ui_staff_no_event_selected), empty.title.toString())
        assertEquals(context.getString(R.string.ui_staff_no_logs_prompt), empty.subtext.toString())

        val action = activity.findViewById<TextView>(R.id.emptyAction)
        assertEquals(View.VISIBLE, action.visibility)
        assertEquals(context.getString(R.string.ui_staff_select_event), action.text.toString())

        assertEquals(View.GONE, activity.findViewById<View>(R.id.recyclerStaffTransactions).visibility)

        val purpose = activity.findViewById<View>(R.id.cardStaffTransactionsPurpose)
        assertFalse("the purpose filter cannot narrow an unselected event", purpose.isEnabled)
        assertFalse(purpose.isClickable)

        // No event means no source: the band says unknown, never a zero it cannot prove.
        assertEquals(
            context.getString(R.string.common_value_unavailable),
            activity.findViewById<TextView>(R.id.txtTotalScans).text.toString(),
        )
    }

    @Test
    fun eventWithScans_rendersTheRows_andHidesTheEmptyState() {
        val activity = Robolectric.buildActivity(StaffTransactionsActivity::class.java).create().get()

        activity.renderTransactions(listOf(transaction()))

        val recycler = activity.findViewById<RecyclerView>(R.id.recyclerStaffTransactions)
        assertEquals(View.VISIBLE, recycler.visibility)
        assertEquals(1, recycler.adapter?.itemCount)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.txtStaffTransactionsEmptyState).visibility)

        val density = context.resources.displayMetrics.density
        recycler.measure(
            View.MeasureSpec.makeMeasureSpec((360 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        recycler.layout(0, 0, recycler.measuredWidth, recycler.measuredHeight)
        assertEquals("the list must render a row, not just hold it", 1, recycler.childCount)
    }

    @Test
    fun eventWithoutScans_showsNoScansYet_andNoAction() {
        val activity = Robolectric.buildActivity(StaffTransactionsActivity::class.java).create().get()

        activity.renderTransactions(emptyList())

        val empty = activity.findViewById<EventQrEmptyState>(R.id.txtStaffTransactionsEmptyState)
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals(context.getString(R.string.ui_staff_no_logs), empty.title.toString())
        assertEquals(context.getString(R.string.ui_staff_no_logs_body), empty.subtext.toString())
        assertEquals(View.GONE, activity.findViewById<View>(R.id.emptyAction).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.recyclerStaffTransactions).visibility)
    }

    @Test
    fun noAssignedEvents_isItsOwnState_withNoDeadControlAndNoStrip() {
        val activity = Robolectric.buildActivity(StaffTransactionsActivity::class.java).create().get()

        activity.showNoAssignedEventsState()

        val empty = activity.findViewById<EventQrEmptyState>(R.id.txtStaffTransactionsEmptyState)
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals(
            context.getString(R.string.event_registrations_no_assigned_events_found),
            empty.title.toString(),
        )
        assertTrue("the state names itself, it needs no body", empty.subtext.toString().isBlank())
        assertEquals(View.GONE, activity.findViewById<View>(R.id.emptyAction).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.recyclerStaffTransactions).visibility)
        // The state is the single report: no strip repeating the same sentence, and no Retry.
        assertEquals(View.GONE, activity.findViewById<View>(R.id.errorStaffTransactions).visibility)

        val event = activity.findViewById<View>(R.id.cardStaffTransactionsEvent)
        assertFalse("the event menu has nothing to offer", event.isEnabled)
        assertFalse(event.isClickable)
        val purpose = activity.findViewById<View>(R.id.cardStaffTransactionsPurpose)
        assertFalse(purpose.isEnabled)
        assertFalse(purpose.isClickable)

        assertEquals(
            context.getString(R.string.common_value_unavailable),
            activity.findViewById<TextView>(R.id.txtTotalScans).text.toString(),
        )
    }

    // ---- initial event selection

    @Test
    fun aSingleAssignedEvent_isSelectedForYou() {
        val only = assignedEvent("QA Staff UI 2026")
        assertEquals(only.eventId.toString(), resolveInitialEventId(listOf(only), null))
    }

    @Test
    fun theLaunchExtra_winsWhenTheEventIsAssigned() {
        val first = assignedEvent("First")
        val second = assignedEvent("Second")
        assertEquals(
            second.eventId.toString(),
            resolveInitialEventId(listOf(first, second), second.eventId.toString()),
        )
    }

    @Test
    fun severalEvents_keepThePromptState() {
        val events = listOf(assignedEvent("First"), assignedEvent("Second"))
        assertEquals(null, resolveInitialEventId(events, null))
    }

    @Test
    fun oneScannableEventAmongSeveral_isSelected() {
        // canScan stays true: the ENDED status alone must rule the event out.
        val ended = assignedEvent("Ended", status = EventStatus.ENDED)
        val live = assignedEvent("Live")
        assertEquals(live.eventId.toString(), resolveInitialEventId(listOf(ended, live), null))
    }

    @Test
    fun anUnknownLaunchExtra_fallsBackToTheOnlyAssignedEvent() {
        val only = assignedEvent("Only")
        assertEquals(only.eventId.toString(), resolveInitialEventId(listOf(only), UUID.randomUUID().toString()))
    }

    private fun assignedEvent(
        title: String,
        status: EventStatus = EventStatus.ACTIVE,
        canScan: Boolean = true,
    ) = StaffAssignedEventResponse(
        assignmentId = UUID.randomUUID(),
        eventId = UUID.randomUUID(),
        title = title,
        status = status,
        canScan = canScan,
        canViewLogs = true,
    )

    private fun transaction() = TransactionResponse(
        transactionId = UUID.randomUUID(),
        eventId = UUID.randomUUID(),
        attendeeUserId = UUID.randomUUID(),
        registrationId = UUID.randomUUID(),
        qrCredentialId = UUID.randomUUID(),
        scanPurposeId = UUID.randomUUID(),
        transactionType = TransactionType.ENTRY,
        transactionResult = TransactionResult.APPROVED,
        pointsDelta = 0,
    )
}
