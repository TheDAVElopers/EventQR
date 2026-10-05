package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
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
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.EventRequestStatus
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.events.model.dto.EventRequestResponse
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MyEventRequestsActivity : AppCompatActivity() {
    private lateinit var repository: AttendeeRepository
    private lateinit var btnBack: ImageButton
    private lateinit var recyclerRequests: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var txtEmpty: TextView
    private lateinit var txtError: TextView
    private lateinit var btnRetry: Button
    private lateinit var adapter: MyEventRequestsAdapter

    private val submittedDateFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("MMM d, yyyy")
        .withZone(ZoneId.of("Asia/Manila"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_event_requests)
        repository = AttendeeRepository(this)

        btnBack = findViewById(R.id.btnBack)
        swipeRefresh = findViewById(R.id.swipeRefreshMyEventRequests)
        recyclerRequests = findViewById(R.id.recyclerMyEventRequests)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        txtEmpty = findViewById(R.id.txtMyRequestsEmpty)
        txtError = findViewById(R.id.txtMyRequestsError)
        btnRetry = findViewById(R.id.btnMyRequestsRetry)

        adapter = MyEventRequestsAdapter(
            submittedDateFormatter = submittedDateFormatter,
            onTap = { request -> onRequestTapped(request) }
        )

        recyclerRequests.layoutManager = LinearLayoutManager(this)
        recyclerRequests.adapter = adapter

        btnBack.setOnClickListener { finish() }
        btnRetry.setOnClickListener { loadRequests() }
        swipeRefresh.setColorSchemeResources(R.color.eventqr_purple)
        swipeRefresh.setOnRefreshListener { loadRequests() }

        findViewById<View>(R.id.btnNewRequest).setOnClickListener {
            startActivity(Intent(this, RequestEventActivity::class.java))
        }

        loadRequests()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            loadRequests()
        }
    }

    private fun loadRequests() {
        showLoadingState()
        lifecycleScope.launch {
            when (val result = repository.getMyEventRequests()) {
                is NetworkResult.Success -> showDataState(result.data)
                is NetworkResult.Error -> {
                    showErrorState(result.message.ifBlank { "Unable to load event requests." })
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun showLoadingState() {
        if (!swipeRefresh.isRefreshing) {
            skeletonLoading.visibility = View.VISIBLE
        }
        txtEmpty.visibility = View.GONE
        txtError.visibility = View.GONE
        btnRetry.visibility = View.GONE
        recyclerRequests.visibility = View.GONE
    }

    private fun showDataState(requests: List<EventRequestResponse>) {
        swipeRefresh.isRefreshing = false
        skeletonLoading.visibility = View.GONE
        txtError.visibility = View.GONE
        btnRetry.visibility = View.GONE

        if (requests.isEmpty()) {
            txtEmpty.visibility = View.VISIBLE
            recyclerRequests.visibility = View.GONE
            txtEmpty.text = "No event requests yet."
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
        recyclerRequests.visibility = View.GONE
        txtEmpty.visibility = View.GONE

        txtError.visibility = View.VISIBLE
        btnRetry.visibility = View.VISIBLE
        txtError.text = message
    }

    private fun onRequestTapped(request: EventRequestResponse) {
        startActivity(
            Intent(this, AttendeeEventRequestDetailActivity::class.java)
                .putExtra(AttendeeEventRequestDetailActivity.EXTRA_EVENT_REQUEST_ID, request.eventRequestId.toString())
        )
    }

    private class MyEventRequestsAdapter(
        private val submittedDateFormatter: DateTimeFormatter,
        private val onTap: (EventRequestResponse) -> Unit,
    ) : RecyclerView.Adapter<MyEventRequestsAdapter.RequestViewHolder>() {

        private val items = mutableListOf<EventRequestResponse>()

        fun submitItems(newItems: List<EventRequestResponse>) {
            items.clear()
            items.addAll(newItems.sortedByDescending { it.createdAt ?: Instant.EPOCH })
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
            val composeView = androidx.compose.ui.platform.ComposeView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            return RequestViewHolder(composeView)
        }

        override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
            holder.bind(items[position], onTap)
        }

        override fun getItemCount(): Int = items.size

        class RequestViewHolder(private val composeView: androidx.compose.ui.platform.ComposeView) : RecyclerView.ViewHolder(composeView) {
            fun bind(
                request: EventRequestResponse,
                onTap: (EventRequestResponse) -> Unit,
            ) {
                composeView.setContent {
                    com.thedavelopers.eventqr.ui.theme.EventQrTheme {
                        com.thedavelopers.eventqr.ui.components.EventRequestCard(
                            request = request,
                            onClick = { onTap(request) },
                        )
                    }
                }
            }
        }
    }
}
