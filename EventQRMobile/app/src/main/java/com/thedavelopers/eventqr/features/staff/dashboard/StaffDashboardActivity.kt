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
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import com.thedavelopers.eventqr.features.transactions.TransactionLogAdapter
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse

open class StaffDashboardActivity : AppCompatActivity(), StaffDashboardContract.View {
    private lateinit var presenter: StaffDashboardPresenter
    private lateinit var adapter: TransactionLogAdapter
    private lateinit var eventAdapter: StaffEventAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var isSwipeRefreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.STAFF.name) {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_dashboard)

        presenter = StaffDashboardPresenter(this, StaffRepository(this))
        adapter = TransactionLogAdapter()
        eventAdapter = StaffEventAdapter { event ->
            val intent = Intent(this, ScannerActivity::class.java)
            intent.putExtra(StaffScreenExtras.EXTRA_EVENT_ID, event.eventId.toString())
            startActivity(intent)
        }

        findViewById<RecyclerView>(R.id.recyclerRecentScans).apply {
            layoutManager = LinearLayoutManager(this@StaffDashboardActivity)
            adapter = this@StaffDashboardActivity.adapter
        }

        findViewById<RecyclerView>(R.id.recyclerAssignedEvents).apply {
            layoutManager = LinearLayoutManager(this@StaffDashboardActivity)
            adapter = eventAdapter
        }

        findViewById<TextView>(R.id.txtStaffName).text = sessionManager.getFullName() ?: sessionManager.getEmail() ?: "Staff User"
        findViewById<TextView>(R.id.txtStaffEmail).text = sessionManager.getEmail() ?: ""

        findViewById<View>(R.id.btnQuickScan).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        findViewById<View>(R.id.btnQuickRegistrations).setOnClickListener {
            startActivity(Intent(this, EventRegistrationsActivity::class.java))
        }

        findViewById<View>(R.id.btnQuickTransactions).setOnClickListener {
            startActivity(Intent(this, StaffTransactionsActivity::class.java))
        }

        findViewById<View>(R.id.btnQuickIdPrinting).setOnClickListener {
            startActivity(Intent(this, IdPrintingActivity::class.java))
        }

        findViewById<View>(R.id.txtScansToday).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        findViewById<View>(R.id.txtCheckinsToday).setOnClickListener {
            startActivity(Intent(this, EventRegistrationsActivity::class.java))
        }

        findViewById<View>(R.id.navScanner).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        findViewById<View>(R.id.navLogs).setOnClickListener {
            startActivity(Intent(this, StaffTransactionsActivity::class.java))
        }

        findViewById<View>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, StaffProfileActivity::class.java))
        }

        swipeRefreshLayout = findViewById(R.id.swipeRefreshDashboard)
        swipeRefreshLayout.setColorSchemeResources(R.color.eventqr_purple)
        swipeRefreshLayout.setOnRefreshListener {
            isSwipeRefreshing = true
            presenter.loadData()
        }

        setupPortalSwitcher(sessionManager)

        presenter.loadData()
    }

    private fun setupPortalSwitcher(sessionManager: SessionManager) {
        val role = sessionManager.getUserRole() ?: return
        val normalizedRole = RoleMapper.normalizeRole(role)
        val allowedPortals = mutableListOf<String>()
        allowedPortals.add("Attendee Portal")

        if (normalizedRole == AccountRole.STAFF.name || normalizedRole == AccountRole.ADMIN.name || normalizedRole == AccountRole.SUPER_ADMIN.name) {
            allowedPortals.add("Staff Portal")
        }
        if (normalizedRole == AccountRole.ORGANIZER.name || normalizedRole == AccountRole.ADMIN.name || normalizedRole == AccountRole.SUPER_ADMIN.name) {
            allowedPortals.add("Organizer Portal")
        }
        if (normalizedRole == AccountRole.ADMIN.name || normalizedRole == AccountRole.SUPER_ADMIN.name) {
            allowedPortals.add("Admin Portal")
        }

        if (allowedPortals.size > 1) {
            val chip = findViewById<View>(R.id.portalSwitcherChip)
            chip.visibility = View.VISIBLE
            findViewById<View>(R.id.txtStaffNameDot).visibility = View.VISIBLE
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
            
            when(portal) {
                "Attendee Portal" -> {
                    icon.setImageResource(R.drawable.ic_nav_profile)
                    subtitle.text = "Events, rewards, and your profile"
                }
                "Staff Portal" -> {
                    icon.setImageResource(R.drawable.ic_qr_scan)
                    subtitle.text = "Scan QR codes and manage entries"
                }
                "Organizer Portal" -> {
                    icon.setImageResource(R.drawable.ic_nav_calendar)
                    subtitle.text = "Manage your events and attendees"
                }
                "Admin Portal" -> {
                    icon.setImageResource(R.drawable.ic_group)
                    subtitle.text = "Platform administration and oversight"
                }
            }

            if (portal == "Staff Portal") {
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
            "Attendee Portal" -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.dashboard.DashboardActivity::class.java))
                finish()
            }
            "Staff Portal" -> {
                // Already here
            }
            "Organizer Portal" -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.organizer.dashboard.OrganizerDashboardActivity::class.java))
                finish()
            }
            "Admin Portal" -> {
                startActivity(Intent(this, com.thedavelopers.eventqr.features.admin.dashboard.AdminDashboardActivity::class.java))
                finish()
            }
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun renderEvents(items: List<com.thedavelopers.eventqr.features.staff.model.dto.StaffAssignedEventResponse>) {
        findViewById<TextView>(R.id.txtAssignedCount).text = items.size.toString()
        eventAdapter.submitItems(items)
        findViewById<TextView>(R.id.txtAssignedEmptyState).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<RecyclerView>(R.id.recyclerAssignedEvents).visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnQuickScan).isEnabled = items.any { it.canScan }

        if (items.isEmpty()) {
            Toast.makeText(this, "No events assigned to you yet", Toast.LENGTH_LONG).show()
        } else if (items.none { it.canScan }) {
            Toast.makeText(this, "No active Scan QR permission for assigned events", Toast.LENGTH_LONG).show()
        }
    }

    override fun renderRecentScans(items: List<TransactionResponse>) {
        adapter.submitItems(items)
    }

    override fun updateStats(scans: Int, checkins: Int) {
        findViewById<TextView>(R.id.txtScansToday).text = scans.toString()
        findViewById<TextView>(R.id.txtCheckinsToday).text = checkins.toString()
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading(isLoading: Boolean) {
        if (isSwipeRefreshing) {
            if (!isLoading) {
                stopSwipeRefresh()
            }
            findViewById<View>(R.id.progressScanner)?.visibility = View.GONE
        } else {
            findViewById<View>(R.id.progressScanner)?.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        findViewById<View>(R.id.btnQuickScan)?.isEnabled = !isLoading
    }

    private fun stopSwipeRefresh() {
        if (isSwipeRefreshing) {
            swipeRefreshLayout.isRefreshing = false
            isSwipeRefreshing = false
        }
    }
}
