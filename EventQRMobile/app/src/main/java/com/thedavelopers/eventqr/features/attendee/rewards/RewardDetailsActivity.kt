package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.util.UiStrings
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
    /** Null with [stockKnown] = true means unlimited; [stockKnown] = false means not provided (unknown). */
    private var stockQuantity: Int? = null
    private var stockKnown: Boolean = false
    private var currentBalance: Int = 0
    /** Null = unknown (status extra absent); availability then relies on stock and balance only. */
    private var rewardActive: Boolean? = null
    private var balanceLoaded: Boolean = false
    private var balanceLoading: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_reward_details)

        presenter = RewardsPresenter(this, AttendeeRepository(this), UiStrings(this))
        eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        rewardId = intent.getStringExtra(EXTRA_REWARD_ID).orEmpty()
        pointsRequired = intent.getIntExtra(EXTRA_REWARD_POINTS, 0)
        if (intent.hasExtra(EXTRA_REWARD_STOCK)) {
            stockQuantity = intent.getIntExtra(EXTRA_REWARD_STOCK, 0)
            stockKnown = true
        } else if (intent.getBooleanExtra(EXTRA_REWARD_STOCK_UNLIMITED, false)) {
            stockQuantity = null
            stockKnown = true
        }
        rewardActive = intent.getStringExtra(EXTRA_REWARD_STATUS)
            ?.let { it != com.thedavelopers.eventqr.core.api.dto.RewardStatus.INACTIVE.name }

        findViewById<View>(R.id.nav_header_back)?.setOnClickListener { finish() }

        val rewardName = intent.getStringExtra(EXTRA_REWARD_NAME).orEmpty().ifBlank { "Reward" }
        findViewById<TextView>(R.id.txtRewardTitle)?.text = rewardName
        findViewById<TextView>(R.id.txtPointsValue)?.text = pointsRequired.toString()
        renderRemainingStock()
        findViewById<TextView>(R.id.txtUserPoints)?.text = getString(R.string.common_0_pts)

        val userId = SessionManager(this).getUserId()
        val willLoadBalance = eventId.isNotBlank() && userId != null
        balanceLoading = willLoadBalance
        updateAvailabilityUi()
        if (willLoadBalance) {
            presenter.load(eventId, userId)
        }
    }

    override fun showLoading(isLoading: Boolean) {
        // The presenter ends loading on success AND failure of the balance call; on failure no balance
        // arrives, so availability falls back to status/stock only.
        if (!isLoading && balanceLoading) {
            balanceLoading = false
            updateAvailabilityUi()
        }
    }

    override fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showBalance(balance: com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse) {
        currentBalance = balance.pointsBalance
        balanceLoaded = true
        balanceLoading = false
        findViewById<TextView>(R.id.txtUserPoints)?.text = getString(R.string.common_points_short, balance.pointsBalance)
        updateAvailabilityUi()
    }

    override fun renderRewards(items: List<com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse>) {
        // The freshly loaded reward is the source of truth for status and stock.
        val reward = items.firstOrNull { it.rewardId.toString() == rewardId } ?: return
        rewardActive = reward.status != com.thedavelopers.eventqr.core.api.dto.RewardStatus.INACTIVE
        stockQuantity = reward.stockQuantity
        stockKnown = true
        renderRemainingStock()
        updateAvailabilityUi()
    }

    private fun updateAvailabilityUi() {
        val status = findViewById<TextView>(R.id.txtRewardStatus)
        val warning = findViewById<TextView>(R.id.warningBox)
        val unavailable = RewardAvailability.evaluate(
            active = rewardActive,
            stockQuantity = stockQuantity,
            pointsRequired = pointsRequired,
            balance = if (balanceLoaded) currentBalance else null,
            balanceLoading = balanceLoading,
        )

        when (unavailable) {
            RewardAvailability.State.UNAVAILABLE -> {
                status?.text = getString(R.string.reward_details_unavailable)
                status?.setBackgroundResource(R.drawable.bg_red_warning)
                status?.setTextColor(0xFFB91C1C.toInt())
                warning?.visibility = View.VISIBLE
                warning?.text = getString(R.string.reward_details_this_reward_is_currently_unavailable)
            }
            RewardAvailability.State.OUT_OF_STOCK -> {
                status?.text = getString(R.string.reward_details_out_of_stock)
                status?.setBackgroundResource(R.drawable.bg_red_warning)
                status?.setTextColor(0xFFB91C1C.toInt())
                warning?.visibility = View.VISIBLE
                warning?.text = getString(R.string.reward_details_this_reward_is_currently_out_of_stoc)
            }
            RewardAvailability.State.NEEDS_POINTS -> {
                status?.text = getString(R.string.reward_details_not_enough_points)
                status?.setBackgroundResource(R.drawable.bg_red_warning)
                status?.setTextColor(0xFFB91C1C.toInt())
                warning?.visibility = View.VISIBLE
                warning?.text = getString(R.string.user_reward_details_you_need_more_points_to_redeem_this)
            }
            RewardAvailability.State.CHECKING -> {
                status?.text = getString(R.string.reward_checking_availability)
                status?.setBackgroundResource(R.drawable.bg_green_pill)
                status?.setTextColor(0xFF065F46.toInt())
                warning?.visibility = View.GONE
            }
            RewardAvailability.State.AVAILABLE -> {
                status?.text = getString(R.string.user_reward_details_available)
                status?.setBackgroundResource(R.drawable.bg_green_pill)
                status?.setTextColor(0xFF065F46.toInt())
                warning?.visibility = View.GONE
            }
        }
    }

    private fun renderRemainingStock() {
        val view = findViewById<TextView>(R.id.txtRewardRemaining) ?: return
        val label = RewardAvailability.remainingLabel(stockKnown, stockQuantity)
        // Unknown stock: hide the whole row rather than claim "Unlimited".
        (view.parent as? View)?.visibility = if (label == null) View.GONE else View.VISIBLE
        view.text = label.orEmpty()
    }
}

/**
 * Derives reward availability from status (null = unknown, not blocking), stock (null = unlimited) and the attendee's balance.
 * While the balance is still loading the result is CHECKING (unless status/stock already decide it);
 * if it failed to load ([balance] null, not loading) only status and stock are considered.
 */
object RewardAvailability {
    enum class State { CHECKING, AVAILABLE, UNAVAILABLE, OUT_OF_STOCK, NEEDS_POINTS }

    fun evaluate(
        active: Boolean?,
        stockQuantity: Int?,
        pointsRequired: Int,
        balance: Int?,
        balanceLoading: Boolean = false,
    ): State = when {
        active == false -> State.UNAVAILABLE
        stockQuantity != null && stockQuantity <= 0 -> State.OUT_OF_STOCK
        balance != null && balance < pointsRequired -> State.NEEDS_POINTS
        balance == null && balanceLoading -> State.CHECKING
        else -> State.AVAILABLE
    }

    /** "Unlimited" only for a known null stock; null (hide the line) when stock was never provided. */
    fun remainingLabel(stockKnown: Boolean, stockQuantity: Int?): String? = when {
        !stockKnown -> null
        stockQuantity == null -> "Unlimited"
        stockQuantity <= 0 -> "Out of stock"
        else -> "$stockQuantity left"
    }
}
