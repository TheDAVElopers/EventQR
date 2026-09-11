package com.thedavelopers.eventqr.features.staff

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.PortalSwitcher
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.core.util.firstNameOnly
import com.thedavelopers.eventqr.features.staff.notifications.StaffNotificationsActivity
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import com.thedavelopers.eventqr.features.transactions.TransactionLogAdapter
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse

open class StaffDashboardActivity : AppCompatActivity(), StaffDashboardContract.View {
    private lateinit var presenter: StaffDashboardPresenter
    private lateinit var repository: StaffRepository
    private lateinit var adapter: TransactionLogAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private var isSwipeRefreshing = false
    private var isFirstResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.STAFF.name) {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_dashboard)

        repository = StaffRepository(this)
        presenter = StaffDashboardPresenter(this, repository)
        adapter = TransactionLogAdapter()
        skeletonLoading = findViewById(R.id.skeletonLoading)

        findViewById<RecyclerView?>(R.id.recyclerRecentScans)?.apply {
            layoutManager = LinearLayoutManager(this@StaffDashboardActivity)
            adapter = this@StaffDashboardActivity.adapter
        }

        findViewById<TextView>(R.id.txtStaffName).text = (sessionManager.getFullName() ?: sessionManager.getEmail() ?: "Staff User").firstNameOnly()
        findViewById<View>(R.id.btnNotification).setOnClickListener {
            startActivity(Intent(this, StaffNotificationsActivity::class.java))
        }

        findViewById<View>(R.id.txtScansToday).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        findViewById<View>(R.id.txtCheckinsToday).setOnClickListener {
            startActivity(Intent(this, StaffAssignedEventsActivity::class.java))
        }

        configureStaffBottomNav(StaffBottomNavItem.DASHBOARD)

        swipeRefreshLayout = findViewById(R.id.swipeRefreshDashboard)
        swipeRefreshLayout.setColorSchemeResources(R.color.eventqr_purple)
        swipeRefreshLayout.setOnRefreshListener {
            isSwipeRefreshing = true
            presenter.loadData()
        }

        setupPortalSwitcher(sessionManager)

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

    private fun setupPortalSwitcher(sessionManager: SessionManager) {
        val role = sessionManager.getUserRole() ?: return
        val normalizedRole = RoleMapper.normalizeRole(role)
        val allowedPortals = PortalSwitcher.portalsForRole(normalizedRole)

        val chip = findViewById<View>(R.id.portalSwitcherChip)
        val dot = findViewById<View>(R.id.txtStaffNameDot)
        chip.visibility = if (allowedPortals.size > 1) View.VISIBLE else View.GONE
        dot.visibility = if (allowedPortals.size > 1) View.VISIBLE else View.GONE
        chip.setOnClickListener(null)

        if (allowedPortals.size > 1) {
            chip.setOnClickListener {
                showPortalSwitcher(allowedPortals)
            }
        }
    }

    private fun showPortalSwitcher(portals: List<String>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_portal_switcher, null)
        val container = view.findViewById<LinearLayout>(R.id.portalOptionsContainer)
        portals.forEach { portal ->
            val portalView = layoutInflater.inflate(R.layout.item_portal_option, container, false)
            portalView.findViewById<TextView>(R.id.txtPortalName).text = portal
            val icon = portalView.findViewById<ImageView>(R.id.imgPortalIcon)
            val subtitle = portalView.findViewById<TextView>(R.id.txtPortalSubtitle)
            icon.setImageResource(PortalSwitcher.iconRes(portal))
            subtitle.text = PortalSwitcher.subtitle(portal)

            if (portal == PortalSwitcher.PORTAL_STAFF) {
                portalView.findViewById<View>(R.id.currentPortalBadge).visibility = View.VISIBLE
            }

            portalView.setOnClickListener {
                dialog.dismiss()
                switchToPortal(portal)
            }
            container.addView(portalView)
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun switchToPortal(portal: String) {
        when(portal) {
            PortalSwitcher.PORTAL_ATTENDEE -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.dashboard.DashboardActivity::class.java))
                finish()
            }
            PortalSwitcher.PORTAL_STAFF -> Unit
            PortalSwitcher.PORTAL_ORGANIZER -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.organizer.dashboard.OrganizerDashboardActivity::class.java))
                finish()
            }
            PortalSwitcher.PORTAL_ADMIN -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity::class.java))
                finish()
            }
            PortalSwitcher.PORTAL_SUPER_ADMIN -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity::class.java))
                finish()
            }
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun renderRecentScans(items: List<TransactionResponse>) {
        skeletonLoading.visibility = View.GONE
        adapter.submitItems(items)
        findViewById<RecyclerView>(R.id.recyclerRecentScans).visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.txtRecentScansEmpty).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun updateStats(scans: Int, checkins: Int) {
        findViewById<TextView>(R.id.txtScansToday).text = scans.toString()
        findViewById<TextView>(R.id.txtCheckinsToday).text = checkins.toString()
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showNotificationBadge(unreadCount: Int) {
        val badge = findViewById<TextView>(R.id.txtNotificationBadge)
        if (unreadCount > 0) {
            badge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
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

    }
