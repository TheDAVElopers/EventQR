package com.thedavelopers.eventqr.features.staff

import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.core.util.addNearEndListener
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.idprinting.AndroidIdPrinter
import com.thedavelopers.eventqr.features.registrations.RegistrationAdapter
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse
import com.thedavelopers.eventqr.features.staff.details.StaffAttendeeDetailsActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

open class EventRegistrationsActivity : AppCompatActivity(), EventRegistrationsContract.View {
    private lateinit var presenter: EventRegistrationsPresenter
    private lateinit var repository: StaffRepository
    private lateinit var adapter: RegistrationAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var searchInput: EditText
    private lateinit var emptyState: EventQrEmptyState
    private lateinit var eventTitleView: TextView
    private lateinit var totalView: TextView
    private lateinit var checkedInView: TextView
    private lateinit var registeredView: TextView
    private lateinit var skeletonLoading: View

    private lateinit var badgeSelection: TextView
    private lateinit var btnSelectForPrint: ImageButton
    private lateinit var batchPrintBar: View
    private lateinit var btnPrintSelectedIds: Button
    private lateinit var btnSelectAll: Button

    private var selectedEventId: String = ""
    private var allRegistrations: List<RegistrationResponse> = emptyList()
    private var selectionMode = false

    // Cached data used to build each attendee's CardData for the batch preview.
    private var batchVisibleFields: List<String> = emptyList()
    private val batchQrValues = mutableMapOf<String, String>()

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
        .withZone(ZoneId.of("Asia/Manila"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (!RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.STAFF)) {
            Toast.makeText(this, this.getString(R.string.staff_dashboard_access_denied_staff_or_above), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_event_registrations)

        repository = StaffRepository(this)
        presenter = EventRegistrationsPresenter(this, repository, UiStrings(this), lifecycleScope)
        adapter = RegistrationAdapter(
            onClick = { registration ->
                startActivity(Intent(this, StaffAttendeeDetailsActivity::class.java).apply {
                    putExtra(StaffScreenExtras.EXTRA_EVENT_ID, registration.eventId.toString())
                    putExtra(StaffScreenExtras.EXTRA_ATTENDEE_ID, registration.attendeeUserId.toString())
                    putExtra(StaffScreenExtras.EXTRA_REGISTRATION_ID, registration.registrationId.toString())
                    putExtra(StaffScreenExtras.EXTRA_QR_CREDENTIAL_ID, registration.qrCredentialId?.toString().orEmpty())
                    putExtra(StaffScreenExtras.EXTRA_ATTENDEE_NAME, registration.attendeeName)
                    putExtra(StaffScreenExtras.EXTRA_ATTENDEE_EMAIL, registration.attendeeEmail)
                    putExtra(StaffScreenExtras.EXTRA_EVENT_TITLE, registration.eventTitle.orEmpty())
                })
            },
            onSelectionChanged = { count -> syncSelectionUi(count) },
        )

        bindViews()
        recycler.apply {
            layoutManager = LinearLayoutManager(this@EventRegistrationsActivity)
            adapter = this@EventRegistrationsActivity.adapter
        }

        selectedEventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID).orEmpty()
        if (selectedEventId.isNotBlank()) {
            findViewById<EditText>(R.id.edtRegistrationsEventId).setText(selectedEventId)
            presenter.load(selectedEventId)
        } else {
            lifecycleScope.launch {
                when (val eventsResult = StaffRepository(this@EventRegistrationsActivity).getEvents()) {
                    is NetworkResult.Success -> {
                        val firstEvent = eventsResult.data.firstOrNull()
                        if (firstEvent == null) {
                            showMessage(getString(R.string.event_registrations_no_assigned_events_found))
                            return@launch
                        }
                        selectedEventId = firstEvent.eventId.toString()
                        eventTitleView.text = firstEvent.title.ifBlank { getString(R.string.event_registrations_assigned_event_fallback) }
                        findViewById<EditText>(R.id.edtRegistrationsEventId).setText(selectedEventId)
                        presenter.load(selectedEventId)
                    }
                    is NetworkResult.Error -> showMessage(eventsResult.message)
                    NetworkResult.Loading -> Unit
                }
            }
        }

        findViewById<Button>(R.id.btnLoadEventRegistrations).setOnClickListener {
            selectedEventId = findViewById<EditText>(R.id.edtRegistrationsEventId).text.toString()
            presenter.load(selectedEventId)
        }
    }

    override fun onDestroy() {
        retrySnackbar?.dismiss()
        presenter.detach()
        super.onDestroy()
    }

    private var retrySnackbar: com.google.android.material.snackbar.Snackbar? = null
    private val recycler: RecyclerView by lazy { findViewById(R.id.recyclerEventRegistrations) }

    private fun bindViews() {
        swipeRefresh = findViewById(R.id.swipeRefreshEventRegistrations)
        searchInput = findViewById(R.id.edtRegistrationSearch)
        emptyState = findViewById(R.id.txtAssignedEventsEmpty)
        eventTitleView = findViewById(R.id.txtEventRegistrationsEventTitle)
        totalView = findViewById(R.id.txtAttendeeTotal)
        checkedInView = findViewById(R.id.txtAttendeeCheckedIn)
        registeredView = findViewById(R.id.txtAttendeeRegistered)
        skeletonLoading = findViewById(R.id.skeletonLoading)

        badgeSelection = findViewById(R.id.badgeSelectionCount)
        btnSelectForPrint = findViewById(R.id.btnSelectForPrint)
        batchPrintBar = findViewById(R.id.batchPrintBar)
        btnPrintSelectedIds = findViewById(R.id.btnPrintSelectedIds)
        btnSelectAll = findViewById(R.id.btnSelectAllRegistrations)

        findViewById<View>(R.id.nav_header_back).setOnClickListener {
            if (selectionMode) {
                exitSelectionMode()
            } else {
                finish()
            }
        }
        btnSelectForPrint.setOnClickListener {
            if (selectionMode) exitSelectionMode() else enterSelectionMode()
        }
        btnSelectAll.setOnClickListener {
            if (adapter.isAllSelected()) adapter.clearSelection() else presenter.selectAllForPrint()
        }
        btnPrintSelectedIds.setOnClickListener { printSelected() }

        swipeRefresh.setOnRefreshListener {
            if (selectedEventId.isNotBlank()) presenter.load(selectedEventId, searchInput.text?.toString().orEmpty()) else swipeRefresh.isRefreshing = false
        }
        recycler.addNearEndListener { presenter.loadMore() }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                presenter.onQueryChanged(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    override fun renderRegistrations(items: List<RegistrationResponse>, hasQuery: Boolean) {
        skeletonLoading.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        val title = items.firstOrNull()?.eventTitle?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_TITLE)?.takeIf { it.isNotBlank() }
        if (title != null || eventTitleView.text.isNullOrBlank()) {
            eventTitleView.text = title ?: getString(R.string.event_registrations_assigned_event_fallback)
        }
        adapter.submitItems(items)
        recycler.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        emptyState.text = getString(if (hasQuery) R.string.search_attendees_no_attendees_match_your_search else R.string.event_registrations_no_attendees_found)
        if (selectionMode) syncSelectionUi(adapter.getSelectedItems().size)
    }

    override fun renderCounts(counts: RegistrationCounts) {
        totalView.text = counts.total?.toString() ?: getString(R.string.common_value_unavailable)
        checkedInView.text = counts.checkedIn?.toString() ?: getString(R.string.common_value_unavailable)
        registeredView.text = counts.registered?.toString() ?: getString(R.string.common_value_unavailable)
    }

    override fun selectAllForPrint(items: List<RegistrationResponse>) {
        adapter.selectAll(items)
    }

    override fun showLoadMoreError(message: String) {
        swipeRefresh.isRefreshing = false
        skeletonLoading.visibility = View.GONE
        retrySnackbar?.dismiss()
        retrySnackbar = com.google.android.material.snackbar.Snackbar.make(recycler, message, com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.common_retry) { presenter.retry() }
        retrySnackbar?.show()
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading(isLoading: Boolean) {
        if (isLoading && !swipeRefresh.isRefreshing) {
            skeletonLoading.visibility = View.VISIBLE
        } else if (!isLoading) {
            skeletonLoading.visibility = View.GONE
            if (swipeRefresh.isRefreshing) {
                swipeRefresh.isRefreshing = false
            }
        }
        findViewById<View>(R.id.btnLoadEventRegistrations)?.isEnabled = !isLoading
    }

    // ------------------------------------------------------------------
    // Batch selection mode
    // ------------------------------------------------------------------

    private fun enterSelectionMode() {
        selectionMode = true
        adapter.setSelectionMode(true)
        badgeSelection.visibility = View.VISIBLE
        batchPrintBar.visibility = View.VISIBLE
        btnSelectForPrint.setBackgroundResource(R.drawable.bg_circle_indigo)
        btnSelectForPrint.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        syncSelectionUi(adapter.getSelectedItems().size)
    }

    private fun exitSelectionMode() {
        selectionMode = false
        adapter.setSelectionMode(false)
        badgeSelection.visibility = View.GONE
        batchPrintBar.visibility = View.GONE
        btnSelectForPrint.setBackgroundResource(R.drawable.bg_circle_light)
        btnSelectForPrint.imageTintList = android.content.res.ColorStateList.valueOf(0xFF111827.toInt())
        syncSelectionUi(0)
    }

    private fun syncSelectionUi(count: Int) {
        if (!::badgeSelection.isInitialized) return
        badgeSelection.text = count.toString()
        badgeSelection.visibility = if (selectionMode && count > 0) View.VISIBLE else if (selectionMode) View.VISIBLE else View.GONE
        btnPrintSelectedIds.isEnabled = count > 0
        btnSelectAll.text = getString(if (adapter.isAllSelected()) R.string.event_registrations_clear_all else R.string.event_registrations_select_all)
    }

    // ------------------------------------------------------------------
    // Batch print flow
    // ------------------------------------------------------------------

    private fun printSelected() {
        val selected = adapter.getSelectedItems().filter { it.qrCredentialId != null }
        if (selected.isEmpty()) {
            showMessage(getString(R.string.event_registrations_select_at_least_one_attendee_with_a))
            return
        }

        showLoading(true)
        lifecycleScope.launch {
            val apiService = com.thedavelopers.eventqr.core.api.ApiClient.getService(this@EventRegistrationsActivity)

            var visibleFields = emptyList<String>()
            val configResult = com.thedavelopers.eventqr.core.api.safeApiCall {
                apiService.getIdTemplateConfig(selectedEventId)
            }
            if (configResult is NetworkResult.Success) {
                visibleFields = configResult.data.visibleFields.filterNotNull()
            }

            batchVisibleFields = visibleFields
            batchQrValues.clear()
            for (reg in selected) {
                val credentialId = reg.qrCredentialId ?: continue
                val qrResult = com.thedavelopers.eventqr.core.api.safeApiCall {
                    apiService.getQrCredentialById(credentialId.toString())
                }
                if (qrResult is NetworkResult.Success) {
                    batchQrValues[reg.attendeeUserId.toString()] = qrResult.data.qrValue
                }
            }

            val cards = selected.map { cardFor(it) }
            showLoading(false)
            showBatchPrintPreview(cards, selected)
        }
    }

    private fun cardFor(reg: RegistrationResponse): AndroidIdPrinter.CardData = AndroidIdPrinter.CardData(
        attendeeName = reg.attendeeName,
        eventName = reg.eventTitle.orEmpty(),
        registrationNumber = reg.registrationNumber,
        role = reg.attendeeRole.orEmpty(),
        eventDate = reg.eventStartAt?.let { dateFormatter.format(it) }.orEmpty(),
        visibleFields = batchVisibleFields,
        qrValue = batchQrValues[reg.attendeeUserId.toString()].orEmpty(),
    )

    private fun showBatchPrintPreview(cards: List<AndroidIdPrinter.CardData>, selected: List<RegistrationResponse>) {
        val pages = AndroidIdPrinter.batchPageCount(cards.size)
        val previewWidthPx = (resources.displayMetrics.widthPixels * 0.82f).toInt()

        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        scroll.addView(container)

        container.addView(TextView(this).apply {
            text = "Print Selected IDs"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF111827.toInt())
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        })
        container.addView(TextView(this).apply {
            text = "${cards.size} attendees selected · $pages sheet${if (pages == 1) "" else "s"} (9 per page) · cut along dashed guides"
            textSize = 13f
            setTextColor(0xFF6B7280.toInt())
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, dp(2), 0, dp(8))
        }.also { it.id = View.generateViewId() })

        val previewHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        container.addView(previewHost)

        for (p in 0 until pages) {
            val pageBlock = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, 0, dp(8))
            }
            pageBlock.addView(TextView(this).apply {
                text = "Page ${p + 1} of $pages"
                textSize = 12f
                setTextColor(0xFF6B7280.toInt())
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            })
            pageBlock.addView(ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(AndroidIdPrinter.renderGridPreview(cards, previewWidthPx, p))
            })
            previewHost.addView(pageBlock)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.event_registrations_review_sheet_layout))
            .setView(scroll)
            .setPositiveButton(getString(R.string.event_registrations_confirm_print)) { _, _ -> executeBatchPrint(cards, selected) }
            .setNegativeButton(getString(R.string.request_event_cancel), null)
            .show()
    }

    private fun executeBatchPrint(cards: List<AndroidIdPrinter.CardData>, selected: List<RegistrationResponse>) {
        showLoading(true)
        lifecycleScope.launch {
            val successes = mutableListOf<AndroidIdPrinter.CardData>()
            val failures = mutableListOf<Pair<String, String>>()
            var index = 0
            for (reg in selected) {
                val card = cards[index]
                index++
                when (val result = repository.printIdBatch(selectedEventId, listOf(reg.attendeeUserId), false)) {
                    is NetworkResult.Success -> successes.add(card)
                    is NetworkResult.Error -> failures.add(reg.attendeeName to result.message)
                    NetworkResult.Loading -> Unit
                }
            }

            if (successes.isNotEmpty()) {
                AndroidIdPrinter.print(
                    this@EventRegistrationsActivity,
                    "EventQR IDs — batch (${successes.size})",
                    successes,
                )
            }

            showLoading(false)
            showBatchPrintResult(successes.size, selected.size, failures)
            exitSelectionMode()
        }
    }

    private fun showBatchPrintResult(printed: Int, total: Int, failures: List<Pair<String, String>>) {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        body.addView(TextView(this).apply {
            text = "$printed of $total IDs printed successfully."
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF111827.toInt())
        })
        if (failures.isNotEmpty()) {
            body.addView(TextView(this).apply {
                text = "The following could not be printed:"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF111827.toInt())
                setPadding(0, dp(14), 0, dp(4))
            })
            for ((name, reason) in failures) {
                body.addView(TextView(this).apply {
                    text = "• $name — ${reason.ifBlank { "unknown error" }}"
                    textSize = 13f
                    setTextColor(0xFFDC2626.toInt())
                    setPadding(dp(8), dp(2), 0, 0)
                })
            }
        } else if (printed == 0) {
            body.addView(TextView(this).apply {
                text = "No IDs were printed."
                textSize = 13f
                setTextColor(0xFFDC2626.toInt())
                setPadding(0, dp(6), 0, 0)
            })
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.event_registrations_print_result))
            .setView(body)
            .setPositiveButton(getString(R.string.event_registrations_ok), null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
