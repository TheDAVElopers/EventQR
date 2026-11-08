package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.util.ScreenInsets
import com.thedavelopers.eventqr.features.rewards.ClaimedRewardAdapter
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.icons.AppIcons
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant

open class ClaimedRewardsActivity : AppCompatActivity(), ClaimedRewardsContract.View {
    private lateinit var presenter: ClaimedRewardsPresenter
    private lateinit var adapter: ClaimedRewardAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var emptyView: EventQrEmptyState
    private lateinit var recyclerView: RecyclerView
    private lateinit var errorStrip: View
    private lateinit var errorRetry: View
    private var eventId: String = ""
    private var registrationsJob: Job? = null

    /** Tests set this before the first load to replace the network repository. */
    @VisibleForTesting
    internal var repositoryOverride: AttendeeRepository? = null

    /** One repository for the presenter and the registrations load; it owns a Retrofit client. */
    private val repository: AttendeeRepository by lazy {
        repositoryOverride ?: AttendeeRepository(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_claimed_rewards)
        ScreenInsets.padContentForNavigationBar(this)

        presenter = ClaimedRewardsPresenter(this, repository)
        adapter = ClaimedRewardAdapter()

        swipeRefresh = findViewById(R.id.swipeRefreshClaimedRewards)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        emptyView = findViewById(R.id.txtClaimedRewardsEmpty)
        recyclerView = findViewById(R.id.recyclerClaimedRewards)
        errorStrip = findViewById(R.id.errorClaimedRewards)
        errorRetry = findViewById(R.id.btnClaimedRewardsRetry)

        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()

        swipeRefresh.setOnRefreshListener { loadClaimedRewards() }
        errorRetry.setOnClickListener { loadClaimedRewards() }

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
        val hasRows = adapter.itemCount > 0
        if (!swipeRefresh.isRefreshing) {
            // A refresh over readable rows keeps them on screen; the skeleton is a first-load state.
            skeletonLoading.visibility = if (isLoading && !hasRows) View.VISIBLE else View.GONE
        }
        if (isLoading) {
            errorStrip.visibility = View.GONE
            if (!hasRows) {
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.GONE
            }
        } else {
            swipeRefresh.isRefreshing = false
        }
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showError(message: String) {
        Log.w(TAG, "claims load failed: $message")
        swipeRefresh.isRefreshing = false
        skeletonLoading.visibility = View.GONE
        if (adapter.itemCount > 0) {
            // The rows are still readable: keep them and report the failed refresh in the flow,
            // under the header, with the Retry beside the sentence (§5.7 line 642).
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            errorStrip.visibility = View.VISIBLE
            // The strip's sentence never changes, so a polite live region would announce only the
            // first failure; announce explicitly so every failed refresh is heard (§9.5).
            errorStrip.announceForAccessibility(getString(R.string.ui_attendee_claims_refresh_failed))
        } else {
            showErrorState(message)
        }
    }

    override fun renderRedemptions(
        items: List<RewardRedemptionResponse>,
        eventTitle: String?,
        rewardNamesById: Map<String, String>,
        eventTitlesById: Map<String, String>,
    ) {
        swipeRefresh.isRefreshing = false
        skeletonLoading.visibility = View.GONE
        errorStrip.visibility = View.GONE

        val sorted = items.sortedByDescending { it.redeemedAt ?: Instant.EPOCH }
        adapter.submitItems(sorted, eventTitle, rewardNamesById, eventTitlesById)

        if (sorted.isEmpty()) {
            showEmpty()
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    /** The empty state and the zero-row error state share one slot, so both are bound in full. */
    private fun showEmpty() {
        errorStrip.visibility = View.GONE
        emptyView.setIcon(AppIcons.Reward)
        emptyView.setContent(
            getString(R.string.ui_attendee_no_claimed_title),
            getString(R.string.ui_attendee_no_claimed_body),
        )
        emptyView.setAction(getString(R.string.ui_attendee_browse_rewards)) { browseRewards() }
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
    }

    private fun showErrorState(message: String = "") {
        errorStrip.visibility = View.GONE
        emptyView.setIcon(AppIcons.Warning)
        emptyView.setContent(
            getString(R.string.ui_attendee_claims_error_title),
            message.ifBlank { getString(R.string.ui_attendee_claims_error_body) },
        )
        emptyView.setAction(getString(R.string.ui_attendee_retry)) { loadClaimedRewards() }
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
        // A zero-row failure replaces the list with the empty-state slot; announce it so the
        // failure is not silent when the screen reader is not already on the block (§9.5).
        emptyView.announceForAccessibility(getString(R.string.ui_attendee_claims_error_title))
    }

    private fun browseRewards() {
        // The extra preselects the event when Rewards is (re)created; an existing instance's onNewIntent may ignore it.
        val intent = Intent(this, AttendeeRewardsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (eventId.isNotBlank()) intent.putExtra(EXTRA_EVENT_ID, eventId)
        startActivity(intent)
        finish()
    }

    private fun loadClaimedRewards() {
        // A retry replaces the previous registrations load instead of racing it.
        registrationsJob?.cancel()
        if (eventId.isNotBlank()) {
            presenter.loadRedemptions(eventId)
            return
        }

        showLoading(true)
        registrationsJob = lifecycleScope.launch {
            when (val registrationsResult = repository.getMyRegistrations()) {
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
                        showEmpty()
                        return@launch
                    }

                    presenter.loadAllRedemptions(eventIds, titles)
                }

                is NetworkResult.Error -> showError(registrationsResult.message)

                NetworkResult.Loading -> Unit
            }
        }
    }

    private companion object {
        const val TAG = "ClaimedRewards"
    }
}
