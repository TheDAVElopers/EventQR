package com.thedavelopers.eventqr.features.staff

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.TransactionResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.features.transactions.TransactionLogAdapter
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

open class StaffTransactionsActivity : AppCompatActivity(), StaffTransactionsContract.View {

    private lateinit var repository: StaffRepository
    private lateinit var adapter: TransactionLogAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var eventCard: LinearLayout
    private lateinit var eventTitleView: TextView
    private lateinit var eventDateView: TextView
    private lateinit var eventChevron: TextView
    private lateinit var purposeCard: LinearLayout
    private lateinit var purposeTitleView: TextView
    private lateinit var purposeChevron: TextView
    private lateinit var txtTotalScans: TextView
    private lateinit var txtSuccessfulScans: TextView
    private lateinit var txtRejectedScans: TextView
    private lateinit var txtEmptyState: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var pbLoadMore: ProgressBar

    private var assignedEvents: List<StaffAssignedEventResponse> = emptyList()
    private var selectedEventId: String? = null
    private var selectedPurposeId: String? = null
    private var purposeOptions: List<PurposeOption> = emptyList()
    private var eventPopup: PopupWindow? = null
    private var purposePopup: PopupWindow? = null
    private var isEventPopupOpen = false
    private var isPurposePopupOpen = false

    // Pagination state
    private val PAGE_SIZE = 20
    private var currentPage = 0
    private var isLastPage = false
    private var isLoadingMore = false
    private var allItems = mutableListOf<TransactionResponse>()

    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (!RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.STAFF)) {
            Toast.makeText(this, "Access Denied: Staff or above", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_transaction_logs)

        repository = StaffRepository(this)
        adapter = TransactionLogAdapter()

        eventCard = findViewById(R.id.cardStaffTransactionsEvent)
        eventTitleView = findViewById(R.id.txtStaffTransactionsEventTitle)
        eventDateView = findViewById(R.id.txtStaffTransactionsEventDate)
        eventChevron = findViewById(R.id.txtStaffTransactionsEventChevron)
        purposeCard = findViewById(R.id.cardStaffTransactionsPurpose)
        purposeTitleView = findViewById(R.id.txtStaffTransactionsPurposeTitle)
        purposeChevron = findViewById(R.id.txtStaffTransactionsPurposeChevron)
        txtTotalScans = findViewById(R.id.txtTotalScans)
        txtSuccessfulScans = findViewById(R.id.txtSuccessfulScans)
        txtRejectedScans = findViewById(R.id.txtRejectedScans)
        txtEmptyState = findViewById(R.id.txtStaffTransactionsEmptyState)
        recyclerView = findViewById(R.id.recyclerStaffTransactions)
        pbLoadMore = findViewById(R.id.pbStaffTransactionsLoadMore)

        swipeRefresh = findViewById(R.id.swipeRefreshStaffTransactions)
        swipeRefresh.setColorSchemeResources(R.color.eventqr_purple)
        swipeRefresh.setOnRefreshListener { refreshTransactions() }

        skeletonLoading = findViewById(R.id.skeletonLoading)

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@StaffTransactionsActivity)
            adapter = this@StaffTransactionsActivity.adapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (!isLastPage && !isLoadingMore && dy > 0) {
                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        val visibleItemCount = layoutManager.childCount
                        val totalItemCount = layoutManager.itemCount
                        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5
                            && firstVisibleItemPosition >= 0
                        ) {
                            loadNextPage()
                        }
                    }
                }
            })
        }

        eventCard.visibility = View.VISIBLE
        purposeCard.visibility = View.VISIBLE

        eventCard.setOnClickListener { toggleEventPopup() }
        purposeCard.setOnClickListener { togglePurposePopup() }

        setupBottomNav()
        loadAssignedEventsAndTransactions()
    }

    private fun loadAssignedEventsAndTransactions() {
        MainScope().launch {
            showLoading(true)
            when (val eventsResult = repository.getEvents()) {
                is NetworkResult.Success -> {
                    assignedEvents = eventsResult.data.filter { it.canScan && it.status.name != "ENDED" }
                    selectedEventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID)
                        ?.takeIf { extra -> assignedEvents.any { it.eventId.toString() == extra } }
                    if (assignedEvents.isEmpty()) {
                        renderTransactions(emptyList())
                        showMessage("No assigned events found")
                        showLoading(false)
                        return@launch
                    }
                    bindEventHeader()
                    if (selectedEventId == null) {
                        showPromptState()
                    } else {
                        refreshTransactions()
                    }
                }
                is NetworkResult.Error -> {
                    renderTransactions(emptyList())
                    showMessage(eventsResult.message)
                    showLoading(false)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun refreshTransactions() {
        if (selectedEventId == null) {
            showPromptState()
            return
        }
        currentPage = 0
        isLastPage = false
        allItems.clear()
        setPurposeCardEnabled(true)
        loadPage()
    }

    private fun showPromptState() {
        skeletonLoading.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        txtTotalScans.text = "0"
        txtSuccessfulScans.text = "0"
        txtRejectedScans.text = "0"
        adapter.submitItems(emptyList())
        txtEmptyState.text = "Select an event to view its transaction logs."
        txtEmptyState.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        setPurposeCardEnabled(false)
    }

    private fun setPurposeCardEnabled(enabled: Boolean) {
        purposeCard.isClickable = enabled
        purposeCard.isEnabled = enabled
        purposeCard.alpha = if (enabled) 1f else 0.5f
    }

    private fun loadPage() {
        if (selectedEventId == null) return
        MainScope().launch {
            isLoadingMore = currentPage > 0
            if (!isLoadingMore) {
                showLoading(true)
            } else {
                pbLoadMore.visibility = View.VISIBLE
            }

            when (val result = repository.getMyTransactions(selectedEventId, selectedPurposeId, currentPage, PAGE_SIZE)) {
                is NetworkResult.Success -> {
                    val newItems = result.data
                    allItems.addAll(newItems)
                    isLastPage = newItems.size < PAGE_SIZE
                    currentPage++

                    if (currentPage == 1) {
                        purposeOptions = buildPurposeOptionsSync(allItems)
                        if (selectedPurposeId != null && purposeOptions.none { it.id == selectedPurposeId }) {
                            selectedPurposeId = null
                        }
                        bindPurposeHeader()
                    }

                    renderTransactions(allItems.sortedByDescending { it.scannedAt ?: Instant.EPOCH })
                }
                is NetworkResult.Error -> {
                    if (currentPage == 1) {
                        renderTransactions(emptyList())
                    }
                    showMessage(result.message)
                }
                NetworkResult.Loading -> Unit
            }

            isLoadingMore = false
            pbLoadMore.visibility = View.GONE
            showLoading(false)
        }
    }

    private fun loadNextPage() {
        if (!isLastPage && !isLoadingMore) {
            loadPage()
        }
    }

    private suspend fun buildPurposeOptionsSync(items: List<TransactionResponse>): List<PurposeOption> {
        selectedEventId?.let { eventId ->
            val purposeResult = repository.getScanPurposesByEvent(eventId)
            if (purposeResult is NetworkResult.Success && purposeResult.data.isNotEmpty()) {
                return purposeResult.data.map {
                    PurposeOption(
                        id = it.scanPurposeId.toString(),
                        label = it.name.takeIf { name -> name.isNotBlank() } ?: it.code.name,
                    )
                }.sortedBy { it.label.lowercase(Locale.US) }
            }
        }
        return items
            .map { tx ->
                val purposeId = tx.scanPurposeId.toString()
                val fallbackName = tx.transactionType.name
                    .lowercase(Locale.US)
                    .split('_')
                    .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase(Locale.US) } }
                PurposeOption(
                    id = purposeId,
                    label = tx.scanPurposeName?.takeIf { it.isNotBlank() } ?: fallbackName,
                )
            }
            .distinctBy { it.id }
            .sortedBy { it.label.lowercase(Locale.US) }
    }

    private fun bindEventHeader() {
        val selected = assignedEvents.firstOrNull { it.eventId.toString() == selectedEventId }
        eventTitleView.text = selected?.title ?: "Select Event"
        eventDateView.text = selected?.eventStartAt
            ?.atZone(manilaZone)
            ?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH))
            .orEmpty()
        applyFilterCardState()
    }

    private fun bindPurposeHeader() {
        val selected = purposeOptions.firstOrNull { it.id == selectedPurposeId }
        purposeTitleView.text = selected?.label ?: "All Purposes"
        applyFilterCardState()
    }

    /**
     * Swaps the filter cards between the neutral outlined state (default) and the
     * filled brand chip state (a specific option is selected). Dropdown wiring and
     * title text are untouched — only background/foreground styling is toggled.
     */
    private fun applyFilterCardState() {
        val eventSelected = selectedEventId != null
        val purposeSelected = selectedPurposeId != null

        eventCard.setBackgroundResource(
            if (eventSelected) R.drawable.bg_staff_filter_card_selected else R.drawable.bg_staff_filter_card_neutral
        )
        eventTitleView.setTextColor(getColor(if (eventSelected) R.color.surface else R.color.text_primary))
        eventDateView.setTextColor(getColor(if (eventSelected) R.color.brand_on_primary_muted else R.color.filter_label))
        eventChevron.setTextColor(getColor(if (eventSelected) R.color.brand_on_primary else R.color.text_secondary))
        findViewById<View>(R.id.viewEventDot).setBackgroundResource(
            if (eventSelected) R.drawable.bg_staff_scanner_status_dot else R.drawable.bg_staff_filter_status_dot_neutral
        )
        findViewById<TextView>(R.id.txtStaffTransactionsEventLabel)
            .setTextColor(getColor(if (eventSelected) R.color.brand_on_primary_muted else R.color.filter_label))

        purposeCard.setBackgroundResource(
            if (purposeSelected) R.drawable.bg_staff_filter_card_selected else R.drawable.bg_staff_filter_card_neutral
        )
        purposeTitleView.setTextColor(getColor(if (purposeSelected) R.color.surface else R.color.text_primary))
        purposeChevron.setTextColor(getColor(if (purposeSelected) R.color.brand_on_primary else R.color.text_secondary))
        findViewById<View>(R.id.viewPurposeDot).setBackgroundResource(
            if (purposeSelected) R.drawable.bg_staff_scanner_status_dot else R.drawable.bg_staff_filter_status_dot_neutral
        )
        findViewById<TextView>(R.id.txtStaffTransactionsPurposeLabel)
            .setTextColor(getColor(if (purposeSelected) R.color.brand_on_primary_muted else R.color.filter_label))
    }

    private fun toggleEventPopup() {
        if (isEventPopupOpen) {
            closeEventPopup()
            return
        }
        if (assignedEvents.isEmpty()) return
        closePurposePopup()
        eventPopup = PopupWindow(
            buildEventDropdownView(),
            eventCard.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener {
                isEventPopupOpen = false
                setChevron(eventChevron, false)
            }
        }
        isEventPopupOpen = true
        setChevron(eventChevron, true)
        eventPopup?.showAsDropDown(eventCard, 0, 0)
    }

    private fun closeEventPopup() {
        eventPopup?.dismiss()
        isEventPopupOpen = false
        setChevron(eventChevron, false)
    }

    private fun togglePurposePopup() {
        if (isPurposePopupOpen) {
            closePurposePopup()
            return
        }
        if (purposeOptions.isEmpty()) {
            showMessage("No scan purposes found for this filter.")
            return
        }
        closeEventPopup()
        purposePopup = PopupWindow(
            buildPurposeDropdownView(),
            purposeCard.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener {
                isPurposePopupOpen = false
                setChevron(purposeChevron, false)
            }
        }
        isPurposePopupOpen = true
        setChevron(purposeChevron, true)
        purposePopup?.showAsDropDown(purposeCard, 0, 0)
    }

    private fun closePurposePopup() {
        purposePopup?.dismiss()
        isPurposePopupOpen = false
        setChevron(purposeChevron, false)
    }

    private fun buildEventDropdownView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        val allSelected = selectedEventId == null
        addView(buildFilterOptionRow("Select Event", allSelected) {
            selectedEventId = null
            selectedPurposeId = null
            bindEventHeader()
            bindPurposeHeader()
            closeEventPopup()
            showPromptState()
        }.apply { if (allSelected) setBackgroundColor(Color.parseColor("#EEF2FF")) })

        assignedEvents.forEach { event ->
            val isSelected = selectedEventId == event.eventId.toString()
            addView(buildFilterOptionRow(event.title, isSelected) {
                selectedEventId = event.eventId.toString()
                selectedPurposeId = null
                bindEventHeader()
                bindPurposeHeader()
                closeEventPopup()
                refreshTransactions()
            }.apply { if (isSelected) setBackgroundColor(Color.parseColor("#EEF2FF")) })
        }
    }

    private fun buildPurposeDropdownView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        val allSelected = selectedPurposeId == null
        addView(buildFilterOptionRow("All Purposes", allSelected) {
            selectedPurposeId = null
            bindPurposeHeader()
            closePurposePopup()
            refreshTransactions()
        }.apply { if (allSelected) setBackgroundColor(Color.parseColor("#EEF2FF")) })

        purposeOptions.forEach { purpose ->
            val isSelected = selectedPurposeId == purpose.id
            addView(buildFilterOptionRow(purpose.label, isSelected) {
                selectedPurposeId = purpose.id
                bindPurposeHeader()
                closePurposePopup()
                refreshTransactions()
            }.apply { if (isSelected) setBackgroundColor(Color.parseColor("#EEF2FF")) })
        }
    }

    private fun buildFilterOptionRow(label: String, isSelected: Boolean, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            if (!isSelected) setBackgroundColor(Color.WHITE)
            setOnClickListener { onClick() }
            addView(TextView(this@StaffTransactionsActivity).apply {
                text = label
                setTextColor(if (isSelected) 0xFF4F46E5.toInt() else 0xFF111827.toInt())
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }

    private fun setChevron(view: TextView, open: Boolean) {
        view.includeFontPadding = false
        view.translationY = -dp(1).toFloat()
        view.text = if (open) "\u25B4" else "\u25BE"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setupBottomNav() {
        configureStaffBottomNav(StaffBottomNavItem.LOGS, selectedEventId)
    }

    override fun onDestroy() {
        eventPopup?.dismiss()
        purposePopup?.dismiss()
        super.onDestroy()
    }

    override fun renderTransactions(items: List<TransactionResponse>) {
        swipeRefresh.isRefreshing = false
        skeletonLoading.visibility = View.GONE
        adapter.submitItems(items)
        txtTotalScans.text = items.size.toString()
        txtSuccessfulScans.text = items.count { it.transactionResult == TransactionResult.APPROVED }.toString()
        txtRejectedScans.text = items.count { it.transactionResult != TransactionResult.APPROVED }.toString()
        txtEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun showMessage(message: String) {
        swipeRefresh.isRefreshing = false
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading(isLoading: Boolean) {
        if (isLoading && !swipeRefresh.isRefreshing) {
            skeletonLoading.visibility = View.VISIBLE
        }
        if (!isLoading) {
            swipeRefresh.isRefreshing = false
        }
    }

    private data class PurposeOption(val id: String, val label: String)
}