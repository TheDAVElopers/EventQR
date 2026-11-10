package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.util.UiStrings
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.session.SessionManager

open class RewardDetailsActivity : AppCompatActivity(), RewardsContract.View {
    /** Tests set this before the first load to replace the network repository. */
    internal var repositoryOverride: AttendeeRepository? = null

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
    /** True when this attendee already redeemed this reward and duplicates are not allowed. */
    private var alreadyClaimed: Boolean = false
    /**
     * False until the claim read succeeds. A failed claim read leaves this false, so availability
     * stays CHECKING and never promises a claim the backend may refuse.
     */
    private var claimStateKnown: Boolean = false
    /** A read that carries the claim state failed: the badge shows a terminal error, never CHECKING. */
    private var claimStateFailed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_reward_details)

        presenter = RewardsPresenter(this, repositoryOverride ?: AttendeeRepository(this), UiStrings(this))
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
            // The reward itself may be sold out or inactive; only the unfiltered read can say so.
            presenter.load(eventId, userId, includeUnavailable = true)
        }
    }

    override fun onClaimStateCheckFailed() {
        claimStateFailed = true
        updateAvailabilityUi()
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

    override fun renderRewards(items: List<com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse>, claimedRewardIds: Set<String>) {
        // Reaching this callback means the claim read succeeded: only now may the screen say
        // Available.
        claimStateKnown = true
        // The freshly loaded reward is the source of truth for status and stock; a reward missing
        // from the claimable list but present in the claim set was already redeemed.
        val reward = items.firstOrNull { it.rewardId.toString() == rewardId }
        if (reward == null) {
            if (rewardId in claimedRewardIds) {
                alreadyClaimed = true
                updateAvailabilityUi()
            } else {
                // The unfiltered read succeeded and still does not hold this reward: it is gone or
                // unknown. Never keep the intent's stale AVAILABLE badge and stock.
                rewardActive = false
                stockKnown = false
                renderRemainingStock()
                updateAvailabilityUi()
            }
            return
        }
        alreadyClaimed = false
        rewardActive = reward.status != com.thedavelopers.eventqr.core.api.dto.RewardStatus.INACTIVE
        stockQuantity = reward.stockQuantity
        stockKnown = true
        renderRemainingStock()
        updateAvailabilityUi()
    }

    private fun updateAvailabilityUi() {
        val status = findViewById<TextView>(R.id.txtRewardStatus)
        val warning = findViewById<TextView>(R.id.warningBox)
        val state = RewardAvailability.evaluate(
            active = rewardActive,
            stockQuantity = stockQuantity,
            pointsRequired = pointsRequired,
            balance = if (balanceLoaded) currentBalance else null,
            balanceLoading = balanceLoading,
            alreadyClaimed = alreadyClaimed,
            claimStateKnown = claimStateKnown,
            claimStateFailed = claimStateFailed,
        )

        val blocked = state == RewardAvailability.State.UNAVAILABLE ||
            state == RewardAvailability.State.OUT_OF_STOCK ||
            state == RewardAvailability.State.NEEDS_POINTS ||
            state == RewardAvailability.State.CLAIM_ERROR
        val statusRes = when (state) {
            RewardAvailability.State.UNAVAILABLE -> R.string.reward_details_unavailable
            RewardAvailability.State.OUT_OF_STOCK -> R.string.reward_details_out_of_stock
            RewardAvailability.State.NEEDS_POINTS -> R.string.reward_details_not_enough_points
            RewardAvailability.State.CHECKING -> R.string.reward_checking_availability
            RewardAvailability.State.CLAIM_ERROR -> R.string.ui_attendee_claim_state_unknown
            RewardAvailability.State.CLAIMED -> R.string.ui_attendee_claimed
            RewardAvailability.State.AVAILABLE -> R.string.user_reward_details_available
        }
        status?.text = getString(statusRes)
        status?.let {
            val container = when {
                state == RewardAvailability.State.CLAIMED -> R.color.ui_surface_sunken
                blocked -> R.color.ui_error_container
                else -> R.color.ui_info_container
            }
            val content = when {
                state == RewardAvailability.State.CLAIMED -> R.color.ui_text_secondary
                blocked -> R.color.ui_on_error_container
                else -> R.color.ui_on_info_container
            }
            ViewCompat.setBackgroundTintList(it, ContextCompat.getColorStateList(this, container))
            it.setTextColor(ContextCompat.getColor(this, content))
            // The tone is the state; the badge never grows an icon (§5.4).
            it.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        }

        val warningRes = when (state) {
            RewardAvailability.State.UNAVAILABLE -> R.string.reward_details_this_reward_is_currently_unavailable
            RewardAvailability.State.OUT_OF_STOCK -> R.string.reward_details_this_reward_is_currently_out_of_stoc
            RewardAvailability.State.NEEDS_POINTS -> R.string.user_reward_details_you_need_more_points_to_redeem_this
            RewardAvailability.State.CLAIMED -> R.string.ui_attendee_reward_already_claimed
            RewardAvailability.State.CLAIM_ERROR -> R.string.ui_attendee_claim_state_unknown_body
            else -> null
        }
        if (warningRes != null) {
            warning?.text = getString(warningRes)
            warning?.visibility = View.VISIBLE
        } else {
            warning?.visibility = View.GONE
        }

        val hintVisible = state != RewardAvailability.State.UNAVAILABLE &&
            state != RewardAvailability.State.OUT_OF_STOCK &&
            state != RewardAvailability.State.CLAIMED
        findViewById<View>(R.id.cardHowToRedeem)?.visibility = if (hintVisible) View.VISIBLE else View.GONE

        renderAffordability(state)
    }

    private fun renderAffordability(state: RewardAvailability.State) {
        val bar = findViewById<ProgressBar>(R.id.progressAffordability) ?: return
        val progressText = findViewById<TextView>(R.id.txtPointsProgress) ?: return
        val gapText = findViewById<TextView>(R.id.txtPointsGap) ?: return
        val card = findViewById<View>(R.id.cardProgress)

        val checking = !balanceLoaded && balanceLoading
        if (!balanceLoaded) {
            // Checking: empty bar only. Balance failed to load: show only the points header.
            bar.progress = 0
            bar.visibility = if (checking) View.VISIBLE else View.GONE
            progressText.visibility = View.GONE
            gapText.visibility = View.GONE
            card?.contentDescription = null
            return
        }

        val ok = state == RewardAvailability.State.AVAILABLE
        bar.progressDrawable = ContextCompat.getDrawable(
            this,
            if (ok) R.drawable.pb_reward_affordability_ok else R.drawable.pb_reward_affordability,
        )
        val free = pointsRequired <= 0
        bar.progress = if (free) 100 else minOf(100, (currentBalance.toLong() * 100 / pointsRequired).toInt())
        bar.visibility = if (free) View.GONE else View.VISIBLE

        val progress = if (free) null else getString(R.string.reward_details_progress_format, currentBalance, pointsRequired)
        progressText.visibility = if (progress == null) View.GONE else View.VISIBLE
        progressText.text = progress.orEmpty()

        val gap: String? = when {
            state == RewardAvailability.State.UNAVAILABLE ||
                state == RewardAvailability.State.OUT_OF_STOCK ||
                state == RewardAvailability.State.CLAIMED -> null
            free -> getString(R.string.reward_details_no_points_needed)
            currentBalance < pointsRequired ->
                getString(R.string.reward_details_points_to_go, pointsRequired - currentBalance)
            else -> getString(R.string.reward_details_enough_points)
        }
        gapText.visibility = if (gap == null) View.GONE else View.VISIBLE
        gapText.text = gap.orEmpty()
        gapText.setTextColor(
            ContextCompat.getColor(
                this,
                if (!free && currentBalance < pointsRequired) R.color.ui_error else R.color.ui_success,
            ),
        )

        card?.contentDescription = listOfNotNull(
            getString(R.string.user_reward_details_your_points) + " " +
                getString(R.string.common_points_short, currentBalance),
            progress,
            gap,
        ).joinToString(". ")
    }

    private fun renderRemainingStock() {
        val view = findViewById<TextView>(R.id.txtRewardRemaining) ?: return
        val label = RewardAvailability.remainingLabel(stockKnown, stockQuantity)
        // Unknown stock: hide the whole wrapper (divider + row) rather than claim "Unlimited".
        (view.parent?.parent as? View)?.visibility = if (label == null) View.GONE else View.VISIBLE
        view.text = label.orEmpty()
    }
}


/**
 * Derives reward availability from status (null = unknown, not blocking), stock (null = unlimited),
 * the attendee's balance and whether they already claimed it. While the balance is still loading the
 * result is CHECKING (unless status/stock/claim already decide it); if it failed to load ([balance]
 * null, not loading) only status, stock and claim are considered. [claimStateKnown] = false with
 * [claimStateFailed] = false is the claim read in flight (CHECKING); with [claimStateFailed] = true
 * the read failed and the state is the terminal CLAIM_ERROR, never AVAILABLE.
 */
object RewardAvailability {
    enum class State { CHECKING, AVAILABLE, UNAVAILABLE, OUT_OF_STOCK, NEEDS_POINTS, CLAIMED, CLAIM_ERROR }

    fun evaluate(
        active: Boolean?,
        stockQuantity: Int?,
        pointsRequired: Int,
        balance: Int?,
        balanceLoading: Boolean = false,
        alreadyClaimed: Boolean = false,
        claimStateKnown: Boolean = true,
        claimStateFailed: Boolean = false,
    ): State = when {
        active == false -> State.UNAVAILABLE
        alreadyClaimed -> State.CLAIMED
        stockQuantity != null && stockQuantity <= 0 -> State.OUT_OF_STOCK
        !claimStateKnown && claimStateFailed -> State.CLAIM_ERROR
        !claimStateKnown -> State.CHECKING
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
