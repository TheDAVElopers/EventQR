package com.thedavelopers.eventqr.features.attendee

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.UUID

/** The in-flow error strip and the zero-row empty-state error on my event requests (§5.7 line 642). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class MyEventRequestsActivityTest {

    private fun request() = EventRequestResponse(
        eventRequestId = UUID.randomUUID(),
        requesterUserId = UUID.randomUUID(),
        eventName = "DevFest Manila",
        status = EventRequestStatus.PENDING,
    )

    private class FakeRepo(context: Context) : AttendeeRepository(context) {
        var requests: NetworkResult<List<EventRequestResponse>> = NetworkResult.Success(emptyList())
        override suspend fun getMyEventRequests() = requests
    }

    private fun build(repo: FakeRepo): ActivityController<MyEventRequestsActivity> {
        val controller = Robolectric.buildActivity(MyEventRequestsActivity::class.java)
        controller.get().repositoryOverride = repo
        controller.create().start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()
        return controller
    }

    private fun MyEventRequestsActivity.recycler() =
        findViewById<RecyclerView>(R.id.recyclerMyEventRequests)

    private fun MyEventRequestsActivity.empty() =
        findViewById<EventQrEmptyState>(R.id.txtMyRequestsEmpty)

    @Test
    fun refreshFailureWithRows_keepsTheRowsAndShowsTheStrip_thenSuccessHidesIt() {
        val repo = FakeRepo(ApplicationProvider.getApplicationContext()).apply {
            requests = NetworkResult.Success(listOf(request()))
        }
        val controller = build(repo)
        val activity = controller.get()
        val strip = activity.findViewById<View>(R.id.errorMyRequests)
        assertEquals(1, activity.recycler().adapter!!.itemCount)
        assertEquals(View.GONE, strip.visibility)

        repo.requests = NetworkResult.Error("offline")
        controller.pause().resume()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, activity.recycler().adapter!!.itemCount)
        assertEquals(View.VISIBLE, activity.recycler().visibility)
        assertEquals(View.GONE, activity.empty().visibility)
        assertEquals(View.VISIBLE, strip.visibility)
        assertEquals(
            activity.getString(R.string.my_event_requests_unable_to_load_event_requests),
            activity.findViewById<TextView>(R.id.txtMyRequestsError).text.toString(),
        )

        repo.requests = NetworkResult.Success(listOf(request(), request()))
        activity.findViewById<View>(R.id.btnMyRequestsRetry).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, activity.recycler().adapter!!.itemCount)
        assertEquals(View.GONE, strip.visibility)
    }

    @Test
    fun firstLoadFailureWithNoRows_showsTheEmptyStateErrorWithRetry_thenSuccessRenders() {
        val repo = FakeRepo(ApplicationProvider.getApplicationContext()).apply {
            requests = NetworkResult.Error("offline")
        }
        val activity = build(repo).get()

        assertEquals(View.VISIBLE, activity.empty().visibility)
        assertEquals(
            activity.getString(R.string.my_event_requests_unable_to_load_event_requests),
            activity.empty().text.toString(),
        )
        assertEquals("offline", activity.empty().subtext.toString())
        val retry = activity.empty().findViewById<TextView>(R.id.emptyAction)
        assertEquals(View.VISIBLE, retry.visibility)
        assertEquals(activity.getString(R.string.ui_attendee_retry), retry.text.toString())
        assertEquals(View.GONE, activity.recycler().visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.errorMyRequests).visibility)

        repo.requests = NetworkResult.Success(listOf(request()))
        retry.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, activity.recycler().adapter!!.itemCount)
        assertEquals(View.VISIBLE, activity.recycler().visibility)
        assertEquals(View.GONE, activity.empty().visibility)
    }
}
