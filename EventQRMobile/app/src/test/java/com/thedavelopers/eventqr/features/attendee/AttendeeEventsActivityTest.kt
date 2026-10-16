package com.thedavelopers.eventqr.features.attendee

import android.os.Looper
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.chip.Chip
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** D1: returning to the list reloads the cards (fresh registered counts) without dropping the typed search. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class AttendeeEventsActivityTest {

    private val start = Instant.now().plus(3, ChronoUnit.DAYS)

    private fun event(title: String, count: Int) = AttendeeEventResponse(
        eventId = UUID.randomUUID(), title = title, capacity = 12, currentAttendeeCount = count,
        eventStartAt = start, eventEndAt = start.plus(2, ChronoUnit.HOURS),
    )

    private class CountingRepo(var result: List<AttendeeEventResponse>) :
        AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        var calls = 0
        override suspend fun getBrowseEvents(): NetworkResult<List<AttendeeEventResponse>> {
            calls++
            return NetworkResult.Success(result)
        }
    }

    /** Creates the screen with its presenter swapped for one backed by [repo], then shows the first page. */
    private fun launch(repo: CountingRepo): ActivityController<AttendeeEventsActivity> {
        val controller = Robolectric.buildActivity(AttendeeEventsActivity::class.java).create()
        val activity = controller.get()
        activity.presenter.detach()
        activity.presenter = EventsPresenter(activity, repo)
        activity.presenter.loadEvents()
        controller.start().resume()
        shadowOf(Looper.getMainLooper()).idle()
        return controller
    }

    private fun AttendeeEventsActivity.cardCount() =
        findViewById<RecyclerView>(R.id.recyclerEvents).adapter!!.itemCount

    @Test
    fun firstResume_doesNotReload_laterResumeReloadsAndKeepsTheSearch() {
        val repo = CountingRepo(listOf(event("Night Market", 0), event("Day Fair", 0)))
        val controller = launch(repo)
        val activity = controller.get()
        assertEquals(1, repo.calls)

        activity.findViewById<EditText>(R.id.inputEventSearch).setText("night")
        assertEquals(1, activity.cardCount())

        // Back from the detail screen after registering: the server now reports 1 registered.
        repo.result = listOf(event("Night Market", 1), event("Day Fair", 0))
        controller.pause().resume()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, repo.calls)
        assertEquals(1, activity.cardCount())
    }

    @Test
    fun filterChip_keepsTheTypedSearch() {
        val repo = CountingRepo(listOf(event("Night Market", 0), event("Day Fair", 0)))
        val activity = launch(repo).get()

        activity.findViewById<EditText>(R.id.inputEventSearch).setText("fair")
        activity.findViewById<Chip>(R.id.chipUpcoming).performClick()
        assertEquals(1, activity.cardCount())

        activity.findViewById<Chip>(R.id.chipAll).performClick()
        assertEquals(1, activity.cardCount())
    }
}
