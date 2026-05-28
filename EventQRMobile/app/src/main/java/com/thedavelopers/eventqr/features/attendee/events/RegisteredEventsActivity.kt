package com.thedavelopers.eventqr.features.attendee

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.registrations.RegisteredEventAdapter
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import java.time.Instant

open class RegisteredEventsActivity : AppCompatActivity(), RegisteredEventsContract.View {
    private lateinit var presenter: RegisteredEventsPresenter
    private lateinit var adapter: RegisteredEventAdapter
    private lateinit var loadingView: View
    private lateinit var chipAll: TextView
    private lateinit var chipRegistered: TextView
    private lateinit var chipCompleted: TextView

    private var allItems: List<RegistrationResponse> = emptyList()
    private var selectedFilter: RegisteredEventFilter = RegisteredEventFilter.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_registered_events)

        presenter = RegisteredEventsPresenter(this, AttendeeRepository(this))
        loadingView = findViewById(R.id.txtRegisteredEventsEmpty)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        chipAll = findViewById(R.id.chipAll)
        chipRegistered = findViewById(R.id.chipRegistered)
        chipCompleted = findViewById(R.id.chipCompleted)

        chipAll.setOnClickListener { selectFilter(RegisteredEventFilter.ALL) }
        chipRegistered.setOnClickListener { selectFilter(RegisteredEventFilter.REGISTERED) }
        chipCompleted.setOnClickListener { selectFilter(RegisteredEventFilter.COMPLETED) }

        adapter = RegisteredEventAdapter()
        findViewById<RecyclerView>(R.id.recyclerRegisteredEvents).apply {
            layoutManager = LinearLayoutManager(this@RegisteredEventsActivity)
            adapter = this@RegisteredEventsActivity.adapter
        }

        updateFilterUI()
        presenter.load()
    }

    private fun selectFilter(filter: RegisteredEventFilter) {
        selectedFilter = filter
        updateFilterUI()
        renderFilteredEvents()
    }

    private fun updateFilterUI() {
        val activeBg = R.drawable.bg_nav_active
        val inactiveBg = R.drawable.bg_soft_input_no_stroke
        val activeColor = androidx.core.content.ContextCompat.getColor(this, android.R.color.white)
        val inactiveColor = android.graphics.Color.parseColor("#6B7280")

        chipAll.setBackgroundResource(if (selectedFilter == RegisteredEventFilter.ALL) activeBg else inactiveBg)
        chipAll.setTextColor(if (selectedFilter == RegisteredEventFilter.ALL) activeColor else inactiveColor)

        chipRegistered.setBackgroundResource(if (selectedFilter == RegisteredEventFilter.REGISTERED) activeBg else inactiveBg)
        chipRegistered.setTextColor(if (selectedFilter == RegisteredEventFilter.REGISTERED) activeColor else inactiveColor)

        chipCompleted.setBackgroundResource(if (selectedFilter == RegisteredEventFilter.COMPLETED) activeBg else inactiveBg)
        chipCompleted.setTextColor(if (selectedFilter == RegisteredEventFilter.COMPLETED) activeColor else inactiveColor)
    }

    private fun renderFilteredEvents() {
        val now = Instant.now()
        val filtered = when (selectedFilter) {
            RegisteredEventFilter.ALL -> allItems
            RegisteredEventFilter.REGISTERED -> allItems.filter { it.eventStartAt?.isAfter(now) ?: true }
            RegisteredEventFilter.COMPLETED -> allItems.filter { it.eventStartAt?.isBefore(now) ?: false }
        }
        adapter.submitItems(filtered)
        findViewById<View>(R.id.txtRegisteredEventsEmpty).visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE

        chipAll.visibility = View.VISIBLE
        chipRegistered.visibility = View.VISIBLE
        chipCompleted.visibility = View.VISIBLE

        chipAll.text = "All (${allItems.size})"
        chipRegistered.text = "Registered (${allItems.count { it.eventStartAt?.isAfter(now) ?: true }})"
        chipCompleted.text = "Completed (${allItems.count { it.eventStartAt?.isBefore(now) ?: false }})"
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        loadingView.visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<RecyclerView>(R.id.recyclerRegisteredEvents).visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showRegisteredEvents(items: List<RegistrationResponse>) {
        allItems = items
        renderFilteredEvents()
    }
}
