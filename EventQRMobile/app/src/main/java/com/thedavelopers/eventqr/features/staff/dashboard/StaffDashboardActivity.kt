package com.thedavelopers.eventqr.features.staff

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.PortalSwitcher
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.core.util.UiStrings
import com.thedavelopers.eventqr.features.staff.notifications.StaffNotificationsActivity
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import com.thedavelopers.eventqr.features.transactions.TransactionLogAdapter
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import com.thedavelopers.eventqr.ui.components.EventQrEmptyState
import com.thedavelopers.eventqr.ui.components.PassHeadBinder
import com.thedavelopers.eventqr.ui.components.PassHeadModel
import com.thedavelopers.eventqr.ui.components.PassStat
import com.thedavelopers.eventqr.ui.theme.applyEventQrSystemBarAppearance
import com.thedavelopers.eventqr.ui.theme.applyEventQrTopInsetPadding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Staff dashboard (§6.3). The pass head is the header - role tabs, "Today", and a two-cell stub
 * (the API exposes scans and successful check-ins; there is no queue metric to show a fake zero
 * for). Below it the screen has one job: open the scanner. That is a 96dp flat primary block, not
 * a card. Recent scans are hairline rows, never raised cards.
 */
open class StaffDashboardActivity : AppCompatActivity(), StaffDashboardContract.View {
    private lateinit var presenter: StaffDashboardPresenter
    private lateinit var repository: StaffRepository
    private lateinit var adapter: TransactionLogAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var recyclerRecentScans: RecyclerView
    private lateinit var emptyRecentScans: EventQrEmptyState
    private lateinit var passHead: View
    private var isSwipeRefreshing = false
    private var isFirstResume = true

    private var headBell: Int = 0
    private var headScans: Int? = null
    private var headCheckIns: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (!RoleMapper.isAtLeast(sessionManager.getUserRole(), AccountRole.STAFF)) {
            Toast.makeText(this, this.getString(R.string.staff_dashboard_access_denied_staff_or_above), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_dashboard)
        applyEventQrSystemBarAppearance(lightStatusBars = false)
        findViewById<View>(R.id.passHeadIdentity).applyEventQrTopInsetPadding()

        repository = StaffRepository(this)
        presenter = StaffDashboardPresenter(this, repository, UiStrings(this))
        adapter = TransactionLogAdapter()
        passHead = findViewById(R.id.passHead)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        recyclerRecentScans = findViewById(R.id.recyclerRecentScans)
        emptyRecentScans = findViewById(R.id.txtRecentScansEmpty)

        recyclerRecentScans.layoutManager = LinearLayoutManager(this)
        recyclerRecentScans.adapter = adapter

        emptyRecentScans.setAction(getString(R.string.ui_staff_scan_a_pass)) { openScanner() }

        findViewById<View>(R.id.btnScanPass).setOnClickListener { openScanner() }
        configureStaffBottomNav(StaffBottomNavItem.DASHBOARD)

        swipeRefreshLayout = findViewById(R.id.swipeRefreshDashboard)
        swipeRefreshLayout.setOnRefreshListener {
            isSwipeRefreshing = true
            presenter.loadData()
        }

        bindPassHead()
        presenter.loadData()
    }

    override fun onResume() {
        super.onResume()
        if (isFirstResume) {
            isFirstResume = false
            return
        }
        presenter.loadData()
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    // ── Pass head ────────────────────────────────────────────────────────────

    private fun bindPassHead() {
        val portals = PortalSwitcher.portalsForRole(
            RoleMapper.normalizeRole(SessionManager(this).getUserRole()),
        )
        val roleNames = portals.map { PortalSwitcher.shortRoleName(this, it) }
        val hasTabs = roleNames.size > 1

        PassHeadBinder.bind(
            passHead,
            PassHeadModel(
                title = getString(R.string.ui_staff_today),
                eyebrow = if (hasTabs) null else getString(R.string.ui_staff_role),
                tabs = if (hasTabs) roleNames else emptyList(),
                selectedTabIndex = portals.indexOf(PortalSwitcher.PORTAL_STAFF).coerceAtLeast(0),
                bellCount = headBell,
                stats = listOf(
                    PassStat(formatCount(headScans), getString(R.string.ui_staff_scans)),
                    PassStat(formatCount(headCheckIns), getString(R.string.ui_staff_check_ins)),
                ),
                onTabSelected = { index -> portals.getOrNull(index)?.let(::switchToPortal) },
                onBellClick = { startActivity(Intent(this, StaffNotificationsActivity::class.java)) },
            ),
        )
    }

    private fun formatCount(value: Int?): String =
        value?.let { NumberFormat.getIntegerInstance(Locale.getDefault()).format(it) }
            ?: getString(R.string.ui_staff_value_unavailable)

    private fun switchToPortal(portal: String) {
        when (portal) {
            PortalSwitcher.PORTAL_ATTENDEE -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.dashboard.DashboardActivity::class.java))
                finish()
            }
            PortalSwitcher.PORTAL_STAFF -> Unit
            PortalSwitcher.PORTAL_ORGANIZER -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.organizer.dashboard.OrganizerDashboardActivity::class.java))
                finish()
            }
            PortalSwitcher.PORTAL_ADMIN, PortalSwitcher.PORTAL_SUPER_ADMIN -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity::class.java))
                finish()
            }
        }
    }

    // ── Contract ─────────────────────────────────────────────────────────────

    override fun renderRecentScans(items: List<TransactionResponse>) {
        skeletonLoading.visibility = View.GONE
        adapter.submitItems(items)
        val empty = items.isEmpty()
        recyclerRecentScans.visibility = if (empty) View.GONE else View.VISIBLE
        emptyRecentScans.visibility = if (empty) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.txtRecentCount).text =
            if (empty) "" else getString(R.string.ui_staff_today_count, items.size)
    }

    override fun updateStats(scans: Int?, checkins: Int?) {
        headScans = scans
        headCheckIns = checkins
        bindPassHead()
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showNotificationBadge(unreadCount: Int) {
        headBell = unreadCount
        bindPassHead()
    }

    override fun showLoading(isLoading: Boolean) {
        if (isSwipeRefreshing) {
            if (!isLoading) stopSwipeRefresh()
        } else {
            skeletonLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun stopSwipeRefresh() {
        if (isSwipeRefreshing) {
            swipeRefreshLayout.isRefreshing = false
            isSwipeRefreshing = false
        }
    }

    private fun openScanner() {
        startActivity(Intent(this, ScannerActivity::class.java))
    }
}
