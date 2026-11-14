package com.thedavelopers.eventqr.features.admin

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.AccountStatus
import com.thedavelopers.eventqr.core.api.dto.PageResponse
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.admin.logs.AdminAuditLogsActivity
import com.thedavelopers.eventqr.features.admin.users.AdminAccountManagementActivity
import com.thedavelopers.eventqr.features.audit.model.dto.AuditLogResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Regression: the Requests / Accounts / Logs compact head (§5.11) is title-only. It used to print a
 * role eyebrow, which read `SUPER_ADMIN` on one screen and a stale `ADMIN` on another; the eyebrow
 * slot stays in the layout for the head's row0 rhythm but must never carry text again.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class AdminCompactHeadTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        SessionManager(context).apply {
            clearSession()
            saveRole(AccountRole.SUPER_ADMIN)
        }
    }

    @Test
    fun requests_headShowsTitleOnly() =
        assertCompactHead(requests(FakeAdminRepository(context)), R.string.ui_admin_requests_title)

    @Test
    fun accounts_headShowsTitleOnly() =
        assertCompactHead(accounts(FakeAdminRepository(context)).get(), R.string.ui_admin_accounts_title)

    @Test
    fun logs_headShowsTitleOnly() =
        assertCompactHead(logs(FakeAdminRepository(context)).get(), R.string.ui_admin_logs_title)

    @Test
    fun requests_countStaysOnTheTitleRow() {
        // The eyebrow is gone, so the count must not follow it out of the head.
        val count = requests(FakeAdminRepository(context)).findViewById<View>(R.id.passHeadTitleCount)
        assertEquals(View.VISIBLE, count.visibility)
    }

    // ---- helpers

    private fun assertCompactHead(activity: Activity, @StringRes titleRes: Int) {
        assertEquals(
            activity.getString(titleRes),
            activity.findViewById<TextView>(R.id.passHeadTitle).text.toString(),
        )
        val eyebrow = activity.findViewById<TextView>(R.id.passHeadEyebrow)
        assertEquals(View.INVISIBLE, eyebrow.visibility)
        assertTrue(eyebrow.text.isNullOrBlank())
    }

    private fun requests(repo: FakeAdminRepository): AdminEventApprovalBackendActivity {
        val controller = Robolectric.buildActivity(AdminEventApprovalBackendActivity::class.java)
        controller.get().repositoryOverride = repo
        controller.create().start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()
        return controller.get()
    }

    private fun accounts(repo: FakeAdminRepository): ActivityController<AdminAccountManagementActivity> {
        val controller = Robolectric.buildActivity(AdminAccountManagementActivity::class.java)
        controller.get().repositoryOverride = repo
        controller.create().start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()
        return controller
    }

    private fun logs(repo: FakeAdminRepository): ActivityController<AdminAuditLogsActivity> {
        val controller = Robolectric.buildActivity(AdminAuditLogsActivity::class.java)
        controller.get().repositoryOverride = repo
        controller.create().start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()
        return controller
    }

    /** Only the calls these three screens make are faked; the rest of the repository is never reached. */
    private class FakeAdminRepository(context: Context) : AdminRepository(context) {
        override suspend fun getCurrentUser(): NetworkResult<UserResponse> =
            NetworkResult.Success(
                UserResponse(
                    userId = UUID.randomUUID(),
                    email = "admin@example.com",
                    fullName = "Admin",
                    role = AccountRole.SUPER_ADMIN,
                    status = AccountStatus.ACTIVE,
                ),
            )

        override suspend fun loadAllEventRequests(): NetworkResult<List<EventRequestResponse>> =
            NetworkResult.Success(emptyList())

        override suspend fun loadUsersPage(
            role: AccountRole?,
            query: String?,
            page: Int,
            size: Int,
        ): NetworkResult<PageResponse<UserResponse>> = NetworkResult.Success(PageResponse())

        override suspend fun loadAuditLogsPage(
            page: Int,
            size: Int,
            actionPrefix: String?,
        ): NetworkResult<PageResponse<AuditLogResponse>> = NetworkResult.Success(PageResponse())
    }
}
