package com.thedavelopers.eventqr.features.staff

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.staff.scanner.ScannerActivity
import com.thedavelopers.eventqr.features.transactions.TransactionLogAdapter
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse
import kotlinx.coroutines.launch

open class StaffTransactionsActivity : AppCompatActivity(), StaffTransactionsContract.View {
    private lateinit var presenter: StaffTransactionsPresenter
    private lateinit var adapter: TransactionLogAdapter
    private var selectedEventId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (RoleMapper.normalizeRole(sessionManager.getUserRole()) != AccountRole.STAFF.name) {
            Toast.makeText(this, "Access Denied: Staff only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_staff_transaction_logs)

        presenter = StaffTransactionsPresenter(this, StaffRepository(this))
        adapter = TransactionLogAdapter()

        findViewById<RecyclerView>(R.id.recyclerStaffTransactions).apply {
            layoutManager = LinearLayoutManager(this@StaffTransactionsActivity)
            adapter = this@StaffTransactionsActivity.adapter
        }

        setupBottomNav()

        selectedEventId = intent.getStringExtra(StaffScreenExtras.EXTRA_EVENT_ID).orEmpty()

        if (selectedEventId.isNotBlank()) {
            findViewById<EditText>(R.id.edtStaffTransactionsEventId).setText(selectedEventId)
            presenter.load(selectedEventId)
        } else {
            kotlinx.coroutines.MainScope().launch {
                when (val eventsResult = StaffRepository(this@StaffTransactionsActivity).getEvents()) {
                    is NetworkResult.Success -> {
                        val firstEvent = eventsResult.data.firstOrNull()
                        if (firstEvent == null) {
                            findViewById<TextView>(R.id.txtStaffTransactionsEmptyState).visibility = View.VISIBLE
                            showMessage("No assigned events found")
                            return@launch
                        }
                        selectedEventId = firstEvent.eventId.toString()
                        findViewById<EditText>(R.id.edtStaffTransactionsEventId).setText(selectedEventId)
                        presenter.load(selectedEventId)
                    }
                    is NetworkResult.Error -> showMessage(eventsResult.message)
                    NetworkResult.Loading -> Unit
                }
            }
        }

        findViewById<Button>(R.id.btnLoadStaffTransactions).setOnClickListener {
            selectedEventId = findViewById<EditText>(R.id.edtStaffTransactionsEventId).text.toString()
            presenter.load(selectedEventId)
        }
    }

    private fun setupBottomNav() {
        findViewById<View>(R.id.navDashboard)?.setOnClickListener {
            startActivity(Intent(this, StaffDashboardActivity::class.java))
            finish()
        }

        findViewById<View>(R.id.navScanner)?.setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
            finish()
        }

        findViewById<View>(R.id.navProfile)?.setOnClickListener {
            startActivity(Intent(this, StaffProfileActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun renderTransactions(items: List<TransactionResponse>) {
        adapter.submitItems(items)
        findViewById<TextView>(R.id.txtTotalScans).text = items.size.toString()
        findViewById<TextView>(R.id.txtSuccessfulScans).text = items.count { it.transactionResult.name == "APPROVED" || it.transactionResult.name == "SUCCESS" }.toString()
        findViewById<TextView>(R.id.txtRejectedScans).text = items.count { it.transactionResult.name != "APPROVED" && it.transactionResult.name != "SUCCESS" }.toString()
        findViewById<TextView>(R.id.txtStaffTransactionsEmptyState).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<RecyclerView>(R.id.recyclerStaffTransactions).visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showLoading(isLoading: Boolean) {
        findViewById<View>(R.id.progressScanner)?.visibility = if (isLoading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnLoadStaffTransactions)?.isEnabled = !isLoading
    }
}
