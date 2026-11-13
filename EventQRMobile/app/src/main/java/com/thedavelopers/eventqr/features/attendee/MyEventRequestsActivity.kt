package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import com.thedavelopers.eventqr.ui.components.EventRequestHolder
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant

class MyEventRequestsActivity : AppCompatActivity() {
    /** Tests set this before the first load to replace the network repository. */
    internal var repositoryOverride: AttendeeRepository? = null

    private lateinit var repository: AttendeeRepository
    private lateinit var btnBack: ImageButton
    private lateinit var recyclerRequests: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var txtEmpty: EventQrEmptyState
    private lateinit var errorStrip: View
    private lateinit var btnRetry: TextView
    private lateinit var adapter: MyEventRequestsAdapter
    private var loadJob: Job? = null
    private var isFirstResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_event_requests)
        applyEventQrSystemBarAppearance()
        repository = repositoryOverride ?: AttendeeRepository(this)

        btnBack = findViewById(R.id.nav_header_back)
        swipeRefresh = findViewById(R.id.swipeRefreshMyEventRequests)
        recyclerRequests = findViewById(R.id.recyclerMyEventRequests)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        txtEmpty = findViewById(R.id.txtMyRequestsEmpty)
        errorStrip = findViewById(R.id.errorMyRequests)
        btnRetry = findViewById(R.id.btnMyRequestsRetry)

        adapter = MyEventRequestsAdapter(
            onTap = { request -> onRequestTapped(request) }
        )

        recyclerRequests.layoutManager = LinearLayoutManager(this)
        recyclerRequests.adapter = adapter

        btnBack.setOnClickListener { finish() }
        btnRetry.setOnClickListener { loadRequests() }
        swipeRefresh.setOnRefreshListener { loadRequests() }

        txtEmpty.setIcon(AppIcons.Request)

        findViewById<View>(R.id.btnNewRequest).setOnClickListener {
            startActivity(Intent(this, RequestEventActivity::class.java))
        }

        loadRequests()
    }

    override fun onResume() {
        super.onResume()
        // onCreate already loads; later resumes (e.g. back from a request detail) refresh the list.
        if (isFirstResume) {
            isFirstResume = false
            return
        }
        loadRequests()
    }

    override fun onDestroy() {
        loadJob?.cancel()
        super.onDestroy()
    }

    private fun loadRequests() {
        loadJob?.cancel()
        showLoadingState()
        loadJob = lifecycleScope.launch {
            when (val result = repository.getMyEventRequests()) {
                is NetworkResult.Success -> showDataState(result.data)
                is NetworkResult.Error -> {
                    showErrorState(
                        result.message.ifBlank {
                            getString(R.string.my_event_requests_unable_to_load_event_requests)
                        },
                    )
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun showLoadingState() {
        val hasRows = ::adapter.isInitialized && adapter.itemCount > 0
        if (!swipeRefresh.isRefreshing) {
            // A refresh over readable rows keeps them on screen; the skeleton is a first-load state.
            skeletonLoading.visibility = if (!hasRows) View.VISIBLE else View.GONE
        }
        errorStrip.visibility = View.GONE
        if (!hasRows) {
            txtEmpty.visibility = View.GONE
            recyclerRequests.visibility = View.GONE
        }
    }

    private fun showDataState(requests: List<EventRequestResponse>) {
        swipeRefresh.isRefreshing = false
        skeletonLoading.visibility = View.GONE
        errorStrip.visibility = View.GONE

        if (requests.isEmpty()) {
            txtEmpty.visibility = View.VISIBLE
            recyclerRequests.visibility = View.GONE
            txtEmpty.setIcon(AppIcons.Request)
            txtEmpty.setAction(null)
            txtEmpty.show(
                getString(R.string.my_event_requests_no_event_requests_yet),
                getString(R.string.ui_attendee_no_requests_body),
            )
            adapter.submitItems(emptyList())
            return
        }

        txtEmpty.visibility = View.GONE
        recyclerRequests.visibility = View.VISIBLE
        adapter.submitItems(requests)
    }

    private fun showErrorState(message: String) {
        swipeRefresh.isRefreshing = false
        skeletonLoading.visibility = View.GONE

        if (adapter.itemCount > 0) {
            // The rows are still readable: keep them and report the failed refresh in the flow,
            // under the header, with Retry beside the sentence (§5.7 line 642).
            errorStrip.visibility = View.VISIBLE
            txtEmpty.visibility = View.GONE
            recyclerRequests.visibility = View.VISIBLE
        } else {
            // Zero-row failure keeps the empty-state slot, with the same in-flow Retry.
            errorStrip.visibility = View.GONE
            txtEmpty.setIcon(AppIcons.Warning)
            txtEmpty.setContent(
                getString(R.string.my_event_requests_unable_to_load_event_requests),
                message.ifBlank { getString(R.string.ui_attendee_load_error_body) },
            )
            txtEmpty.setAction(getString(R.string.ui_attendee_retry)) { loadRequests() }
            recyclerRequests.visibility = View.GONE
            txtEmpty.visibility = View.VISIBLE
            txtEmpty.announceForAccessibility(
                getString(R.string.my_event_requests_unable_to_load_event_requests),
            )
        }
    }

    private fun onRequestTapped(request: EventRequestResponse) {
        startActivity(
            Intent(this, AttendeeEventRequestDetailActivity::class.java)
                .putExtra(AttendeeEventRequestDetailActivity.EXTRA_EVENT_REQUEST_ID, request.eventRequestId.toString())
        )
    }

    private class MyEventRequestsAdapter(
        private val onTap: (EventRequestResponse) -> Unit,
    ) : RecyclerView.Adapter<MyEventRequestsAdapter.RequestViewHolder>() {

        private val items = mutableListOf<EventRequestResponse>()

        fun submitItems(newItems: List<EventRequestResponse>) {
            items.clear()
            items.addAll(newItems.sortedByDescending { it.createdAt ?: Instant.EPOCH })
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
            return RequestViewHolder(EventRequestHolder(parent.context))
        }

        override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
            holder.bind(items[position], onTap)
        }

        override fun getItemCount(): Int = items.size

        class RequestViewHolder(
            private val holder: EventRequestHolder,
        ) : RecyclerView.ViewHolder(holder.view) {

            fun bind(
                request: EventRequestResponse,
                onTap: (EventRequestResponse) -> Unit,
            ) {
                holder.onClick = onTap
                holder.update(request)
            }
        }
    }
}
