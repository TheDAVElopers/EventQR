package com.thedavelopers.eventqr.features.staff

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.staff.details.StaffAttendeeDetailsActivity
import com.thedavelopers.eventqr.features.registrations.RegistrationAdapter
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import kotlinx.coroutines.launch

open class EventRegistrationsActivity : AppCompatActivity(), EventRegistrationsContract.View {
    private lateinit var presenter: EventRegistrationsPresenter
    private lateinit var adapter: RegistrationAdapter
    private var selectedEventId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.STAFF.name) {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_event_registrations)

        presenter = EventRegistrationsPresenter(this, StaffRepository(this))
        adapter = RegistrationAdapter { registration ->
            startActivity(Intent(this, StaffAttendeeDetailsActivity::class.java).apply {
                putExtra(StaffScreenExtras.EXTRA_EVENT_ID, registration.eventId.toString())
                putExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID, registration.attendeeUserId.toString())
                putExtra(StaffScreenExtras.EXTRA_REGISTRATION_ID, registration.registrationId.toString())
                putExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID, registration.qrCredentialId?.toString().orEmpty())
                putExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME, registration.attendeeName)
                putExtra(StaffScreenExtras.EXTRA_ATTENDEE_EMAIL, registration.attendeeEmail)
                putExtra(StaffScreenExtras.EXTRA_EVENT_TITLE, registration.eventTitle.orEmpty())
            })
        }

        findViewById<RecyclerView>(R.id.recyclerEventRegistrations).apply {
            layoutManager = LinearLayoutManager(this@EventRegistrationsActivity)
            adapter = this@EventRegistrationsActivity.adapter
        }

        selectedEventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID).orEmpty()
        if (selectedEventId.isNotBlank()) {
            findViewById<EditText>(R.id.edtRegistrationsEventId).setText(selectedEventId)
            presenter.load(selectedEventId)
        } else {
            kotlinx.coroutines.MainScope().launch {
                when (val eventsResult = StaffRepository(this@EventRegistrationsActivity).getEvents()) {
                    is NetworkResult.Success -> {
                        val firstEvent = eventsResult.data.firstOrNull()
                        if (firstEvent == null) {
                            showMessage("No assigned events found")
                            return@launch
                        }
                        selectedEventId = firstEvent.eventId.toString()
                        findViewById<EditText>(R.id.edtRegistrationsEventId).setText(selectedEventId)
                        presenter.load(selectedEventId)
                    }
                    is NetworkResult.Error -> showMessage(eventsResult.message)
                    NetworkResult.Loading -> Unit
                }
            }
        }

        findViewById<Button>(R.id.btnLoadEventRegistrations).setOnClickListener {
            selectedEventId = findViewById<EditText>(R.id.edtRegistrationsEventId).text.toString()
            presenter.load(selectedEventId)
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun renderRegistrations(items: List<RegistrationResponse>) {
        adapter.submitItems(items)
        findViewById<RecyclerView>(R.id.recyclerEventRegistrations).visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading(isLoading: Boolean) {
        findViewById<View>(R.id.progressScanner)?.visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnLoadEventRegistrations)?.isEnabled = !isLoading
    }
}
