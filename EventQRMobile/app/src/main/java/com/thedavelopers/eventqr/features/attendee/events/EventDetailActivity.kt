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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventAvailabilityResponse
import com.thedavelopers.eventqr.features.organizer.events.EventManagementHubActivity
import com.thedavelopers.eventqr.ui.theme.applyEventQrBottomInsetPadding
import java.time.Instant
import kotlinx.coroutines.launch

open class EventDetailActivity : AppCompatActivity(), EventDetailContract.View {
    private lateinit var repository: AttendeeRepository
    private lateinit var presenter: EventDetailPresenter
    private lateinit var eventId: String
    private var currentEvent: AttendeeEventResponse? = null
    private var isAlreadyRegistered = false
    private var isOwnedByCurrentOrganizer = false
    private var isFirstResume = true
    private var registrationStatusCheckFailed = false
    private var cancellableRegistrationId: String? = null
    private var cancelDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        findViewById<View>(R.id.layoutBottomBar)?.applyEventQrBottomInsetPadding()

        repository = AttendeeRepository(this)
        presenter = EventDetailPresenter(this, repository, UiStrings(this))
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()

        findViewById<View>(R.id.nav_header_back).setOnClickListener { finish() }

        findViewById<TextView>(R.id.txtDetailTitle).text = intent.getStringExtra(EXTRA_EVENT_TITLE).orEmpty()
        findViewById<TextView>(R.id.txtDetailDescription).text = intent.getStringExtra(EXTRA_EVENT_DESCRIPTION).orEmpty()
        findViewById<TextView>(R.id.txtDetailVenue).text = intent.getStringExtra(EXTRA_EVENT_LOCATION).orEmpty().ifBlank { "Location not specified" }
        
        intent.getStringExtra(EXTRA_EVENT_COUNT)?.let { countStr ->
            intent.getStringExtra(EXTRA_EVENT_CAPACITY)?.let { capacityStr ->
                updateRegistrationStatusUI(countStr.toIntOrNull() ?: 0, capacityStr.toIntOrNull() ?: 0)
            }
        }

        findViewById<Button>(R.id.btnRegisterForEvent).apply {
            isEnabled = false
            text = "Loading..."
            setBackgroundResource(R.drawable.bg_disabled_button)
        }

        findViewById<View>(R.id.layoutRewardsRow)?.setOnClickListener {
            startActivity(Intent(this, AttendeeRewardsActivity::class.java).putExtra(EXTRA_EVENT_ID, eventId))
        }

        findViewById<Button>(R.id.btnCancelRegistration).setOnClickListener { confirmCancelRegistration() }

        findViewById<Button>(R.id.btnRegisterForEvent).setOnClickListener {
            if (isOwnedByCurrentOrganizer) {
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
    }

    override fun renderEvent(event: AttendeeEventResponse) {
        currentEvent = event
        // The header keeps its fixed "Event Details" title; the event name sits on the banner above the description.
        findViewById<TextView>(R.id.txtDetailTitle).text = event.title
        findViewById<TextView>(R.id.txtDetailDescription).text = event.description?.takeIf { it.isNotBlank() } ?: "No event description provided."
        findViewById<TextView>(R.id.txtDetailVenue).text = event.location?.takeIf { it.isNotBlank() } ?: "Location not specified."
        renderEventPoster(event.eventLogoUrl)

        val manilaZone = java.time.ZoneId.of("Asia/Manila")
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", java.util.Locale.ENGLISH).withZone(manilaZone)
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.ENGLISH).withZone(manilaZone)
        
        if (event.eventStartAt != null) {
            val endAt = event.eventEndAt
            val multiDay = endAt != null &&
                event.eventStartAt.atZone(manilaZone).toLocalDate() != endAt.atZone(manilaZone).toLocalDate()
            findViewById<TextView>(R.id.txtDetailDate).text = if (multiDay && endAt != null) {
                val shortFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH).withZone(manilaZone)
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

        val regFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a", java.util.Locale.ENGLISH).withZone(manilaZone)
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

    private fun renderEventPoster(eventLogoUrl: String?) {
        val posterView = findViewById<ImageView>(R.id.imgEventPosterHero)
        val overlayView = findViewById<View>(R.id.viewEventPosterOverlay)
        val fileId = eventLogoUrl?.trim().orEmpty()
        if (fileId.isBlank()) {
            posterView.visibility = View.GONE
            overlayView.visibility = View.GONE
            return
        }

        lifecycleScope.launch {
            when (val result = repository.getStoredFile(fileId)) {
                is NetworkResult.Success -> {
                    val encoded = result.data.contentBase64
                    if (encoded.isNullOrBlank()) {
                        posterView.visibility = View.GONE
                        overlayView.visibility = View.GONE
                        return@launch
                    }
                    runCatching {
                        val bytes = Base64.decode(encoded, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.onSuccess { bitmap ->
                        if (bitmap != null) {
                            posterView.setImageBitmap(bitmap)
                            posterView.visibility = View.VISIBLE
                            overlayView.visibility = View.VISIBLE
                        } else {
                            posterView.visibility = View.GONE
                            overlayView.visibility = View.GONE
                        }
                    }.onFailure {
                        posterView.visibility = View.GONE
                        overlayView.visibility = View.GONE
                    }
                }
                is NetworkResult.Error -> {
                    posterView.visibility = View.GONE
                    overlayView.visibility = View.GONE
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
        if (isOwnedByCurrentOrganizer) {
            setOwnedEventState()
        } else {
            loadEventAvailability(event)
        }
    }

    private fun loadEventAvailability(event: AttendeeEventResponse) {
        lifecycleScope.launch {
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

    private fun updateRegisterButtonFromAvailability(event: AttendeeEventResponse, availability: EventAvailabilityResponse) {
        if (isOwnedByCurrentOrganizer) {
            setOwnedEventState()
            return
        }

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
            btn.setBackgroundResource(R.drawable.bg_detail_register_button)
            logRegistrationWindow(event, availability, true)
            return
        }

        btn.isEnabled = false
        btn.text = availability.message.ifBlank { "Registration Unavailable" }
        btn.setBackgroundResource(R.drawable.bg_disabled_button)
        logRegistrationWindow(event, availability, false)
    }

    private fun updateRegisterButtonWithFallback(event: AttendeeEventResponse, message: String) {
        if (isOwnedByCurrentOrganizer) {
            setOwnedEventState()
            return
        }

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
        btn.setBackgroundResource(R.drawable.bg_detail_register_button)
        logRegistrationWindow(event, null, true)
    }

    override fun updateRegistrationStatus(isRegistered: Boolean) {
        isAlreadyRegistered = isRegistered
        registrationStatusCheckFailed = false
        if (isOwnedByCurrentOrganizer) {
            setOwnedEventState()
            return
        }
        if (isRegistered) {
            val btn = findViewById<Button>(R.id.btnRegisterForEvent)
            setAlreadyRegisteredState(btn)
        }
    }

    override fun setCancellableRegistration(registrationId: String?) {
        cancellableRegistrationId = registrationId
        updateCancelButtonVisibility()
    }

    // Hidden for organizers on their own event and once the server would always refuse (event started/ended).
    private fun updateCancelButtonVisibility() {
        val visible = cancellableRegistrationId != null &&
            !isOwnedByCurrentOrganizer &&
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

    override fun onRegistrationCancelled(message: String) {
        showMessage(message)
        // Drop the stale "Already Registered" label right away; the availability check decides the final state.
        isAlreadyRegistered = false
        findViewById<Button>(R.id.btnRegisterForEvent).apply {
            isEnabled = false
            text = getString(R.string.event_detail_loading)
            setBackgroundResource(R.drawable.bg_disabled_button)
        }
        // Register becomes available again if the registration window is still open.
        currentEvent?.let { loadEventAvailability(it) }
    }

    override fun onDestroy() {
        cancelDialog?.dismiss()
        cancelDialog = null
        presenter.detach()
        super.onDestroy()
    }

    override fun onRegistrationStatusCheckFailed() {
        registrationStatusCheckFailed = true
        if (isOwnedByCurrentOrganizer) {
            setOwnedEventState()
            return
        }
        setUnverifiableState(findViewById(R.id.btnRegisterForEvent))
    }

    override fun showLoading(isLoading: Boolean) {
        findViewById<View>(R.id.skeletonDetailTitle).visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.skeletonDetailDescription).visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.skeletonDetailDate).visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.skeletonDetailTime).visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.skeletonDetailVenue).visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.skeletonDetailCapacity).visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.skeletonDetailRewards).visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.nav_header_title).visibility = if (isLoading) View.GONE else View.VISIBLE
        findViewById<View>(R.id.txtDetailDescription).visibility = if (isLoading) View.GONE else View.VISIBLE
        findViewById<View>(R.id.txtDetailDate).visibility = if (isLoading) View.GONE else View.VISIBLE
        findViewById<View>(R.id.txtDetailTime).visibility = if (isLoading) View.GONE else View.VISIBLE
        findViewById<View>(R.id.txtDetailVenue).visibility = if (isLoading) View.GONE else View.VISIBLE
        findViewById<View>(R.id.txtDetailCapacity).visibility = if (isLoading) View.GONE else View.VISIBLE
        findViewById<View>(R.id.txtRewardsAvailable).visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun openRegistration(eventId: String, eventTitle: String, email: String, fullName: String, phoneNumber: String) {
        if (isOwnedByCurrentOrganizer) {
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
                val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH).withZone(manilaZone)
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
        button.setBackgroundResource(R.drawable.bg_disabled_button)
    }

    private fun setUnverifiableState(button: Button) {
        button.isEnabled = false
        button.text = getString(R.string.event_detail_can_t_verify_registration)
        button.setBackgroundResource(R.drawable.bg_disabled_button)
    }

    private fun setOwnedEventState() {
        findViewById<Button>(R.id.btnRegisterForEvent).apply {
            isEnabled = true
            text = getString(R.string.event_detail_manage_event)
            setBackgroundResource(R.drawable.bg_detail_register_button)
        }
    }

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
