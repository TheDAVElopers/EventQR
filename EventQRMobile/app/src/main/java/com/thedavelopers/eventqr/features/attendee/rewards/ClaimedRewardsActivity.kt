package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import kotlinx.coroutines.launch
import java.time.Instant

open class ClaimedRewardsActivity : AppCompatActivity(), ClaimedRewardsContract.View {
    private lateinit var presenter: ClaimedRewardsPresenter
    private lateinit var adapter: com.thedavelopers.eventqr.features.rewards.ClaimedRewardAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var emptyView: EventQrEmptyState
    private lateinit var errorView: TextView
    private lateinit var retryButton: Button
    private lateinit var recyclerView: RecyclerView
    private var eventId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_claimed_rewards)

        presenter = ClaimedRewardsPresenter(this, AttendeeRepository(this))
        adapter = com.thedavelopers.eventqr.features.rewards.ClaimedRewardAdapter()

        swipeRefresh = findViewById(R.id.swipeRefreshClaimedRewards)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        emptyView = findViewById(R.id.txtClaimedRewardsEmpty)
        errorView = findViewById(R.id.txtClaimedRewardsError)
        retryButton = findViewById(R.id.btnClaimedRewardsRetry)
        recyclerView = findViewById(R.id.recyclerClaimedRewards)

        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()

        findViewById<View>(R.id.nav_header_back)?.setOnClickListener { finish() }
        retryButton.setOnClickListener { loadClaimedRewards() }
        swipeRefresh.setOnRefreshListener { loadClaimedRewards() }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ClaimedRewardsActivity)
            adapter = this@ClaimedRewardsActivity.adapter
        }

        loadClaimedRewards()
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        if (!swipeRefresh.isRefreshing) {
            skeletonLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        if (isLoading) {
            emptyView.visibility = View.GONE
            errorView.visibility = View.GONE
            retryButton.visibility = View.GONE
            recyclerView.visibility = View.GONE
        } else {
            swipeRefresh.isRefreshing = false
        }
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showError(message: String) {
        swipeRefresh.isRefreshing = false
        skeletonLoading.visibility = View.GONE

        errorView.text = message.ifBlank { "Unable to load claimed rewards." }
        errorView.visibility = View.VISIBLE
        retryButton.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }

    override fun renderRedemptions(
        items: List<RewardRedemptionResponse>,
        eventTitle: String?,
        rewardNamesById: Map<String, String>,
        eventTitlesById: Map<String, String>,
    ) {
        swipeRefresh.isRefreshing = false
        skeletonLoading.visibility = View.GONE
        errorView.visibility = View.GONE
        retryButton.visibility = View.GONE

        val sorted = items.sortedByDescending { it.redeemedAt ?: Instant.EPOCH }
        adapter.submitItems(sorted, eventTitle, rewardNamesById, eventTitlesById)

        val isEmpty = sorted.isEmpty()
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun loadClaimedRewards() {
        if (eventId.isNotBlank()) {
            presenter.loadRedemptions(eventId)
            return
        }

        showLoading(true)
        lifecycleScope.launch {
            when (val registrationsResult = AttendeeRepository(this@ClaimedRewardsActivity).getMyRegistrations()) {
                is NetworkResult.Success -> {
                    // "My Claims" without an event: gather claims across every registration the user has,
                    // including cancelled ones, since a claim made earlier is still the user's history.
                    val registrations = registrationsResult.data
                    val eventIds = registrations.map { it.eventId.toString() }.distinct()
                    val titles = registrations
                        .mapNotNull { reg -> reg.eventTitle?.takeIf { it.isNotBlank() }?.let { reg.eventId.toString() to it } }
                        .toMap()
                    if (eventIds.isEmpty()) {
                        swipeRefresh.isRefreshing = false
                        skeletonLoading.visibility = View.GONE
                        emptyView.text = getString(R.string.claimed_rewards_no_claimed_rewards_yet)
                        emptyView.visibility = View.VISIBLE
                        retryButton.visibility = View.GONE
                        recyclerView.visibility = View.GONE
                        return@launch
                    }

                    presenter.loadAllRedemptions(eventIds, titles)
                }

                is NetworkResult.Error -> {
                    showError(registrationsResult.message.ifBlank { "Unable to load claimed rewards." })
                }

                NetworkResult.Loading -> Unit
            }
        }
    }
}
