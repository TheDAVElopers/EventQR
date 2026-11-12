package com.thedavelopers.eventqr.features.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.attendee.AttendeeRepository
import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardSummary
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class DashboardPresenterTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class RecordingView : DashboardContract.View {
        var summary: DashboardSummary? = null
        override fun showLoading(isLoading: Boolean) = Unit
        override fun showSummary(summary: DashboardSummary) {
            this.summary = summary
        }

        override fun showError(message: String) = Unit
        override fun showMessage(message: String) = Unit
        override fun showPartialError(source: String, message: String) = Unit
        override fun openSection(title: String, message: String) = Unit
        override fun updateHeader(role: String?, name: String?) = Unit
    }

    private inner class FakeDashboardRepo(
        private val summary: NetworkResult<DashboardSummary>,
    ) : DashboardRepository(context) {
        override suspend fun getSummary() = summary
        override suspend fun getCurrentUser(): NetworkResult<UserResponse> = NetworkResult.Error("skip")
    }

    private inner class FakeAttendeeRepo(
        private val registrations: NetworkResult<List<RegistrationResponse>>,
    ) : AttendeeRepository(context) {
        // Non-empty so the presenter never falls back to the real getEvents() call.
        override suspend fun getBrowseEvents(): NetworkResult<List<AttendeeEventResponse>> =
            NetworkResult.Success(listOf(AttendeeEventResponse(eventId = UUID.randomUUID(), title = "Event")))

        override suspend fun getMyRegistrations() = registrations
    }

    private fun summary() = DashboardSummary(
        totalEvents = 0,
        totalRegistrations = 7,
        totalTransactions = 0,
        totalPoints = 0,
        completedEventsCount = 3,
        totalNotifications = 0,
    )

    /** Runs presenter work eagerly on the calling thread; the fakes never suspend. */
    private fun presenter(view: RecordingView, registrations: NetworkResult<List<RegistrationResponse>>) =
        DashboardPresenter(
            view,
            FakeDashboardRepo(NetworkResult.Success(summary())),
            FakeAttendeeRepo(registrations),
            SessionManager(context),
            UiStrings(context),
            CoroutineScope(Dispatchers.Unconfined),
        )

    @Test
    fun registrationsFailure_withSummarySuccess_leavesEveryCellUnknown() {
        val view = RecordingView()
        presenter(view, NetworkResult.Error("boom")).loadDashboard()

        val summary = view.summary
        assertNotNull(summary)
        // The summary's own totals must not be copied into the stub: they are not the same definition.
        assertNull(summary?.totalRegistrations)
        assertNull(summary?.completedEventsCount)
        assertNull(summary?.checkInsCount)
        assertNull(summary?.lifetimeRegistrationsCount)
    }

    @Test
    fun registrationsSuccess_fillsAllThreeCells() {
        val view = RecordingView()
        presenter(view, NetworkResult.Success(emptyList())).loadDashboard()

        val summary = view.summary
        assertEquals(0L, summary?.totalRegistrations)
        assertEquals(0L, summary?.checkInsCount)
        assertEquals(0L, summary?.lifetimeRegistrationsCount)
    }
}
