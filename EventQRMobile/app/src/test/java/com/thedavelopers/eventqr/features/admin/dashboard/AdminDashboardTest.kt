package com.thedavelopers.eventqr.features.admin.dashboard

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.admin.AdminEventApprovalBackendActivity
import com.thedavelopers.eventqr.features.admin.AdminEventRequestDetailActivity
import com.thedavelopers.eventqr.features.admin.model.dto.AdminPendingPreview
import com.thedavelopers.eventqr.features.admin.model.dto.AdminStatsResponse
import com.thedavelopers.eventqr.features.audit.model.dto.AuditLogResponse
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The admin dashboard is a pass head plus two real blocks, not a menu (§6.5).
 *
 * Covered: the three head counts and their failure dashes, the 3-row preview cap, `View all
 * requests` only beyond three, the zero state, the super-admin title, the deleted menu blocks and
 * the supplementary activity block hiding on failure.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class AdminDashboardTest {

    private val context: android.content.Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        SessionManager(context).apply {
            clearSession()
            saveRole(AccountRole.ADMIN)
        }
    }

    private fun dashboard(): AdminDashboardActivity =
        Robolectric.buildActivity(AdminDashboardActivity::class.java).create().get()

    // ---- loading

    @Test
    fun firstLoad_showsHeadAndRowSkeletons_thenRevealsTheRealHead() {
        val activity = dashboard()

        assertEquals(
            View.VISIBLE,
            activity.findViewById<View>(R.id.passHeadSkeleton).visibility,
        )
        assertEquals(View.GONE, activity.findViewById<View>(R.id.passHead).visibility)
        assertEquals(
            View.VISIBLE,
            activity.findViewById<View>(R.id.skeletonDashboard).visibility,
        )

        activity.renderStats(AdminStatsResponse(pendingEventRequests = 0))

        assertEquals(
            View.GONE,
            activity.findViewById<View>(R.id.passHeadSkeleton).visibility,
        )
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.passHead).visibility)
        assertEquals(
            View.GONE,
            activity.findViewById<View>(R.id.skeletonDashboard).visibility,
        )
    }

    // ---- pass head

    @Test
    fun headValues_comeFromStats_andFailureRendersDashes() {
        val activity = dashboard()

        activity.renderStats(null)
        assertEquals("--", headValue(activity, R.id.passHeadStat1Value))
        assertEquals("--", headValue(activity, R.id.passHeadStat2Value))
        assertEquals("--", headValue(activity, R.id.passHeadStat3Value))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.layoutDashboardError).visibility)

        activity.renderStats(
            AdminStatsResponse(pendingEventRequests = 2, activeEvents = 3, totalAccounts = 12),
        )
        assertEquals("2", headValue(activity, R.id.passHeadStat1Value))
        assertEquals("3", headValue(activity, R.id.passHeadStat2Value))
        assertEquals("12", headValue(activity, R.id.passHeadStat3Value))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.layoutDashboardError).visibility)
    }

    @Test
    fun headLabels_arePendingActiveAccounts() {
        val activity = dashboard()

        assertEquals("Pending", activity.findViewById<TextView>(R.id.passHeadStat1Label).text.toString())
        assertEquals("Active", activity.findViewById<TextView>(R.id.passHeadStat2Label).text.toString())
        assertEquals("Accounts", activity.findViewById<TextView>(R.id.passHeadStat3Label).text.toString())
    }

    @Test
    fun superAdmin_getsTheSuperAdminTitle_adminGetsAdmin() {
        SessionManager(context).saveRole(AccountRole.SUPER_ADMIN)
        assertEquals("Super admin", headTitle(dashboard()))

        SessionManager(context).saveRole(AccountRole.ADMIN)
        assertEquals("Admin", headTitle(dashboard()))
    }

    // ---- waiting queue

    @Test
    fun preview_isCappedAtThreeRows_andShowsRequesterAsTitle() {
        val activity = dashboard()

        activity.renderStats(
            AdminStatsResponse(
                pendingEventRequests = 5,
                pendingPreview = (1..5).map { index ->
                    AdminPendingPreview(
                        id = UUID.randomUUID().toString(),
                        requesterName = "Requester $index",
                        eventName = "Event $index",
                        createdAt = Instant.parse("2026-03-14T05:30:00Z"),
                    )
                },
            ),
        )

        val rows = activity.findViewById<LinearLayout>(R.id.pendingRowsContainer)
        assertEquals(3, rows.childCount)
        val first = rows.getChildAt(0)
        assertEquals("Requester 1", first.findViewById<TextView>(R.id.listRowTitle).text.toString())
        assertEquals("Event 1, submitted 14 Mar 2026", first.findViewById<TextView>(R.id.listRowMeta).text.toString())
        assertEquals("Pending review", first.findViewById<TextView>(R.id.listRowBadge).text.toString())
    }

    @Test
    fun viewAll_isVisibleOnlyWhenPendingExceedsThree() {
        val activity = dashboard()

        activity.renderStats(pending(pendingEventRequests = 4))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnViewAllRequests).visibility)

        activity.renderStats(pending(pendingEventRequests = 3))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.btnViewAllRequests).visibility)
    }

    @Test
    fun zeroPending_showsTheEmptyRowWithNoAction() {
        val activity = dashboard()

        activity.renderStats(AdminStatsResponse(pendingEventRequests = 0, pendingPreview = emptyList()))

        val empty = activity.findViewById<TextView>(R.id.textNoPending)
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals("No requests waiting", empty.text.toString())
        assertFalse(empty.isClickable)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.btnViewAllRequests).visibility)
        assertEquals(
            View.GONE,
            activity.findViewById<LinearLayout>(R.id.pendingRowsContainer).visibility,
        )
    }

    @Test
    fun pendingWithoutPreview_showsAFallbackThatDoesNotClaimZero() {
        val activity = dashboard()

        activity.renderStats(AdminStatsResponse(pendingEventRequests = 2, pendingPreview = emptyList()))

        val fallback = activity.findViewById<TextView>(R.id.textNoPending)
        assertEquals(View.VISIBLE, fallback.visibility)
        assertEquals(
            activity.getString(R.string.ui_admin_pending_not_listed),
            fallback.text.toString(),
        )
        assertEquals(
            View.GONE,
            activity.findViewById<LinearLayout>(R.id.pendingRowsContainer).visibility,
        )
    }

    @Test
    fun pendingBadge_usesTheWarningToneNotTheLayoutSuccessFill() {
        val activity = dashboard()

        activity.renderStats(pending(pendingEventRequests = 2))

        val badge = activity.findViewById<LinearLayout>(R.id.pendingRowsContainer)
            .getChildAt(0)
            .findViewById<TextView>(R.id.listRowBadge)
        assertEquals(
            ContextCompat.getColor(activity, R.color.ui_warning_container),
            badge.backgroundTintList?.defaultColor,
        )
        assertEquals(
            ContextCompat.getColor(activity, R.color.ui_on_warning_container),
            badge.currentTextColor,
        )
    }

    @Test
    fun pendingRowTap_opensThatRequestsDetail() {
        val activity = dashboard()
        val request = AdminPendingPreview(
            id = UUID.randomUUID().toString(),
            requesterName = "Requester 1",
            eventName = "Event 1",
            createdAt = Instant.parse("2026-03-14T05:30:00Z"),
        )
        activity.renderStats(AdminStatsResponse(pendingEventRequests = 1, pendingPreview = listOf(request)))

        activity.findViewById<LinearLayout>(R.id.pendingRowsContainer)
            .getChildAt(0)
            .findViewById<View>(R.id.listRowContainer)
            .performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(AdminEventRequestDetailActivity::class.java.name, intent.component?.className)
        assertEquals(request.id, intent.getStringExtra(AdminEventRequestDetailActivity.EXTRA_REQUEST_ID))
    }

    @Test
    fun viewAllRequestsTap_opensTheRequestsScreen() {
        val activity = dashboard()
        activity.renderStats(pending(pendingEventRequests = 4))

        activity.findViewById<View>(R.id.btnViewAllRequests).performClick()

        assertEquals(
            AdminEventApprovalBackendActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )
    }

    @Test
    fun refreshFailure_keepsTheRowsAndOffersRetryInASnackbar() {
        val activity = dashboard()
        activity.applyStatsResult(NetworkResult.Success(pending(pendingEventRequests = 2)))
        shadowOf(Looper.getMainLooper()).idle()

        val rows = activity.findViewById<LinearLayout>(R.id.pendingRowsContainer)
        assertEquals(2, rows.childCount)
        assertEquals(View.VISIBLE, rows.visibility)

        activity.applyStatsResult(NetworkResult.Error("offline"))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, rows.childCount)
        assertEquals(View.VISIBLE, rows.visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.layoutDashboardError).visibility)
        val snackbar = activity.snackbarTexts()
        assertTrue("snackbar was $snackbar", snackbar.contains(activity.getString(R.string.ui_admin_dashboard_error)))
        assertTrue("Retry missing from $snackbar", snackbar.contains(activity.getString(R.string.common_retry)))
    }

    @Test
    fun failure_hidesTheQueueAndShowsOneInFlowRetry() {
        val activity = dashboard()

        activity.renderStats(pending(pendingEventRequests = 2))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.pendingRowsContainer).visibility)

        activity.renderStats(null)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.pendingRowsContainer).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.btnViewAllRequests).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.layoutDashboardError).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.skeletonDashboard).visibility)
    }

    // ---- deleted menu

    @Test
    fun deletedMenuBlocks_areGone() {
        val activity = dashboard()

        listOf(
            "rowPendingAlert",
            "metricActiveEvents",
            "metricTotalAccounts",
            "rowEventRequests",
            "rowAccountManagement",
            "rowAuditLogs",
            "progressDashboardLoading",
            "textDashboardLoadHint",
        ).forEach { id ->
            assertEquals(
                "the dashboard must not carry $id: it duplicated the bottom nav or the stub",
                0,
                activity.resources.getIdentifier(id, "id", activity.packageName),
            )
        }
    }

    // ---- recent activity

    @Test
    fun recentActivity_failureHidesTheBlock_andSuccessShowsReadOnlyRows() {
        val activity = dashboard()

        activity.renderRecentActivity(null)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.recentActivityBlock).visibility)

        activity.renderRecentActivity(listOf(auditEntry("Approved an event request")))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.recentActivityBlock).visibility)
        val rows = activity.findViewById<LinearLayout>(R.id.auditRowsContainer)
        assertEquals(1, rows.childCount)
        val row = rows.getChildAt(0)
        assertEquals("Approved an event request", row.findViewById<TextView>(R.id.textAuditAction).text.toString())
        assertFalse(row.isClickable)

        activity.renderRecentActivity(null)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.recentActivityBlock).visibility)
    }

    @Test
    fun recentActivity_isCappedAtThreeRows() {
        val activity = dashboard()

        activity.renderRecentActivity((1..5).map { auditEntry("Action $it") })

        assertEquals(
            3,
            activity.findViewById<LinearLayout>(R.id.auditRowsContainer).childCount,
        )
    }

    // ---- helpers

    private fun headValue(activity: AdminDashboardActivity, id: Int): String =
        activity.findViewById<TextView>(id).text.toString()

    private fun headTitle(activity: AdminDashboardActivity): String =
        activity.findViewById<TextView>(R.id.passHeadTitle).text.toString()

    private fun View.textViews(): List<TextView> = when (this) {
        is TextView -> listOf(this)
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).textViews() }
        else -> emptyList()
    }

    /** The Material Snackbar root, added to the content view when one is shown. */
    private fun View.snackbar(): ViewGroup? {
        if (this::class.java.name.contains("Snackbar") && this is ViewGroup) return this
        if (this is ViewGroup) return (0 until childCount).firstNotNullOfOrNull { getChildAt(it).snackbar() }
        return null
    }

    private fun AdminDashboardActivity.snackbarTexts(): List<String> =
        window.decorView.snackbar()?.textViews()?.map { it.text.toString() }.orEmpty()

    private fun pending(pendingEventRequests: Long): AdminStatsResponse = AdminStatsResponse(
        pendingEventRequests = pendingEventRequests,
        activeEvents = 1,
        totalAccounts = 10,
        pendingPreview = (1..pendingEventRequests.coerceAtMost(3).toInt()).map { index ->
            AdminPendingPreview(
                id = UUID.randomUUID().toString(),
                requesterName = "Requester $index",
                eventName = "Event $index",
                createdAt = Instant.parse("2026-03-14T05:30:00Z"),
            )
        },
    )

    private fun auditEntry(action: String): AuditLogResponse = AuditLogResponse(
        auditLogId = UUID.randomUUID(),
        action = "EVENT_REQUEST_APPROVED",
        performedByUserId = UUID.randomUUID(),
        performedByFullName = "Maria Santos",
        timestamp = Instant.parse("2026-03-14T05:30:00Z"),
        details = action,
    )
}
