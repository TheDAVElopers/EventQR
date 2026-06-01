package com.thedavelopers.eventqr.features.staff

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.registrations.RegistrationAdapter
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.staff.details.StaffAttendeeDetailsActivity
import kotlinx.coroutines.launch
import java.util.Locale

open class EventRegistrationsActivity : AppCompatActivity(), EventRegistrationsContract.View {
    private lateinit var presenter: EventRegistrationsPresenter
    private lateinit var adapter: RegistrationAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var searchInput: EditText
    private lateinit var emptyState: TextView
    private lateinit var eventTitleView: TextView
    private lateinit var totalView: TextView
    private lateinit var checkedInView: TextView
    private lateinit var registeredView: TextView

    private var selectedEventId: String = ""
    private var allRegistrations: List<RegistrationResponse> = emptyList()

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

        bindViews()
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
                        eventTitleView.text = firstEvent.title.ifBlank { "Assigned Event" }
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

    private fun bindViews() {
        swipeRefresh = findViewById(R.id.swipeRefreshEventRegistrations)
        searchInput = findViewById(R.id.edtRegistrationSearch)
        emptyState = findViewById(R.id.txtAssignedEventsEmpty)
        eventTitleView = findViewById(R.id.txtEventRegistrationsEventTitle)
        totalView = findViewById(R.id.txtAttendeeTotal)
        checkedInView = findViewById(R.id.txtAttendeeCheckedIn)
        registeredView = findViewById(R.id.txtAttendeeRegistered)

        findViewById<View>(R.id.btnBackEventRegistrations).setOnClickListener { finish() }
        swipeRefresh.setColorSchemeResources(R.color.eventqr_purple)
        swipeRefresh.setOnRefreshListener {
            if (selectedEventId.isNotBlank()) presenter.load(selectedEventId) else swipeRefresh.isRefreshing = false
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    override fun renderRegistrations(items: List<RegistrationResponse>) {
        allRegistrations = items
        val title = items.firstOrNull()?.eventTitle?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_TITLE)?.takeIf { it.isNotBlank() }
            ?: "Assigned Event"
        eventTitleView.text = title
        totalView.text = items.size.toString()
        checkedInView.text = items.count { it.status == RegistrationStatus.ENTERED || it.status == RegistrationStatus.EXITED }.toString()
        registeredView.text = items.count { it.status == RegistrationStatus.REGISTERED }.toString()
        applyFilter(searchInput.text?.toString().orEmpty())
    }

    private fun applyFilter(query: String) {
        val normalized = query.trim().lowercase(Locale.US)
        val filtered = if (normalized.isBlank()) {
            allRegistrations
        } else {
            allRegistrations.filter {
                it.attendeeName.lowercase(Locale.US).contains(normalized) ||
                    it.attendeeEmail.lowercase(Locale.US).contains(normalized) ||
                    it.registrationId.toString().lowercase(Locale.US).contains(normalized)
            }
        }
        adapter.submitItems(filtered)
        findViewById<RecyclerView>(R.id.recyclerEventRegistrations).visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        emptyState.text = if (allRegistrations.isEmpty()) "No attendees found." else "No attendees match your search."
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading(isLoading: Boolean) {
        findViewById<View>(R.id.progressScanner)?.visibility = if (isLoading && !swipeRefresh.isRefreshing) View.VISIBLE else View.GONE
        swipeRefresh.isRefreshing = isLoading && swipeRefresh.isRefreshing
        findViewById<View>(R.id.btnLoadEventRegistrations)?.isEnabled = !isLoading
    }
}
