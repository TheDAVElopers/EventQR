package com.thedavelopers.eventqr.features.organizer.notifications

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.core.api.dto.NotificationType
import com.thedavelopers.eventqr.features.notifications.NotificationAdapter
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import java.util.UUID

class NotificationManagementActivity : AppCompatActivity() {

    private lateinit var viewModel: OrganizerNotificationsViewModel
    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_organizer_notifications)

        val repo = OrganizerRepository(this)
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return OrganizerNotificationsViewModel(repo) as T
            }
        })[OrganizerNotificationsViewModel::class.java]

        setupBack()
        setupMarkAllRead(repo)
        setupFilters(repo)
        setupRecycler(repo)
        observeState()

        viewModel.load()
    }

    private fun setupBack() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupMarkAllRead(repo: OrganizerRepository) {
        findViewById<TextView>(R.id.txtMarkAllRead).setOnClickListener {
            viewModel.markAllRead { repo.markAllNotificationsRead() }
        }
    }

    private fun setupFilters(repo: OrganizerRepository) {
        val events = repo.getApprovedOrganizerEvents()
        val eventNames = listOf("All events") + events.map { it.title }
        val eventSpinner = findViewById<Spinner>(R.id.spinnerEventFilter)
        eventSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, eventNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        eventSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val event = if (position == 0) null else events[position - 1]
                viewModel.setEventFilter(event?.id?.let { UUID.fromString(it) })
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val typeNames = listOf("All types") + NotificationType.entries.map { it.name }
        val typeSpinner = findViewById<Spinner>(R.id.spinnerTypeFilter)
        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, typeNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val type = if (position == 0) null else NotificationType.entries[position - 1]
                viewModel.setTypeFilter(type)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRecycler(repo: OrganizerRepository) {
        val recycler = findViewById<RecyclerView>(R.id.recyclerNotifications)
        adapter = NotificationAdapter { item ->
            if (item.status != NotificationStatus.READ) {
                viewModel.markRead(item) { target -> repo.markNotificationRead(target.notificationId.toString()) }
            }
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        findViewById<SwipeRefreshLayout>(R.id.swipeRefreshNotifications).setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun observeState() {
        viewModel.loading.observe(this) { loading ->
            findViewById<View>(R.id.skeletonLoading).visibility = if (loading) View.VISIBLE else View.GONE
            findViewById<SwipeRefreshLayout>(R.id.swipeRefreshNotifications).isRefreshing = loading
        }
        viewModel.error.observe(this) { error ->
            findViewById<View>(R.id.layoutNotificationError).visibility = if (error != null) View.VISIBLE else View.GONE
        }
        viewModel.list.observe(this) { items ->
            adapter.submitItems(items)
            findViewById<View>(R.id.layoutNotificationEmpty).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.txtMarkAllRead).isEnabled = true
        }
    }
}