package com.thedavelopers.eventqr.features.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.ui.components.CountLabel
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.components.asFilterChips
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * Admin event requests list (§6.5). Compact pass head, one shared chip row, flat 88dp page rows.
 * The filter chips carry their state in the fill (§5.6), so no state drawables and no per-chip
 * elevation are set from code. Pending is the default filter, so there is no `All` chip: the chip
 * row is exactly the queue an admin works.
 */
class AdminEventApprovalBackendActivity : AppCompatActivity() {

    /** Tests set this before the first load to replace the network repository. */
    @VisibleForTesting
    internal var repositoryOverride: AdminRepository? = null

    private val repository: AdminRepository by lazy {
        repositoryOverride ?: AdminRepository(this)
    }

    private lateinit var adapter: AdminEventRequestAdapter

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var chipPending: TextView
    private lateinit var chipApproved: TextView
    private lateinit var chipRejected: TextView
    private lateinit var textCount: TextView

    private lateinit var skeletonRequests: View
    private lateinit var textEmpty: EventQrEmptyState
    private lateinit var recyclerRequests: RecyclerView

    private var allRequests = emptyList<EventRequestResponse>()
    private var currentFilter: EventRequestFilter = EventRequestFilter.PENDING
    private var loadFailed = false
    private var retrySnackbar: Snackbar? = null

    private val detailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        loadRequests(showLoading = false)
    }

    override fun onDestroy() {
        retrySnackbar?.dismiss()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_event_requests)
        applyEventQrSystemBarAppearance(lightStatusBars = false)

        bindViews()
        bindHeader()
        bindNavigation()
        bindFilters()
        restoreFilter(savedInstanceState)

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
        swipeRefresh = findViewById(R.id.swipeRefreshRequests)
        chipPending = findViewById(R.id.chipPending)
        chipApproved = findViewById(R.id.chipApproved)
        chipRejected = findViewById(R.id.chipRejected)
        asFilterChips(chipPending, chipApproved, chipRejected)

        skeletonRequests = findViewById(R.id.skeletonRequests)
        textEmpty = findViewById(R.id.textEmpty)
        recyclerRequests = findViewById(R.id.recyclerRequests)

        swipeRefresh.setOnRefreshListener { loadRequests(showLoading = false) }
    }

    private fun bindHeader() {
        val head = findViewById<View>(R.id.passHead)
        head.findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()
        PassHeadBinder.bindCompact(
            root = head,
            title = getString(R.string.ui_admin_requests_title),
        )
        textCount = head.findViewById<TextView>(R.id.passHeadTitleCount).apply {
            visibility = View.VISIBLE
        }
    }

    private fun bindNavigation() {
        configureAdminBottomNav(AdminBottomNavItem.REQUESTS)
    }

    private fun bindFilters() {
        chipPending.setOnClickListener { applyFilter(EventRequestFilter.PENDING) }
        chipApproved.setOnClickListener { applyFilter(EventRequestFilter.APPROVED) }
        chipRejected.setOnClickListener { applyFilter(EventRequestFilter.REJECTED) }
        updateFilterChips(EventRequestFilter.PENDING)
    }

    /**
     * A rotation must not move the admin off the tab they were reading. The filter is UI state, not
     * data, so the bundle is enough; an unknown stored name keeps the default.
     */
    private fun restoreFilter(savedInstanceState: Bundle?) {
        val saved = savedInstanceState?.getString(STATE_FILTER) ?: return
        val filter = EventRequestFilter.entries.firstOrNull { it.name == saved } ?: return
        currentFilter = filter
        updateFilterChips(filter)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_FILTER, currentFilter.name)
    }

    private fun verifyAdminAccess() {
        setLoadingState(true)
        lifecycleScope.launch {
            when (val result = repository.getCurrentUser()) {
                is NetworkResult.Success -> {
                    if (result.data.role != AccountRole.ADMIN && result.data.role != AccountRole.SUPER_ADMIN) {
                        showAccessDenied()
                    } else {
                        loadRequests(showLoading = true)
                    }
                }
                is NetworkResult.Error -> {
                    setLoadingState(false)
                    showError(toFriendlyError(result.message)) { verifyAdminAccess() }
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun loadRequests(showLoading: Boolean) {
        retrySnackbar?.dismiss()
        if (showLoading && !swipeRefresh.isRefreshing) {
            setLoadingState(true)
        }

        lifecycleScope.launch {
            when (val result = repository.loadAllEventRequests()) {
                is NetworkResult.Success -> {
                    swipeRefresh.isRefreshing = false
                    allRequests = result.data
                    loadFailed = false
                    setLoadingState(false)
                    renderList()
                }
                is NetworkResult.Error -> {
                    swipeRefresh.isRefreshing = false
                    setLoadingState(false)
                    val message = toFriendlyError(result.message)
                    // Rows already on screen stay put: the failure is a transient banner, not a
                    // page state. Only a page with no data at all hands the slot to the error state.
                    if (allRequests.isEmpty()) {
                        showError(message) { loadRequests(showLoading = true) }
                    } else {
                        showRetrySnackbar(message)
                    }
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun applyFilter(filter: EventRequestFilter) {
        currentFilter = filter
        updateFilterChips(filter)
        // The failure owns the slot until a load succeeds; a filter must not replace it with empty copy.
        if (loadFailed) return
        renderList()
    }

    private fun renderList() {
        // Pending is a work queue: oldest first, so the request that has waited longest is on top.
        // The decided tabs answer "what just happened": newest first. Undated rows sink last in both.
        val comparator = when (currentFilter) {
            EventRequestFilter.PENDING -> compareBy<EventRequestResponse> { it.createdAt ?: Instant.MAX }
            EventRequestFilter.APPROVED, EventRequestFilter.REJECTED ->
                compareByDescending { it.createdAt ?: Instant.MIN }
        }
        val filtered = allRequests
            .filter { it.status == currentFilter.status }
            .sortedWith(comparator)
        adapter.submit(filtered)
        // shortcut: GET /admin/event-requests returns the whole list with no page or total, so the
        // loaded list is the page total. Swap in a server total when the endpoint grows paging.
        textCount.text = CountLabel.format(filtered.size.toLong(), getString(R.string.ui_admin_requests_count))
        if (filtered.isEmpty()) {
            recyclerRequests.visibility = View.GONE
            showFilterEmpty()
        } else {
            recyclerRequests.visibility = View.VISIBLE
            textEmpty.hide()
        }
    }

    /** §6.8: each chip owns its empty copy. With no All chip there is no state to clear. */
    private fun showFilterEmpty() {
        val titleRes: Int
        val bodyRes: Int
        when (currentFilter) {
            EventRequestFilter.PENDING -> {
                titleRes = R.string.ui_admin_requests_empty_pending_title
                bodyRes = R.string.ui_admin_requests_empty_pending_body
            }
            EventRequestFilter.APPROVED -> {
                titleRes = R.string.ui_admin_requests_empty_approved_title
                bodyRes = R.string.ui_admin_requests_empty_approved_body
            }
            EventRequestFilter.REJECTED -> {
                titleRes = R.string.ui_admin_requests_empty_rejected_title
                bodyRes = R.string.ui_admin_requests_empty_rejected_body
            }
        }
        textEmpty.setIcon(AppIcons.Request)
        textEmpty.show(getString(titleRes), getString(bodyRes))
        textEmpty.setAction(null)
    }

    private fun updateFilterChips(filter: EventRequestFilter) {
        chipPending.isSelected = filter == EventRequestFilter.PENDING
        chipApproved.isSelected = filter == EventRequestFilter.APPROVED
        chipRejected.isSelected = filter == EventRequestFilter.REJECTED
    }

    private fun setLoadingState(loading: Boolean) {
        skeletonRequests.visibility = if (loading && !swipeRefresh.isRefreshing) View.VISIBLE else View.GONE
        recyclerRequests.visibility = if (loading) View.GONE else View.VISIBLE
        if (loading) {
            textEmpty.hide()
        }
    }

    /** §6.8 state 3: a failed load owns the empty slot, with the recovery as its only action. */
    private fun showError(message: String, retry: () -> Unit) {
        loadFailed = true
        textCount.text = ""
        recyclerRequests.visibility = View.GONE
        textEmpty.setIcon(AppIcons.Warning)
        textEmpty.show(getString(R.string.ui_admin_requests_error_title), message)
        textEmpty.setAction(getString(R.string.common_retry)) { retry() }
    }

    /**
     * A refresh over rows that are already on screen keeps them and offers the retry in a banner:
     * replacing readable rows with an error state would lose the admin's place in the queue.
     */
    private fun showRetrySnackbar(message: String) {
        retrySnackbar?.dismiss()
        retrySnackbar = Snackbar.make(recyclerRequests, message, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.common_retry) { loadRequests(showLoading = false) }
        retrySnackbar?.show()
    }

    /** Access denied is one in-flow error with one action. No toast duplicates the message. */
    private fun showAccessDenied() {
        loadFailed = true
        setLoadingState(false)
        textCount.text = ""
        recyclerRequests.visibility = View.GONE
        textEmpty.setIcon(AppIcons.Warning)
        textEmpty.show(getString(R.string.ui_admin_error_admin_required))
        textEmpty.setAction(getString(R.string.ui_admin_go_back)) { finish() }
    }

    private fun toFriendlyError(message: String): String {
        val normalized = message.lowercase()
        return when {
            normalized.contains("disabled") || normalized.contains("suspend") -> getString(R.string.ui_admin_error_disabled)
            normalized.contains("401") || normalized.contains("unauthorized") -> getString(R.string.ui_admin_error_session)
            normalized.contains("403") || normalized.contains("forbidden") || normalized.contains("admin access") -> getString(R.string.ui_admin_error_admin_required)
            normalized.contains("404") || normalized.contains("not found") -> getString(R.string.ui_admin_error_not_found)
            normalized.contains("409") || normalized.contains("conflict") -> getString(R.string.ui_admin_error_conflict)
            normalized.contains("400") || normalized.contains("invalid") || normalized.contains("bad request") -> getString(R.string.ui_admin_error_bad_request)
            normalized.contains("500") -> getString(R.string.ui_admin_error_server)
            normalized.contains("unable to resolve host") || normalized.contains("failed to connect") || normalized.contains("timeout") -> getString(R.string.ui_admin_error_network)
            else -> message
        }
    }

    private companion object {
        const val STATE_FILTER = "adminRequestsFilter"
    }

    private enum class EventRequestFilter(val status: EventRequestStatus) {
        PENDING(EventRequestStatus.PENDING),
        APPROVED(EventRequestStatus.APPROVED),
        REJECTED(EventRequestStatus.REJECTED),
    }
}
