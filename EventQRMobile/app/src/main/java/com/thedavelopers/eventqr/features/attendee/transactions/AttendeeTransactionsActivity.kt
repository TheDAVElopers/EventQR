package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.icons.AppIcons
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenOffset
import com.thedavelopers.eventqr.ui.theme.setTextSizeRes
import com.thedavelopers.eventqr.ui.theme.strong
import com.thedavelopers.eventqr.features.transactions.TransactionHistoryAdapter
import com.thedavelopers.eventqr.features.transactions.TransactionHistoryRow

open class AttendeeTransactionsActivity : AppCompatActivity(), TransactionHistoryContract.View {
    /** Tests set this before the first load to replace the network repository. */
    internal var repositoryOverride: AttendeeRepository? = null

    private lateinit var presenter: TransactionHistoryPresenter
    private val repository: AttendeeRepository by lazy {
        repositoryOverride ?: AttendeeRepository(this)
    }
    private lateinit var adapter: TransactionHistoryAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var emptyText: EventQrEmptyState
    private lateinit var errorStrip: View
    private lateinit var retryButton: TextView
    private lateinit var filterSpinner: Spinner
    private lateinit var summaryCountText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var spinnerArrow: ImageView
    private lateinit var cardSelectedEvent: View
    private lateinit var txtSelectedEventTitle: TextView

    private var allTransactions: List<TransactionHistoryRow> = emptyList()
    private var eventFilterOptions: List<Pair<String?, String>> = emptyList()
    private var selectedEventId: String? = null
    private var pendingInitialEventId: String? = null
    private var eventPopup: PopupWindow? = null
    private var isEventDropdownOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_transaction_history)
        configureAttendeeBottomNav(AttendeeBottomNavItem.PROFILE)

        presenter = TransactionHistoryPresenter(this, repository)

        swipeRefresh = findViewById(R.id.swipeRefreshTransactions)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        emptyText = findViewById(R.id.txtTransactionsEmpty)
        errorStrip = findViewById(R.id.errorTransactions)
        retryButton = findViewById(R.id.btnTransactionsRetry)
        filterSpinner = findViewById(R.id.spinnerEventFilter)
        cardSelectedEvent = findViewById(R.id.cardSelectedEvent)
        txtSelectedEventTitle = findViewById(R.id.txtSelectedEventTitle)
        spinnerArrow = findViewById(R.id.spinnerArrow)
        summaryCountText = findViewById(R.id.txtHistoryTransactionCount)
        recyclerView = findViewById(R.id.recyclerTransactions)

        findViewById<View>(R.id.nav_header_back).setOnClickListener { finish() }
        retryButton.setOnClickListener { presenter.load() }
        swipeRefresh.setOnRefreshListener { presenter.load() }
        cardSelectedEvent.setOnClickListener { setEventDropdownOpen(!isEventDropdownOpen) }

        pendingInitialEventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty().ifBlank { null }

        emptyText.setIcon(AppIcons.Logs)

        adapter = TransactionHistoryAdapter()
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@AttendeeTransactionsActivity)
            adapter = this@AttendeeTransactionsActivity.adapter
        }

        presenter.load()
    }

    override fun onDestroy() {
        presenter.detach()
        eventPopup?.dismiss()
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
                emptyText.visibility = View.GONE
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
        swipeRefresh.isRefreshing = false
        skeletonLoading.visibility = View.GONE

        if (adapter.itemCount > 0) {
            // The rows are still readable: keep them and report the failed refresh in the flow,
            // under the filter, with Retry beside the sentence (§5.7 line 642).
            errorStrip.visibility = View.VISIBLE
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        } else {
            showErrorState(message)
        }
    }

    /** A zero-row failure replaces the list with the empty-state slot. */
    private fun showErrorState(message: String) {
        errorStrip.visibility = View.GONE
        summaryCountText.text = getString(R.string.user_transaction_history_0_transactions)
        emptyText.setIcon(AppIcons.Warning)
        emptyText.setContent(
            getString(R.string.user_transaction_history_unable_to_load_transactions),
            message.ifBlank { getString(R.string.ui_attendee_load_error_body) },
        )
        emptyText.setAction(getString(R.string.ui_attendee_retry)) { presenter.load() }
        recyclerView.visibility = View.GONE
        emptyText.visibility = View.VISIBLE
        emptyText.announceForAccessibility(
            getString(R.string.user_transaction_history_unable_to_load_transactions),
        )
    }

    override fun renderTransactions(items: List<TransactionHistoryRow>) {
        swipeRefresh.isRefreshing = false
        skeletonLoading.visibility = View.GONE
        errorStrip.visibility = View.GONE
        allTransactions = items
        updateFilterOptions(items)
        applySelectedFilter()
    }

    private fun updateFilterOptions(items: List<TransactionHistoryRow>) {
        val groupedEvents = items
            .groupBy { it.eventId }
            .entries
            .sortedBy { (_, rows) ->
                rows.firstNotNullOfOrNull { it.eventTitle?.takeIf(String::isNotBlank) }.orEmpty().lowercase()
            }

        val options = mutableListOf<Pair<String?, String>>()
        options += null to "All Events"
        groupedEvents.forEach { (eventId, eventItems) ->
            val title = eventItems.firstNotNullOfOrNull { it.eventTitle?.takeIf(String::isNotBlank) } ?: "Event"
            options += eventId to title
        }
        eventFilterOptions = options

        val labels = options.map { it.second }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        filterSpinner.adapter = spinnerAdapter

        val initialEventId = pendingInitialEventId
        val initialIndex = if (initialEventId == null) 0 else options.indexOfFirst { it.first == initialEventId }.coerceAtLeast(0)
        selectedEventId = options[initialIndex].first
        pendingInitialEventId = null
        filterSpinner.setSelection(initialIndex, false)
        bindSelectedEventHeader()

        filterSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                bindSelectedEventHeader()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun bindSelectedEventHeader() {
        val option = eventFilterOptions.getOrNull(filterSpinner.selectedItemPosition)
        txtSelectedEventTitle.text = option?.second ?: "All Events"
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
        eventFilterOptions.forEachIndexed { index, option ->
            addView(LinearLayout(this@AttendeeTransactionsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    dimenOffset(R.dimen.space_lg), dimenOffset(R.dimen.space_14),
                    dimenOffset(R.dimen.space_lg), dimenOffset(R.dimen.space_14),
                )
                setBackgroundColor(if (index == filterSpinner.selectedItemPosition) color(R.color.accent_container) else color(R.color.surface))
                setOnClickListener {
                    filterSpinner.setSelection(index, false)
                    bindSelectedEventHeader()
                    renderEventDropdown()
                    setEventDropdownOpen(false)
                    selectedEventId = option.first
                    applySelectedFilter()
                }
                addView(TextView(this@AttendeeTransactionsActivity).apply {
                    text = option.second
                    setTextColor(if (index == filterSpinner.selectedItemPosition) color(R.color.primary) else color(R.color.text_primary))
                    setTextSizeRes(R.dimen.type_body)
                    strong()
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            })
        }
    }

    private fun setEventDropdownOpen(open: Boolean) {
        if (open && eventFilterOptions.isEmpty()) return
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


    private fun applySelectedFilter() {
        val filtered = if (selectedEventId.isNullOrBlank()) {
            allTransactions
        } else {
            allTransactions.filter { it.eventId == selectedEventId }
        }

        adapter.submitItems(filtered)
        emptyText.setIcon(AppIcons.Logs)
        emptyText.setAction(null)
        emptyText.setContent(
            getString(R.string.attendee_transactions_no_transactions_found_for_the_select),
            getString(R.string.ui_attendee_no_transactions_body),
        )
        emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE

        summaryCountText.text = if (filtered.size == 1) "1 transaction" else "${filtered.size} transactions"
    }
}
