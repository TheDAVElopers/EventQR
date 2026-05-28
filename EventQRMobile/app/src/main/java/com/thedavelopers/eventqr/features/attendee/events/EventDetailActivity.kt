package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse
import java.time.Instant

open class EventDetailActivity : AppCompatActivity(), EventDetailContract.View {
    private lateinit var presenter: EventDetailPresenter
    private lateinit var eventId: String
    private var currentEvent: AttendeeEventResponse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        presenter = EventDetailPresenter(this, AttendeeRepository(this))
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.txtDetailTitle).text = intent.getStringExtra(EXTRA_EVENT_TITLE).orEmpty()
        findViewById<TextView>(R.id.txtDetailDescription).text = intent.getStringExtra(EXTRA_EVENT_DESCRIPTION).orEmpty()
        findViewById<TextView>(R.id.txtDetailVenue).text = intent.getStringExtra(EXTRA_EVENT_LOCATION).orEmpty().ifBlank { "Location not specified" }
        findViewById<TextView>(R.id.txtTagCategory).text = intent.getStringExtra(EXTRA_EVENT_CATEGORY).orEmpty().ifBlank { "Technology" }
        findViewById<TextView>(R.id.txtDetailStart).text = intent.getStringExtra(EXTRA_EVENT_START).orEmpty()
        findViewById<TextView>(R.id.txtDetailStatus).text = intent.getStringExtra(EXTRA_EVENT_STATUS).orEmpty()
        findViewById<TextView>(R.id.txtDetailCapacity).text = "${intent.getStringExtra(EXTRA_EVENT_COUNT).orEmpty()} / ${intent.getStringExtra(EXTRA_EVENT_CAPACITY).orEmpty()} Registered"

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

        val startStr = DateFormatters.formatInstant(event.eventStartAt)
        val endStr = if (event.eventEndAt != null) " - ${DateFormatters.formatInstant(event.eventEndAt)}" else ""
        findViewById<TextView>(R.id.txtDetailStart).text = if (event.eventStartAt != null) "$startStr$endStr" else "Date and time not specified."

        findViewById<TextView>(R.id.txtDetailCapacity).text = "${event.currentAttendeeCount} / ${event.capacity} Registered"

        if (!event.category.isNullOrBlank()) {
            findViewById<View>(R.id.layoutDetailCategory)?.visibility = View.VISIBLE
            findViewById<TextView>(R.id.txtTagCategory).text = event.category
        } else {
            findViewById<View>(R.id.layoutDetailCategory)?.visibility = View.GONE
        }

        findViewById<View>(R.id.layoutDetailRewards)?.visibility = if (event.rewardsEnabled) View.VISIBLE else View.GONE

        updateRegisterButton(event)
    }

    private fun updateRegisterButton(event: AttendeeEventResponse) {
        val btn = findViewById<Button>(R.id.btnRegisterForEvent)
        val now = Instant.now()

        val isFull = event.currentAttendeeCount >= event.capacity
        val isCompleted = event.eventEndAt?.isBefore(now) == true
        val registrationNotOpen = event.registrationOpenAt?.isAfter(now) == true
        val registrationClosed = event.registrationCloseAt?.isBefore(now) == true

        when {
            isCompleted -> {
                btn.isEnabled = false
                btn.text = "Event Completed"
                btn.setBackgroundResource(R.drawable.bg_disabled_button)
            }
            isFull -> {
                btn.isEnabled = false
                btn.text = "Event Full"
                btn.setBackgroundResource(R.drawable.bg_disabled_button)
            }
            registrationNotOpen -> {
                btn.isEnabled = false
                btn.text = "Registration Not Open"
                btn.setBackgroundResource(R.drawable.bg_disabled_button)
            }
            registrationClosed -> {
                btn.isEnabled = false
                btn.text = "Registration Closed"
                btn.setBackgroundResource(R.drawable.bg_disabled_button)
            }
            else -> {
                btn.isEnabled = true
                btn.text = "Register"
                btn.setBackgroundResource(R.drawable.bg_eventqr_gradient)
            }
        }
    }

    override fun updateRegistrationStatus(isRegistered: Boolean) {
        if (isRegistered) {
            val btn = findViewById<Button>(R.id.btnRegisterForEvent)
            btn.isEnabled = false
            btn.text = "Already Registered"
            btn.setBackgroundResource(R.drawable.bg_disabled_button)

            if (currentEvent?.rewardsEnabled == true) {
                findViewById<View>(R.id.btnViewRewards)?.visibility = View.VISIBLE
            }
        }
    }

    override fun showLoading(isLoading: Boolean) = Unit

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun openRegistration(eventId: String, eventTitle: String, email: String, fullName: String) {
        startActivity(
            Intent(this, AttendeeRegistrationActivity::class.java)
                .putExtra(EXTRA_EVENT_ID, eventId)
                .putExtra(EXTRA_EVENT_TITLE, eventTitle)
                .putExtra(EXTRA_PREFILL_EMAIL, email)
                .putExtra(EXTRA_PREFILL_FULL_NAME, fullName)
        )
    }

    override fun getSessionUserId(): String? = SessionManager(this).getUserId()
    override fun getSessionEmail(): String = SessionManager(this).getEmail().orEmpty()
    override fun getSessionFullName(): String = SessionManager(this).getFullName().orEmpty()
}
