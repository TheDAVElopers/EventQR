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
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.events.EventStatusBadgeStyler
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventAvailabilityResponse
import com.thedavelopers.eventqr.features.organizer.events.EventManagementHubActivity
import com.thedavelopers.eventqr.ui.theme.applyEventQrBottomInsetPadding
import java.time.Instant
import kotlinx.coroutines.launch

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
    private var cancelDialog: androidx.appcompat.app.AlertDialog? = null
    private var failureDialog: androidx.appcompat.app.AlertDialog? = null
    private var availabilityJob: kotlinx.coroutines.Job? = null
    // Re-draws the button from the last availability outcome when the registration status changes afterwards.
    private var reapplyAvailability: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        findViewById<View>(R.id.layoutBottomBar)?.applyEventQrBottomInsetPadding()

        repository = AttendeeRepository(this)
        presenter = EventDetailPresenter(this, repository, UiStrings(this))
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()

        findViewById<View>(R.id.nav_header_back).setOnClickListener { finish() }

        val launchTitle = intent.getStringExtra(EXTRA_EVENT_TITLE).orEmpty()
        findViewById<TextView>(R.id.txtDetailTitle).text = launchTitle
        findViewById<TextView>(R.id.txtDetailTitlePoster).text = launchTitle
        findViewById<TextView>(R.id.txtDetailDescription).text = intent.getStringExtra(EXTRA_EVENT_DESCRIPTION).orEmpty()
        findViewById<TextView>(R.id.txtDetailVenue).text = intent.getStringExtra(EXTRA_EVENT_LOCATION).orEmpty().ifBlank { "Location not specified" }
        
        intent.getStringExtra(EXTRA_EVENT_COUNT)?.let { countStr ->
            intent.getStringExtra(EXTRA_EVENT_CAPACITY)?.let { capacityStr ->
                updateRegistrationStatusUI(countStr.toIntOrNull() ?: 0, capacityStr.toIntOrNull() ?: 0)
            }
        }

        findViewById<Button>(R.id.btnRegisterForEvent).apply {
            isEnabled = false
            text = getString(R.string.event_detail_loading)
        }

        findViewById<View>(R.id.layoutRewardsRow)?.setOnClickListener {
            startActivity(Intent(this, AttendeeRewardsActivity::class.java).putExtra(EXTRA_EVENT_ID, eventId))
        }

        findViewById<Button>(R.id.btnCancelRegistration).setOnClickListener { confirmCancelRegistration() }

        findViewById<Button>(R.id.btnRegisterForEvent).setOnClickListener {
            if (canManageEvent()) {
                openOrganizerEventManagement()
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
        findViewById<TextView>(R.id.txtDetailDescription).text = event.description?.takeIf { it.isNotBlank() } ?: getString(R.string.ui_attendee_no_description)
        findViewById<TextView>(R.id.txtDetailVenue).text = event.location?.takeIf { it.isNotBlank() } ?: getString(R.string.ui_attendee_venue_tbd)
        renderEventPoster(event.eventLogoUrl)

        val manilaZone = java.time.ZoneId.of("Asia/Manila")
        // §7.6: `12 Oct 2026` for a date, `12 Oct 2026, 9:41 AM` for a date and time.
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH).withZone(manilaZone)
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH).withZone(manilaZone)
        
        if (event.eventStartAt != null) {
            val endAt = event.eventEndAt
            val multiDay = endAt != null &&
                event.eventStartAt.atZone(manilaZone).toLocalDate() != endAt.atZone(manilaZone).toLocalDate()
            findViewById<TextView>(R.id.txtDetailDate).text = if (multiDay && endAt != null) {
                val shortFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH).withZone(manilaZone)
                getString(R.string.event_detail_date_range, shortFormatter.format(event.eventStartAt), shortFormatter.format(endAt))
            } else {
                dateFormatter.format(event.eventStartAt)
            }
            val startTime = timeFormatter.format(event.eventStartAt)
            findViewById<TextView>(R.id.txtDetailTime).text =
                event.eventEndAt?.let { "$startTime - ${timeFormatter.format(it)}" } ?: startTime
        } else {
            findViewById<TextView>(R.id.txtDetailDate).text = "--"
            findViewById<TextView>(R.id.txtDetailTime).text = "-"
        }

        val category = event.category?.takeIf { it.isNotBlank() }
        findViewById<TextView>(R.id.txtDetailCategory).text = category.orEmpty()
        val categoryVisibility = if (category != null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutCategoryRow).visibility = categoryVisibility
        findViewById<View>(R.id.viewCategoryDivider).visibility = categoryVisibility

        val audience = event.targetAudience?.takeIf { it.isNotBlank() }
        findViewById<TextView>(R.id.txtDetailAudience).text = audience.orEmpty()
        val audienceVisibility = if (audience != null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutAudienceRow).visibility = audienceVisibility
        findViewById<View>(R.id.viewAudienceDivider).visibility = audienceVisibility

        val regFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", java.util.Locale.ENGLISH).withZone(manilaZone)
        val regOpen = event.registrationOpenAt?.let { regFormatter.format(it) }
        val regClose = event.registrationCloseAt?.let { regFormatter.format(it) }
        val regWindow = when {
            regOpen != null && regClose != null -> "$regOpen - $regClose"
            regClose != null -> "Closes $regClose"
            regOpen != null -> "Opens $regOpen"
            else -> null
        }
        findViewById<TextView>(R.id.txtDetailRegWindow).text = regWindow.orEmpty()
        val regVisibility = if (regWindow != null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutRegWindowRow).visibility = regVisibility
        findViewById<View>(R.id.viewRegWindowDivider).visibility = regVisibility

        updateRegistrationStatusUI(event.currentAttendeeCount, event.capacity)

        val rewardsRow = findViewById<View>(R.id.layoutRewardsRow)
        val rewardsDivider = findViewById<View>(R.id.viewRewardsDivider)
        if (event.rewardsEnabled) {
            rewardsRow?.visibility = View.VISIBLE
            rewardsDivider?.visibility = View.VISIBLE
        } else {
            rewardsRow?.visibility = View.GONE
            rewardsDivider?.visibility = View.GONE
        }

        checkOwnedEventThenAvailability(event)
        updateCancelButtonVisibility()
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
        val percentView = findViewById<TextView>(R.id.txtRegPercent)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.pbRegistrationDetail)
        val remainingView = findViewById<TextView>(R.id.txtRemainingSpots)

        if (capacity <= 0) {
            // Capacity 0 means unlimited on the backend, not "unknown".
            capacityView.text = getString(R.string.event_detail_capacity_unlimited, current)
            percentView.text = ""
            remainingView.text = ""
            progressBar.progress = 0
            return
        }

        capacityView.text = getString(R.string.event_detail_capacity_registered, current, capacity)
        val percent = (current.toFloat() / capacity.toFloat() * 100).toInt().coerceIn(0, 100)
        val remaining = (capacity - current).coerceAtLeast(0)
        percentView.text = getString(R.string.event_detail_percent_full, percent)
        progressBar.progress = percent
        remainingView.text = getString(R.string.event_detail_spots_remaining, remaining)
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
                    updateRegisterButtonWithFallback(event, result.message.ifBlank { "Availability unavailable" })
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
        btn.text = availability.message.ifBlank { "Registration Unavailable" }
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

    override fun setCancellableRegistration(registrationId: String?) {
        cancellableRegistrationId = registrationId
        updateCancelButtonVisibility()
    }

    // Hidden for organizers on their own event and once the server would always refuse (event started/ended).
    private fun updateCancelButtonVisibility() {
        val visible = cancellableRegistrationId != null &&
            !canManageEvent() &&
            EventDetailPresenter.isCancelWindowOpen(currentEvent)
        findViewById<Button>(R.id.btnCancelRegistration).visibility = if (visible) View.VISIBLE else View.GONE
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
        findViewById<Button>(R.id.btnCancelRegistration).apply {
            isEnabled = !isCancelling
            text = getString(
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
        button.isEnabled = false
        button.text = getString(R.string.event_detail_already_registered)
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
}
