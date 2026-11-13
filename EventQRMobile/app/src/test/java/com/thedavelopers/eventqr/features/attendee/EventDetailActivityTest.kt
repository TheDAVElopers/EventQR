package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.api.dto.RedemptionStatus
import com.thedavelopers.eventqr.core.api.dto.RewardStatus
import com.thedavelopers.eventqr.core.format.formatEventTime
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.ShadowEncryptedSharedPreferences
import com.thedavelopers.eventqr.features.ShadowMasterKeys
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_TITLE
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventAvailabilityResponse
import com.thedavelopers.eventqr.features.organizer.events.EventManagementHubActivity
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.time.Instant
import java.util.UUID

/**
 * `TextView.JUSTIFICATION_MODE_NONE` / `_INTER_WORD` are the platform's own values but are not in the
 * public SDK stubs, so the assertions name them here.
 */
private const val JUSTIFICATION_MODE_NONE = 0
private const val JUSTIFICATION_MODE_INTER_WORD = 1

/** The header is a fixed "Event details"; the event name belongs on the banner above the description. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [ShadowMasterKeys::class, ShadowEncryptedSharedPreferences::class])
class EventDetailActivityTest {

    private fun build(title: String?): EventDetailActivity {
        val intent = Intent(ApplicationProvider.getApplicationContext(), EventDetailActivity::class.java)
        title?.let { intent.putExtra(EXTRA_EVENT_TITLE, it) }
        return Robolectric.buildActivity(EventDetailActivity::class.java, intent).create().get()
    }

    private fun event(title: String) = AttendeeEventResponse(eventId = UUID.randomUUID(), title = title, description = "About the event")

    @After
    fun resetFontScale() {
        RuntimeEnvironment.setFontScale(1.0f)
    }

    /**
     * A font-scale change recreates the activity (no `configChanges` in the manifest), so the scale
     * must be set before the activity is built. Availability is queued, never fetched.
     */
    private fun buildWithRegWindow(fontScale: Float): EventDetailActivity {
        RuntimeEnvironment.setFontScale(fontScale)
        val activity = build(null)
        activity.repository = QueuedAvailabilityRepo()
        activity.renderEvent(
            event("Windowed").copy(
                registrationOpenAt = Instant.now().plusSeconds(86_400),
                registrationCloseAt = Instant.now().plusSeconds(172_800),
            ),
        )
        return activity
    }

    @Test
    fun regWindowRow_aboveTheFontScaleThreshold_stacksTheValueUnderTheLabel() {
        val activity = buildWithRegWindow(2.0f)
        val row = activity.findViewById<LinearLayout>(R.id.layoutRegWindowRow)
        val value = activity.findViewById<TextView>(R.id.txtDetailRegWindow)
        val params = value.layoutParams as LinearLayout.LayoutParams

        assertEquals(LinearLayout.VERTICAL, row.orientation)
        assertEquals(Gravity.START, value.gravity and Gravity.START)
        assertEquals(LinearLayout.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(0f, params.weight, 0f)
        assertTrue(params.topMargin > 0)
    }

    @Test
    fun regWindowRow_atTheDefaultFontScale_keepsTheSharedRow() {
        val activity = buildWithRegWindow(1.0f)
        val row = activity.findViewById<LinearLayout>(R.id.layoutRegWindowRow)
        val params = activity.findViewById<TextView>(R.id.txtDetailRegWindow).layoutParams as LinearLayout.LayoutParams

        assertEquals(LinearLayout.HORIZONTAL, row.orientation)
        assertEquals(0, params.width)
        assertEquals(1f, params.weight, 0f)
    }

    // One range treatment, one row geometry: EVENT TIME and REGISTRATION WINDOW are the only two
    // rows that print a range, so they must not differ in floor or padding (the organizer page's
    // ui_metric_row_height + space_12), which is what makes them break at the same place.
    @Test
    fun regWindowRow_hasTheSameGeometryAsTheEventTimeRow() {
        val activity = buildWithRegWindow(1.0f)
        val floor = activity.resources.getDimensionPixelSize(R.dimen.ui_metric_row_height)
        val inset = activity.resources.getDimensionPixelSize(R.dimen.space_12)

        listOf(R.id.layoutEventTimeRow, R.id.layoutRegWindowRow).forEach { rowId ->
            val row = activity.findViewById<View>(rowId)
            val name = activity.resources.getResourceEntryName(rowId)
            assertEquals("$name floor", floor, row.minimumHeight)
            assertEquals("$name top inset", inset, row.paddingTop)
            assertEquals("$name bottom inset", inset, row.paddingBottom)
        }
    }

    @Test
    fun whileLoading_headerSaysEventDetailsAndTheBannerShowsTheNameFromTheIntent() {
        val activity = build("Summer Fest")

        assertEquals("Event details", activity.findViewById<TextView>(R.id.nav_header_title).text.toString())
        assertEquals("Summer Fest", activity.findViewById<TextView>(R.id.txtDetailTitle).text.toString())
    }

    @Test
    fun renderEvent_putsTheNameOnTheBannerAndLeavesTheHeaderAlone() {
        val activity = build(null)

        activity.renderEvent(event("Night Market"))

        assertEquals("Night Market", activity.findViewById<TextView>(R.id.txtDetailTitle).text.toString())
        assertEquals("Event details", activity.findViewById<TextView>(R.id.nav_header_title).text.toString())
        assertEquals("About the event", activity.findViewById<TextView>(R.id.txtDetailDescription).text.toString())
    }

    @Test
    fun renderEvent_unlimitedCapacityIsLabelledUnlimitedNotDashes() {
        val activity = build(null)

        activity.renderEvent(event("Open Gate").copy(capacity = 0, currentAttendeeCount = 7))

        // renderEvent shows availability through the registration status block once the availability call returns;
        // the intent-extra path renders the same text immediately, so exercise that path too.
        val intent = Intent(ApplicationProvider.getApplicationContext(), EventDetailActivity::class.java)
            .putExtra(EXTRA_EVENT_COUNT, "7").putExtra(EXTRA_EVENT_CAPACITY, "0")
        val fromIntent = Robolectric.buildActivity(EventDetailActivity::class.java, intent).create().get()

        assertEquals("Unlimited (7 registered)", fromIntent.findViewById<TextView>(R.id.txtDetailCapacity).text.toString())
    }

    private fun availability(event: AttendeeEventResponse, count: Int, capacity: Int) = EventAvailabilityResponse(
        eventId = event.eventId, capacity = capacity, currentAttendeeCount = count,
        registrationOpen = true, full = false, available = true, message = "",
    )

    @Test
    fun availability_redrawsTheCapacityLineWithTheFreshCount() {
        // Launch snapshot from the list said 0 / 12; the availability call after registering says 1 / 12.
        val intent = Intent(ApplicationProvider.getApplicationContext(), EventDetailActivity::class.java)
            .putExtra(EXTRA_EVENT_COUNT, "0").putExtra(EXTRA_EVENT_CAPACITY, "12")
        val activity = Robolectric.buildActivity(EventDetailActivity::class.java, intent).create().get()
        val capacityView = activity.findViewById<TextView>(R.id.txtDetailCapacity)
        val fest = event("Fest").copy(capacity = 12, currentAttendeeCount = 0)
        val before = capacityView.text.toString()

        activity.updateRegisterButtonFromAvailability(fest, availability(fest, count = 1, capacity = 12))

        assertEquals(activity.getString(R.string.event_detail_capacity_registered, 0, 12), before)
        assertEquals(activity.getString(R.string.event_detail_capacity_registered, 1, 12), capacityView.text.toString())

        // After a cancel the next availability response drops the count back.
        activity.updateRegisterButtonFromAvailability(fest, availability(fest, count = 0, capacity = 12))
        assertEquals(activity.getString(R.string.event_detail_capacity_registered, 0, 12), capacityView.text.toString())
    }

    @Test
    fun statusTurningUnregisteredAfterAvailability_restoresRegisterInsteadOfAlreadyRegistered() {
        val activity = build(null)
        val button = activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent)
        val fest = event("Fest").copy(capacity = 12, currentAttendeeCount = 1)

        // Availability lands while the (stale) status still says registered.
        activity.updateRegistrationStatus(true)
        activity.updateRegisterButtonFromAvailability(fest, availability(fest, count = 0, capacity = 12))
        assertEquals(activity.getString(R.string.event_detail_already_registered), button.text.toString())

        // The fresh status (cancelled elsewhere) arrives afterwards.
        activity.updateRegistrationStatus(false)

        assertEquals(activity.getString(R.string.event_detail_register), button.text.toString())
        assertEquals(true, button.isEnabled)
    }

    // A registered attendee's primary action is the pass (§6.2). The pass moved here when the
    // registered-events list stopped carrying a per-row button.
    @Test
    fun registeredAttendee_opensThePassFromThePrimaryAction() {
        val activity = build(null)
        val button = activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent)

        activity.updateRegistrationStatus(true)
        assertEquals(activity.getString(R.string.event_detail_already_registered), button.text.toString())

        activity.setRegisteredId("registration-1")

        assertEquals(activity.getString(R.string.ui_attendee_view_pass), button.text.toString())
        assertEquals(true, button.isEnabled)

        button.performClick()
        assertEquals(
            QrDisplayActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )
    }

    // A checked-in (ENTERED) attendee still owns a pass: the pass id is the live registration id,
    // not the stricter cancellable id, which only a REGISTERED row with no recorded activity yields.
    @Test
    fun checkedInAttendee_stillGetsAnEnabledViewPass() {
        val activity = build(null)
        val future = event("Checked in").copy(eventStartAt = Instant.now().plusSeconds(86_400))
        val button = activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent)

        activity.renderEvent(future)
        activity.updateRegistrationStatus(true)
        activity.setRegisteredId("registration-entered")

        assertEquals(activity.getString(R.string.ui_attendee_view_pass), button.text.toString())
        assertEquals(true, button.isEnabled)

        button.performClick()
        assertEquals(
            QrDisplayActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )
        // Cancel keeps its own gate: no verified cancellable id, no cancel action.
        assertEquals(View.GONE, activity.findViewById<View>(R.id.btnCancelRegistration).visibility)
    }

    @Test
    fun cancellableIdAlone_neverDrivesThePassLabel() {
        val activity = build(null)
        val button = activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent)

        activity.updateRegistrationStatus(true)
        activity.setCancellableRegistration("registration-1")

        assertEquals(activity.getString(R.string.event_detail_already_registered), button.text.toString())
        assertEquals(false, button.isEnabled)
    }

    @Test
    fun unregisteredStatusBeforeAnyAvailability_leavesTheButtonForTheAvailabilityCall() {
        val activity = build(null)
        val fest = event("Fest").copy(capacity = 12)

        activity.updateRegistrationStatus(false)

        assertEquals(activity.getString(R.string.event_detail_loading), activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent).text.toString())
        activity.updateRegisterButtonFromAvailability(fest, availability(fest, count = 0, capacity = 12))
        assertEquals(activity.getString(R.string.event_detail_register), activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent).text.toString())
    }

    @Test
    fun cancelThenAvailability_dropsTheCount() {
        val activity = build(null)
        val capacityView = activity.findViewById<TextView>(R.id.txtDetailCapacity)
        val fest = event("Fest").copy(capacity = 12, currentAttendeeCount = 1)
        activity.updateRegisterButtonFromAvailability(fest, availability(fest, count = 0, capacity = 12))
        assertEquals(activity.getString(R.string.event_detail_capacity_registered, 0, 12), capacityView.text.toString())
    }

    // Audit X6: an approved attendee-owned event must not offer "Manage Event" — every organizer
    // management endpoint rejects an ATTENDEE, so the hub would only show a 403 error state.
    @Test
    fun attendeeOwnedEvent_offersRegisterNotManageEvent() {
        SessionManager(ApplicationProvider.getApplicationContext()).saveRole(AccountRole.ATTENDEE)
        val activity = build(null)
        activity.repository = QueuedAvailabilityRepo()
        val button = activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent)

        activity.renderEvent(event("Owned by me").copy(isOwnedByCurrentUser = true))
        shadowOf(Looper.getMainLooper()).idle()

        assertNotEquals(activity.getString(R.string.event_detail_manage_event), button.text.toString())
    }

    @Test
    fun organizerOwnedEvent_stillOffersManageEvent() {
        SessionManager(ApplicationProvider.getApplicationContext()).saveRole(AccountRole.ORGANIZER)
        val activity = build(null)
        val button = activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent)

        activity.renderEvent(event("Owned by me").copy(isOwnedByCurrentUser = true))

        assertEquals(activity.getString(R.string.event_detail_manage_event), button.text.toString())
    }

    // Precedence: an organizer who also holds a live registration (role promotion, ownership
    // transfer) manages the event. The registration status and its id must not repaint the label
    // or redirect the click to an attendee pass.
    @Test
    fun managerWithALiveRegistration_keepsManageEventAndItsDestination() {
        SessionManager(ApplicationProvider.getApplicationContext()).saveRole(AccountRole.ORGANIZER)
        val activity = build(null)
        val button = activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent)

        activity.renderEvent(event("Owned by me").copy(isOwnedByCurrentUser = true))
        activity.updateRegistrationStatus(true)
        activity.setRegisteredId("registration-1")

        assertEquals(activity.getString(R.string.event_detail_manage_event), button.text.toString())
        button.performClick()
        assertEquals(
            EventManagementHubActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )
    }

    // An attendee-owned event offers Register, so it must also offer Cancel once registered,
    // otherwise the attendee is stuck in a registration they cannot drop.
    @Test
    fun attendeeOwnedEvent_showsCancelRegistrationOnceRegistered() {
        SessionManager(ApplicationProvider.getApplicationContext()).saveRole(AccountRole.ATTENDEE)
        val activity = build(null)
        activity.repository = QueuedAvailabilityRepo()
        val future = event("Owned by me").copy(
            isOwnedByCurrentUser = true,
            eventStartAt = Instant.now().plusSeconds(86_400),
        )

        activity.renderEvent(future)
        activity.setCancellableRegistration("registration-1")

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnCancelRegistration).visibility)
    }

    @Test
    fun organizerOwnedEvent_hidesCancelRegistration() {
        SessionManager(ApplicationProvider.getApplicationContext()).saveRole(AccountRole.ORGANIZER)
        val activity = build(null)
        val future = event("Owned by me").copy(
            isOwnedByCurrentUser = true,
            eventStartAt = Instant.now().plusSeconds(86_400),
        )

        activity.renderEvent(future)
        activity.setCancellableRegistration("registration-1")

        assertEquals(View.GONE, activity.findViewById<View>(R.id.btnCancelRegistration).visibility)
    }

    // ── §6.2 STATUS row: one binding path for all eight lifecycle values ─────────────────────────

    /** A repo whose availability read never lands: the button state in this test is explicit. */
    private class LoadingAvailabilityRepo : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        override suspend fun getEventAvailability(eventId: String): NetworkResult<EventAvailabilityResponse> =
            NetworkResult.Loading
    }

    /**
     * Dates that make [EventStatusBadgeStyler.resolve] return the same status: the time-derived
     * values come from the range, the rest pass the raw status through.
     */
    private fun statusFixture(status: EventStatus): AttendeeEventResponse {
        val now = Instant.now()
        val (start, end) = when (status) {
            EventStatus.APPROVED -> now.plusSeconds(86_400) to now.plusSeconds(172_800)
            EventStatus.ACTIVE -> now.minusSeconds(3_600) to now.plusSeconds(3_600)
            EventStatus.ENDED -> now.minusSeconds(172_800) to now.minusSeconds(86_400)
            else -> null to null
        }
        return event("Status ${status.name}").copy(status = status, eventStartAt = start, eventEndAt = end)
    }

    @Test
    fun statusRow_bindsAllEightStatusesAndThePrimaryActionTheCodeDefines() {
        EventStatus.values().forEach { raw ->
            val fixture = statusFixture(raw)
            val resolved = EventStatusBadgeStyler.resolve(raw, fixture.eventStartAt, fixture.eventEndAt)
            // A resolver that collapsed every raw value to UNKNOWN would make the loop pass on paper
            // alone; the fixture picks dates so each status resolves to itself.
            assertEquals("$raw: resolves to itself", raw, resolved)
            val activity = build(null)
            activity.repository = LoadingAvailabilityRepo()
            activity.renderEvent(fixture)
            shadowOf(Looper.getMainLooper()).idle()

            val row = activity.findViewById<View>(R.id.layoutStatusRow)
            val badge = activity.findViewById<TextView>(R.id.txtDetailStatus)
            if (resolved == EventStatus.UNKNOWN) {
                assertEquals("$raw: an unnamed status hides the row", View.GONE, row.visibility)
                assertEquals(
                    "$raw: and its hairline",
                    View.GONE,
                    activity.findViewById<View>(R.id.viewStatusDivider).visibility,
                )
            } else {
                assertEquals("$raw: STATUS row is shown", View.VISIBLE, row.visibility)
                assertEquals(
                    "$raw: badge text",
                    AttendeeBadges.label(activity, resolved),
                    badge.text.toString(),
                )
                val tone = AttendeeBadges.tone(resolved)
                assertEquals(
                    "$raw: badge content tone",
                    ContextCompat.getColor(activity, tone.content),
                    badge.currentTextColor,
                )
                assertEquals(
                    "$raw: badge container tone",
                    ContextCompat.getColor(activity, tone.container),
                    ViewCompat.getBackgroundTintList(badge)?.defaultColor,
                )
            }

            // The primary action as the code defines it: a cancelled event outranks every other state.
            activity.updateRegisterButtonFromAvailability(fixture, availability(fixture, count = 0, capacity = 12))
            val button = activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent)
            if (raw == EventStatus.CANCELLED) {
                assertEquals(
                    "$raw: primary action",
                    activity.getString(R.string.event_detail_event_cancelled),
                    button.text.toString(),
                )
                assertFalse("$raw: a cancelled event's action is inert", button.isEnabled)
            } else {
                assertEquals(
                    "$raw: primary action",
                    activity.getString(R.string.event_detail_register),
                    button.text.toString(),
                )
                assertTrue("$raw: an open event offers Register", button.isEnabled)
            }
            activity.finish()
        }
    }

    /** Each availability call suspends on its own deferred, so tests decide the order responses land in. */
    private class QueuedAvailabilityRepo : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        val pending = mutableListOf<CompletableDeferred<NetworkResult<EventAvailabilityResponse>>>()
        override suspend fun getEventAvailability(eventId: String): NetworkResult<EventAvailabilityResponse> =
            CompletableDeferred<NetworkResult<EventAvailabilityResponse>>().also { pending += it }.await()
    }

    @Test
    fun olderAvailabilityResponseLandingLast_isDroppedAfterACancel() {
        val activity = build(null)
        val repo = QueuedAvailabilityRepo()
        activity.repository = repo
        val capacityView = activity.findViewById<TextView>(R.id.txtDetailCapacity)
        val fest = event("Fest").copy(capacity = 12, currentAttendeeCount = 1)

        activity.renderEvent(fest) // first availability request (pre-cancel, slow)
        shadowOf(Looper.getMainLooper()).idle()
        activity.onRegistrationCancelled("Registration cancelled") // second request supersedes it
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, repo.pending.size)

        repo.pending[1].complete(NetworkResult.Success(availability(fest, count = 0, capacity = 12)))
        shadowOf(Looper.getMainLooper()).idle()
        repo.pending[0].complete(NetworkResult.Success(availability(fest, count = 1, capacity = 12)))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(activity.getString(R.string.event_detail_capacity_registered, 0, 12), capacityView.text.toString())
        assertEquals(
            activity.getString(R.string.event_detail_register),
            activity.findViewById<android.widget.Button>(R.id.btnRegisterForEvent).text.toString(),
        )
    }

    // ── §6.2 spec table: one row order, one formatter, a read-only REWARDS indicator ─────────────

    /**
     * The rewards read is queued, so a test decides when (and whether) it lands; the attendee's own
     * redemptions resolve at once (the Rewards screen's claim narrowing), and availability resolves
     * at once too, because these assertions are not about the register button.
     */
    private class QueuedRewardsRepo(
        private val redemptions: NetworkResult<List<RewardRedemptionResponse>> =
            NetworkResult.Success(emptyList()),
    ) : AttendeeRepository(ApplicationProvider.getApplicationContext()) {
        val pendingRewards = CompletableDeferred<NetworkResult<List<RewardResponse>>>()
        var rewardsRequests = 0
        var redemptionRequests = 0

        override suspend fun getMyRewardRedemptions(eventId: String): NetworkResult<List<RewardRedemptionResponse>> {
            redemptionRequests++
            return redemptions
        }

        override suspend fun getRewardsByEvent(
            eventId: String,
            includeUnavailable: Boolean,
        ): NetworkResult<List<RewardResponse>> {
            rewardsRequests++
            return pendingRewards.await()
        }

        override suspend fun getEventAvailability(eventId: String) = NetworkResult.Success(
            EventAvailabilityResponse(
                eventId = UUID.fromString(eventId),
                capacity = 12,
                currentAttendeeCount = 0,
                registrationOpen = true,
                full = false,
                available = true,
                message = "",
            ),
        )
    }

    private fun reward(event: AttendeeEventResponse, allowDuplicateClaims: Boolean = false) = RewardResponse(
        rewardId = UUID.randomUUID(),
        eventId = event.eventId,
        name = "Sticker pack",
        pointsRequired = 50,
        status = RewardStatus.ACTIVE,
        allowDuplicateClaims = allowDuplicateClaims,
    )

    /** A reward this attendee already holds, which the indicator must stop calling available. */
    private fun redeemed(event: AttendeeEventResponse, reward: RewardResponse) = RewardRedemptionResponse(
        redemptionId = UUID.randomUUID(),
        eventId = event.eventId,
        attendeeUserId = UUID.randomUUID(),
        rewardId = reward.rewardId,
        pointsSpent = reward.pointsRequired,
        status = RedemptionStatus.REDEEMED,
    )

    /** A real transport error, so the code path reads the HTTP status instead of the message text. */
    private fun httpError(code: Int) = NetworkResult.Error(
        "HTTP $code",
        HttpException(Response.error<Any>(code, "".toResponseBody())),
    )

    private fun renderWithRewards(
        event: AttendeeEventResponse,
        repo: QueuedRewardsRepo = QueuedRewardsRepo(),
    ): Pair<EventDetailActivity, QueuedRewardsRepo> {
        val activity = build(null)
        activity.repository = repo
        activity.renderEvent(event)
        shadowOf(Looper.getMainLooper()).idle()
        return activity to repo
    }

    @Test
    fun rewardsRow_readsAvailableWhenTheEventListsOneReward() {
        val fest = event("Fest").copy(rewardsEnabled = true)
        val (activity, repo) = renderWithRewards(fest)
        val value = activity.findViewById<TextView>(R.id.txtRewardsAvailable)

        assertEquals(1, repo.rewardsRequests)
        assertEquals(
            "the row stays up and the value stays silent until the read lands",
            View.INVISIBLE,
            value.visibility,
        )

        repo.pendingRewards.complete(NetworkResult.Success(listOf(reward(fest))))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(activity.getString(R.string.ui_attendee_available), value.text.toString())
        assertEquals(View.VISIBLE, value.visibility)
    }

    @Test
    fun rewardsRow_readsNoneWhenTheEventListsNoRewards() {
        val fest = event("Fest").copy(rewardsEnabled = true)
        val (activity, repo) = renderWithRewards(fest)

        repo.pendingRewards.complete(NetworkResult.Success(emptyList()))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            activity.getString(R.string.ui_attendee_none),
            activity.findViewById<TextView>(R.id.txtRewardsAvailable).text.toString(),
        )
    }

    // A failed read that is not the registration gate shows nothing at all: "None" would claim the
    // event has no rewards. Read from a real 5xx, never from the message text.
    @Test
    fun rewardsValue_staysSilentOnAFailedRead() {
        val fest = event("Fest").copy(rewardsEnabled = true)
        val (activity, repo) = renderWithRewards(fest)

        repo.pendingRewards.complete(httpError(500))
        shadowOf(Looper.getMainLooper()).idle()

        val value = activity.findViewById<TextView>(R.id.txtRewardsAvailable)
        assertEquals(View.INVISIBLE, value.visibility)
        assertEquals("", value.text.toString())
    }

    // The rewards read is gated by `requireRegistered` too: a viewer who is not registered for the
    // event (an organizer browsing as attendee, or an attendee before registering) gets 403 with no
    // list at all. The organizer enabled rewards, so the row reads Available; a wrong "None" would
    // claim the event has nothing to claim.
    @Test
    fun rewardsRow_readsAvailableWhenTheRewardsReadIsForbidden() {
        val fest = event("Fest").copy(rewardsEnabled = true)
        val (activity, repo) = renderWithRewards(fest)

        repo.pendingRewards.complete(httpError(403))
        shadowOf(Looper.getMainLooper()).idle()

        val value = activity.findViewById<TextView>(R.id.txtRewardsAvailable)
        assertEquals(activity.getString(R.string.ui_attendee_available), value.text.toString())
        assertEquals(View.VISIBLE, value.visibility)
        assertEquals("no redemption read behind a forbidden list", 0, repo.redemptionRequests)
    }

    // The Rewards screen drops a reward the attendee already holds unless duplicates are allowed;
    // the indicator must narrow by the same rule, so a fully redeemed event reads None.
    @Test
    fun rewardsRow_readsNoneWhenEveryRewardIsAlreadyRedeemed() {
        val fest = event("Fest").copy(rewardsEnabled = true)
        val rewards = listOf(reward(fest), reward(fest))
        val repo = QueuedRewardsRepo(
            NetworkResult.Success(rewards.map { redeemed(fest, it) }),
        )
        val (activity, _) = renderWithRewards(fest, repo)

        repo.pendingRewards.complete(NetworkResult.Success(rewards))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, repo.redemptionRequests)
        assertEquals(
            activity.getString(R.string.ui_attendee_none),
            activity.findViewById<TextView>(R.id.txtRewardsAvailable).text.toString(),
        )
    }

    // A duplicate-friendly reward stays available after a claim: same rule, other direction.
    @Test
    fun rewardsRow_keepsAReclaimedRewardAvailableWhenDuplicatesAreAllowed() {
        val fest = event("Fest").copy(rewardsEnabled = true)
        val repeatable = reward(fest, allowDuplicateClaims = true)
        val repo = QueuedRewardsRepo(NetworkResult.Success(listOf(redeemed(fest, repeatable))))
        val (activity, _) = renderWithRewards(fest, repo)

        repo.pendingRewards.complete(NetworkResult.Success(listOf(repeatable)))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            activity.getString(R.string.ui_attendee_available),
            activity.findViewById<TextView>(R.id.txtRewardsAvailable).text.toString(),
        )
    }

    // The redemptions read needs a registration; a failed one must not silence a known rewards list.
    @Test
    fun rewardsRow_readsAvailableWhenTheRedemptionReadFails() {
        val fest = event("Fest").copy(rewardsEnabled = true)
        val repo = QueuedRewardsRepo(NetworkResult.Error("offline", null))
        val (activity, _) = renderWithRewards(fest, repo)

        repo.pendingRewards.complete(NetworkResult.Success(listOf(reward(fest))))
        shadowOf(Looper.getMainLooper()).idle()

        val value = activity.findViewById<TextView>(R.id.txtRewardsAvailable)
        assertEquals(activity.getString(R.string.ui_attendee_available), value.text.toString())
        assertEquals(View.VISIBLE, value.visibility)
    }

    // An organizer browsing as attendee is not registered, so `claimed-rewards` answers 403 while the
    // event's rewards list does answer: the row names availability from the list, never blank. The 403
    // is a real transport error, because the fix reads the status code, not the message text.
    @Test
    fun rewardsRow_readsAvailableForAnUnregisteredViewerWhoseRedemptionsAreForbidden() {
        val fest = event("Fest").copy(rewardsEnabled = true)
        val repo = QueuedRewardsRepo(httpError(403))
        val (activity, _) = renderWithRewards(fest, repo)

        repo.pendingRewards.complete(NetworkResult.Success(listOf(reward(fest))))
        shadowOf(Looper.getMainLooper()).idle()

        val value = activity.findViewById<TextView>(R.id.txtRewardsAvailable)
        assertEquals(activity.getString(R.string.ui_attendee_available), value.text.toString())
        assertEquals(View.VISIBLE, value.visibility)
    }

    // A re-bind of the same event (resume re-check) must not blank a value that already settled.
    @Test
    fun rewardsRow_keepsItsSettledValueWhenTheSameEventBindsAgain() {
        val fest = event("Fest").copy(rewardsEnabled = true)
        val (activity, repo) = renderWithRewards(fest)
        repo.pendingRewards.complete(NetworkResult.Success(listOf(reward(fest))))
        shadowOf(Looper.getMainLooper()).idle()
        val value = activity.findViewById<TextView>(R.id.txtRewardsAvailable)

        activity.renderEvent(fest)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, value.visibility)
        assertEquals(activity.getString(R.string.ui_attendee_available), value.text.toString())
    }

    @Test
    fun rewardsRow_startsBlankForADifferentEvent() {
        val fest = event("Fest").copy(rewardsEnabled = true)
        val (activity, repo) = renderWithRewards(fest)
        repo.pendingRewards.complete(NetworkResult.Success(listOf(reward(fest))))
        shadowOf(Looper.getMainLooper()).idle()
        val value = activity.findViewById<TextView>(R.id.txtRewardsAvailable)

        // A fresh repo keeps the other event's read pending, so only the reset is observable here.
        activity.repository = QueuedRewardsRepo()
        activity.renderEvent(event("Other").copy(rewardsEnabled = true))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.INVISIBLE, value.visibility)
        assertEquals("", value.text.toString())
    }

    // The row is a fact for every event: a disabled reward switch reads None and makes no read.
    @Test
    fun rewardsRow_readsNoneWhenTheEventHasRewardsDisabled() {
        val activity = build(null)
        val repo = QueuedRewardsRepo()
        activity.repository = repo
        activity.renderEvent(event("Fest").copy(rewardsEnabled = false))

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.layoutRewardsRow).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.viewRewardsDivider).visibility)
        val value = activity.findViewById<TextView>(R.id.txtRewardsAvailable)
        assertEquals(activity.getString(R.string.ui_attendee_none), value.text.toString())
        assertEquals(View.VISIBLE, value.visibility)
        assertEquals("no read for an event without rewards", 0, repo.rewardsRequests)
        assertEquals("no redemption read either", 0, repo.redemptionRequests)
    }

    // REWARDS reads like CAPACITY: two TalkBack facts, no action, no chevron, no ripple.
    @Test
    fun rewardsRow_isAnIndicatorNotALink() {
        val activity = build(null)
        activity.repository = QueuedRewardsRepo()
        activity.renderEvent(event("Fest").copy(rewardsEnabled = true))

        val row = activity.findViewById<LinearLayout>(R.id.layoutRewardsRow)
        val value = activity.findViewById<TextView>(R.id.txtRewardsAvailable)
        assertEquals(View.VISIBLE, row.visibility)
        assertEquals("label + value, nothing after the value", 2, row.childCount)
        assertFalse("no click target", row.isClickable)
        assertFalse("no focus stop", row.isFocusable)
        assertFalse("no listener", row.hasOnClickListeners())
        assertFalse(value.isClickable)
        assertFalse(value.isFocusable)
    }

    @Test
    fun eventTimeRow_usesTheSharedFormatterAndHidesWithoutAStart() {
        val sameDay = Instant.parse("2026-05-01T09:00:00Z")
        val fest = event("Fest").copy(eventStartAt = sameDay, eventEndAt = sameDay.plusSeconds(3 * 3600))
        val activity = build(null)
        activity.repository = QueuedRewardsRepo()
        activity.renderEvent(fest)
        val row = activity.findViewById<View>(R.id.layoutEventTimeRow)
        val value = activity.findViewById<TextView>(R.id.txtDetailEventTime)

        assertEquals("Event time", (row as LinearLayout).getChildAt(0).let { (it as TextView).text.toString() })
        assertEquals(formatEventTime(activity, fest.eventStartAt, fest.eventEndAt), value.text.toString())
        assertEquals(View.VISIBLE, row.visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.viewEventTimeDivider).visibility)

        // No start: the row and its hairline go together, never an empty label or an orphan rule.
        activity.renderEvent(event("No start"))
        assertEquals(View.GONE, row.visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.viewEventTimeDivider).visibility)
    }

    // Literal values, not the formatter compared with itself: both ends carry the year and the clock,
    // and the range breaks after the start date - `– end` on line 2 - so the break never depends on
    // how wide the value column happens to be.
    @Test
    fun regWindowRow_printsBothEndsInFull() {
        val fest = event("Fest").copy(
            registrationOpenAt = Instant.parse("2026-05-01T01:00:00Z"), // 1 May 2026, 9:00 AM
            registrationCloseAt = Instant.parse("2026-06-01T15:59:00Z"), // 1 Jun 2026, 11:59 PM
        )
        val activity = build(null)
        activity.repository = QueuedRewardsRepo()

        activity.renderEvent(fest)

        assertEquals(
            "1\u00A0May\u00A02026, 9:00\u00A0AM\n\u2013\u00A01\u00A0Jun\u00A02026, 11:59\u00A0PM",
            activity.findViewById<TextView>(R.id.txtDetailRegWindow).text.toString(),
        )
    }

    @Test
    fun regWindowRow_singleEndPrintsAloneAndNoEndHidesTheRow() {
        val fest = event("Fest").copy(registrationCloseAt = Instant.parse("2026-06-01T15:59:00Z"))
        val activity = build(null)
        activity.repository = QueuedRewardsRepo()
        val row = activity.findViewById<View>(R.id.layoutRegWindowRow)

        activity.renderEvent(fest)
        assertEquals(
            "1\u00A0Jun\u00A02026, 11:59\u00A0PM",
            activity.findViewById<TextView>(R.id.txtDetailRegWindow).text.toString(),
        )
        assertEquals(View.VISIBLE, row.visibility)

        activity.renderEvent(event("No window"))
        assertEquals(View.GONE, row.visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.viewRegWindowDivider).visibility)
    }

    // The old DATE and TIME rows are deleted ids, not hidden ones: one EVENT TIME row replaced them.
    @Test
    fun dateAndTimeRows_areDeletedIds() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf("txtDetailDate", "txtDetailTime", "layoutDateRow", "layoutTimeRow").forEach { gone ->
            assertEquals(
                "$gone must stay deleted: EVENT TIME is the one schedule row",
                0,
                context.resources.getIdentifier(gone, "id", context.packageName),
            )
        }
    }

    @Test
    fun eventTimeAndRegWindowRows_bothStackAboveTheFontScaleThreshold() {
        val activity = buildWithRegWindow(2.0f)

        assertTrue(
            "the production list must carry EVENT TIME",
            attendeeEventDetailStackedRows.contains(
                R.id.layoutEventTimeRow to R.id.txtDetailEventTime,
            ),
        )
        assertTrue(
            "the production list must carry REGISTRATION WINDOW",
            attendeeEventDetailStackedRows.contains(
                R.id.layoutRegWindowRow to R.id.txtDetailRegWindow,
            ),
        )

        attendeeEventDetailStackedRows.forEach { (rowId, valueId) ->
            val row = activity.findViewById<LinearLayout>(rowId)
            val value = activity.findViewById<TextView>(valueId)
            val params = value.layoutParams as LinearLayout.LayoutParams
            val name = activity.resources.getResourceEntryName(rowId)
            assertEquals("$name orientation", LinearLayout.VERTICAL, row.orientation)
            assertEquals("$name width", LinearLayout.LayoutParams.MATCH_PARENT, params.width)
            assertEquals("$name weight", 0f, params.weight, 0f)
            assertEquals("$name align", Gravity.START, value.gravity and Gravity.START)
        }
    }

    @Test
    fun blankDescription_hidesAboutAndItsHairlineOnly() {
        val fest = event("Fest").copy(
            description = " ",
            category = "Conference",
            registrationOpenAt = Instant.parse("2026-05-01T01:00:00Z"),
            registrationCloseAt = Instant.parse("2026-06-01T15:59:00Z"),
        )
        val activity = build(null)
        activity.repository = QueuedRewardsRepo()

        activity.renderEvent(fest)

        assertEquals(View.GONE, activity.findViewById<View>(R.id.txtDetailAboutLabel).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.txtDetailDescription).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.viewAboutDivider).visibility)
        assertEquals(
            "the row above keeps its hairline",
            View.VISIBLE,
            activity.findViewById<View>(R.id.viewRegWindowDivider).visibility,
        )
        assertEquals(
            "the row below keeps its hairline",
            View.VISIBLE,
            activity.findViewById<View>(R.id.viewCategoryDivider).visibility,
        )
    }

    /**
     * The About body is the one justified surface in the app (§6.2): inter-word, so the last line stays
     * start-aligned. Every label and value around it stays ragged-right.
     */
    @Test
    fun aboutBody_isInterWordJustifiedAndItsLabelIsNot() {
        val activity = build(null)
        activity.repository = QueuedRewardsRepo()

        activity.renderEvent(
            event("Fest").copy(description = "A one-day developer conference for students and professionals."),
        )

        assertEquals(
            JUSTIFICATION_MODE_INTER_WORD,
            activity.findViewById<TextView>(R.id.txtDetailDescription).justificationMode,
        )
        assertEquals(
            "the ABOUT label stays ragged-right",
            JUSTIFICATION_MODE_NONE,
            activity.findViewById<TextView>(R.id.txtDetailAboutLabel).justificationMode,
        )
    }

    /**
     * The one row order both event details use: STATUS, LOCATION, EVENT TIME, REGISTRATION WINDOW,
     * ABOUT, then the role rows (CATEGORY, AUDIENCE, CAPACITY, REWARDS). Every row is followed by its
     * own hairline, and About closes with one too.
     */
    @Test
    fun specTable_orderIsStatusLocationTimeWindowAboutThenRoleRows() {
        val fest = event("Fest").copy(
            location = "Cebu",
            category = "Conference",
            targetAudience = "Students",
            eventStartAt = Instant.parse("2026-05-01T09:00:00Z"),
            eventEndAt = Instant.parse("2026-05-01T17:00:00Z"),
            registrationOpenAt = Instant.parse("2026-04-01T09:00:00Z"),
            registrationCloseAt = Instant.parse("2026-04-30T17:00:00Z"),
            capacity = 12,
            rewardsEnabled = true,
        )
        val activity = build(null)
        activity.repository = QueuedRewardsRepo()
        activity.renderEvent(fest)

        val table = activity.findViewById<LinearLayout>(R.id.layoutSpecTable)
        val expected = listOf(
            R.id.layoutStatusRow,
            R.id.viewStatusDivider,
            R.id.layoutLocationRow,
            R.id.viewLocationDivider,
            R.id.layoutEventTimeRow,
            R.id.viewEventTimeDivider,
            R.id.layoutRegWindowRow,
            R.id.viewRegWindowDivider,
            R.id.txtDetailAboutLabel,
            R.id.txtDetailDescription,
            R.id.viewAboutDivider,
            R.id.layoutCategoryRow,
            R.id.viewCategoryDivider,
            R.id.layoutAudienceRow,
            R.id.viewAudienceDivider,
            R.id.layoutCapacityRow,
            R.id.viewCapacityDivider,
            R.id.layoutRewardsRow,
            R.id.viewRewardsDivider,
        )
        assertEquals(expected.size, table.childCount)
        expected.forEachIndexed { index, id ->
            assertEquals(
                "child $index is ${activity.resources.getResourceEntryName(id)}",
                id,
                table.getChildAt(index).id,
            )
        }
    }
}
