package com.thedavelopers.eventqr.features.admin

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.widget.TextView
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Regression: the Requests / Accounts / Logs compact-head eyebrow hard-coded `ui_admin_tab`, so a
 * SUPER_ADMIN read ADMIN. [AdminRoleLabelsTest] covers only the map; these tests cover the call
 * sites, so reverting a screen to `getString(R.string.ui_admin_tab)` fails here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class AdminRoleEyebrowTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        SessionManager(context).apply {
            clearSession()
            saveRole(AccountRole.SUPER_ADMIN)
        }
    }

    @Test
    fun requests_eyebrowUsesTheAuthoritativeRoleOverTheStaleCachedOne() {
        // Promoted server-side: prefs still say ADMIN, auth/me says SUPER_ADMIN and must win.
        SessionManager(context).saveRole(AccountRole.ADMIN)
        assertEquals(
            "SUPER ADMIN",
            requests(FakeAdminRepository(context).apply { role = AccountRole.SUPER_ADMIN }).eyebrow(),
        )

        // Demoted server-side: the cached SUPER_ADMIN must not survive auth/me either.
        SessionManager(context).saveRole(AccountRole.SUPER_ADMIN)
        assertEquals(
            "ADMIN",
            requests(FakeAdminRepository(context).apply { role = AccountRole.ADMIN }).eyebrow(),
        )
    }

    @Test
    fun accounts_eyebrowShowsTheSignedInRoleAndRefreshesOnResume() {
        val controller = accounts(FakeAdminRepository(context))
        assertEquals("SUPER ADMIN", controller.get().eyebrow())

        // A role change persisted while the screen sits in the back stack is picked up on resume.
        SessionManager(context).saveRole(AccountRole.ADMIN)
        controller.pause().resume()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("ADMIN", controller.get().eyebrow())
    }

    @Test
    fun logs_eyebrowShowsTheSignedInRoleAndRefreshesOnResume() {
        val controller = logs(FakeAdminRepository(context))
        assertEquals("SUPER ADMIN", controller.get().eyebrow())

        SessionManager(context).saveRole(AccountRole.ADMIN)
        controller.pause().resume()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("ADMIN", controller.get().eyebrow())
    }

    // ---- helpers

    /** The eyebrow is overline (all caps); [TextView.getText] keeps the sentence-case source. */
    private fun Activity.eyebrow(): String =
        findViewById<TextView>(R.id.passHeadEyebrow).text.toString().uppercase()

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
        var role: AccountRole = AccountRole.SUPER_ADMIN

        override suspend fun getCurrentUser(): NetworkResult<UserResponse> =
            NetworkResult.Success(
                UserResponse(
                    userId = UUID.randomUUID(),
                    email = "admin@example.com",
                    fullName = "Admin",
                    role = role,
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
