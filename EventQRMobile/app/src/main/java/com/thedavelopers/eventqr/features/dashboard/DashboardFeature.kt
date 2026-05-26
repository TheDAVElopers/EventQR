package com.thedavelopers.eventqr.features.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.attendee.AttendeeBottomNavItem
import com.thedavelopers.eventqr.features.attendee.configureAttendeeBottomNav
import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

interface DashboardContract {
    interface View {
        fun showLoading(isLoading: Boolean)
        fun showSummary(summary: DashboardSummary)
        fun showError(message: String)
        fun showMessage(message: String)
        fun openSection(title: String, message: String)
        fun updateHeader(role: String?, name: String?)
    }
}

class DashboardRepository(private val context: android.content.Context) {
    private val apiService = com.thedavelopers.eventqr.core.api.ApiClient.getService(context)

    suspend fun getSummary(): NetworkResult<DashboardSummary> {
        return runCatching {
            apiService.getDashboard()
        }.fold(
            onSuccess = { response ->
                if (response.success && response.data != null) {
                    NetworkResult.Success(response.data, response.message)
                } else {
                    NetworkResult.Error(response.message ?: "Unable to load dashboard")
                }
            },
            onFailure = { throwable ->
                NetworkResult.Error(throwable.message ?: "Unable to load dashboard", throwable)
            }
        )
    }
}

class DashboardPresenter(
    private var view: DashboardContract.View?,
    private val repository: DashboardRepository,
    private val sessionManager: SessionManager,
) {
    private var dashboardJob: Job? = null

    fun attach(view: DashboardContract.View) {
        this.view = view
    }

    fun detach() {
        dashboardJob?.cancel()
        view = null
    }

    fun loadDashboard() {
        view?.updateHeader(sessionManager.getUserRole(), sessionManager.getFullName())
        view?.showLoading(true)
        dashboardJob = kotlinx.coroutines.MainScope().launch {
            when (val result = repository.getSummary()) {
                is NetworkResult.Success -> {
                    view?.showLoading(false)
                    view?.showSummary(result.data)
                }
                is NetworkResult.Error -> {
                    view?.showLoading(false)
                    view?.showError(result.message)
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun openSection(title: String, message: String) {
        view?.openSection(title, message)
    }

    fun logout() {
        sessionManager.clearSession()
    }
}

open class DashboardActivity : AppCompatActivity(), DashboardContract.View {
    private lateinit var presenter: DashboardPresenter
    private lateinit var sessionManager: SessionManager
    private lateinit var welcomeText: TextView
    private lateinit var roleText: TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_dashboard)
        configureAttendeeBottomNav(AttendeeBottomNavItem.DASHBOARD)

        sessionManager = SessionManager(this)
        presenter = DashboardPresenter(this, DashboardRepository(this), sessionManager)
        presenter.attach(this)

        welcomeText = findViewById(R.id.txtDashboardWelcome)
        roleText = findViewById(R.id.txtDashboardRole)
        summaryEvents = findViewById(R.id.txtTotalEvents)
        summaryRegistrations = findViewById(R.id.txtTotalRegistrations)
        summaryTransactions = findViewById(R.id.txtTotalTransactions)
        summaryRewards = findViewById(R.id.txtTotalRewards)
        summaryNotifications = findViewById(R.id.txtTotalNotifications)
        loadingText = findViewById(R.id.txtDashboardLoading)
        attendeeCard = findViewById(R.id.btnAttendeeHub)
        staffCard = findViewById(R.id.btnStaffHub)
        organizerCard = findViewById(R.id.btnOrganizerHub)
        notificationsCard = findViewById(R.id.btnNotificationsHub)
        rewardsCard = findViewById(R.id.btnRewardsHub)
        reportsCard = findViewById(R.id.btnReportsHub)
        logoutCard = findViewById(R.id.btnLogout)
        notificationBell = findViewById(R.id.btnDashboardNotifications)
        notificationBell.setOnClickListener {
            startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeNotificationsActivity::class.java))
        }

        configureActions(sessionManager.getUserRole())

        presenter.loadDashboard()
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        loadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun showSummary(summary: DashboardSummary) {
        summaryEvents.text = summary.totalEvents.toString()
        summaryRegistrations.text = summary.totalRegistrations.toString()
        summaryTransactions.text = summary.totalTransactions.toString()
        summaryRewards.text = summary.totalRewards.toString()
        summaryNotifications.text = summary.totalNotifications.toString()
    }

    override fun showError(message: String) {
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
        roleText.text = name?.takeIf { it.isNotBlank() } ?: "Dharell Dave"
    }

    private fun configureActions(role: String?) {
        val normalizedRole = role?.uppercase().orEmpty()
        when (normalizedRole) {
            "STAFF" -> {
                attendeeCard.text = "Scanner"
                staffCard.text = "Transactions"
                organizerCard.text = "ID Printing"
                notificationsCard.text = "Event Registrations"
                rewardsCard.text = "Notifications"
                reportsCard.visibility = View.GONE
                logoutCard.visibility = View.VISIBLE

                attendeeCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.staff.ScannerActivity::class.java)) }
                staffCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.staff.StaffTransactionsActivity::class.java)) }
                organizerCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.staff.IdPrintingActivity::class.java)) }
                notificationsCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.staff.EventRegistrationsActivity::class.java)) }
                rewardsCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeNotificationsActivity::class.java)) }
                logoutCard.setOnClickListener { performLogout() }
            }
            "ORGANIZER", "ADMIN" -> {
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
                staffCard.text = "My Events"
                organizerCard.text = "Rewards"
                notificationsCard.text = "Request Event"
                rewardsCard.visibility = View.GONE
                reportsCard.visibility = View.GONE
                logoutCard.visibility = View.GONE

                attendeeCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeEventsActivity::class.java)) }
                staffCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.RegisteredEventsActivity::class.java)) }
                organizerCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.AttendeeRewardsActivity::class.java)) }
                notificationsCard.setOnClickListener { startActivity(Intent(this, com.thedavelopers.eventqr.features.attendee.RequestEventActivity::class.java)) }
            }
        }
    }

    private fun performLogout() {
        sessionManager.clearSession()
        startActivity(Intent(this, com.thedavelopers.eventqr.SplashScreen::class.java))
        finish()
    }
}
