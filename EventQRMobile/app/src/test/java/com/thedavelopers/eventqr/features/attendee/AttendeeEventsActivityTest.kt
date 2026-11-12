package com.thedavelopers.eventqr.features.attendee

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.widget.TextView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.ui.components.CountLabel
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
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

    private fun eventAt(title: String, startAt: Instant) = AttendeeEventResponse(
        eventId = UUID.randomUUID(), title = title, capacity = 12, currentAttendeeCount = 0,
        eventStartAt = startAt, eventEndAt = startAt.plus(2, ChronoUnit.HOURS),
    )

    private fun nextMonth(monthsAhead: Long = 1): YearMonth =
        YearMonth.now(DateFilterRanges.ZONE).plusMonths(monthsAhead)

    private fun at(month: YearMonth, day: Int): Instant =
        month.atDay(day).atTime(9, 0).atZone(DateFilterRanges.ZONE).toInstant()

    private fun registration(eventId: UUID, status: RegistrationStatus) = RegistrationResponse(
        registrationId = UUID.randomUUID(),
        eventId = eventId,
        attendeeUserId = UUID.randomUUID(),
        attendeeEmail = "attendee@example.com",
        attendeeName = "Attendee",
        status = status,
    )

    private class CountingRepo(
        var result: List<AttendeeEventResponse>,
        var registrations: NetworkResult<List<RegistrationResponse>> =
            NetworkResult.Success(emptyList()),
    ) : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        var calls = 0
        override suspend fun getBrowseEvents(): NetworkResult<List<AttendeeEventResponse>> {
            calls++
            return NetworkResult.Success(result)
        }

        override suspend fun getMyRegistrations(): NetworkResult<List<RegistrationResponse>> =
            registrations
    }

    /** First browse call suspends until the test completes it, so the skeleton can be inspected. */
    private class DeferredRepo(
        private val firstCall: CompletableDeferred<NetworkResult<List<AttendeeEventResponse>>>,
    ) : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        override suspend fun getBrowseEvents(): NetworkResult<List<AttendeeEventResponse>> =
            firstCall.await()

        override suspend fun getMyRegistrations(): NetworkResult<List<RegistrationResponse>> =
            NetworkResult.Success(emptyList())
    }

    /** Events answer immediately; registrations stay in flight so the Registered chip's first render is observable. */
    private class DeferredRegistrationsRepo(
        private val events: List<AttendeeEventResponse>,
        private val registrations: CompletableDeferred<NetworkResult<List<RegistrationResponse>>>,
    ) : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        override suspend fun getBrowseEvents(): NetworkResult<List<AttendeeEventResponse>> =
            NetworkResult.Success(events)

        override suspend fun getMyRegistrations(): NetworkResult<List<RegistrationResponse>> =
            registrations.await()
    }

    /** Creates the screen with its presenter swapped for one backed by [repo], then shows the first page. */
    private fun launch(
        repo: AttendeeRepository,
        state: Bundle? = null,
    ): ActivityController<AttendeeEventsActivity> {
        val controller = Robolectric.buildActivity(AttendeeEventsActivity::class.java).create(state)
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

    /** The pass-head result count, formatted with the same component the screen uses. */
    private fun AttendeeEventsActivity.passCount(): String =
        findViewById<TextView>(R.id.passHeadTitleCount).text.toString()

    private fun AttendeeEventsActivity.countLabel(n: Int): String =
        CountLabel.format(n.toLong(), getString(R.string.ui_attendee_events_count))

    private fun AttendeeEventsActivity.emptyState() = findViewById<EventQrEmptyState>(R.id.txtEventsEmpty)

    private fun AttendeeEventsActivity.emptyTitle() = emptyState().title.toString()

    private fun AttendeeEventsActivity.emptyAction() =
        emptyState().findViewById<TextView>(R.id.emptyAction)

    private fun AttendeeEventsActivity.applySheetMonth(sheet: DateFilterSheet, month: YearMonth) {
        sheet.view.findViewById<TextView>(R.id.chipModeMonth).performClick()
        val yearRow = sheet.view.findViewById<LinearLayout>(R.id.yearChipRow)
        val yearChip = (0 until yearRow.childCount)
            .map { yearRow.getChildAt(it) as TextView }
            .first { it.text.toString() == month.year.toString() }
        yearChip.performClick()
        val grid = sheet.view.findViewById<LinearLayout>(R.id.monthChipGrid)
        val row = grid.getChildAt((month.monthValue - 1) / 4) as LinearLayout
        (row.getChildAt((month.monthValue - 1) % 4) as TextView).performClick()
    }

    /**
     * Sheets and platform pickers outlive their test otherwise; `ShadowDialog.getLatestDialog()`
     * then hands a stale surface to the next test instead of the one that test opened.
     */
    @After
    fun dismissOpenSurfaces() {
        ShadowDialog.getShownDialogs().toList().forEach { dialog ->
            if (dialog.isShowing) dialog.dismiss()
        }
        shadowOf(Looper.getMainLooper()).idle()
        RegistrationsCache.clear()
    }

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

    /** First load hides the list behind the flat 96dp skeleton until the events arrive. */
    @Test
    fun firstLoad_showsTheFlatSkeletonAndHidesTheList() {
        val deferred = CompletableDeferred<NetworkResult<List<AttendeeEventResponse>>>()
        val activity = launch(DeferredRepo(deferred)).get()

        val skeleton = activity.findViewById<ViewGroup>(R.id.skeletonLoading)
        assertEquals(View.VISIBLE, skeleton.visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.recyclerEvents).visibility)
        assertEquals(
            "six 96dp rows plus five hairlines",
            11,
            skeleton.childCount,
        )

        deferred.complete(NetworkResult.Success(listOf(event("Night Market", 0))))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, skeleton.visibility)
        assertEquals(1, activity.cardCount())
    }

    @Test
    fun filterChip_keepsTheTypedSearch() {
        val repo = CountingRepo(listOf(event("Night Market", 0), event("Day Fair", 0)))
        val activity = launch(repo).get()

        activity.findViewById<EditText>(R.id.inputEventSearch).setText("fair")
        activity.findViewById<TextView>(R.id.chipThisWeek).performClick()
        assertEquals(1, activity.cardCount())

        activity.findViewById<TextView>(R.id.chipAll).performClick()
        assertEquals(1, activity.cardCount())
    }

    /** `This week` is a start instant in [now, now + 7 days]; a past or undated event never matches. */
    @Test
    fun thisWeekChip_keepsOnlyStartsInsideTheSevenDayWindow() {
        val now = Instant.now()
        val repo = CountingRepo(
            listOf(
                eventAt("Just Started", now.minus(1, ChronoUnit.HOURS)),
                eventAt("Tomorrow", now.plus(1, ChronoUnit.DAYS)),
                eventAt("Next Week", now.plus(8, ChronoUnit.DAYS)),
                AttendeeEventResponse(
                    eventId = UUID.randomUUID(),
                    title = "Date TBD",
                    capacity = 12,
                    currentAttendeeCount = 0,
                ),
            ),
        )
        val activity = launch(repo).get()
        assertEquals(4, activity.cardCount())

        activity.findViewById<TextView>(R.id.chipThisWeek).performClick()

        assertEquals(1, activity.cardCount())
        assertEquals(activity.countLabel(1), activity.passCount())
    }

    /** An empty week uses the week's own copy, not the generic "no events" line. */
    @Test
    fun thisWeekChip_emptyWindowUsesItsOwnCopy() {
        val repo = CountingRepo(listOf(eventAt("Next Month", Instant.now().plus(30, ChronoUnit.DAYS))))
        val activity = launch(repo).get()

        activity.findViewById<TextView>(R.id.chipThisWeek).performClick()

        assertEquals(0, activity.cardCount())
        assertEquals(
            activity.getString(R.string.ui_attendee_no_events_this_week),
            activity.emptyTitle(),
        )
        assertEquals(View.GONE, activity.emptyAction().visibility)
    }

    /** `Registered` is a live registration: cancelled and no-show rows never appear. */
    @Test
    fun registeredChip_keepsOnlyLiveRegistrations() {
        val live = event("Live Pass", 0)
        val entered = event("Already In", 0)
        val cancelled = event("Cancelled Pass", 0)
        val noShow = event("No Show", 0)
        val repo = CountingRepo(
            result = listOf(live, entered, cancelled, noShow),
            registrations = NetworkResult.Success(
                listOf(
                    registration(live.eventId, RegistrationStatus.REGISTERED),
                    registration(entered.eventId, RegistrationStatus.ENTERED),
                    registration(cancelled.eventId, RegistrationStatus.CANCELLED),
                    registration(noShow.eventId, RegistrationStatus.NO_SHOW),
                ),
            ),
        )
        val activity = launch(repo).get()

        activity.findViewById<TextView>(R.id.chipRegistered).performClick()

        assertEquals(2, activity.cardCount())
        assertEquals(activity.countLabel(2), activity.passCount())
        assertTrue(activity.findViewById<TextView>(R.id.chipRegistered).isSelected)
    }

    /** A failed registration load offers Retry, never the "no registrations" claim (§6.2). */
    @Test
    fun registeredChip_failedRegistrationLoadOffersRetry() {
        val repo = CountingRepo(
            result = listOf(event("Night Market", 0)),
            registrations = NetworkResult.Error("Offline"),
        )
        val activity = launch(repo).get()

        activity.findViewById<TextView>(R.id.chipRegistered).performClick()

        assertEquals(0, activity.cardCount())
        assertEquals(
            activity.getString(R.string.ui_attendee_registrations_error_title),
            activity.emptyTitle(),
        )
        assertEquals(
            activity.getString(R.string.ui_attendee_retry),
            activity.emptyAction().text.toString(),
        )
    }

    /** A failed pull-to-refresh keeps the rows and the last live-registration set; it never wipes them. */
    @Test
    fun registeredChip_refreshFailureKeepsRowsAndTheLiveSet() {
        val live = event("Live Pass", 0)
        val repo = CountingRepo(
            result = listOf(live),
            registrations = NetworkResult.Success(
                listOf(registration(live.eventId, RegistrationStatus.REGISTERED)),
            ),
        )
        val controller = launch(repo)
        val activity = controller.get()
        activity.findViewById<TextView>(R.id.chipRegistered).performClick()
        assertEquals(1, activity.cardCount())

        repo.registrations = NetworkResult.Error("Offline")
        // The real refresh trigger: returning to the list runs the silent reload.
        controller.pause().resume()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("a failed refresh must not delete the Registered rows", 1, activity.cardCount())
        assertEquals(View.GONE, activity.emptyState().visibility)
        assertTrue(activity.findViewById<TextView>(R.id.chipRegistered).isSelected)
    }

    /**
     * A restored Registered chip renders before the registrations answer exists. It must keep the
     * skeleton (and no result count) instead of claiming "no registered events" and 0 EVENTS.
     */
    @Test
    fun registeredChip_waitsForTheFirstAnswerInsteadOfPaintingEmpty() {
        val live = event("Live Pass", 0)
        val registrations = CompletableDeferred<NetworkResult<List<RegistrationResponse>>>()
        val state = Bundle().apply { putString("discover.filter.status", "REGISTERED") }
        val activity = launch(DeferredRegistrationsRepo(listOf(live), registrations), state).get()

        assertTrue(activity.findViewById<TextView>(R.id.chipRegistered).isSelected)
        assertEquals(
            "the skeleton stays while the answer is in flight",
            View.VISIBLE,
            activity.findViewById<ViewGroup>(R.id.skeletonLoading).visibility,
        )
        assertEquals(
            "no empty state may paint without an answer",
            View.GONE,
            activity.emptyState().visibility,
        )
        assertEquals(
            activity.getString(R.string.common_value_unavailable),
            activity.passCount(),
        )

        registrations.complete(
            NetworkResult.Success(listOf(registration(live.eventId, RegistrationStatus.REGISTERED))),
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, activity.findViewById<ViewGroup>(R.id.skeletonLoading).visibility)
        assertEquals(View.GONE, activity.emptyState().visibility)
        assertEquals(1, activity.cardCount())
        assertEquals(activity.countLabel(1), activity.passCount())
    }

    /** Success with nothing live still reads as "no live registrations", not an error. */
    @Test
    fun registeredChip_noLiveRegistrationUsesTheHonestEmptyCopy() {
        val activity = launch(CountingRepo(listOf(event("Night Market", 0)))).get()

        activity.findViewById<TextView>(R.id.chipRegistered).performClick()

        assertEquals(0, activity.cardCount())
        assertEquals(
            activity.getString(R.string.ui_attendee_no_registered_events_to_show),
            activity.emptyTitle(),
        )
        assertEquals(View.GONE, activity.emptyAction().visibility)
    }

    /** Status -> date -> search, all ANDed; a status tap never drops the date or the query. */
    @Test
    fun dateFilter_andsWithStatusAndSearch() {
        val month = nextMonth()
        val repo = CountingRepo(
            listOf(
                eventAt("Night Market", at(month, 10)),
                eventAt("Day Fair", at(month, 20)),
                eventAt("Night Market Old", at(nextMonth(2), 10)),
            ),
        )
        val activity = launch(repo).get()
        assertEquals(3, activity.cardCount())
        assertEquals(activity.countLabel(3), activity.passCount())

        activity.applyDateFilter(DiscoverDateFilter.MonthOf(month))
        assertEquals(2, activity.cardCount())
        assertEquals("the pass-head count is the filtered result count", activity.countLabel(2), activity.passCount())
        val chip = activity.findViewById<TextView>(R.id.chipDateFilter)
        assertTrue("the applied filter must fill the chip", chip.isSelected)
        assertEquals(DateFormatters.formatMonthYear(month), chip.text.toString())

        activity.findViewById<EditText>(R.id.inputEventSearch).setText("night")
        assertEquals(1, activity.cardCount())
        assertEquals(activity.countLabel(1), activity.passCount())

        activity.findViewById<TextView>(R.id.chipRegistered).performClick()
        assertEquals(0, activity.cardCount())
        assertEquals(activity.countLabel(0), activity.passCount())
        assertTrue("a status tap must not clear the date", chip.isSelected)
        assertEquals(DateFormatters.formatMonthYear(month), chip.text.toString())
    }

    /** Date empty copy + `Clear date` only when clearing really reveals rows. */
    @Test
    fun dateEmptyState_offersClearDateWhenClearingHelps() {
        val month = nextMonth()
        val repo = CountingRepo(listOf(eventAt("Night Market", at(nextMonth(2), 10))))
        val activity = launch(repo).get()

        activity.applyDateFilter(DiscoverDateFilter.MonthOf(month))
        assertEquals(0, activity.cardCount())
        assertEquals(
            activity.getString(
                R.string.ui_attendee_filter_empty_month,
                DateFormatters.formatMonthYear(month),
            ),
            activity.emptyTitle(),
        )
        val action = activity.emptyAction()
        assertEquals(View.VISIBLE, action.visibility)
        assertEquals(
            activity.getString(R.string.ui_attendee_filter_clear_date),
            action.text.toString(),
        )

        action.performClick()
        assertEquals(1, activity.cardCount())
        assertEquals(View.GONE, activity.findViewById<View>(R.id.txtEventsEmpty).visibility)
        assertFalse(activity.findViewById<TextView>(R.id.chipDateFilter).isSelected)
    }

    /**
     * Date copy needs both an empty window and a clear that would reveal a match. Here the window
     * has a row; only the search misses, so the ordinary copy stays and offers nothing to clear.
     */
    @Test
    fun emptyState_keepsTheNormalCopyWhenClearingWouldNotHelp() {
        val month = nextMonth()
        val repo = CountingRepo(listOf(eventAt("Night Market", at(month, 10))))
        val activity = launch(repo).get()

        activity.applyDateFilter(DiscoverDateFilter.MonthOf(month))
        activity.findViewById<EditText>(R.id.inputEventSearch).setText("zzz")
        assertEquals(0, activity.cardCount())
        assertEquals(
            activity.getString(R.string.attendee_events_no_events_available_yet),
            activity.emptyTitle(),
        )
        assertEquals(View.GONE, activity.emptyAction().visibility)
    }

    /**
     * The search misses inside the window while a match sits outside it: the window itself has
     * rows, so the date is not what emptied the list and the generic copy wins over
     * "No events in <month>" plus Clear date.
     */
    @Test
    fun emptyState_keepsTheNormalCopyWhenTheWindowItselfHasRows() {
        val month = nextMonth()
        val repo = CountingRepo(
            listOf(
                eventAt("Night Market", at(month, 10)),
                eventAt("Day Fair", at(month.plusMonths(1), 10)),
            ),
        )
        val activity = launch(repo).get()

        activity.applyDateFilter(DiscoverDateFilter.MonthOf(month))
        assertEquals(1, activity.cardCount())
        activity.findViewById<EditText>(R.id.inputEventSearch).setText("fair")
        assertEquals(0, activity.cardCount())
        assertEquals(
            activity.getString(R.string.attendee_events_no_events_available_yet),
            activity.emptyTitle(),
        )
        assertEquals(View.GONE, activity.emptyAction().visibility)
    }

    /** Empty date window, but clearing the date would not reveal a matching row. */
    @Test
    fun emptyState_keepsTheNormalCopyWhenTheEmptyWindowHasNoSearchMatch() {
        val month = nextMonth()
        val repo = CountingRepo(listOf(eventAt("Night Market", at(nextMonth(2), 10))))
        val activity = launch(repo).get()

        activity.applyDateFilter(DiscoverDateFilter.MonthOf(month))
        assertEquals(0, activity.cardCount())
        activity.findViewById<EditText>(R.id.inputEventSearch).setText("zzz-no-match")
        assertEquals(0, activity.cardCount())
        assertEquals(
            activity.getString(R.string.attendee_events_no_events_available_yet),
            activity.emptyTitle(),
        )
        assertEquals(View.GONE, activity.emptyAction().visibility)
    }

    /** The day-specific copy names the day with its year, per the §7.6 filter exception. */
    @Test
    fun dayEmptyState_namesTheDayWithTheYear() {
        val month = nextMonth()
        val eventDay = month.atDay(10)
        val repo = CountingRepo(listOf(eventAt("Night Market", at(month, 10))))
        val activity = launch(repo).get()

        val emptyDay = eventDay.plusDays(1)
        activity.applyDateFilter(DiscoverDateFilter.DayOf(emptyDay))
        assertEquals(0, activity.cardCount())
        assertEquals(
            activity.getString(
                R.string.ui_attendee_filter_empty_day,
                DateFormatters.formatEventDate(emptyDay.atStartOfDay(DateFilterRanges.ZONE).toInstant()),
            ),
            activity.emptyTitle(),
        )
        assertEquals(
            activity.getString(R.string.ui_attendee_filter_clear_date),
            activity.emptyAction().text.toString(),
        )
    }

    /** Date -> "Date" with nothing applied; Month -> "Nov 2026"; Day -> "12 Nov 2026". */
    @Test
    fun dateChip_showsTheAppliedValueAndSelectedState() {
        val activity = launch(CountingRepo(emptyList())).get()
        val chip = activity.findViewById<TextView>(R.id.chipDateFilter)

        assertEquals(activity.getString(R.string.ui_attendee_date), chip.text.toString())
        assertFalse(chip.isSelected)
        assertEquals(
            activity.getString(R.string.ui_attendee_filter_date),
            chip.contentDescription.toString(),
        )

        activity.applyDateFilter(DiscoverDateFilter.MonthOf(YearMonth.of(2026, 11)))
        assertEquals("Nov 2026", chip.text.toString())
        assertTrue(chip.isSelected)
        assertEquals(
            activity.getString(R.string.ui_attendee_filter_date_value, "Nov 2026"),
            chip.contentDescription.toString(),
        )

        activity.applyDateFilter(DiscoverDateFilter.DayOf(LocalDate.of(2026, 11, 12)))
        assertEquals("12 Nov 2026", chip.text.toString())
        assertTrue(chip.isSelected)
        assertEquals(
            activity.getString(R.string.ui_attendee_filter_date_value, "12 Nov 2026"),
            chip.contentDescription.toString(),
        )
    }

    /** Rotation keeps the filter; the recreated screen renders with it already applied. */
    @Test
    fun savedState_restoresTheAppliedDateFilter() {
        val month = nextMonth()
        val repo = CountingRepo(
            listOf(
                eventAt("Night Market", at(month, 10)),
                eventAt("Day Fair", at(nextMonth(2), 10)),
            ),
        )
        val controller = launch(repo)
        val activity = controller.get()
        activity.applyDateFilter(DiscoverDateFilter.MonthOf(month))
        assertEquals(1, activity.cardCount())

        val state = Bundle()
        controller.saveInstanceState(state)
        controller.pause().stop().destroy()

        val recreated = launch(repo, state).get()
        assertEquals(1, recreated.cardCount())
        val chip = recreated.findViewById<TextView>(R.id.chipDateFilter)
        assertTrue(chip.isSelected)
        assertEquals(DateFormatters.formatMonthYear(month), chip.text.toString())
    }

    /** Rotation reopens the sheet with the uncommitted draft; the draft itself filters nothing yet. */
    @Test
    fun savedState_reopensTheDateSheetWithItsDraft() {
        val repo = CountingRepo(listOf(eventAt("Night Market", at(nextMonth(), 10))))
        val controller = launch(repo)
        val activity = controller.get()

        activity.findViewById<TextView>(R.id.chipDateFilter).performClick()
        val sheet = activity.dateSheet
        assertNotNull("the date chip must open the sheet", sheet)
        sheet!!.view.findViewById<TextView>(R.id.chipModeMonth).performClick()
        assertEquals(DateFilterMode.MONTH, sheet.draft.mode)

        val state = Bundle()
        controller.saveInstanceState(state)
        controller.pause().stop().destroy()

        val recreated = launch(repo, state).get()
        val reopened = recreated.dateSheet
        assertNotNull("the sheet must reopen with the recreated screen", reopened)
        assertEquals(DateFilterMode.MONTH, reopened!!.draft.mode)
        assertNull("an uncommitted draft must not filter the list", recreated.dateFilter)
    }

    /** A pull-to-refresh (or a return from detail) reloads the cards without dropping the date. */
    @Test
    fun refresh_keepsTheAppliedDateFilter() {
        val month = nextMonth()
        val repo = CountingRepo(
            listOf(eventAt("Night Market", at(month, 10)), eventAt("Day Fair", at(nextMonth(2), 10))),
        )
        val controller = launch(repo)
        val activity = controller.get()
        activity.applyDateFilter(DiscoverDateFilter.MonthOf(month))
        assertEquals(1, activity.cardCount())

        repo.result = listOf(
            eventAt("Night Market", at(month, 11)),
            eventAt("Day Fair", at(nextMonth(2), 11)),
        )
        controller.pause().resume()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, repo.calls)
        assertEquals(1, activity.cardCount())
        assertTrue(activity.findViewById<TextView>(R.id.chipDateFilter).isSelected)
    }

    /** The status chip survives rotation too, and the restored screen renders with it applied. */
    @Test
    fun savedState_restoresTheStatusChip() {
        val repo = CountingRepo(listOf(event("Night Market", 0), event("Day Fair", 0)))
        val controller = launch(repo)
        controller.get().findViewById<TextView>(R.id.chipThisWeek).performClick()
        assertTrue(controller.get().findViewById<TextView>(R.id.chipThisWeek).isSelected)

        val state = Bundle()
        controller.saveInstanceState(state)
        controller.pause().stop().destroy()

        val recreated = launch(repo, state).get()
        assertTrue(
            "the status chip must be selected again, not reset to All",
            recreated.findViewById<TextView>(R.id.chipThisWeek).isSelected,
        )
        assertFalse(recreated.findViewById<TextView>(R.id.chipAll).isSelected)
        assertEquals(2, recreated.cardCount())
    }

    /** Sheet Apply is the only commit: it sets the month filter and re-renders the list. */
    @Test
    fun dateSheet_applyCommitsTheMonth() {
        val month = nextMonth()
        val repo = CountingRepo(
            listOf(eventAt("Night Market", at(month, 10)), eventAt("Day Fair", at(nextMonth(2), 10))),
        )
        val activity = launch(repo).get()
        activity.findViewById<TextView>(R.id.chipDateFilter).performClick()
        val sheet = activity.dateSheet
        assertNotNull("the date chip must open the sheet", sheet)

        activity.applySheetMonth(sheet!!, month)
        sheet.view.findViewById<View>(R.id.sheetPrimaryAction).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(DiscoverDateFilter.MonthOf(month), activity.dateFilter)
        assertEquals(1, activity.cardCount())
        val chip = activity.findViewById<TextView>(R.id.chipDateFilter)
        assertTrue(chip.isSelected)
        assertEquals(DateFormatters.formatMonthYear(month), chip.text.toString())
        assertNull("Apply closes the sheet", activity.dateSheet)
    }

    /** Reset rewinds the draft only; the applied filter and the list stay as they were. */
    @Test
    fun dateSheet_resetRewindsTheDraftWithoutApplying() {
        val month = nextMonth()
        val repo = CountingRepo(
            listOf(eventAt("Night Market", at(month, 10)), eventAt("Day Fair", at(nextMonth(2), 10))),
        )
        val activity = launch(repo).get()
        activity.applyDateFilter(DiscoverDateFilter.MonthOf(month))
        assertEquals(1, activity.cardCount())

        activity.findViewById<TextView>(R.id.chipDateFilter).performClick()
        val sheet = activity.dateSheet!!
        assertEquals("the sheet seeds from the applied filter", DateFilterMode.MONTH, sheet.draft.mode)
        sheet.view.findViewById<TextView>(R.id.chipModeDay).performClick()
        assertEquals(DateFilterMode.DAY, sheet.draft.mode)

        sheet.view.findViewById<View>(R.id.sheetSecondaryAction).performClick()

        assertEquals("Reset must not leak a mode", DateFilterMode.ANY, sheet.draft.mode)
        assertEquals(DiscoverDateFilter.MonthOf(month), activity.dateFilter)
        assertEquals(1, activity.cardCount())
        assertTrue(activity.findViewById<TextView>(R.id.chipDateFilter).isSelected)
    }

    /** A dismiss throws the draft away: the chip and the list keep the applied (here: no) filter. */
    @Test
    fun dateSheet_dismissDiscardsTheDraft() {
        val repo = CountingRepo(listOf(event("Night Market", 0), event("Day Fair", 0)))
        val activity = launch(repo).get()
        assertEquals(2, activity.cardCount())

        activity.findViewById<TextView>(R.id.chipDateFilter).performClick()
        val sheet = activity.dateSheet!!
        sheet.view.findViewById<TextView>(R.id.chipModeMonth).performClick()
        assertEquals(DateFilterMode.MONTH, sheet.draft.mode)

        sheet.dismiss()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull("the draft must not become the applied filter", activity.dateFilter)
        assertNull(activity.dateSheet)
        assertEquals(2, activity.cardCount())
        val chip = activity.findViewById<TextView>(R.id.chipDateFilter)
        assertFalse(chip.isSelected)
        assertEquals(activity.getString(R.string.ui_attendee_date), chip.text.toString())
    }

    /** Exact day with no day cannot describe a window, so Apply stays disabled. */
    @Test
    fun dateSheet_applyIsDisabledWhileExactDayHasNoDay() {
        val activity = launch(CountingRepo(listOf(event("Night Market", 0)))).get()
        activity.findViewById<TextView>(R.id.chipDateFilter).performClick()
        val sheet = activity.dateSheet!!
        val apply = sheet.view.findViewById<View>(R.id.sheetPrimaryAction)
        assertTrue("Any date is always applicable", apply.isEnabled)

        sheet.view.findViewById<TextView>(R.id.chipModeDay).performClick()

        assertNull("a fresh draft has no day yet", sheet.draft.dayOrNull())
        assertFalse("Apply must be disabled until a day is picked", apply.isEnabled)
        sheet.dismiss()
    }

    /** The picker result is the second half of Exact day: Apply becomes enabled in place. */
    @Test
    fun dateSheet_pickerResult_enablesApply() {
        val activity = launch(CountingRepo(listOf(event("Night Market", 0)))).get()
        activity.findViewById<TextView>(R.id.chipDateFilter).performClick()
        val sheet = activity.dateSheet!!
        val apply = sheet.view.findViewById<View>(R.id.sheetPrimaryAction)

        sheet.view.findViewById<TextView>(R.id.chipModeDay).performClick()
        assertFalse("Exact day is unapplicable until a day exists", apply.isEnabled)

        val picker = ShadowDialog.getLatestDialog() as DatePickerDialog
        val month = YearMonth.of(sheet.draft.year, sheet.draft.month)
        picker.datePicker.updateDate(month.year, month.monthValue - 1, 12)
        picker.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(LocalDate.of(month.year, month.monthValue, 12), sheet.draft.dayOrNull())
        assertTrue("a picked day makes the window describable", apply.isEnabled)
    }

    /** Choosing Exact day opens the platform picker immediately, bounded to the draft month. */
    @Test
    fun dateSheet_dayMode_opensThePickerBoundedToTheDraftMonth() {
        val activity = launch(CountingRepo(listOf(event("Night Market", 0)))).get()
        activity.findViewById<TextView>(R.id.chipDateFilter).performClick()
        val sheet = activity.dateSheet!!

        sheet.view.findViewById<TextView>(R.id.chipModeDay).performClick()

        val picker = ShadowDialog.getLatestDialog() as DatePickerDialog
        val month = YearMonth.of(sheet.draft.year, sheet.draft.month)
        val zone = ZoneId.systemDefault()
        assertEquals(
            month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            picker.datePicker.minDate,
        )
        assertEquals(
            month.atEndOfMonth().atStartOfDay(zone).toInstant().toEpochMilli(),
            picker.datePicker.maxDate,
        )
        sheet.dismiss()
    }

    /** With a valid day already in the draft, picking the mode again must not reopen a picker. */
    @Test
    fun dateSheet_dayMode_doesNotReopenThePickerWhenADayIsAlreadySet() {
        val activity = launch(CountingRepo(listOf(event("Night Market", 0)))).get()
        activity.applyDateFilter(DiscoverDateFilter.DayOf(LocalDate.now(DateFilterRanges.ZONE)))
        activity.findViewById<TextView>(R.id.chipDateFilter).performClick()
        val sheet = activity.dateSheet!!
        assertEquals(DateFilterMode.DAY, sheet.draft.mode)
        assertNotNull(sheet.draft.dayOrNull())

        val sheetDialog = ShadowDialog.getLatestDialog()
        sheet.view.findViewById<TextView>(R.id.chipModeDay).performClick()

        assertSame("a set day must not reopen the picker", sheetDialog, ShadowDialog.getLatestDialog())
        sheet.dismiss()
    }

    /** At the normal scale the date chip stays on the search row, as the plan specifies. */
    @Test
    fun standardFont_keepsTheDateChipOnTheSearchRow() {
        val activity = launch(CountingRepo(emptyList())).get()
        val row = activity.findViewById<LinearLayout>(R.id.searchRow)
        val chip = activity.findViewById<TextView>(R.id.chipDateFilter)

        assertEquals(LinearLayout.HORIZONTAL, row.orientation)
        assertTrue((chip.layoutParams as LinearLayout.LayoutParams).marginStart > 0)
        // The chip paints 36dp but carries a 48dp target (§9.2:1253).
        assertEquals(activity.resources.getDimensionPixelSize(R.dimen.ui_touch_min), chip.minHeight)
    }

    /** 1.3 is the threshold itself: the row stacks at that scale, not only above it. */
    @Test
    @Config(fontScale = 1.3f)
    fun thresholdFont_stacksTheDateChipAtOnePointThree() {
        val activity = launch(CountingRepo(emptyList())).get()
        assertEquals(
            LinearLayout.VERTICAL,
            activity.findViewById<LinearLayout>(R.id.searchRow).orientation,
        )
    }

    /** At 2.0 the row stacks, so neither label can collapse; the field keeps its 48dp target. */
    @Test
    @Config(fontScale = 2.0f)
    fun largeFont_stacksTheDateChipUnderTheFullWidthSearchField() {
        val activity = launch(CountingRepo(emptyList())).get()
        val row = activity.findViewById<LinearLayout>(R.id.searchRow)
        val field = activity.findViewById<EditText>(R.id.inputEventSearch)
        val chip = activity.findViewById<TextView>(R.id.chipDateFilter)

        assertEquals(LinearLayout.VERTICAL, row.orientation)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, field.layoutParams.width)
        // Height grows with the font scale; the 48dp target is a minimum, not a fixed height.
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, field.layoutParams.height)
        // The style's minHeight is the floor; the field grows past it at large font scales.
        assertEquals(
            activity.resources.getDimensionPixelSize(R.dimen.ui_field_search_height),
            field.minimumHeight,
        )
        val params = chip.layoutParams as LinearLayout.LayoutParams
        assertEquals(Gravity.END, params.gravity)
        assertEquals(activity.resources.getDimensionPixelSize(R.dimen.space_sm), params.topMargin)
        assertEquals(0, params.marginStart)
    }
}
