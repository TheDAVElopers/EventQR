package com.thedavelopers.eventqr.features.staff

import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse
import com.thedavelopers.eventqr.features.transactions.TransactionLogAdapter
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.google.android.material.snackbar.Snackbar
import com.thedavelopers.eventqr.core.util.PagedAccumulator
import com.thedavelopers.eventqr.core.util.addNearEndListener
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.features.staff.model.dto.StaffTransactionSummary
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import kotlinx.coroutines.Job
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale
import com.thedavelopers.eventqr.ui.theme.color
import com.thedavelopers.eventqr.ui.theme.dimenOffset
import com.thedavelopers.eventqr.ui.theme.dimenFloat

open class StaffTransactionsActivity : AppCompatActivity(), StaffTransactionsContract.View {

    private lateinit var repository: StaffRepository
    private lateinit var adapter: TransactionLogAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var eventCard: LinearLayout
    private lateinit var eventTitleView: TextView
    private lateinit var eventChevron: ImageView
    private lateinit var purposeCard: LinearLayout
    private lateinit var purposeTitleView: TextView
    private lateinit var purposeChevron: ImageView
    private lateinit var txtTotalScans: TextView
    private lateinit var txtSuccessfulScans: TextView
    private lateinit var txtRejectedScans: TextView
    private lateinit var txtEmptyState: EventQrEmptyState
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

    // Pagination state: rows come newest-first from the server, one page at a time.
    private val paging = PagedAccumulator<TransactionResponse, java.util.UUID> { it.transactionId }
    private var purposesLoaded = false
    private var summaryJob: Job? = null
    private var retrySnackbar: Snackbar? = null

    private companion object {
        const val PAGE_SIZE = 20
        const val CHEVRON_OPEN_DEGREES = 180f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (!RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.STAFF)) {
            Toast.makeText(this, this.getString(R.string.staff_dashboard_access_denied_staff_or_above), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_transaction_logs)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()
        PassHeadBinder.bindCompact(
            findViewById(R.id.passHead),
            title = getString(R.string.ui_staff_logs),
            eyebrow = getString(R.string.ui_staff_role),
        )

        repository = StaffRepository(this)
        adapter = TransactionLogAdapter()

        eventCard = findViewById(R.id.cardStaffTransactionsEvent)
        eventTitleView = findViewById(R.id.txtStaffTransactionsEventTitle)
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
        swipeRefresh.setOnRefreshListener { refreshTransactions() }

        skeletonLoading = findViewById(R.id.skeletonLoading)

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@StaffTransactionsActivity)
            adapter = this@StaffTransactionsActivity.adapter
            addNearEndListener { loadPage() }
        }

        eventCard.visibility = View.VISIBLE
        purposeCard.visibility = View.VISIBLE

        eventCard.setOnClickListener { toggleEventPopup() }
        purposeCard.setOnClickListener { togglePurposePopup() }

        setupBottomNav()
        loadAssignedEventsAndTransactions()
    }

    private fun loadAssignedEventsAndTransactions() {
        lifecycleScope.launch {
            showLoading(true)
            when (val eventsResult = repository.getEvents()) {
                is NetworkResult.Success -> {
                    assignedEvents = eventsResult.data.filter { it.canScan || it.canViewLogs }
                    selectedEventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID)
                        ?.takeIf { extra -> assignedEvents.any { it.eventId.toString() == extra } }
                    if (assignedEvents.isEmpty()) {
                        renderTransactions(emptyList())
                        showMessage(getString(R.string.event_registrations_no_assigned_events_found))
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
        retrySnackbar?.dismiss()
        paging.reset()
        purposesLoaded = false
        setPurposeCardEnabled(true)
        loadSummary()
        loadPage()
    }

    private fun loadSummary() {
        val eventId = selectedEventId ?: return
        summaryJob?.cancel()
        renderSummary(null)
        summaryJob = lifecycleScope.launch {
            when (val result = repository.getMyTransactionSummary(eventId, selectedPurposeId)) {
                is NetworkResult.Success -> renderSummary(result.data)
                is NetworkResult.Error -> renderSummary(null)
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun showPromptState() {
        skeletonLoading.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        summaryJob?.cancel()
        retrySnackbar?.dismiss()
        paging.reset()
        renderSummary(StaffTransactionSummary())
        adapter.submitItems(emptyList())
        txtEmptyState.text = getString(R.string.ui_staff_no_logs_prompt)
        txtEmptyState.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        setPurposeCardEnabled(false)
    }

    private fun setPurposeCardEnabled(enabled: Boolean) {
        purposeCard.isClickable = enabled
        purposeCard.isEnabled = enabled
        purposeCard.alpha = if (enabled) 1f else dimenFloat(R.dimen.alpha_disabled)
    }

    private fun loadPage() {
        val eventId = selectedEventId ?: return
        val ticket = paging.begin() ?: return
        lifecycleScope.launch {
            if (ticket.page == 0) showLoading(true) else pbLoadMore.visibility = View.VISIBLE
            var current = true

            when (val result = repository.getMyTransactions(eventId, selectedPurposeId, ticket.page, PAGE_SIZE)) {
                is NetworkResult.Success -> {
                    current = paging.onSuccess(ticket, result.data)
                    if (current) {
                        if (ticket.page == 0 && !purposesLoaded) {
                            purposesLoaded = true
                            purposeOptions = buildPurposeOptionsSync(paging.items)
                            if (selectedPurposeId != null && purposeOptions.none { it.id == selectedPurposeId }) {
                                selectedPurposeId = null
                            }
                            bindPurposeHeader()
                        }
                        renderTransactions(paging.items)
                    }
                }
                is NetworkResult.Error -> {
                    current = paging.onFailure(ticket)
                    if (current) {
                        if (paging.isEmpty) renderTransactions(emptyList())
                        showLoadError(result.message)
                    }
                }
                NetworkResult.Loading -> Unit
            }

            // A stale response must not hide the loading state of the newer request that replaced it.
            if (current) {
                pbLoadMore.visibility = View.GONE
                showLoading(false)
            }
        }
    }

    private fun showLoadError(message: String) {
        swipeRefresh.isRefreshing = false
        retrySnackbar?.dismiss()
        retrySnackbar = Snackbar.make(recyclerView, message, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.common_retry) {
                paging.retry()
                loadPage()
            }
        retrySnackbar?.show()
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
        eventTitleView.text = selected?.title ?: getString(R.string.ui_staff_select_event)
        applyFilterCardState()
    }

    private fun bindPurposeHeader() {
        val selected = purposeOptions.firstOrNull { it.id == selectedPurposeId }
        purposeTitleView.text = selected?.label ?: getString(R.string.ui_staff_all_purposes)
        applyFilterCardState()
    }

    /**
     * Selected means "this filter is narrowing the list": the value moves from secondary to
     * primary and the chevron follows. No card fill, no dot, no stroke swap (§5.6).
     */
    private fun applyFilterCardState() {
        val selectedColor = getColor(R.color.ui_text_primary)
        val idleColor = getColor(R.color.ui_text_secondary)

        eventTitleView.setTextColor(if (selectedEventId != null) selectedColor else idleColor)
        ImageViewCompat.setImageTintList(eventChevron, ColorStateList.valueOf(if (selectedEventId != null) selectedColor else idleColor))

        purposeTitleView.setTextColor(if (selectedPurposeId != null) selectedColor else idleColor)
        ImageViewCompat.setImageTintList(purposeChevron, ColorStateList.valueOf(if (selectedPurposeId != null) selectedColor else idleColor))
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
            elevation = dimenOffset(R.dimen.elevation_overlay).toFloat()
            setBackgroundDrawable(ColorDrawable(color(R.color.transparent)))
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
            showMessage(getString(R.string.ui_staff_no_purposes))
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
            elevation = dimenOffset(R.dimen.elevation_overlay).toFloat()
            setBackgroundDrawable(ColorDrawable(color(R.color.transparent)))
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
        setBackgroundResource(R.drawable.bg_ui_card)
        val allSelected = selectedEventId == null
        addView(buildFilterOptionRow(getString(R.string.ui_staff_select_event), allSelected) {
            selectedEventId = null
            selectedPurposeId = null
            bindEventHeader()
            bindPurposeHeader()
            closeEventPopup()
            showPromptState()
        }.apply { if (allSelected) setBackgroundColor(color(R.color.ui_primary_wash)) })

        assignedEvents.forEach { event ->
            val isSelected = selectedEventId == event.eventId.toString()
            addView(buildFilterOptionRow(event.title, isSelected) {
                selectedEventId = event.eventId.toString()
                selectedPurposeId = null
                bindEventHeader()
                bindPurposeHeader()
                closeEventPopup()
                refreshTransactions()
            }.apply { if (isSelected) setBackgroundColor(color(R.color.ui_primary_wash)) })
        }
    }

    private fun buildPurposeDropdownView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_ui_card)
        val allSelected = selectedPurposeId == null
        addView(buildFilterOptionRow(getString(R.string.ui_staff_all_purposes), allSelected) {
            selectedPurposeId = null
            bindPurposeHeader()
            closePurposePopup()
            refreshTransactions()
        }.apply { if (allSelected) setBackgroundColor(color(R.color.ui_primary_wash)) })

        purposeOptions.forEach { purpose ->
            val isSelected = selectedPurposeId == purpose.id
            addView(buildFilterOptionRow(purpose.label, isSelected) {
                selectedPurposeId = purpose.id
                bindPurposeHeader()
                closePurposePopup()
                refreshTransactions()
            }.apply { if (isSelected) setBackgroundColor(color(R.color.ui_primary_wash)) })
        }
    }

    private fun buildFilterOptionRow(label: String, isSelected: Boolean, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dimenOffset(R.dimen.ui_touch_min)
            setPadding(dimenOffset(R.dimen.ui_gutter), dimenOffset(R.dimen.space_md), dimenOffset(R.dimen.ui_gutter), dimenOffset(R.dimen.space_md))
            if (!isSelected) setBackgroundColor(color(R.color.ui_surface))
            setOnClickListener { onClick() }
            addView(TextView(this@StaffTransactionsActivity).apply {
                text = label
                setTextColor(if (isSelected) color(R.color.ui_primary) else color(R.color.ui_text_primary))
                setTextAppearance(R.style.TextAppearance_EventQR_Type_Title)
            })
        }

    /** Chevron state is rotation, never a text glyph (§3.6). */
    private fun setChevron(view: ImageView, open: Boolean) {
        view.rotation = if (open) CHEVRON_OPEN_DEGREES else 0f
    }


    private fun setupBottomNav() {
        configureStaffBottomNav(StaffBottomNavItem.LOGS, intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID)
            ?.takeIf { extra -> assignedEvents.any { it.eventId.toString() == extra } } ?: selectedEventId)
    }

    override fun onDestroy() {
        retrySnackbar?.dismiss()
        eventPopup?.dismiss()
        purposePopup?.dismiss()
        super.onDestroy()
    }

    override fun renderTransactions(items: List<TransactionResponse>) {
        swipeRefresh.isRefreshing = false
        skeletonLoading.visibility = View.GONE
        adapter.submitItems(items)
        txtEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    /** Tiles come from the server summary, never from the loaded rows. A null summary shows "--". */
    override fun renderSummary(summary: StaffTransactionSummary?) {
        txtTotalScans.text = summary?.total?.toString() ?: getString(R.string.common_value_unavailable)
        txtSuccessfulScans.text = summary?.approved?.toString() ?: getString(R.string.common_value_unavailable)
        txtRejectedScans.text = summary?.rejected?.toString() ?: getString(R.string.common_value_unavailable)
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