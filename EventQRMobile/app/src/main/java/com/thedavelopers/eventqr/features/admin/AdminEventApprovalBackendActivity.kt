package com.thedavelopers.eventqr.features.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.features.organizer.ManageUsersActivity
import com.thedavelopers.eventqr.features.organizer.TransactionLogsActivity
import kotlinx.coroutines.launch

class AdminEventApprovalBackendActivity : AppCompatActivity() {

    private lateinit var repository: AdminRepository
    private lateinit var adapter: AdminEventRequestAdapter

    private lateinit var chipAll: TextView
    private lateinit var chipPending: TextView
    private lateinit var chipApproved: TextView
    private lateinit var chipRejected: TextView

    private lateinit var loadingRequests: ProgressBar
    private lateinit var textError: TextView
    private lateinit var buttonRetry: Button
    private lateinit var textEmpty: TextView
    private lateinit var recyclerRequests: RecyclerView

    private lateinit var navDashboard: LinearLayout
    private lateinit var navRequests: LinearLayout
    private lateinit var navAccounts: LinearLayout
    private lateinit var navLogs: LinearLayout

    private var allRequests = emptyList<com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse>()
    private var currentFilter: EventRequestFilter = EventRequestFilter.ALL

    private val detailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        loadRequests(showLoading = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_event_requests)

        repository = AdminRepository(this)
        bindViews()
        bindNavigation()
        bindFilters()

        adapter = AdminEventRequestAdapter { request ->
            val detailIntent = Intent(this, AdminEventRequestDetailActivity::class.java)
                .putExtra(AdminEventRequestDetailActivity.EXTRA_REQUEST_ID, request.eventRequestId.toString())
            detailLauncher.launch(detailIntent)
        }

        recyclerRequests.layoutManager = LinearLayoutManager(this)
        recyclerRequests.adapter = adapter

        verifyAdminAccess()
    }

    private fun bindViews() {
        chipAll = findViewById(R.id.chipAll)
        chipPending = findViewById(R.id.chipPending)
        chipApproved = findViewById(R.id.chipApproved)
        chipRejected = findViewById(R.id.chipRejected)

        loadingRequests = findViewById(R.id.loadingRequests)
        textError = findViewById(R.id.textError)
        buttonRetry = findViewById(R.id.buttonRetry)
        textEmpty = findViewById(R.id.textEmpty)
        recyclerRequests = findViewById(R.id.recyclerRequests)

        navDashboard = findViewById(R.id.navDashboard)
        navRequests = findViewById(R.id.navRequests)
        navAccounts = findViewById(R.id.navAccounts)
        navLogs = findViewById(R.id.navLogs)

        buttonRetry.setOnClickListener { loadRequests(showLoading = true) }
    }

    private fun bindNavigation() {
        navDashboard.setOnClickListener {
            startActivity(Intent(this, com.thedavelopers.eventqr.features.dashboard.DashboardActivity::class.java))
            finish()
        }

        navRequests.setOnClickListener {
            // current tab
        }

        navAccounts.setOnClickListener {
            startActivity(Intent(this, ManageUsersActivity::class.java))
        }

        navLogs.setOnClickListener {
            startActivity(Intent(this, TransactionLogsActivity::class.java))
        }
    }

    private fun bindFilters() {
        chipAll.setOnClickListener { applyFilter(EventRequestFilter.ALL) }
        chipPending.setOnClickListener { applyFilter(EventRequestFilter.PENDING) }
        chipApproved.setOnClickListener { applyFilter(EventRequestFilter.APPROVED) }
        chipRejected.setOnClickListener { applyFilter(EventRequestFilter.REJECTED) }
        updateFilterChips(EventRequestFilter.ALL)
    }

    private fun verifyAdminAccess() {
        setLoadingState(true)
        lifecycleScope.launch {
            when (val result = repository.getCurrentUser()) {
                is NetworkResult.Success -> {
                    if (result.data.role != AccountRole.ADMIN) {
                        showError("Admin access required.", showRetry = false)
                        Toast.makeText(this@AdminEventApprovalBackendActivity, "Admin access required.", Toast.LENGTH_SHORT).show()
                    } else {
                        loadRequests(showLoading = true)
                    }
                }

                is NetworkResult.Error -> {
                    showError(toFriendlyError(result.message), showRetry = false)
                }

                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun loadRequests(showLoading: Boolean) {
        if (showLoading) {
            setLoadingState(true)
        }

        lifecycleScope.launch {
            when (val result = repository.loadAllEventRequests()) {
                is NetworkResult.Success -> {
                    allRequests = result.data
                    setLoadingState(false)
                    renderList()
                }

                is NetworkResult.Error -> {
                    setLoadingState(false)
                    showError(toFriendlyError(result.message), showRetry = true)
                }

                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun applyFilter(filter: EventRequestFilter) {
        currentFilter = filter
        updateFilterChips(filter)
        renderList()
    }

    private fun renderList() {
        hideErrorAndEmpty()

        val filtered = when (currentFilter) {
            EventRequestFilter.ALL -> allRequests
            EventRequestFilter.PENDING -> allRequests.filter { it.status == EventRequestStatus.PENDING }
            EventRequestFilter.APPROVED -> allRequests.filter { it.status == EventRequestStatus.APPROVED }
            EventRequestFilter.REJECTED -> allRequests.filter { it.status == EventRequestStatus.REJECTED }
        }

        adapter.submit(filtered)
        textEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateFilterChips(filter: EventRequestFilter) {
        setChipState(chipAll, filter == EventRequestFilter.ALL)
        setChipState(chipPending, filter == EventRequestFilter.PENDING)
        setChipState(chipApproved, filter == EventRequestFilter.APPROVED)
        setChipState(chipRejected, filter == EventRequestFilter.REJECTED)
    }

    private fun setChipState(chip: TextView, selected: Boolean) {
        chip.setBackgroundResource(if (selected) R.drawable.bg_segment_selected else android.R.color.transparent)
        chip.setTextColor(if (selected) 0xFF111827.toInt() else 0xFF4B5563.toInt())
    }

    private fun setLoadingState(loading: Boolean) {
        loadingRequests.visibility = if (loading) View.VISIBLE else View.GONE
        recyclerRequests.visibility = if (loading) View.GONE else View.VISIBLE
        if (loading) {
            textError.visibility = View.GONE
            buttonRetry.visibility = View.GONE
            textEmpty.visibility = View.GONE
        }
    }

    private fun showError(message: String, showRetry: Boolean) {
        recyclerRequests.visibility = View.GONE
        textEmpty.visibility = View.GONE
        textError.visibility = View.VISIBLE
        textError.text = message
        buttonRetry.visibility = if (showRetry) View.VISIBLE else View.GONE
    }

    private fun hideErrorAndEmpty() {
        recyclerRequests.visibility = View.VISIBLE
        textError.visibility = View.GONE
        buttonRetry.visibility = View.GONE
    }

    private fun toFriendlyError(message: String): String {
        val normalized = message.lowercase()
        return when {
            normalized.contains("401") || normalized.contains("unauthorized") -> "Session expired. Please sign in again."
            normalized.contains("403") || normalized.contains("forbidden") || normalized.contains("admin access") -> "Admin access required."
            normalized.contains("404") || normalized.contains("not found") -> "Request not found."
            normalized.contains("400") || normalized.contains("invalid") || normalized.contains("bad request") -> "Request failed. Please verify request state and try again."
            normalized.contains("500") -> "Server error. Please try again later."
            normalized.contains("unable to resolve host") || normalized.contains("failed to connect") || normalized.contains("timeout") -> "No internet connection. Check your network and try again."
            else -> message
        }
    }

    private enum class EventRequestFilter {
        ALL,
        PENDING,
        APPROVED,
        REJECTED,
    }
}
