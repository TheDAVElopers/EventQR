package com.thedavelopers.eventqr.features.staff

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

        presenter.loadData()
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
        findViewById<View>(R.id.progressScanner)?.visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnQuickScan)?.isEnabled = !isLoading
    }
}
