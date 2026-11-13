package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.util.UiStrings
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.EventStatus
import com.thedavelopers.eventqr.core.format.formatEventTime
import com.thedavelopers.eventqr.core.format.formatRegistrationWindow
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.attendee.claimableRewards
import com.thedavelopers.eventqr.features.attendee.redeemedRewardIds
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventAvailabilityResponse
import com.thedavelopers.eventqr.features.organizer.events.EventManagementHubActivity
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.MetricRowStacking
import com.thedavelopers.eventqr.ui.components.showInlineError
import com.thedavelopers.eventqr.ui.theme.applyEventQrBottomInsetPadding
import java.time.Instant
import kotlinx.coroutines.launch
import retrofit2.HttpException

// §6.2: above fontScale 1.3 the EVENT TIME and REGISTRATION WINDOW rows stack instead of sharing one
// line. The threshold and the reflow live in MetricRowStacking, shared with the organizer spec table.

open class EventDetailActivity : AppCompatActivity(), EventDetailContract.View {
    @VisibleForTesting
    internal lateinit var repository: AttendeeRepository
    private lateinit var presenter: EventDetailPresenter
    private lateinit var eventId: String
    private var currentEvent: AttendeeEventResponse? = null
    private var isAlreadyRegistered = false
    private var isOwnedByCurrentOrganizer = false
    private var isFirstResume = true
    private var registrationStatusCheckFailed = false
    private var cancellableRegistrationId: String? = null
    // The live registration's id, any status: this is what the pass opens (§6.2). Cancelling stays
    // gated on cancellableRegistrationId, which additionally requires REGISTERED + no recorded activity.
    private var registeredRegistrationId: String? = null
    private var cancelDialog: androidx.appcompat.app.AlertDialog? = null
    private var failureDialog: androidx.appcompat.app.AlertDialog? = null
    private var availabilityJob: kotlinx.coroutines.Job? = null
    private var rewardsAvailabilityJob: kotlinx.coroutines.Job? = null
    // The last settled REWARDS label and its event: a same-event re-bind re-shows it instead of
    // blanking a finished value while the fresh read runs.
    private var rewardsLabelEventId: String? = null
    private var rewardsLabel: String? = null
    // Re-draws the button from the last availability outcome when the registration status changes afterwards.
    private var reapplyAvailability: (() -> Unit)? = null
    private lateinit var errorStrip: View
    private lateinit var emptyState: EventQrEmptyState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        findViewById<View>(R.id.layoutBottomBar)?.applyEventQrBottomInsetPadding()

        errorStrip = findViewById(R.id.errorEventDetail)
        // §5.16: Retry re-runs the same load; the strip is hidden until a retry also fails.
        errorStrip.findViewById<View>(R.id.btnErrorStripRetry).setOnClickListener { retryLoad() }
        emptyState = findViewById(R.id.emptyEventDetailState)
        // §6.8 row 20: with no event yet, the spec table and About stay hidden, so a failed first
        // load can never print "--" rows or a blank body under a half-empty page. A description the
        // caller already holds is real content: it shows with its eyebrow while the load runs.
        val launchDescription = intent.getStringExtra(EXTRA_EVENT_DESCRIPTION).orEmpty()
        val hasLaunchDescription = launchDescription.isNotBlank()
        findViewById<View>(R.id.layoutSpecTable).visibility = View.GONE
        findViewById<View>(R.id.txtDetailAboutLabel).visibility = if (hasLaunchDescription) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.txtDetailDescription).apply {
            text = launchDescription
            visibility = if (hasLaunchDescription) View.VISIBLE else View.GONE
        }
        findViewById<View>(R.id.viewAboutDivider).visibility = if (hasLaunchDescription) View.VISIBLE else View.GONE

        repository = AttendeeRepository(this)
        presenter = EventDetailPresenter(this, repository, UiStrings(this))
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()

        findViewById<View>(R.id.nav_header_back).setOnClickListener { finish() }

        val launchTitle = intent.getStringExtra(EXTRA_EVENT_TITLE).orEmpty()
        // A blank head cannot carry the failure sentence, so it stays hidden until an event arrives.
        if (launchTitle.isBlank()) findViewById<View>(R.id.layoutEventHero).visibility = View.GONE
        findViewById<TextView>(R.id.txtDetailTitle).text = launchTitle
        findViewById<TextView>(R.id.txtDetailTitlePoster).text = launchTitle
        findViewById<TextView>(R.id.txtDetailVenue).text = intent.getStringExtra(EXTRA_EVENT_LOCATION).orEmpty()
            .ifBlank { getString(R.string.ui_attendee_location_not_specified) }
        
        intent.getStringExtra(EXTRA_EVENT_COUNT)?.let { countStr ->
            intent.getStringExtra(EXTRA_EVENT_CAPACITY)?.let { capacityStr ->
                updateRegistrationStatusUI(countStr.toIntOrNull() ?: 0, capacityStr.toIntOrNull() ?: 0)
            }
        }

        findViewById<Button>(R.id.btnRegisterForEvent).apply {
            isEnabled = false
            text = getString(R.string.event_detail_loading)
        }

        findViewById<View>(R.id.btnCancelRegistration).setOnClickListener { confirmCancelRegistration() }

        findViewById<Button>(R.id.btnRegisterForEvent).setOnClickListener {
            // The organizer label outranks the pass: an owner or manager who also holds a live
            // registration manages the event, never opens an attendee pass.
            if (canManageEvent()) {
                openOrganizerEventManagement()
                return@setOnClickListener
            }
            // The registered attendee's one action is the pass (§6.2); the id arrives with the
            // status check, so the button is inert until it is known.
            if (isAlreadyRegistered) {
                registeredRegistrationId?.let { openPass(it) }
                return@setOnClickListener
            }
            currentEvent?.let { event ->
                presenter.registerForEvent(eventId, event.title)
            } ?: presenter.registerForEvent(eventId, intent.getStringExtra(EXTRA_EVENT_TITLE).orEmpty())
        }

        if (eventId.isNotBlank()) {
            presenter.loadEventDetails(eventId)
        } else {
            showMessage(getString(R.string.event_detail_missing_event_information))
        }
    }

    // EventQR - registration status re-sync on resume (fixes stale button state after QR dismiss)
    override fun onResume() {
        super.onResume()
        // Skip the very first resume: onCreate's initial load already performs this check,
        // so firing here too would duplicate the network call on first open.
        if (isFirstResume) {
            isFirstResume = false
            return
        }
        if (!this::eventId.isInitialized || eventId.isBlank()) return
        presenter.refreshRegistrationStatus(eventId)
        // Returning from registration/QR: the availability call also carries the live registered count,
        // so the capacity line is redrawn instead of keeping the count from when the screen opened.
        currentEvent?.takeIf { !canManageEvent() }?.let { loadEventAvailability(it) }
    }

    override fun renderEvent(event: AttendeeEventResponse) {
        currentEvent = event
        // The event arrived: the body is readable again and no failure surface survives a success.
        findViewById<View>(R.id.layoutEventHero).visibility = View.VISIBLE
        errorStrip.visibility = View.GONE
        emptyState.hide()
        findViewById<View>(R.id.layoutSpecTable).visibility = View.VISIBLE
        // The top bar keeps its fixed "Event details" title; the event name sits in the media head:
        // in the no-poster band, or on the page background under the poster.
        findViewById<TextView>(R.id.txtDetailTitle).text = event.title
        findViewById<TextView>(R.id.txtDetailTitlePoster).text = event.title
        // §6.2: status is the first spec-table row. A status we cannot name is not a row —
        // an unlabelled "Unknown" above the dates reads as a failed load.
        val status = EventStatusBadgeStyler.resolve(event.status, event.eventStartAt, event.eventEndAt)
        val statusVisibility = if (status == EventStatus.UNKNOWN) View.GONE else View.VISIBLE
        findViewById<View>(R.id.layoutStatusRow).visibility = statusVisibility
        findViewById<View>(R.id.viewStatusDivider).visibility = statusVisibility
        if (status != EventStatus.UNKNOWN) {
            AttendeeBadges.bind(findViewById(R.id.txtDetailStatus), status)
        }
        findViewById<TextView>(R.id.txtDetailVenue).text = event.location?.takeIf { it.isNotBlank() } ?: getString(R.string.ui_attendee_venue_tbd)
        renderEventPoster(event.eventLogoUrl)

        // §6.2: one EVENT TIME row, the organizer page's value through the one shared helper, and
        // the same full registration window. A missing start hides the row with its hairline.
        bindOptionalRow(
            R.id.layoutEventTimeRow,
            R.id.viewEventTimeDivider,
            R.id.txtDetailEventTime,
            formatEventTime(this, event.eventStartAt, event.eventEndAt),
        )
        bindOptionalRow(
            R.id.layoutRegWindowRow,
            R.id.viewRegWindowDivider,
            R.id.txtDetailRegWindow,
            formatRegistrationWindow(this, event.registrationOpenAt, event.registrationCloseAt),
        )

        // About answers "what is it" between the facts and the role-specific rows; a blank
        // description hides the block and its hairline (§6.2).
        val about = event.description?.takeIf { it.isNotBlank() }
        val aboutVisibility = if (about != null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.txtDetailAboutLabel).visibility = aboutVisibility
        findViewById<View>(R.id.txtDetailDescription).visibility = aboutVisibility
        findViewById<View>(R.id.viewAboutDivider).visibility = aboutVisibility
        about?.let { findViewById<TextView>(R.id.txtDetailDescription).text = it }

        bindOptionalRow(
            R.id.layoutCategoryRow,
            R.id.viewCategoryDivider,
            R.id.txtDetailCategory,
            event.category?.takeIf { it.isNotBlank() },
        )
        bindOptionalRow(
            R.id.layoutAudienceRow,
            R.id.viewAudienceDivider,
            R.id.txtDetailAudience,
            event.targetAudience?.takeIf { it.isNotBlank() },
        )

        updateRegistrationStatusUI(event.currentAttendeeCount, event.capacity)

        // §6.2: REWARDS is an indicator, not a link, and the row always shows. Only a failed rewards
        // read leaves the value silent, so the table never jumps or claims a wrong "None".
        findViewById<View>(R.id.layoutRewardsRow).visibility = View.VISIBLE
        findViewById<View>(R.id.viewRewardsDivider).visibility = View.VISIBLE
        val rewardsEventId = event.eventId.toString()
        if (rewardsEventId != rewardsLabelEventId) {
            rewardsLabelEventId = null
            rewardsLabel = null
        }
        if (event.rewardsEnabled) {
            // A value already settled for this event survives the re-bind; the fresh read updates it.
            showRewardsLabel(rewardsLabel)
            loadRewardsAvailability(rewardsEventId)
        } else {
            setRewardsLabel(rewardsEventId, getString(R.string.ui_attendee_none))
        }

        applyStackedRows()

        checkOwnedEventThenAvailability(event)
        updateCancelButtonVisibility()
    }

    /**
     * "Available" when the event lists at least one reward the attendee may still claim, "None" when
     * it lists none, and no value at all when the rewards read fails for any other reason. The
     * redemptions read only narrows by the Rewards screen's own rule (`claimableRewards`), so an
     * already-claimed reward cannot read as available here; that read needs a registration, so a 403
     * from an unregistered viewer falls back to the rewards list instead of silencing the row.
     *
     * The rewards read itself is gated the same way (`requireRegistered`): it answers 403 to any
     * viewer who is not registered for the event - an organizer browsing as attendee, or an attendee
     * who has not registered yet - while the organizer has rewards enabled for it. That viewer cannot
     * see the list, so the row reads "Available", never a wrong "None". Other failures stay blank.
     */
    private fun loadRewardsAvailability(eventId: String) {
        rewardsAvailabilityJob?.cancel()
        rewardsAvailabilityJob = lifecycleScope.launch {
            val rewards = repository.getRewardsByEvent(eventId)
            if (rewards is NetworkResult.Error && rewards.isForbidden()) {
                setRewardsLabel(eventId, getString(R.string.ui_attendee_available))
                return@launch
            }
            if (rewards !is NetworkResult.Success) return@launch
            val label = if (rewards.data.isEmpty()) {
                getString(R.string.ui_attendee_none)
            } else {
                val redemptions = repository.getMyRewardRedemptions(eventId)
                val claimable = if (redemptions is NetworkResult.Success) {
                    claimableRewards(rewards.data, redeemedRewardIds(redemptions.data))
                } else {
                    rewards.data
                }
                getString(
                    if (claimable.isNotEmpty()) R.string.ui_attendee_available else R.string.ui_attendee_none,
                )
            }
            setRewardsLabel(eventId, label)
        }
    }

    /**
     * HTTP 403 from the rewards read: the viewer is not registered for this event, so the list is
     * not theirs to see. Read through the transport error, never the message text.
     */
    private fun NetworkResult.Error.isForbidden(): Boolean =
        (throwable as? HttpException)?.code() == HTTP_FORBIDDEN

    /** Records a settled label for [eventId] and draws it; a same-event re-bind re-shows it. */
    private fun setRewardsLabel(eventId: String, label: String) {
        rewardsLabelEventId = eventId
        rewardsLabel = label
        showRewardsLabel(label)
    }

    /** A null label is a blank value: invisible keeps the row's height so the table never jumps. */
    private fun showRewardsLabel(label: String?) {
        findViewById<TextView>(R.id.txtRewardsAvailable).apply {
            text = label
            visibility = if (label != null) View.VISIBLE else View.INVISIBLE
        }
    }

    /** A blank value hides the row and its own hairline — no empty label, no orphan rule. */
    private fun bindOptionalRow(rowId: Int, dividerId: Int, valueId: Int, value: String?) {
        val visible = !value.isNullOrBlank()
        findViewById<View>(rowId).visibility = if (visible) View.VISIBLE else View.GONE
        findViewById<View>(dividerId).visibility = if (visible) View.VISIBLE else View.GONE
        value?.let { findViewById<TextView>(valueId).text = it }
    }

    // §6.2/§9.3: above fontScale 1.3 the datetime rows stack, label over a full-width value. The
    // overline token is 11sp, so at the 1.3 threshold it is ~14.3sp / ~200dp and leaves no room for
    // a range beside it. At or below 1.3 the layout's label-left/value-right anatomy stands. This
    // branch assumes a fresh activity: a font-scale change recreates the activity (no
    // `configChanges` in the manifest), so both branches must be reset here if that ever changes.
    private fun applyStackedRows() {
        attendeeEventDetailStackedRows.forEach { (rowId, valueId) ->
            MetricRowStacking.apply(this, findViewById(rowId), findViewById(valueId))
        }
    }

    // Poster and band are exclusive: nothing is ever drawn over the image, and the no-poster plate
    // always carries the title in the band. Callers set both title views before toggling.
    private fun showPoster(hasPoster: Boolean) {
        findViewById<View>(R.id.imgEventPosterHero).visibility = if (hasPoster) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutHeroBand).visibility = if (hasPoster) View.GONE else View.VISIBLE
        findViewById<View>(R.id.txtDetailTitlePoster).visibility = if (hasPoster) View.VISIBLE else View.GONE
    }

    private fun renderEventPoster(eventLogoUrl: String?) {
        val posterView = findViewById<ImageView>(R.id.imgEventPosterHero)
        val fileId = eventLogoUrl?.trim().orEmpty()
        if (fileId.isBlank()) {
            showPoster(false)
            return
        }

        lifecycleScope.launch {
            when (val result = repository.getStoredFile(fileId)) {
                is NetworkResult.Success -> {
                    val encoded = result.data.contentBase64
                    if (encoded.isNullOrBlank()) {
                        showPoster(false)
                        return@launch
                    }
                    runCatching {
                        val bytes = Base64.decode(encoded, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.onSuccess { bitmap ->
                        if (bitmap != null) {
                            posterView.setImageBitmap(bitmap)
                            showPoster(true)
                        } else {
                            showPoster(false)
                        }
                    }.onFailure {
                        showPoster(false)
                    }
                }
                is NetworkResult.Error -> {
                    showPoster(false)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun updateRegistrationStatusUI(current: Int, capacity: Int) {
        val capacityView = findViewById<TextView>(R.id.txtDetailCapacity)

        if (capacity <= 0) {
            // Capacity 0 means unlimited on the backend, not "unknown".
            capacityView.text = getString(R.string.event_detail_capacity_unlimited, current)
            return
        }

        capacityView.text = getString(R.string.event_detail_capacity_registered, current, capacity)
    }

    private fun checkOwnedEventThenAvailability(event: AttendeeEventResponse) {
        // Ownership is decided by the backend flag regardless of the current role.
        isOwnedByCurrentOrganizer = event.isOwnedByCurrentUser == true
        if (canManageEvent()) {
            setOwnedEventState()
        } else {
            loadEventAvailability(event)
        }
    }

    private fun loadEventAvailability(event: AttendeeEventResponse) {
        // Only the newest request may draw: an older response landing last would show a stale count/state.
        availabilityJob?.cancel()
        availabilityJob = lifecycleScope.launch {
            when (val result = repository.getEventAvailability(event.eventId.toString())) {
                is NetworkResult.Success -> {
                    updateRegisterButtonFromAvailability(event, result.data)
                }
                is NetworkResult.Error -> {
                    updateRegisterButtonWithFallback(
                        event,
                        result.message.ifBlank { getString(R.string.ui_attendee_availability_unavailable) },
                    )
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    @VisibleForTesting
    internal fun updateRegisterButtonFromAvailability(event: AttendeeEventResponse, availability: EventAvailabilityResponse) {
        reapplyAvailability = { updateRegisterButtonFromAvailability(event, availability) }
        // The availability response is fresher than the launch snapshot or the loaded event.
        updateRegistrationStatusUI(availability.currentAttendeeCount, availability.capacity)
        if (canManageEvent()) {
            setOwnedEventState()
            return
        }
        if (applyCancelledEventState()) return

        val btn = findViewById<Button>(R.id.btnRegisterForEvent)

        if (isAlreadyRegistered) {
            setAlreadyRegisteredState(btn)
            logRegistrationWindow(event, availability, false)
            return
        }

        if (registrationStatusCheckFailed) {
            setUnverifiableState(btn)
            logRegistrationWindow(event, availability, false)
            return
        }

        if (availability.available) {
            btn.isEnabled = true
            btn.text = getString(R.string.event_detail_register)
            logRegistrationWindow(event, availability, true)
            return
        }

        btn.isEnabled = false
        btn.text = availability.message.ifBlank { getString(R.string.ui_attendee_registration_unavailable) }
        logRegistrationWindow(event, availability, false)
    }

    private fun updateRegisterButtonWithFallback(event: AttendeeEventResponse, message: String) {
        reapplyAvailability = { updateRegisterButtonWithFallback(event, message) }
        if (canManageEvent()) {
            setOwnedEventState()
            return
        }
        if (applyCancelledEventState()) return

        val btn = findViewById<Button>(R.id.btnRegisterForEvent)

        if (isAlreadyRegistered) {
            setAlreadyRegisteredState(btn)
            logRegistrationWindow(event, null, false)
            return
        }

        if (registrationStatusCheckFailed) {
            setUnverifiableState(btn)
            return
        }

        btn.isEnabled = true
        btn.text = getString(R.string.event_detail_register)
        logRegistrationWindow(event, null, true)
    }

    override fun updateRegistrationStatus(isRegistered: Boolean) {
        isAlreadyRegistered = isRegistered
        registrationStatusCheckFailed = false
        if (canManageEvent()) {
            setOwnedEventState()
            return
        }
        if (applyCancelledEventState()) return
        if (isRegistered) {
            val btn = findViewById<Button>(R.id.btnRegisterForEvent)
            setAlreadyRegisteredState(btn)
        } else {
            // No longer registered (e.g. cancelled elsewhere): an "Already Registered" label drawn from an
            // earlier availability result must give way to what availability actually allows.
            reapplyAvailability?.invoke()
        }
    }

    override fun setRegisteredId(registrationId: String?) {
        registeredRegistrationId = registrationId
        // The id may land after the status check: re-draw the pass label now that it is known.
        // The organizer label is never overwritten (same precedence as the click listener).
        if (isAlreadyRegistered && !canManageEvent()) {
            setAlreadyRegisteredState(findViewById(R.id.btnRegisterForEvent))
        }
    }

    override fun setCancellableRegistration(registrationId: String?) {
        cancellableRegistrationId = registrationId
        updateCancelButtonVisibility()
    }

    // Hidden for organizers on their own event and once the server would always refuse (event started/ended).
    private fun updateCancelButtonVisibility() {
        val visible = cancellableRegistrationId != null &&
            !canManageEvent() &&
            EventDetailPresenter.isCancelWindowOpen(currentEvent)
        findViewById<View>(R.id.btnCancelRegistration).visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun confirmCancelRegistration() {
        val registrationId = cancellableRegistrationId ?: return
        val title = currentEvent?.title ?: intent.getStringExtra(EXTRA_EVENT_TITLE).orEmpty()
        cancelDialog?.dismiss()
        cancelDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.event_detail_cancel_registration_title)
            .setMessage(getString(R.string.event_detail_cancel_registration_message, title))
            .setPositiveButton(R.string.event_detail_cancel_registration_confirm) { dialog, _ ->
                dialog.dismiss()
                presenter.cancelRegistration(registrationId)
            }
            .setNegativeButton(R.string.event_detail_cancel_registration_keep) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun showCancelling(isCancelling: Boolean) {
        findViewById<View>(R.id.btnCancelRegistration).apply {
            isEnabled = !isCancelling
            contentDescription = getString(
                if (isCancelling) R.string.event_detail_cancelling_registration else R.string.event_detail_cancel_registration,
            )
        }
    }

    override fun showCancelFailure(message: String) {
        failureDialog?.dismiss()
        failureDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.event_detail_cancel_failed_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onRegistrationCancelled(message: String) {
        showMessage(message)
        // Drop the stale "Already Registered" label right away; the availability check decides the final state.
        isAlreadyRegistered = false
        findViewById<Button>(R.id.btnRegisterForEvent).apply {
            isEnabled = false
            text = getString(R.string.event_detail_loading)
        }
        // Register becomes available again if the registration window is still open.
        currentEvent?.let { loadEventAvailability(it) }
    }

    override fun onDestroy() {
        cancelDialog?.dismiss()
        cancelDialog = null
        failureDialog?.dismiss()
        failureDialog = null
        presenter.detach()
        super.onDestroy()
    }

    override fun onRegistrationStatusCheckFailed() {
        registrationStatusCheckFailed = true
        if (canManageEvent()) {
            setOwnedEventState()
            return
        }
        if (applyCancelledEventState()) return
        setUnverifiableState(findViewById(R.id.btnRegisterForEvent))
    }

    override fun showLoading(isLoading: Boolean) {
        // The screen paints the values it was launched with, so loading never blanks content: it
        // only disables the primary action until availability is known (set in onCreate).
        if (!isLoading) {
            updateCancelButtonVisibility()
        }
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * A failed load (§6.8 row 20): the sentence sits under the media head with its inline Retry.
     * Only a page with no head to carry it falls back to the error empty state, so the screen is
     * never a blank plate. Server text goes to the log, never into the UI (§7.5).
     */
    override fun showLoadError(message: String) {
        Log.w(TAG, "event detail load failed: $message")
        val sentence = getString(R.string.ui_event_detail_load_failed)
        if (findViewById<TextView>(R.id.txtDetailTitle).text.isNullOrBlank()) {
            errorStrip.visibility = View.GONE
            emptyState.setContent(sentence, getString(R.string.ui_attendee_load_error_body))
            emptyState.setAction(getString(R.string.ui_attendee_retry)) { retryLoad() }
            emptyState.visibility = View.VISIBLE
            // The copy never changes, so a live region would stay silent on a repeat failure.
            emptyState.announceForAccessibility(sentence)
        } else {
            emptyState.hide()
            errorStrip.showInlineError(sentence)
        }
    }

    private fun retryLoad() {
        errorStrip.visibility = View.GONE
        emptyState.hide()
        if (eventId.isNotBlank()) presenter.loadEventDetails(eventId)
    }

    override fun openRegistration(eventId: String, eventTitle: String, email: String, fullName: String, phoneNumber: String) {
        if (canManageEvent()) {
            openOrganizerEventManagement()
            return
        }

        val intent = Intent(this, AttendeeRegistrationActivity::class.java)
            .putExtra(EXTRA_EVENT_ID, eventId)
            .putExtra(EXTRA_EVENT_TITLE, eventTitle)
            .putExtra(EXTRA_PREFILL_EMAIL, email)
            .putExtra(EXTRA_PREFILL_FULL_NAME, fullName)
            .putExtra(EXTRA_PREFILL_PHONE, phoneNumber)
        
        currentEvent?.let { event ->
            intent.putExtra(EXTRA_EVENT_CATEGORY, event.category)
            intent.putExtra(EXTRA_EVENT_LOCATION, event.location)
            event.eventStartAt?.let {
                val manilaZone = java.time.ZoneId.of("Asia/Manila")
                val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH).withZone(manilaZone)
                intent.putExtra(EXTRA_EVENT_START, formatter.format(it))
            }
        }
        
        startActivity(intent)
    }

    override fun getSessionUserId(): String? = SessionManager(this).getUserId()
    override fun getSessionEmail(): String = SessionManager(this).getEmail().orEmpty()
    override fun getSessionFullName(): String = SessionManager(this).getFullName().orEmpty()
    override fun getSessionPhone(): String = SessionManager(this).getPhone().orEmpty()

    private fun setAlreadyRegisteredState(button: Button) {
        // With the live registration id the primary action opens the pass, whatever the row's status
        // (a checked-in or exited attendee still owns a pass, §6.2). Without it (status known, id
        // still loading) the label stays inert instead of opening nothing.
        val passRegistrationId = registeredRegistrationId
        button.isEnabled = passRegistrationId != null
        button.text = getString(
            if (passRegistrationId != null) {
                R.string.ui_attendee_view_pass
            } else {
                R.string.event_detail_already_registered
            },
        )
    }

    private fun openPass(registrationId: String) {
        startActivity(
            Intent(this, QrDisplayActivity::class.java)
                .putExtra(EXTRA_REGISTRATION_ID, registrationId),
        )
    }

    private fun setUnverifiableState(button: Button) {
        button.isEnabled = false
        button.text = getString(R.string.event_detail_can_t_verify_registration)
    }

    // A cancelled event outranks every other primary-action state (Register, Already Registered, closed, ...).
    private fun applyCancelledEventState(): Boolean {
        if (!EventDetailPresenter.isEventCancelled(currentEvent)) return false
        findViewById<Button>(R.id.btnRegisterForEvent).apply {
            isEnabled = false
            text = getString(R.string.event_detail_event_cancelled)
        }
        return true
    }

    private fun setOwnedEventState() {
        findViewById<Button>(R.id.btnRegisterForEvent).apply {
            isEnabled = true
            text = getString(R.string.event_detail_manage_event)
        }
    }

    /**
     * The management hub and every endpoint behind it require ORGANIZER/ADMIN/SUPER_ADMIN, while an
     * approved event request leaves the requester's account role untouched. An attendee who owns an
     * event therefore gets no "Manage Event": it would only lead to a 403 hub.
     */
    private fun canManageEvent(): Boolean =
        isOwnedByCurrentOrganizer &&
            RoleMapper.isAtLeast(SessionManager(this).getUserRole(), AccountRole.ORGANIZER)

    private fun openOrganizerEventManagement() {
        val event = currentEvent
        val targetId = event?.eventId?.toString() ?: eventId
        val targetTitle = event?.title ?: intent.getStringExtra(EXTRA_EVENT_TITLE).orEmpty()
        startActivity(Intent(this, EventManagementHubActivity::class.java).apply {
            putExtra("event_id", targetId)
            putExtra("event_title", targetTitle)
        })
    }

    private fun logRegistrationWindow(
        event: AttendeeEventResponse,
        availability: EventAvailabilityResponse?,
        finalButtonEnabled: Boolean,
    ) {
        Log.d(
            "EventRegistrationWindow",
            "eventId=${event.eventId}," +
                " deviceNow=${Instant.now()}," +
                " serverNow=${availability?.serverNow}," +
                " zoneUsed=Asia/Manila," +
                " registrationOpenAt=${availability?.registrationOpenAt ?: event.registrationOpenAt}," +
                " registrationCloseAt=${availability?.registrationCloseAt ?: event.registrationCloseAt}," +
                " eventStartAt=${event.eventStartAt}," +
                " eventEndAt=${event.eventEndAt}," +
                " available=${availability?.available}," +
                " finalButtonEnabled=$finalButtonEnabled"
        )
    }

    private companion object {
        const val TAG = "EventDetail"

        /** Not registered for this event: the attendee rewards read's own gate. */
        const val HTTP_FORBIDDEN = 403
    }
}

/**
 * The spec-table rows that stack above `fontScale` 1.3 (§6.2/§9.3), as (row, value) pairs in table
 * order: EVENT TIME and REGISTRATION WINDOW hold ranges, and the 11sp overline leaves no room for a
 * range beside it at the threshold. REWARDS stacks for the same reason (a value slot that must never
 * be clipped). `applyStackedRows()` iterates this list and the Robolectric tests iterate the same
 * list, so the production set and the asserted set cannot drift.
 */
internal val attendeeEventDetailStackedRows: List<Pair<Int, Int>> = listOf(
    R.id.layoutEventTimeRow to R.id.txtDetailEventTime,
    R.id.layoutRegWindowRow to R.id.txtDetailRegWindow,
    R.id.layoutRewardsRow to R.id.txtRewardsAvailable,
)

