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
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.ui.components.CountLabel
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.components.asFilterChips
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import kotlinx.coroutines.launch

/**
 * Admin event requests list (§6.5). Compact pass head, one shared chip row, flat 88dp page rows.
 * The filter chips carry their state in the fill (§5.6), so no state drawables and no per-chip
 * elevation are set from code.
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
    private lateinit var chipAll: TextView
    private lateinit var chipPending: TextView
    private lateinit var chipApproved: TextView
    private lateinit var chipRejected: TextView
    private lateinit var textCount: TextView

    private lateinit var skeletonRequests: View
    private lateinit var textEmpty: EventQrEmptyState
    private lateinit var recyclerRequests: RecyclerView

    private var allRequests = emptyList<com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse>()
    private var currentFilter: EventRequestFilter = EventRequestFilter.ALL
    private var loadFailed = false

    private val detailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        loadRequests(showLoading = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_event_requests)
        applyEventQrSystemBarAppearance(lightStatusBars = false)

        bindViews()
        bindHeader()
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
        swipeRefresh = findViewById(R.id.swipeRefreshRequests)
        chipAll = findViewById(R.id.chipAll)
        chipPending = findViewById(R.id.chipPending)
        chipApproved = findViewById(R.id.chipApproved)
        chipRejected = findViewById(R.id.chipRejected)
        asFilterChips(chipAll, chipPending, chipApproved, chipRejected)

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
            eyebrow = getString(AdminRoleLabels.eyebrowRes(SessionManager(this).getUserRole())),
        )
        textCount = head.findViewById<TextView>(R.id.passHeadTitleCount).apply {
            visibility = View.VISIBLE
        }
    }

    /** Paints the eyebrow from a role. Prefs paint first; the auth/me role replaces it once it lands. */
    private fun bindRoleEyebrow(role: String?) {
        findViewById<TextView>(R.id.passHeadEyebrow)?.text =
            getString(AdminRoleLabels.eyebrowRes(role))
    }

    private fun bindNavigation() {
        configureAdminBottomNav(AdminBottomNavItem.REQUESTS)
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
                    if (result.data.role != AccountRole.ADMIN && result.data.role != AccountRole.SUPER_ADMIN) {
                        showAccessDenied()
                    } else {
                        bindRoleEyebrow(result.data.role.name)
                        loadRequests(showLoading = true)
                    }
                }
                is NetworkResult.Error -> {
                    setLoadingState(false)
                    showError(toFriendlyError(result.message), showRetry = false)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun loadRequests(showLoading: Boolean) {
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
                    showError(toFriendlyError(result.message), showRetry = true)
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
        val filtered = when (currentFilter) {
            EventRequestFilter.ALL -> allRequests
            EventRequestFilter.PENDING -> allRequests.filter { it.status == EventRequestStatus.PENDING }
            EventRequestFilter.APPROVED -> allRequests.filter { it.status == EventRequestStatus.APPROVED }
            EventRequestFilter.REJECTED -> allRequests.filter { it.status == EventRequestStatus.REJECTED }
        }
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

    /** §6.8: each chip owns its empty copy and a ghost Clear filter; no filter owns no action. */
    private fun showFilterEmpty() {
        val titleRes: Int
        val bodyRes: Int
        when (currentFilter) {
            EventRequestFilter.ALL -> {
                titleRes = R.string.ui_admin_requests_empty_title
                bodyRes = R.string.ui_admin_requests_empty_body
            }
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
        if (currentFilter == EventRequestFilter.ALL) {
            textEmpty.setAction(null)
        } else {
            textEmpty.setAction(getString(R.string.ui_admin_filter_clear)) {
                applyFilter(EventRequestFilter.ALL)
            }
        }
    }

    private fun updateFilterChips(filter: EventRequestFilter) {
        chipAll.isSelected = filter == EventRequestFilter.ALL
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
    private fun showError(message: String, showRetry: Boolean) {
        loadFailed = true
        recyclerRequests.visibility = View.GONE
        textEmpty.setIcon(AppIcons.Warning)
        textEmpty.show(getString(R.string.ui_admin_requests_error_title), message)
        textEmpty.setAction(if (showRetry) getString(R.string.common_retry) else null) {
            loadRequests(showLoading = true)
        }
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

    private enum class EventRequestFilter {
        ALL,
        PENDING,
        APPROVED,
        REJECTED,
    }
}
