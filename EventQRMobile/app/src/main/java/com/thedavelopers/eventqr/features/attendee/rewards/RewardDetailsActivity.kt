package com.thedavelopers.eventqr.features.attendee

import android.os.Bundle
import android.view.View
import android.widget.Button
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_reward_details)

        presenter = RewardsPresenter(this, AttendeeRepository(this))
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        rewardId = intent.getStringExtra(EXTRA_REWARD_ID).orEmpty()
        pointsRequired = intent.getIntExtra(EXTRA_REWARD_POINTS, 0)

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<TextView>(R.id.txtRewardTitle)?.text = intent.getStringExtra(EXTRA_REWARD_NAME)
        findViewById<TextView>(R.id.txtPointsValue)?.text = pointsRequired.toString()

        val userId = SessionManager(this).getUserId()
        if (eventId.isNotBlank() && userId != null) {
            presenter.load(eventId, userId)
        }

        findViewById<Button>(R.id.btnRedeemReward)?.setOnClickListener {
            presenter.redeem(eventId, userId, rewardId)
        }
    }

    override fun showLoading(isLoading: Boolean) = Unit
    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showBalance(balance: com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse) {
        if (balance.pointsBalance < pointsRequired) {
            findViewById<View>(R.id.warningBox)?.visibility = View.VISIBLE
            findViewById<TextView>(R.id.txtWarningMessage)?.text =
                "You need ${pointsRequired - balance.pointsBalance} more points to redeem this reward. Current balance: ${balance.pointsBalance} points."
            findViewById<Button>(R.id.btnRedeemReward)?.isEnabled = false
            findViewById<Button>(R.id.btnRedeemReward)?.alpha = 0.5f
        } else {
            findViewById<View>(R.id.warningBox)?.visibility = View.GONE
            findViewById<Button>(R.id.btnRedeemReward)?.isEnabled = true
            findViewById<Button>(R.id.btnRedeemReward)?.alpha = 1.0f
        }
    }

    override fun renderRewards(items: List<com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse>) = Unit
}
