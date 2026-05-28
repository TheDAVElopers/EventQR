package com.thedavelopers.eventqr.features.attendee

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R

open class ClaimedRewardsActivity : AppCompatActivity(), ClaimedRewardsContract.View {
    private lateinit var presenter: ClaimedRewardsPresenter
    private lateinit var adapter: com.thedavelopers.eventqr.features.rewards.ClaimedRewardAdapter
    private var eventId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_claimed_rewards)

        presenter = ClaimedRewardsPresenter(this, AttendeeRepository(this))
        adapter = com.thedavelopers.eventqr.features.rewards.ClaimedRewardAdapter()

        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<RecyclerView>(R.id.recyclerClaimedRewards)?.apply {
            layoutManager = LinearLayoutManager(this@ClaimedRewardsActivity)
            adapter = this@ClaimedRewardsActivity.adapter
        }

        if (eventId.isNotBlank()) {
            presenter.loadRedemptions(eventId)
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) = Unit
    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun renderRedemptions(items: List<com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse>) {
        adapter.submitItems(items)
        findViewById<TextView>(R.id.txtTotalRewardsValue)?.text = items.size.toString()
        findViewById<TextView>(R.id.txtPointsUsedValue)?.text = items.sumOf { it.pointsSpent }.toString()
    }
}
