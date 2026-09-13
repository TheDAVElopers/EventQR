package com.thedavelopers.eventqr.features.organizer.notifications

import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.core.api.dto.NotificationType
import com.thedavelopers.eventqr.features.notifications.NotificationAdapter
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.features.organizer.OrganizerRepository
import java.util.UUID

class NotificationManagementActivity : AppCompatActivity() {

    private lateinit var viewModel: OrganizerNotificationsViewModel
    private lateinit var adapter: NotificationAdapter

    private lateinit var eventSpinner: Spinner
    private lateinit var typeSpinner: Spinner
    private val eventOptions = mutableListOf<String>()
    private val typeOptions = listOf("All types") + NotificationType.entries.map { it.name }
    private val eventIdsByLabel = mutableMapOf<String, UUID?>()

    private var eventPopup: PopupWindow? = null
    private var typePopup: PopupWindow? = null
    private var isEventOpen = false
    private var isTypeOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_organizer_notifications)

        val repo = OrganizerRepository(this)
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return OrganizerNotificationsViewModel(repo) as T
            }
        })[OrganizerNotificationsViewModel::class.java]

        setupBack()
        setupMarkAllRead(repo)
        setupFilters(repo)
        setupRecycler(repo)
        observeState()

        viewModel.load()
    }

    private fun setupBack() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupMarkAllRead(repo: OrganizerRepository) {
        findViewById<TextView>(R.id.txtMarkAllRead).setOnClickListener {
            viewModel.markAllRead { repo.markAllNotificationsRead() }
        }
    }

    private fun setupFilters(repo: OrganizerRepository) {
        val events = repo.getApprovedOrganizerEvents()
        eventOptions.clear()
        eventOptions.add("All events")
        events.forEach { event ->
            eventOptions.add(event.title.ifBlank { event.id })
            eventIdsByLabel[event.title.ifBlank { event.id }] = event.id.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        }

        eventSpinner = findViewById(R.id.spinnerEventFilter)
        eventSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, eventOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        eventSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val label = eventOptions[position]
                findViewById<TextView>(R.id.txtEventFilter).text = label
                viewModel.setEventFilter(eventIdsByLabel[label])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        typeSpinner = findViewById(R.id.spinnerTypeFilter)
        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, typeOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                findViewById<TextView>(R.id.txtTypeFilter).text = typeOptions[position]
                viewModel.setTypeFilter(if (position == 0) null else NotificationType.entries[position - 1])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<View>(R.id.cardEventFilter).setOnClickListener { toggleEventDropdown() }
        findViewById<View>(R.id.cardTypeFilter).setOnClickListener { toggleTypeDropdown() }
    }

    private fun toggleEventDropdown() {
        if (eventPopup == null || findViewById<View>(R.id.cardEventFilter).width > 0 && eventPopup?.width != findViewById<View>(R.id.cardEventFilter).width) buildEventPopup()
        if (isEventOpen) {
            eventPopup?.dismiss()
            return
        }
        isEventOpen = true
        rotateChevron(findViewById(R.id.arrowEventFilter), true)
        eventPopup?.showAsDropDown(findViewById(R.id.cardEventFilter), 0, 0)
    }

    private fun toggleTypeDropdown() {
        if (typePopup == null || findViewById<View>(R.id.cardTypeFilter).width > 0 && typePopup?.width != findViewById<View>(R.id.cardTypeFilter).width) buildTypePopup()
        if (isTypeOpen) {
            typePopup?.dismiss()
            return
        }
        isTypeOpen = true
        rotateChevron(findViewById(R.id.arrowTypeFilter), true)
        typePopup?.showAsDropDown(findViewById(R.id.cardTypeFilter), 0, 0)
    }

    private fun buildEventPopup() {
        eventPopup?.dismiss()
        eventPopup = PopupWindow(buildDropdownList(eventOptions, eventSpinner) { position ->
            eventSpinner.setSelection(position)
            rotateChevron(findViewById(R.id.arrowEventFilter), false)
            eventPopup?.dismiss()
        }, findViewById<View>(R.id.cardEventFilter).width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener { isEventOpen = false; rotateChevron(findViewById(R.id.arrowEventFilter), false) }
        }
    }

    private fun buildTypePopup() {
        typePopup?.dismiss()
        typePopup = PopupWindow(buildDropdownList(typeOptions, typeSpinner) { position ->
            typeSpinner.setSelection(position)
            rotateChevron(findViewById(R.id.arrowTypeFilter), false)
            typePopup?.dismiss()
        }, findViewById<View>(R.id.cardTypeFilter).width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener { isTypeOpen = false; rotateChevron(findViewById(R.id.arrowTypeFilter), false) }
        }
    }

    private fun buildDropdownList(
        options: List<String>,
        spinner: Spinner,
        onSelect: (Int) -> Unit,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        options.forEachIndexed { index, label ->
            addView(LinearLayout(this@NotificationManagementActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setBackgroundColor(if (index == spinner.selectedItemPosition) Color.parseColor("#EEF2FF") else Color.WHITE)
                isClickable = true
                setOnClickListener { onSelect(index) }
                addView(TextView(this@NotificationManagementActivity).apply {
                    text = label
                    setTextColor(if (index == spinner.selectedItemPosition) 0xFF4F46E5.toInt() else 0xFF111827.toInt())
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            })
        }
    }

    private fun rotateChevron(view: View, open: Boolean) {
        if (view is ImageView) view.rotation = if (open) 180f else 0f
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setupRecycler(repo: OrganizerRepository) {
        val recycler = findViewById<RecyclerView>(R.id.recyclerNotifications)
        adapter = NotificationAdapter { item ->
            if (item.status != NotificationStatus.READ) {
                viewModel.markRead(item) { target -> repo.markNotificationRead(target.notificationId.toString()) }
            }
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        findViewById<SwipeRefreshLayout>(R.id.swipeRefreshNotifications).setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun observeState() {
        viewModel.loading.observe(this) { loading ->
            findViewById<View>(R.id.skeletonLoading).visibility = if (loading) View.VISIBLE else View.GONE
            findViewById<SwipeRefreshLayout>(R.id.swipeRefreshNotifications).isRefreshing = loading
        }
        viewModel.error.observe(this) { error ->
            findViewById<View>(R.id.layoutNotificationError).visibility = if (error != null) View.VISIBLE else View.GONE
        }
        viewModel.list.observe(this) { items ->
            adapter.submitItems(items)
            findViewById<View>(R.id.layoutNotificationEmpty).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.txtMarkAllRead).isEnabled = true
        }
    }
}