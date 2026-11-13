package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.core.util.UiStrings
import android.content.Intent
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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenOffset
import com.thedavelopers.eventqr.ui.theme.setTextSizeRes
import com.thedavelopers.eventqr.ui.theme.strong
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.RegistrationStatus
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.registrations.RegistrationsCache
import com.thedavelopers.eventqr.features.rewards.RewardAdapter
import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardResponse
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.components.asFilterChips
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import kotlinx.coroutines.launch
import java.time.Instant

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
    private lateinit var retryButton: TextView
    private lateinit var emptyEventsText: View
    private lateinit var emptyNotRegisteredText: View
    private lateinit var emptyRewardsText: EventQrEmptyState
    private lateinit var eventSpinner: Spinner
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
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()
        configureAttendeeBottomNav(AttendeeBottomNavItem.REWARDS)
        PassHeadBinder.bindCompact(
            findViewById(R.id.passHead),
            title = getString(R.string.ui_attendee_rewards),
        )
        findViewById<EventQrEmptyState>(R.id.layoutNoRegisteredEvents).apply {
            setIcon(AppIcons.Reward)
            setContent(
                getString(R.string.common_no_registered_events_yet),
                getString(R.string.user_rewards_register_for_an_event_to_start_earni),
            )
        }
        findViewById<EventQrEmptyState>(R.id.layoutNotRegisteredForEvent).apply {
            setIcon(AppIcons.Reward)
            setContent(
                getString(R.string.user_rewards_not_registered_for_this_event),
                getString(R.string.user_rewards_register_for_this_event_to_see_its_re),
            )
        }
        findViewById<EventQrEmptyState>(R.id.layoutRewardsEmpty).apply {
            setIcon(AppIcons.Reward)
            setContent(
                getString(R.string.user_rewards_no_rewards_available_yet),
                getString(R.string.user_rewards_check_back_later_for_rewards_for_thi),
            )
        }

        repository = AttendeeRepository(this)
        presenter = RewardsPresenter(this, repository, UiStrings(this))
        adapter = RewardAdapter { reward ->
            val currentEventId = selectedEventId.orEmpty()
            if (currentEventId.isBlank()) {
                // Defensive only: rows render after an event is selected, so no toast can be reached.
                return@RewardAdapter
            }

            val detailsIntent = Intent(this, RewardDetailsActivity::class.java)
                .putExtra(EXTRA_EVENT_ID, currentEventId)
                .putExtra(EXTRA_REWARD_ID, reward.rewardId.toString())
                .putExtra(EXTRA_REWARD_NAME, reward.name)
                .putExtra(EXTRA_REWARD_POINTS, reward.pointsRequired)
                .putExtra(EXTRA_REWARD_STATUS, reward.status.name)
            // A null stock is explicitly "unlimited"; an absent extra means "unknown" to the receiver.
            val stock = reward.stockQuantity
            if (stock != null) {
                detailsIntent.putExtra(EXTRA_REWARD_STOCK, stock)
            } else {
                detailsIntent.putExtra(EXTRA_REWARD_STOCK_UNLIMITED, true)
            }
            startActivity(detailsIntent)
        }

        swipeRefresh = findViewById(R.id.swipeRefreshRewards)
        errorContainer = findViewById(R.id.errorRewardsContainer)
        errorText = findViewById(R.id.txtRewardsError)
        retryButton = findViewById(R.id.btnRewardsRetry)
        emptyEventsText = findViewById(R.id.layoutNoRegisteredEvents)
        emptyNotRegisteredText = findViewById(R.id.layoutNotRegisteredForEvent)
        emptyRewardsText = findViewById(R.id.layoutRewardsEmpty)
        eventSpinner = findViewById(R.id.spinnerRegisteredEvents)
        cardSelectedEvent = findViewById(R.id.cardSelectedEvent)
        txtSelectedEventTitle = findViewById(R.id.txtSelectedEventTitle)
        selectEventLabel = findViewById(R.id.txtSelectEventLabel)
        layoutRewardsContent = findViewById(R.id.layoutRewardsContent)
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

        claimsAction.setOnClickListener { openClaimedRewards() }

        // The chip row is the section switcher (§6.2): Available (this screen), Claimed, History.
        findViewById<View>(R.id.txtRewardsHistory).setOnClickListener {
            startActivity(Intent(this, AttendeeTransactionsActivity::class.java))
        }
        val availableChip = findViewById<TextView>(R.id.chipAvailable)
        availableChip.isSelected = true
        asFilterChips(availableChip, claimsAction, findViewById(R.id.txtRewardsHistory))

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

            skeletonEventName.visibility = if (showSkeleton) View.VISIBLE else View.GONE

            balanceText.visibility = View.VISIBLE

            rewardsRecycler.visibility = View.GONE
            layoutRewardsSkeleton.visibility = if (showSkeleton) View.VISIBLE else View.GONE
        } else {
            layoutDropdownContent.visibility = View.VISIBLE
            skeletonDropdown.visibility = View.GONE

            skeletonEventName.visibility = View.GONE

            balanceText.visibility = View.VISIBLE

            layoutRewardsSkeleton.visibility = View.GONE

            swipeRefresh.isRefreshing = false
        }
    }

    override fun showMessage(message: String) {
        // No rewards read reports here: failures arrive through showError(), which renders the
        // in-flow error block. AttendeeView declares the member, so the screen keeps the override.
    }

    override fun showError(message: String) {
        swipeRefresh.isRefreshing = false
        errorContainer.visibility = View.VISIBLE
        errorText.text = message.ifBlank { getString(R.string.ui_attendee_rewards_load_failed) }
        errorText.visibility = View.VISIBLE
        retryButton.visibility = View.VISIBLE
        emptyEventsText.visibility = View.GONE
        emptyRewardsText.visibility = View.GONE

        layoutDropdownContent.visibility = View.VISIBLE
        skeletonDropdown.visibility = View.GONE

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

    override fun renderRewards(items: List<RewardResponse>, claimedRewardIds: Set<String>) {
        swipeRefresh.isRefreshing = false
        adapter.submitItems(items)
        errorContainer.visibility = View.GONE
        errorText.visibility = View.GONE
        retryButton.visibility = View.GONE
        rewardsBalanceCard.visibility = View.VISIBLE

        layoutDropdownContent.visibility = View.VISIBLE
        skeletonDropdown.visibility = View.GONE

        skeletonEventName.visibility = View.GONE

        balanceText.visibility = View.VISIBLE

        layoutRewardsSkeleton.visibility = View.GONE
        emptyRewardsText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        rewardsRecycler.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        renderRewardsEmptyCopy(allClaimed = items.isEmpty() && claimedRewardIds.isNotEmpty())
    }

    /**
     * A list emptied only by claims must not read "No rewards available yet": the rewards exist,
     * this attendee holds them. Point that state at the Claimed tab instead.
     */
    private fun renderRewardsEmptyCopy(allClaimed: Boolean) {
        if (allClaimed) {
            emptyRewardsText.setContent(
                getString(R.string.ui_attendee_rewards_all_claimed_title),
                getString(R.string.ui_attendee_rewards_all_claimed_body),
            )
            emptyRewardsText.setAction(getString(R.string.ui_attendee_claimed_rewards)) { openClaimedRewards() }
        } else {
            emptyRewardsText.setContent(
                getString(R.string.user_rewards_no_rewards_available_yet),
                getString(R.string.user_rewards_check_back_later_for_rewards_for_thi),
            )
            emptyRewardsText.setAction(null, null)
        }
    }

    private fun openClaimedRewards() {
        val intent = Intent(this, ClaimedRewardsActivity::class.java)
        selectedEventId?.takeIf { it.isNotBlank() }?.let { intent.putExtra(EXTRA_EVENT_ID, it) }
        startActivity(intent)
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
        skeletonEventName.visibility = if (showSkeleton) View.VISIBLE else View.GONE
        balanceText.visibility = View.VISIBLE
        layoutRewardsSkeleton.visibility = if (showSkeleton) View.VISIBLE else View.GONE
        layoutRewardsContent.visibility = if (cacheSaysNoRegistrations) View.GONE else View.VISIBLE
        emptyNotRegisteredText.visibility = View.GONE

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

                    val initialIndex = resolveRewardsEventIndex(
                        eventOptions.map { it.eventId },
                        preferredEventId,
                        requestedEventId = intent.getStringExtra(EXTRA_EVENT_ID),
                    )
                    if (initialIndex == null) {
                        // An explicit event was requested but the attendee has no live registration for
                        // it: never fall back to another event's rewards and balance.
                        showNotRegisteredEventState()
                        return@launch
                    }

                    eventSpinner.setSelection(initialIndex, false)
                    bindSelectedEventHeader()
                    loadSelectedEventRewards(eventOptions[initialIndex])
                }

                is NetworkResult.Error -> {
                    swipeRefresh.isRefreshing = false
                    showError(
                        registrationsResult.message.ifBlank {
                            getString(R.string.ui_attendee_registered_events_load_failed)
                        },
                    )
                }

                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun bindSelectedEventHeader() {
        val option = eventOptions.getOrNull(eventSpinner.selectedItemPosition)
        txtSelectedEventTitle.text = option?.title ?: getString(R.string.common_select_event)
    }

    private fun renderEventDropdown() {
        eventPopup?.dismiss()
        eventPopup = PopupWindow(buildEventDropdownView(), cardSelectedEvent.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = dimenOffset(R.dimen.elevation_overlay).toFloat()
            setBackgroundDrawable(ColorDrawable(color(R.color.transparent)))
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
                setPadding(
                    dimenOffset(R.dimen.space_lg), dimenOffset(R.dimen.space_14),
                    dimenOffset(R.dimen.space_lg), dimenOffset(R.dimen.space_14),
                )
                setBackgroundColor(if (index == eventSpinner.selectedItemPosition) color(R.color.accent_container) else color(R.color.surface))
                setOnClickListener {
                    eventSpinner.setSelection(index, false)
                    bindSelectedEventHeader()
                    renderEventDropdown()
                    setEventDropdownOpen(false)
                    loadSelectedEventRewards(eventOptions[index])
                }
                addView(TextView(this@AttendeeRewardsActivity).apply {
                    text = option.title
                    setTextColor(if (index == eventSpinner.selectedItemPosition) color(R.color.primary) else color(R.color.text_primary))
                    setTextSizeRes(R.dimen.type_body)
                    strong()
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

    /**
     * Explicit event id with no live registration for it: no balance, rewards or claims for that
     * event, but the event selector stays usable so the attendee can open one they are registered
     * for. Never picks another event on its own.
     */
    private fun showNotRegisteredEventState() {
        swipeRefresh.isRefreshing = false
        selectedEventId = null
        selectedEventTitle = ""
        layoutRewardsContent.visibility = View.VISIBLE
        layoutDropdownContent.visibility = View.VISIBLE
        skeletonDropdown.visibility = View.GONE
        skeletonEventName.visibility = View.GONE
        layoutRewardsSkeleton.visibility = View.GONE
        emptyEventsText.visibility = View.GONE
        emptyRewardsText.visibility = View.GONE
        rewardsRecycler.visibility = View.GONE
        rewardsBalanceCard.visibility = View.GONE
        rewardsSectionTitle.visibility = View.GONE
        claimsAction.visibility = View.GONE
        findViewById<View>(R.id.chipAvailable).visibility = View.GONE
        findViewById<View>(R.id.txtRewardsHistory).visibility = View.GONE
        txtSelectedEventTitle.text = getString(R.string.common_select_event)
        emptyNotRegisteredText.visibility = View.VISIBLE
    }

    private fun loadSelectedEventRewards(option: RegisteredEventOption) {
        selectedEventId = option.eventId
        selectedEventTitle = option.title
        skeletonEventName.visibility = View.GONE
        emptyNotRegisteredText.visibility = View.GONE
        rewardsSectionTitle.visibility = View.VISIBLE
        claimsAction.visibility = View.VISIBLE
        findViewById<View>(R.id.chipAvailable).visibility = View.VISIBLE
        findViewById<View>(R.id.txtRewardsHistory).visibility = View.VISIBLE
        presenter.load(option.eventId, attendeeUserId)
    }

}
