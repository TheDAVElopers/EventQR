package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.rewards.ClaimedRewardAdapter
import com.thedavelopers.eventqr.features.rewards.RewardAdapter
import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse

open class AttendeeRewardsActivity : AppCompatActivity(), RewardsContract.View {
    private lateinit var presenter: RewardsPresenter
    private lateinit var adapter: RewardAdapter
    private lateinit var loadingText: TextView
    private lateinit var balanceText: TextView
    private var eventId: String = ""
    private var attendeeUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_rewards)
        configureAttendeeBottomNav(AttendeeBottomNavItem.REWARDS)

        presenter = RewardsPresenter(this, AttendeeRepository(this))
        adapter = RewardAdapter { reward ->
            startActivity(
                Intent(this, RewardDetailsActivity::class.java)
                    .putExtra(EXTRA_EVENT_ID, eventId)
                    .putExtra(EXTRA_REWARD_ID, reward.rewardId.toString())
                    .putExtra(EXTRA_REWARD_NAME, reward.name)
                    .putExtra(EXTRA_REWARD_POINTS, reward.pointsRequired)
                    .putExtra(EXTRA_REWARD_STOCK, reward.stockQuantity ?: -1)
            )
        }
        loadingText = findViewById(R.id.txtRewardsLoading)
        balanceText = findViewById(R.id.txtRewardsBalance)

        findViewById<RecyclerView>(R.id.recyclerRewards).apply {
            layoutManager = LinearLayoutManager(this@AttendeeRewardsActivity)
            adapter = this@AttendeeRewardsActivity.adapter
        }

        findViewById<View>(R.id.btnViewClaimed).setOnClickListener {
            startActivity(Intent(this, ClaimedRewardsActivity::class.java).putExtra(EXTRA_EVENT_ID, eventId))
        }

        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        attendeeUserId = SessionManager(this).getUserId()
        findViewById<Button>(R.id.btnLoadRewards).setOnClickListener {
            presenter.load(findViewById<EditText>(R.id.edtRewardsEventId).text.toString(), attendeeUserId)
        }

        if (eventId.isNotBlank()) {
            findViewById<EditText>(R.id.edtRewardsEventId).setText(eventId)
            presenter.load(eventId, attendeeUserId)
        } else {
            balanceText.text = "Choose an event to see reward balance and available rewards."
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        loadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showBalance(balance: PointBalanceResponse) {
        balanceText.text = balance.pointsBalance.toString()
    }

    override fun renderRewards(items: List<RewardResponse>) {
        adapter.submitItems(items)
        if (items.isEmpty()) {
            findViewById<TextView>(R.id.txtRewardsEmpty).visibility = View.VISIBLE
        }
    }
}
