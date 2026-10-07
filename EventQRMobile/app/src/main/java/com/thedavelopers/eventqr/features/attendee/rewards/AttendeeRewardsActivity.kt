package com.thedavelopers.eventqr.features.attendee

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.rewards.RewardAdapter
import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

open class AttendeeRewardsActivity : AppCompatActivity(), RewardsContract.View {
    private data class RegisteredEventOption(
        val eventId: String,
        val title: String,
    )

    private lateinit var presenter: RewardsPresenter
    private lateinit var repository: AttendeeRepository
    private lateinit var adapter: RewardAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var errorContainer: View
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var emptyEventsText: View
    private lateinit var emptyRewardsText: View
    private lateinit var eventSpinner: Spinner
    private lateinit var eventTitleText: TextView
    private lateinit var balanceText: TextView
    private lateinit var rewardsSectionTitle: TextView
    private lateinit var claimsAction: TextView
    private lateinit var rewardsRecycler: RecyclerView
    private lateinit var rewardsBalanceCard: View
    private lateinit var spinnerArrow: ImageView
    private lateinit var cardSelectedEvent: View
    private lateinit var txtSelectedEventTitle: TextView
    private lateinit var selectEventLabel: View
    private lateinit var layoutRewardsContent: View

    private lateinit var layoutDropdownContent: View
    private lateinit var skeletonDropdown: View
    private lateinit var skeletonEventName: View
    private lateinit var layoutRewardsSkeleton: View

    private val eventOptions = mutableListOf<RegisteredEventOption>()
    private var selectedEventId: String? = null
    private var selectedEventTitle: String = ""
    private var attendeeUserId: String? = null
    private var eventPopup: PopupWindow? = null
    private var isEventDropdownOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_rewards)
        configureAttendeeBottomNav(AttendeeBottomNavItem.REWARDS)

        repository = AttendeeRepository(this)
        presenter = RewardsPresenter(this, repository)
        adapter = RewardAdapter { reward ->
            val currentEventId = selectedEventId.orEmpty()
            if (currentEventId.isBlank()) {
                Toast.makeText(this, "Select a registered event first.", Toast.LENGTH_SHORT).show()
                return@RewardAdapter
            }

            startActivity(
                Intent(this, RewardDetailsActivity::class.java)
                    .putExtra(EXTRA_EVENT_ID, currentEventId)
                    .putExtra(EXTRA_REWARD_ID, reward.rewardId.toString())
                    .putExtra(EXTRA_REWARD_NAME, reward.name)
                    .putExtra(EXTRA_REWARD_POINTS, reward.pointsRequired)
                    .putExtra(EXTRA_REWARD_STOCK, reward.stockQuantity ?: -1)
            )
        }

        swipeRefresh = findViewById(R.id.swipeRefreshRewards)
        errorContainer = findViewById(R.id.errorRewardsContainer)
        errorText = findViewById(R.id.txtRewardsError)
        retryButton = findViewById(R.id.btnRewardsRetry)
        emptyEventsText = findViewById(R.id.layoutNoRegisteredEvents)
        emptyRewardsText = findViewById(R.id.layoutRewardsEmpty)
        eventSpinner = findViewById(R.id.spinnerRegisteredEvents)
        cardSelectedEvent = findViewById(R.id.cardSelectedEvent)
        txtSelectedEventTitle = findViewById(R.id.txtSelectedEventTitle)
        selectEventLabel = findViewById(R.id.txtSelectEventLabel)
        layoutRewardsContent = findViewById(R.id.layoutRewardsContent)
        eventTitleText = findViewById(R.id.txtRewardsEventTitle)
        balanceText = findViewById(R.id.txtRewardsBalance)
        rewardsSectionTitle = findViewById(R.id.txtRewardsSectionTitle)
        claimsAction = findViewById(R.id.txtMyClaims)
        rewardsRecycler = findViewById(R.id.recyclerRewards)
        rewardsBalanceCard = findViewById(R.id.cardRewardsBalance)
        spinnerArrow = findViewById(R.id.spinnerArrow)

        layoutDropdownContent = findViewById(R.id.layoutDropdownContent)
        skeletonDropdown = findViewById(R.id.skeletonDropdown)
        skeletonEventName = findViewById(R.id.skeletonEventName)
        layoutRewardsSkeleton = findViewById(R.id.layoutRewardsSkeleton)

        rewardsRecycler.apply {
            layoutManager = LinearLayoutManager(this@AttendeeRewardsActivity)
            adapter = this@AttendeeRewardsActivity.adapter
        }

        claimsAction.setOnClickListener {
            val intent = Intent(this, ClaimedRewardsActivity::class.java)
            selectedEventId?.takeIf { it.isNotBlank() }?.let { intent.putExtra(EXTRA_EVENT_ID, it) }
            startActivity(intent)
        }

        retryButton.setOnClickListener { refreshRewardsPreservingSelection() }
        swipeRefresh.setOnRefreshListener { refreshRewardsPreservingSelection() }

        cardSelectedEvent.setOnClickListener { setEventDropdownOpen(!isEventDropdownOpen) }

        attendeeUserId = SessionManager(this).getUserId()
        loadRegisteredEvents()
    }

    override fun onDestroy() {
        presenter.detach()
        eventPopup?.dismiss()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        val showSkeleton = isLoading && !swipeRefresh.isRefreshing
        if (isLoading) {
            errorContainer.visibility = View.GONE
            errorText.visibility = View.GONE
            retryButton.visibility = View.GONE
            emptyRewardsText.visibility = View.GONE

            layoutDropdownContent.visibility = if (showSkeleton) View.GONE else View.VISIBLE
            skeletonDropdown.visibility = if (showSkeleton) View.VISIBLE else View.GONE

            eventTitleText.visibility = if (showSkeleton) View.GONE else View.VISIBLE
            skeletonEventName.visibility = if (showSkeleton) View.VISIBLE else View.GONE

            balanceText.visibility = View.VISIBLE

            rewardsRecycler.visibility = View.GONE
            layoutRewardsSkeleton.visibility = if (showSkeleton) View.VISIBLE else View.GONE
        } else {
            layoutDropdownContent.visibility = View.VISIBLE
            skeletonDropdown.visibility = View.GONE

            eventTitleText.visibility = View.VISIBLE
            skeletonEventName.visibility = View.GONE

            balanceText.visibility = View.VISIBLE

            layoutRewardsSkeleton.visibility = View.GONE

            swipeRefresh.isRefreshing = false
        }
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showError(message: String) {
        swipeRefresh.isRefreshing = false
        errorContainer.visibility = View.VISIBLE
        errorText.text = message.ifBlank { "Unable to load rewards." }
        errorText.visibility = View.VISIBLE
        retryButton.visibility = View.VISIBLE
        emptyEventsText.visibility = View.GONE
        emptyRewardsText.visibility = View.GONE

        layoutDropdownContent.visibility = View.VISIBLE
        skeletonDropdown.visibility = View.GONE

        eventTitleText.visibility = View.VISIBLE
        skeletonEventName.visibility = View.GONE

        balanceText.visibility = View.VISIBLE

        layoutRewardsSkeleton.visibility = View.GONE
        rewardsRecycler.visibility = View.GONE
        rewardsBalanceCard.visibility = View.GONE
    }

    override fun showBalance(balance: PointBalanceResponse) {
        rewardsBalanceCard.visibility = View.VISIBLE
        balanceText.text = balance.pointsBalance.toString()
    }

    override fun renderRewards(items: List<RewardResponse>) {
        swipeRefresh.isRefreshing = false
        adapter.submitItems(items)
        errorContainer.visibility = View.GONE
        errorText.visibility = View.GONE
        retryButton.visibility = View.GONE
        rewardsBalanceCard.visibility = View.VISIBLE

        layoutDropdownContent.visibility = View.VISIBLE
        skeletonDropdown.visibility = View.GONE

        eventTitleText.visibility = View.VISIBLE
        skeletonEventName.visibility = View.GONE

        balanceText.visibility = View.VISIBLE

        layoutRewardsSkeleton.visibility = View.GONE
        emptyRewardsText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        rewardsRecycler.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun refreshRewardsPreservingSelection() {
        val previousEventId = selectedEventId
        if (previousEventId.isNullOrBlank()) {
            loadRegisteredEvents()
            return
        }
        loadRegisteredEvents(preferredEventId = previousEventId)
    }

    private fun loadRegisteredEvents(preferredEventId: String? = selectedEventId ?: intent.getStringExtra(EXTRA_EVENT_ID).orEmpty().ifBlank { null }) {
        errorContainer.visibility = View.GONE
        errorText.visibility = View.GONE
        retryButton.visibility = View.GONE
        emptyEventsText.visibility = View.GONE
        emptyRewardsText.visibility = View.GONE
        rewardsRecycler.visibility = View.GONE
        rewardsBalanceCard.visibility = View.VISIBLE
        claimsAction.visibility = View.GONE

        // If we already know from cache that the user has no registered events,
        // skip the skeleton loading and immediately show the empty state.
        val cacheSaysNoRegistrations = RegistrationsCache.get()?.none {
            it.status != RegistrationStatus.CANCELLED &&
                it.status != RegistrationStatus.NO_SHOW &&
                it.eventId.toString().isNotBlank()
        } == true

        val showSkeleton = !swipeRefresh.isRefreshing && !cacheSaysNoRegistrations
        layoutDropdownContent.visibility = if (showSkeleton) View.GONE else View.VISIBLE
        skeletonDropdown.visibility = if (showSkeleton) View.VISIBLE else View.GONE
        eventTitleText.visibility = if (showSkeleton) View.GONE else View.VISIBLE
        skeletonEventName.visibility = if (showSkeleton) View.VISIBLE else View.GONE
        balanceText.visibility = View.VISIBLE
        layoutRewardsSkeleton.visibility = if (showSkeleton) View.VISIBLE else View.GONE
        layoutRewardsContent.visibility = if (cacheSaysNoRegistrations) View.GONE else View.VISIBLE

        if (cacheSaysNoRegistrations) {
            swipeRefresh.isRefreshing = false
            selectedEventId = null
            selectedEventTitle = ""
            emptyEventsText.visibility = View.VISIBLE
            claimsAction.visibility = View.GONE
        }

        lifecycleScope.launch {
            when (val registrationsResult = repository.getMyRegistrations()) {
                is NetworkResult.Success -> {
                    val options = registrationsResult.data
                        .filter { it.status != RegistrationStatus.CANCELLED && it.status != RegistrationStatus.NO_SHOW }
                        .sortedByDescending { it.registeredAt ?: Instant.EPOCH }
                        .distinctBy { it.eventId }
                        .mapNotNull { registration ->
                            val eventId = registration.eventId.toString().takeIf { it.isNotBlank() }
                            val title = registration.eventTitle?.takeIf { it.isNotBlank() }
                                ?: registration.eventId.toString()
                            if (eventId == null) null else RegisteredEventOption(eventId, title)
                        }

                    eventOptions.clear()
                    eventOptions.addAll(options)

                    if (eventOptions.isEmpty()) {
                        swipeRefresh.isRefreshing = false
                        selectedEventId = null
                        selectedEventTitle = ""
                        emptyEventsText.visibility = View.VISIBLE
                        layoutRewardsContent.visibility = View.GONE
                        claimsAction.visibility = View.GONE
                        return@launch
                    }

                    layoutRewardsContent.visibility = View.VISIBLE
                    claimsAction.visibility = View.VISIBLE
                    emptyEventsText.visibility = View.GONE

                    val spinnerAdapter = ArrayAdapter(
                        this@AttendeeRewardsActivity,
                        android.R.layout.simple_spinner_item,
                        eventOptions.map { it.title },
                    ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                    eventSpinner.adapter = spinnerAdapter

                    val initialIndex = eventOptions.indexOfFirst { it.eventId == preferredEventId }
                        .takeIf { it >= 0 }
                        ?: 0

                    eventSpinner.setSelection(initialIndex, false)
                    bindSelectedEventHeader()
                    loadSelectedEventRewards(eventOptions[initialIndex])
                }

                is NetworkResult.Error -> {
                    swipeRefresh.isRefreshing = false
                    showError(registrationsResult.message.ifBlank { "Unable to load registered events." })
                }

                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun bindSelectedEventHeader() {
        val option = eventOptions.getOrNull(eventSpinner.selectedItemPosition)
        txtSelectedEventTitle.text = option?.title ?: "Select event"
    }

    private fun renderEventDropdown() {
        eventPopup?.dismiss()
        eventPopup = PopupWindow(buildEventDropdownView(), cardSelectedEvent.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener { isEventDropdownOpen = false; normalizeChevron(spinnerArrow, false) }
        }
    }

    private fun buildEventDropdownView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        eventOptions.forEachIndexed { index, option ->
            addView(LinearLayout(this@AttendeeRewardsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setBackgroundColor(if (index == eventSpinner.selectedItemPosition) Color.parseColor("#EEF2FF") else Color.WHITE)
                setOnClickListener {
                    eventSpinner.setSelection(index, false)
                    bindSelectedEventHeader()
                    renderEventDropdown()
                    setEventDropdownOpen(false)
                    loadSelectedEventRewards(eventOptions[index])
                }
                addView(TextView(this@AttendeeRewardsActivity).apply {
                    text = option.title
                    setTextColor(if (index == eventSpinner.selectedItemPosition) 0xFF4F46E5.toInt() else 0xFF111827.toInt())
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            })
        }
    }

    private fun setEventDropdownOpen(open: Boolean) {
        if (open && eventOptions.isEmpty()) return
        if (open) {
            if (eventPopup == null || cardSelectedEvent.width > 0 && eventPopup?.width != cardSelectedEvent.width) renderEventDropdown()
            isEventDropdownOpen = true
            normalizeChevron(spinnerArrow, true)
            eventPopup?.showAsDropDown(cardSelectedEvent, 0, 0)
        } else {
            eventPopup?.dismiss()
            isEventDropdownOpen = false
            normalizeChevron(spinnerArrow, false)
        }
    }

    private fun normalizeChevron(view: View, open: Boolean) {
        if (view is ImageView) {
            view.rotation = if (open) 180f else 0f
        }
    }

    private fun loadSelectedEventRewards(option: RegisteredEventOption) {
        selectedEventId = option.eventId
        selectedEventTitle = option.title
        eventTitleText.text = option.title.uppercase(Locale.getDefault())
        eventTitleText.visibility = View.VISIBLE
        skeletonEventName.visibility = View.GONE
        presenter.load(option.eventId, attendeeUserId)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
