package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import com.thedavelopers.eventqr.features.events.model.dto.EventAvailabilityResponse
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

open class EventDetailActivity : AppCompatActivity(), EventDetailContract.View {
    private lateinit var repository: AttendeeRepository
    private lateinit var presenter: EventDetailPresenter
    private lateinit var eventId: String
    private var currentEvent: AttendeeEventResponse? = null
    private var isAlreadyRegistered = false

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
        findViewById<TextView>(R.id.txtTagCategory).text = intent.getStringExtra(EXTRA_EVENT_CATEGORY).orEmpty().ifBlank { "Technology" }
        
        // Initial binding from intent extras if available
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

        findViewById<View>(R.id.layoutDetailCategory)?.visibility = View.GONE
        findViewById<View>(R.id.layoutDetailRewards)?.visibility = View.GONE
        findViewById<View>(R.id.layoutDetailAgenda)?.visibility = View.GONE

        findViewById<Button>(R.id.btnViewRewards).setOnClickListener {
            startActivity(Intent(this, AttendeeRewardsActivity::class.java).putExtra(EXTRA_EVENT_ID, eventId))
        }

        findViewById<Button>(R.id.btnRegisterForEvent).setOnClickListener {
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

    override fun renderEvent(event: AttendeeEventResponse) {
        currentEvent = event
        findViewById<TextView>(R.id.txtDetailTitle).text = event.title
        findViewById<TextView>(R.id.txtDetailDescription).text = event.description?.takeIf { it.isNotBlank() } ?: "No event description provided."
        findViewById<TextView>(R.id.txtDetailVenue).text = event.location?.takeIf { it.isNotBlank() } ?: "Location not specified."

        // Date & Time
        val manilaZone = java.time.ZoneId.of("Asia/Manila")
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", java.util.Locale.ENGLISH).withZone(manilaZone)
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.ENGLISH).withZone(manilaZone)
        
        event.eventStartAt?.let {
            findViewById<TextView>(R.id.txtDetailDate).text = dateFormatter.format(it)
            findViewById<TextView>(R.id.txtDetailTime).text = timeFormatter.format(it)
        }

        updateRegistrationStatusUI(event.currentAttendeeCount, event.capacity)

        if (!event.category.isNullOrBlank()) {
            findViewById<TextView>(R.id.txtTagCategory).visibility = View.VISIBLE
            findViewById<TextView>(R.id.txtTagCategory).text = event.category
        } else {
            findViewById<TextView>(R.id.txtTagCategory).text = "Event"
        }

        // Rewards row inside info card
        val rewardsRow = findViewById<View>(R.id.layoutRewardsRow)
        val rewardsDivider = findViewById<View>(R.id.viewRewardsDivider)
        if (event.rewardsEnabled) {
            rewardsRow?.visibility = View.VISIBLE
            rewardsDivider?.visibility = View.VISIBLE
        } else {
            rewardsRow?.visibility = View.GONE
            rewardsDivider?.visibility = View.GONE
        }

        // Hide old separate rewards layout just in case
        findViewById<View>(R.id.layoutDetailRewards)?.visibility = View.GONE

        // Status Label
        val now = Instant.now()
        val statusText = when {
            event.eventEndAt?.isBefore(now) == true -> "Completed"
            event.eventStartAt?.isAfter(now) == true -> "Upcoming"
            event.eventStartAt != null && event.eventEndAt != null &&
                !event.eventStartAt.isAfter(now) && !event.eventEndAt.isBefore(now) -> "Active"
            else -> "Scheduled"
        }
        findViewById<TextView>(R.id.txtDetailStatus).text = statusText

        loadEventAvailability(event)
    }

    private fun updateRegistrationStatusUI(current: Int, capacity: Int) {
        findViewById<TextView>(R.id.txtDetailCapacity).text = "$current / $capacity registered"
        
        val cap = capacity.coerceAtLeast(1)
        val percent = (current.toFloat() / cap.toFloat() * 100).toInt().coerceIn(0, 100)
        val remaining = (capacity - current).coerceAtLeast(0)
        
        findViewById<TextView>(R.id.txtRegPercent).text = "$percent% full"
        findViewById<android.widget.ProgressBar>(R.id.pbRegistrationDetail).progress = percent
        findViewById<TextView>(R.id.txtRemainingSpots).text = "$remaining spots remaining"
    }

    private fun loadEventAvailability(event: AttendeeEventResponse) {
        lifecycleScope.launch {
            when (val result = repository.getEventAvailability(event.eventId.toString())) {
                is NetworkResult.Success -> {
                    updateRegisterButtonFromAvailability(event, result.data)
                }
                is NetworkResult.Error -> {
                    // Fallback path: backend registration validation remains the final gate.
                    updateRegisterButtonWithFallback(event, result.message.ifBlank { "Availability unavailable" })
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun updateRegisterButtonFromAvailability(event: AttendeeEventResponse, availability: EventAvailabilityResponse) {
        val btn = findViewById<Button>(R.id.btnRegisterForEvent)
        val statusView = findViewById<TextView>(R.id.txtDetailStatus)

        // Refine status based on availability
        val now = Instant.now()
        val isPast = event.eventEndAt?.isBefore(now) == true
        val isOngoing = !isPast && event.eventStartAt != null && event.eventEndAt != null &&
                !event.eventStartAt.isAfter(now) && !event.eventEndAt.isBefore(now)

        if (isPast) {
            statusView.text = "Completed"
        } else if (isOngoing) {
            statusView.text = "Active"
        } else if (!availability.registrationOpen || availability.full) {
            statusView.text = "Registration Closed"
        } else {
            statusView.text = "Upcoming"
        }

        if (isAlreadyRegistered) {
            setAlreadyRegisteredState(btn)
            logRegistrationWindow(event, availability, "Already Registered", false)
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
        val btn = findViewById<Button>(R.id.btnRegisterForEvent)

        if (isAlreadyRegistered) {
            setAlreadyRegisteredState(btn)
            logRegistrationWindow(event, null, "Already Registered", false)
            return
        }

        btn.isEnabled = true
        btn.text = "Register"
        btn.setBackgroundResource(R.drawable.bg_detail_register_button)
        logRegistrationWindow(event, null, "Availability endpoint failed: $message", true)
    }

    override fun updateRegistrationStatus(isRegistered: Boolean) {
        isAlreadyRegistered = isRegistered
        if (isRegistered) {
            val btn = findViewById<Button>(R.id.btnRegisterForEvent)
            setAlreadyRegisteredState(btn)

            if (currentEvent?.rewardsEnabled == true) {
                findViewById<View>(R.id.btnViewRewards)?.visibility = View.VISIBLE
            }
        }
    }

    override fun showLoading(isLoading: Boolean) = Unit

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun openRegistration(eventId: String, eventTitle: String, email: String, fullName: String, phoneNumber: String) {
        startActivity(
            Intent(this, AttendeeRegistrationActivity::class.java)
                .putExtra(EXTRA_EVENT_ID, eventId)
                .putExtra(EXTRA_EVENT_TITLE, eventTitle)
                .putExtra(EXTRA_PREFILL_EMAIL, email)
                .putExtra(EXTRA_PREFILL_FULL_NAME, fullName)
                .putExtra(EXTRA_PREFILL_PHONE, phoneNumber)
        )
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
