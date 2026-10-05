package com.thedavelopers.eventqr.features.attendee

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
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventAvailabilityResponse
import com.thedavelopers.eventqr.features.organizer.events.EventManagementHubActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        repository = AttendeeRepository(this)
        presenter = EventDetailPresenter(this, repository)
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

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
            showMessage("Missing event information.")
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
        findViewById<TextView>(R.id.txtDetailTitle).text = event.title
        findViewById<TextView>(R.id.txtDetailDescription).text = event.description?.takeIf { it.isNotBlank() } ?: "No event description provided."
        findViewById<TextView>(R.id.txtDetailVenue).text = event.location?.takeIf { it.isNotBlank() } ?: "Location not specified."
        renderEventPoster(event.eventLogoUrl)

        val manilaZone = java.time.ZoneId.of("Asia/Manila")
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", java.util.Locale.ENGLISH).withZone(manilaZone)
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.ENGLISH).withZone(manilaZone)
        
        if (event.eventStartAt != null) {
            findViewById<TextView>(R.id.txtDetailDate).text = dateFormatter.format(event.eventStartAt)
            findViewById<TextView>(R.id.txtDetailTime).text = timeFormatter.format(event.eventStartAt)
        } else {
            findViewById<TextView>(R.id.txtDetailDate).text = "--"
            findViewById<TextView>(R.id.txtDetailTime).text = "-"
        }

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
            capacityView.text = "--"
            percentView.text = ""
            remainingView.text = ""
            progressBar.progress = 0
            return
        }

        capacityView.text = "$current / $capacity registered"
        val percent = (current.toFloat() / capacity.toFloat() * 100).toInt().coerceIn(0, 100)
        val remaining = (capacity - current).coerceAtLeast(0)
        percentView.text = "$percent% full"
        progressBar.progress = percent
        remainingView.text = "$remaining spots remaining"
    }

    private fun checkOwnedEventThenAvailability(event: AttendeeEventResponse) {
        val normalizedRole = RoleMapper.normalizeRole(SessionManager(this).getUserRole())
        val roleCanOwnEvents = normalizedRole.contains("ORGANIZER") || normalizedRole.contains("ADMIN") || normalizedRole.contains("SUPER_ADMIN")
        if (!roleCanOwnEvents) {
            loadEventAvailability(event)
            return
        }

        // Backend-derived flag replaces the full organizer-events list fetch.
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
            logRegistrationWindow(event, availability, "Already Registered", false)
            return
        }

        if (registrationStatusCheckFailed) {
            setUnverifiableState(btn)
            logRegistrationWindow(event, availability, "Registration status unavailable", false)
            return
        }

        if (availability.available) {
            btn.isEnabled = true
            btn.text = "Register"
            btn.setBackgroundResource(R.drawable.bg_detail_register_button)
            logRegistrationWindow(event, availability, availability.message, true)
            return
        }

        btn.isEnabled = false
        btn.text = availability.message.ifBlank { "Registration Unavailable" }
        btn.setBackgroundResource(R.drawable.bg_disabled_button)
        logRegistrationWindow(event, availability, availability.message, false)
    }

    private fun updateRegisterButtonWithFallback(event: AttendeeEventResponse, message: String) {
        if (isOwnedByCurrentOrganizer) {
            setOwnedEventState()
            return
        }

        val btn = findViewById<Button>(R.id.btnRegisterForEvent)

        if (isAlreadyRegistered) {
            setAlreadyRegisteredState(btn)
            logRegistrationWindow(event, null, "Already Registered", false)
            return
        }

        if (registrationStatusCheckFailed) {
            setUnverifiableState(btn)
            return
        }

        btn.isEnabled = true
        btn.text = "Register"
        btn.setBackgroundResource(R.drawable.bg_detail_register_button)
        logRegistrationWindow(event, null, "Availability endpoint failed: $message", true)
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
        findViewById<View>(R.id.txtDetailTitle).visibility = if (isLoading) View.GONE else View.VISIBLE
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
        button.text = "Already Registered"
        button.setBackgroundResource(R.drawable.bg_disabled_button)
    }

    private fun setUnverifiableState(button: Button) {
        button.isEnabled = false
        button.text = "Can't verify registration"
        button.setBackgroundResource(R.drawable.bg_disabled_button)
    }

    private fun setOwnedEventState() {
        findViewById<Button>(R.id.btnRegisterForEvent).apply {
            isEnabled = true
            text = "Manage Event"
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
        availabilityMessage: String,
        finalButtonEnabled: Boolean,
    ) {
        val finalButtonLabel = findViewById<Button>(R.id.btnRegisterForEvent).text?.toString().orEmpty()
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
                " availabilityMessage=$availabilityMessage," +
                " finalButtonState=enabled:$finalButtonEnabled,text:$finalButtonLabel"
        )
    }
}
