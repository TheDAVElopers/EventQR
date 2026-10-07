package com.thedavelopers.eventqr.features.attendee

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.session.SessionManager

open class RewardDetailsActivity : AppCompatActivity(), RewardsContract.View {
    private lateinit var presenter: RewardsPresenter
    private var eventId: String = ""
    private var rewardId: String = ""
    private var pointsRequired: Int = 0
    private var stockQuantity: Int = -1
    private var currentBalance: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_reward_details)

        presenter = RewardsPresenter(this, AttendeeRepository(this))
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        rewardId = intent.getStringExtra(EXTRA_REWARD_ID).orEmpty()
        pointsRequired = intent.getIntExtra(EXTRA_REWARD_POINTS, 0)
        stockQuantity = intent.getIntExtra(EXTRA_REWARD_STOCK, -1)

        findViewById<View>(R.id.nav_header_back)?.setOnClickListener { finish() }

        val rewardName = intent.getStringExtra(EXTRA_REWARD_NAME).orEmpty().ifBlank { "Reward" }
        findViewById<TextView>(R.id.txtRewardTitle)?.text = rewardName
        findViewById<TextView>(R.id.txtPointsValue)?.text = pointsRequired.toString()
        findViewById<TextView>(R.id.txtRewardRemaining)?.text = formatRemainingStock()
        findViewById<TextView>(R.id.txtUserPoints)?.text = "0 pts"
        updateAvailabilityUi()

        val userId = SessionManager(this).getUserId()
        if (eventId.isNotBlank() && userId != null) {
            presenter.load(eventId, userId)
        }
    }

    override fun showLoading(isLoading: Boolean) = Unit

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showBalance(balance: com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse) {
        currentBalance = balance.pointsBalance
        findViewById<TextView>(R.id.txtUserPoints)?.text = "${balance.pointsBalance} pts"
        updateAvailabilityUi()
    }

    override fun renderRewards(items: List<com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse>) = Unit

    private fun updateAvailabilityUi() {
        val isOutOfStock = stockQuantity == 0
        val status = findViewById<TextView>(R.id.txtRewardStatus)
        val warning = findViewById<TextView>(R.id.warningBox)

        if (isOutOfStock) {
            status?.text = "Out of Stock"
            status?.setBackgroundResource(R.drawable.bg_red_warning)
            status?.setTextColor(0xFFB91C1C.toInt())
            warning?.visibility = View.VISIBLE
            warning?.text = "This reward is currently out of stock."
            return
        }

        status?.text = "Available"
        status?.setBackgroundResource(R.drawable.bg_green_pill)
        status?.setTextColor(0xFF065F46.toInt())
        warning?.visibility = View.GONE
    }

    private fun formatRemainingStock(): String {
        return if (stockQuantity < 0) {
            "Stock unavailable"
        } else {
            "$stockQuantity left"
        }
    }
}
