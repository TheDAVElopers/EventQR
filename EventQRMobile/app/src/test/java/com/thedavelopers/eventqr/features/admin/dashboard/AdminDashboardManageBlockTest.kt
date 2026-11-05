package com.thedavelopers.eventqr.features.admin.dashboard

import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.admin.model.dto.AdminStatsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * MANAGE is a static navigation list (requests / accounts / audit logs), so it has no loading state.
 * The redesign placeholder skeleton used to sit stacked on top of those rows in a FrameLayout and was
 * never hidden, which left three skeleton rows covering the rows forever while OVERVIEW above it loaded.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class AdminDashboardManageBlockTest {

    private val context: android.content.Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        SessionManager(context).apply {
            clearSession()
            saveRole(AccountRole.SUPER_ADMIN)
        }
    }

    private fun dashboard(): AdminDashboardActivity =
        Robolectric.buildActivity(AdminDashboardActivity::class.java).create().get()

    /** No skeleton view exists in the MANAGE block at any point, so none can outlive a load. */
    private fun assertNoSkeleton(activity: AdminDashboardActivity) {
        val skeleton: View? = activity.findViewById(R.id.skeletonLoading)
        assertNull("MANAGE must not carry a skeleton: it is a static row list, not a loaded list", skeleton)
    }

    private fun assertManageRowsVisible(activity: AdminDashboardActivity) {
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.rowEventRequests).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.rowAccountManagement).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.rowAuditLogs).visibility)
    }

    @Test
    fun manageRows_areVisibleWithNoSkeleton_afterSuccessCallback() {
        val activity = dashboard()

        activity.renderStats(AdminStatsResponse(totalAccounts = 46, activeEvents = 0, auditLogCount = 50))

        assertNoSkeleton(activity)
        assertManageRowsVisible(activity)
    }

    @Test
    fun manageRows_areVisibleWithNoSkeleton_afterErrorCallback() {
        val activity = dashboard()

        activity.renderStats(null)

        assertNoSkeleton(activity)
        assertManageRowsVisible(activity)
    }
}
