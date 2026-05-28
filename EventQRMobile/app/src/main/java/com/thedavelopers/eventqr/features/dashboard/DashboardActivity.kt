package com.thedavelopers.eventqr.features.dashboard

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.DateFormatters
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.attendee.AttendeeBottomNavItem
import com.thedavelopers.eventqr.features.attendee.AttendeeRepository
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_CAPACITY
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_CATEGORY
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_COUNT
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_DESCRIPTION
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_END
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_ID
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_LOCATION
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_START
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_STATUS
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_TITLE
import com.thedavelopers.eventqr.features.attendee.configureAttendeeBottomNav
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse

open class DashboardActivity : AppCompatActivity(), DashboardContract.View {
    private lateinit var presenter: DashboardPresenter
    private lateinit var sessionManager: SessionManager
    private lateinit var welcomeText: TextView
    private lateinit var nameText: TextView
    private lateinit var summaryEvents: TextView
    private lateinit var summaryRegistrations: TextView
    private lateinit var summaryTransactions: TextView
    private lateinit var summaryRewards: TextView
    private lateinit var summaryNotifications: TextView
    private lateinit var loadingText: TextView
    private lateinit var attendeeCard: Button
    private lateinit var staffCard: Button
    private lateinit var organizerCard: Button
    private lateinit var notificationsCard: Button
    private lateinit var rewardsCard: Button
    private lateinit var reportsCard: Button
    private lateinit var logoutCard: Button
    private lateinit var notificationBell: ImageView
    private lateinit var upcomingEventsLayout: LinearLayout
    private lateinit var recentActivityLayout: LinearLayout
    private lateinit var upcomingEventsViewAll: TextView
    private lateinit var transactionHistoryViewAll: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_dashboard)
        configureAttendeeBottomNav(AttendeeBottomNavItem.DASHBOARD)

        sessionManager = SessionManager(this)
        presenter = DashboardPresenter(
            this,
            DashboardRepository(this),
            AttendeeRepository(this),
            sessionManager
        )
        presenter.attach(this)

        welcomeText = findViewById(R.id.txtDashboardWelcome)
        nameText = findViewById(R.id.txtDashboardName)
        summaryEvents = findViewById(R.id.txtTotalEvents)
        summaryRegistrations = findViewById(R.id.txtTotalRegistrations)
        summaryTransactions = findViewById(R.id.txtTotalTransactions)
        summaryRewards = findViewById(R.id.txtTotalRewards)
        summaryNotifications = findViewById(R.id.txtTotalNotifications)
        loadingText = findViewById(R.id.txtDashboardLoading)
        attendeeCard = findViewById(R.id.btnAttendeeHub)
        staffCard = findViewById(R.id.btnStaffHub)
        organizerCard = findViewById(R.id.btnTransactionHistory)
        notificationsCard = findViewById(R.id.btnNotificationsHub)
        rewardsCard = findViewById(R.id.btnRewardsHub)
        reportsCard = findViewById(R.id.btnReportsHub)
        logoutCard = findViewById(R.id.btnLogout)
        notificationBell = findViewById(R.id.btnDashboardNotifications)
        upcomingEventsLayout = findViewById(R.id.layoutUpcomingEvents)
        recentActivityLayout = findViewById(R.id.layoutRecentActivity)
        upcomingEventsViewAll = findViewById(R.id.txtUpcomingEventsViewAll)
        transactionHistoryViewAll = findViewById(R.id.txtTransactionHistoryViewAll)
        notificationBell.setOnClickListener {
            startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeNotificationsActivity::class.java))
        }
        upcomingEventsViewAll.setOnClickListener {
            startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeEventsActivity::class.java))
        }
        transactionHistoryViewAll.setOnClickListener {
            startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeTransactionsActivity::class.java))
        }

        configureActions(sessionManager.getUserRole())

        presenter.loadDashboard()
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        loadingText.text = "Loading dashboard..."
        loadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun showSummary(summary: com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardSummary) {
        loadingText.visibility = View.GONE
        nameText.text = summary.fullName?.takeIf { it.isNotBlank() }
            ?: sessionManager.getFullName()?.takeIf { it.isNotBlank() }
            ?: "Attendee"
        summaryEvents.text = summary.totalEvents.toString()
        summaryRegistrations.text = summary.totalRegistrations.toString()
        summaryTransactions.text = summary.totalTransactions.toString()
        summaryRewards.text = summary.totalRewards.toString()
        summaryNotifications.text = summary.totalNotifications.toString()
        renderUpcomingEvents(summary.upcomingEvents.orEmpty())
    }

    override fun showTransactionHistoryLoading(isLoading: Boolean) {
        renderRecentActivityState(if (isLoading) "Loading transactions..." else null, 0xFF6B7280.toInt())
    }

    override fun showTransactionHistory(items: List<TransactionResponse>) {
        while (recentActivityLayout.childCount > 1) {
            recentActivityLayout.removeViewAt(1)
        }

        if (items.isEmpty()) {
            renderRecentActivityState("No transactions yet.", 0xFF6B7280.toInt())
            return
        }

        items.take(3).forEach { item ->
            recentActivityLayout.addView(createTransactionPreviewRow(item))
        }
    }

    override fun showTransactionHistoryError(message: String) {
        renderRecentActivityState(message.ifBlank { "Unable to load transaction history." }, 0xFFB91C1C.toInt())
    }

    override fun showError(message: String) {
        loadingText.text = message
        loadingText.visibility = View.VISIBLE
        renderUpcomingEvents(emptyList())
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun openSection(title: String, message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun updateHeader(role: String?, name: String?) {
        welcomeText.text = "Welcome back!"
        nameText.text = name?.takeIf { it.isNotBlank() } ?: "Attendee"
    }

    private fun renderUpcomingEvents(events: List<com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardUpcomingEvent>?) {
        while (upcomingEventsLayout.childCount > 1) {
            upcomingEventsLayout.removeViewAt(1)
        }
        if (events.isNullOrEmpty()) {
            upcomingEventsLayout.addView(createUpcomingEmptyState())
            return
        }
        events.forEachIndexed { index, event ->
            upcomingEventsLayout.addView(createUpcomingEventRow(event, index == 0))
        }
    }

    private fun createUpcomingEventRow(event: com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardUpcomingEvent, isFirst: Boolean): View {
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(78),
            ).apply {
                topMargin = if (isFirst) dp(23) else dp(10)
            }
            setBackgroundResource(R.drawable.bg_soft_gray_pill)
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(11), 0, dp(11), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val intent = Intent(this@DashboardActivity, com.thedavelopers.eventqr.features.attendee.EventDetailActivity::class.java).apply {
                    putExtra(EXTRA_EVENT_ID, event.eventId.toString())
                    putExtra(EXTRA_EVENT_TITLE, event.title)
                    putExtra(EXTRA_EVENT_LOCATION, event.location ?: "")
                    putExtra(EXTRA_EVENT_DESCRIPTION, event.description ?: "")
                    putExtra(EXTRA_EVENT_CATEGORY, event.category ?: "")
                    putExtra(EXTRA_EVENT_START, DateFormatters.formatInstant(event.eventStartAt))
                    putExtra(EXTRA_EVENT_END, DateFormatters.formatInstant(event.eventEndAt))
                    putExtra(EXTRA_EVENT_STATUS, event.status ?: "Upcoming")
                    putExtra(EXTRA_EVENT_CAPACITY, event.capacity.toString())
                    putExtra(EXTRA_EVENT_COUNT, event.currentAttendeeCount.toString())
                }
                startActivity(intent)
            }
        }

        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(78), dp(56))
            setBackgroundResource(R.drawable.bg_white_pill_button)
            gravity = Gravity.CENTER
            text = "▣"
            setTextColor(0xFF111111.toInt())
            textSize = 22f
        })

        val textColumn = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(15)
            }
            orientation = LinearLayout.VERTICAL
        }
        textColumn.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            text = event.title.ifBlank { "Untitled event" }
            setTextColor(0xFF000000.toInt())
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        textColumn.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            }
            text = DateFormatters.formatInstant(event.eventStartAt)
            setTextColor(0xFF6B7280.toInt())
            textSize = 11f
        })
        textColumn.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            }
            text = event.location?.takeIf { it.isNotBlank() } ?: "Upcoming"
            setTextColor(0xFF000000.toInt())
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
        })
        row.addView(textColumn)
        return row
    }

    private fun createUpcomingEmptyState(): View {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(23)
            }
            gravity = Gravity.CENTER
            text = "No upcoming events yet."
            setTextColor(0xFF6B7280.toInt())
            textSize = 13f
            setPadding(dp(12), dp(20), dp(12), dp(20))
        }
    }

    private fun renderRecentActivity(activities: List<Any>?) {
        while (recentActivityLayout.childCount > 1) {
            recentActivityLayout.removeViewAt(1)
        }
        if (activities.isNullOrEmpty()) {
            recentActivityLayout.addView(createRecentActivityEmptyState())
            return
        }
    }

    private fun renderRecentActivityState(text: String?, textColor: Int) {
        while (recentActivityLayout.childCount > 1) {
            recentActivityLayout.removeViewAt(1)
        }
        if (text.isNullOrBlank()) {
            return
        }

        recentActivityLayout.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(18)
            }
            gravity = Gravity.CENTER
            setTextColor(textColor)
            textSize = 13f
            setPadding(dp(12), dp(18), dp(12), dp(18))
            this.text = text
        })
    }

    private fun createTransactionPreviewRow(item: TransactionResponse): View {
        val row = LayoutInflater.from(this).inflate(R.layout.item_transaction, recentActivityLayout, false)
        val titleView = row.findViewById<TextView>(R.id.txtTransactionTitle)
        val eventView = row.findViewById<TextView>(R.id.txtTransactionEvent)
        val timeView = row.findViewById<TextView>(R.id.txtTransactionTime)
        val pointsView = row.findViewById<TextView>(R.id.txtTransactionPoints)
        val tagView = row.findViewById<TextView>(R.id.txtTransactionTag)
        val iconLayout = row.findViewById<View>(R.id.layoutTransactionIcon)
        val trendIcon = row.findViewById<ImageView>(R.id.imgTransactionTrend)

        val isEarned = item.pointsDelta >= 0
        val isApproved = item.transactionResult.name == "APPROVED"

        titleView.text = item.reason ?: (if (isEarned) "Points Earned" else "Points Redeemed")
        eventView.text = item.eventTitle ?: "Attendee transaction"
        timeView.text = DateFormatters.formatInstant(item.scannedAt)

        val deltaPrefix = if (isEarned) "+" else ""
        pointsView.text = "$deltaPrefix${item.pointsDelta}"
        pointsView.setTextColor(Color.parseColor(if (isEarned) "#10B981" else "#EF4444"))

        tagView.text = if (isApproved) "Success" else "Failed"
        tagView.setBackgroundResource(if (isApproved) R.drawable.bg_green_pill else R.drawable.bg_red_warning)
        tagView.setTextColor(Color.parseColor(if (isApproved) "#059669" else "#DC2626"))

        iconLayout.setBackgroundResource(if (isEarned) R.drawable.bg_transaction_earned_icon else R.drawable.bg_transaction_redeemed_icon)
        trendIcon.setImageResource(if (isEarned) R.drawable.ic_trend_up else R.drawable.ic_trend_down)
        return row
    }

    private fun createRecentActivityEmptyState(): View {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(23)
            }
            gravity = Gravity.CENTER
            text = "No recent activity yet."
            setTextColor(0xFF6B7280.toInt())
            textSize = 13f
            setPadding(dp(12), dp(20), dp(12), dp(20))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun configureActions(role: String?) {
        val normalizedRole = RoleMapper.normalizeRole(role)
        when (normalizedRole) {
            AccountRole.STAFF.name -> {
                attendeeCard.text = "Scanner"
                staffCard.text = "Transactions"
                organizerCard.text = "ID Printing"
                notificationsCard.text = "Event Registrations"
                rewardsCard.text = "Notifications"
                reportsCard.visibility = View.GONE
                logoutCard.visibility = View.VISIBLE

                attendeeCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity::class.java)) }
                staffCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.staff.StaffTransactionsActivity::class.java)) }
                organizerCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.staff.IdPrintingActivity::class.java)) }
                notificationsCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.staff.EventRegistrationsActivity::class.java)) }
                rewardsCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeNotificationsActivity::class.java)) }
                logoutCard.setOnClickListener { performLogout() }
            }
            AccountRole.ORGANIZER.name, AccountRole.ADMIN.name, AccountRole.SUPER_ADMIN.name -> {
                attendeeCard.text = "Manage Events"
                staffCard.text = "Manage Users"
                organizerCard.text = "Scan Purposes"
                notificationsCard.text = "Rewards"
                rewardsCard.text = "Reports"
                reportsCard.text = "Notifications"
                reportsCard.visibility = View.VISIBLE
                logoutCard.visibility = View.VISIBLE

                attendeeCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.organizer.ManageEventsActivity::class.java)) }
                staffCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.organizer.ManageUsersActivity::class.java)) }
                organizerCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.organizer.ManageScanPurposesActivity::class.java)) }
                notificationsCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.organizer.ManageRewardsActivity::class.java)) }
                rewardsCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.organizer.ReportsActivity::class.java)) }
                reportsCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.organizer.NotificationManagementActivity::class.java)) }
                logoutCard.setOnClickListener { performLogout() }
            }
            else -> {
                attendeeCard.text = "Browse Events"
                staffCard.text = "My Registered Events"
                organizerCard.text = "Transaction History"
                notificationsCard.text = "Request Event"
                
                rewardsCard.visibility = View.GONE
                reportsCard.visibility = View.GONE
                logoutCard.visibility = View.GONE

                attendeeCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeEventsActivity::class.java)) }
                staffCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.RegisteredEventsActivity::class.java)) }
                organizerCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeTransactionsActivity::class.java)) }
                notificationsCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.RequestEventActivity::class.java)) }
            }
        }
    }

    private fun configureStandaloneAction(button: Button, label: String, onClick: () -> Unit) {
        button.visibility = View.VISIBLE
        button.text = label
        button.isAllCaps = false
        button.setTextColor(0xFF000000.toInt())
        button.setBackgroundResource(R.drawable.bg_quick_action_button)
        button.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(84),
        ).apply {
            topMargin = dp(16)
        }
        button.setOnClickListener { onClick() }
    }

    private fun performLogout() {
        sessionManager.clearSession()
        startActivity(Intent(this, com.thedavelopers.eventqr.features.landing.LandingActivity::class.java))
        finish()
    }
}