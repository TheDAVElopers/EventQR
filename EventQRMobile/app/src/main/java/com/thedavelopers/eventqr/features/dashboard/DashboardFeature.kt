package com.thedavelopers.eventqr.features.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.auth.AuthRepository
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

open class DashboardActivity : com.thedavelopers.eventqr.core.ui.BaseNavActivity(), DashboardContract.View {
    private lateinit var presenter: DashboardPresenter
    private lateinit var sessionManager: SessionManager
    private lateinit var welcomeText: TextView
    private lateinit var dashboardName: TextView
    private lateinit var summaryEvents: TextView
    private lateinit var summaryRegistrations: TextView
    private lateinit var summaryTransactions: TextView
    private lateinit var summaryRewards: TextView
    private lateinit var summaryNotifications: TextView
    private lateinit var loadingText: TextView
    private lateinit var upcomingEvents : TextView
    private lateinit var recentEvents : TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        sessionManager = SessionManager(this)
        presenter = DashboardPresenter(this, DashboardRepository(this), sessionManager)
        presenter.attach(this)

        welcomeText = findViewById(R.id.txtDashboardWelcome)
        dashboardName = findViewById(R.id.txtDashboardFullName)
        summaryEvents = findViewById(R.id.txtTotalEvents)
        summaryRegistrations = findViewById(R.id.txtTotalRegistrations)
        summaryTransactions = findViewById(R.id.txtTotalTransactions)
        summaryRewards = findViewById(R.id.txtTotalRewards)
        summaryNotifications = findViewById(R.id.txtTotalNotifications)
        loadingText = findViewById(R.id.txtDashboardLoading)
        upcomingEvents = findViewById(R.id.txtbtnUpcomingEvents)
        recentEvents = findViewById(R.id.txtbtnRecentActivity)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation) ?: findViewById<BottomNavigationView>(R.id.nav_view_container)
        setupBottomNavigation(bottomNav)
        updateBottomNavSelection(bottomNav, R.id.nav_dashboard)

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
        welcomeText.text = "Welcome to EventQR!"
        dashboardName.text = if(name.isNullOrBlank()) "John Doe" else "$name"
    }
}
