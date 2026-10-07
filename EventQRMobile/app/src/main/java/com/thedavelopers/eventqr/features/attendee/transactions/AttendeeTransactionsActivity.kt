package com.thedavelopers.eventqr.features.attendee

import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.transactions.TransactionAdapter
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse

open class AttendeeTransactionsActivity : AppCompatActivity(), TransactionHistoryContract.View {
    private lateinit var presenter: TransactionHistoryPresenter
    private lateinit var adapter: TransactionAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var emptyText: EventQrEmptyState
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var filterSpinner: Spinner
    private lateinit var summaryCountText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var spinnerArrow: ImageView
    private lateinit var cardSelectedEvent: View
    private lateinit var txtSelectedEventTitle: TextView

    private var allTransactions: List<TransactionResponse> = emptyList()
    private var eventFilterOptions: List<Pair<String?, String>> = emptyList()
    private var selectedEventId: String? = null
    private var pendingInitialEventId: String? = null
    private var eventPopup: PopupWindow? = null
    private var isEventDropdownOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_transaction_history)
        configureAttendeeBottomNav(AttendeeBottomNavItem.PROFILE)

        presenter = TransactionHistoryPresenter(this, AttendeeRepository(this))

        swipeRefresh = findViewById(R.id.swipeRefreshTransactions)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        emptyText = findViewById(R.id.txtTransactionsEmpty)
        errorText = findViewById(R.id.txtTransactionsError)
        retryButton = findViewById(R.id.btnTransactionsRetry)
        filterSpinner = findViewById(R.id.spinnerEventFilter)
        cardSelectedEvent = findViewById(R.id.cardSelectedEvent)
        txtSelectedEventTitle = findViewById(R.id.txtSelectedEventTitle)
        spinnerArrow = findViewById(R.id.spinnerArrow)
        summaryCountText = findViewById(R.id.txtHistoryTransactionCount)
        recyclerView = findViewById(R.id.recyclerTransactions)

        findViewById<View>(R.id.nav_header_back).setOnClickListener { finish() }
        retryButton.setOnClickListener { presenter.load(null) }
        swipeRefresh.setOnRefreshListener { presenter.load(null) }
        cardSelectedEvent.setOnClickListener { setEventDropdownOpen(!isEventDropdownOpen) }

        pendingInitialEventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty().ifBlank { null }

        adapter = TransactionAdapter()
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@AttendeeTransactionsActivity)
            adapter = this@AttendeeTransactionsActivity.adapter
        }

        presenter.load(null)
    }

    override fun onDestroy() {
        presenter.detach()
        eventPopup?.dismiss()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        if (!swipeRefresh.isRefreshing) {
            skeletonLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        if (isLoading) {
            retryButton.visibility = View.GONE
            errorText.visibility = View.GONE
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.GONE
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

        summaryCountText.text = "0 transactions"
        errorText.text = message.ifBlank { "Unable to load transactions." }
        errorText.visibility = View.VISIBLE
        retryButton.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }

    override fun renderTransactions(items: List<TransactionResponse>) {
        swipeRefresh.isRefreshing = false
        skeletonLoading.visibility = View.GONE
        retryButton.visibility = View.GONE
        errorText.visibility = View.GONE
        allTransactions = items
        updateFilterOptions(items)
        applySelectedFilter()
    }

    private fun updateFilterOptions(items: List<TransactionResponse>) {
        val groupedEvents = items
            .groupBy { it.eventId.toString() }
            .toSortedMap(compareBy<String> { key ->
                items.firstOrNull { it.eventId.toString() == key }?.eventTitle.orEmpty().lowercase()
            })

        val options = mutableListOf<Pair<String?, String>>()
        options += null to "All Events"
        groupedEvents.forEach { (eventId, eventItems) ->
            val title = eventItems.firstOrNull()?.eventTitle?.takeIf { it.isNotBlank() } ?: "Event"
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
            elevation = dp(8).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setBackgroundColor(if (index == filterSpinner.selectedItemPosition) Color.parseColor("#EEF2FF") else Color.WHITE)
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
                    setTextColor(if (index == filterSpinner.selectedItemPosition) 0xFF4F46E5.toInt() else 0xFF111827.toInt())
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun applySelectedFilter() {
        val filtered = if (selectedEventId.isNullOrBlank()) {
            allTransactions
        } else {
            allTransactions.filter { it.eventId.toString() == selectedEventId }
        }

        adapter.submitItems(filtered)
        emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        emptyText.text = "No transactions found for the selected event."

        summaryCountText.text = if (filtered.size == 1) "1 transaction" else "${filtered.size} transactions"
    }
}
