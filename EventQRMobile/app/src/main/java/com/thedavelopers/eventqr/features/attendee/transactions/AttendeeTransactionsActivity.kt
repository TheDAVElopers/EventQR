package com.thedavelopers.eventqr.features.attendee

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.features.transactions.TransactionAdapter
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse

open class AttendeeTransactionsActivity : AppCompatActivity(), TransactionHistoryContract.View {
    private lateinit var presenter: TransactionHistoryPresenter
    private lateinit var adapter: TransactionAdapter
    private lateinit var loadingText: TextView
    private lateinit var emptyText: TextView
    private lateinit var eventTitleText: TextView
    private lateinit var totalEarnedText: TextView
    private lateinit var totalRedeemedText: TextView
    private lateinit var currentBalanceText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_transaction_history)

        presenter = TransactionHistoryPresenter(this, AttendeeRepository(this))

        loadingText = findViewById(R.id.txtTransactionsLoading)
        emptyText = findViewById(R.id.txtTransactionsEmpty)
        eventTitleText = findViewById(R.id.txtHistoryEventTitle)
        totalEarnedText = findViewById(R.id.txtHistoryTotalEarned)
        totalRedeemedText = findViewById(R.id.txtHistoryTotalRedeemed)
        currentBalanceText = findViewById(R.id.txtHistoryCurrentBalance)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE).orEmpty()
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        eventTitleText.text = eventTitle.ifBlank { "My Transaction History" }

        adapter = TransactionAdapter(eventTitle)
        findViewById<RecyclerView>(R.id.recyclerTransactions).apply {
            layoutManager = LinearLayoutManager(this@AttendeeTransactionsActivity)
            adapter = this@AttendeeTransactionsActivity.adapter
        }

        if (eventId.isNotBlank()) {
            presenter.load(eventId)
        } else {
            presenter.load(null)
        }
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        loadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun renderTransactions(items: List<TransactionResponse>) {
        emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = if (items.isEmpty()) "No transactions found yet." else emptyText.text
        adapter.submitItems(items)

        val earned = items.filter { it.pointsDelta > 0 }.sumOf { it.pointsDelta }
        val redeemed = items.filter { it.pointsDelta < 0 }.sumOf { it.pointsDelta }
        val balance = earned + redeemed

        totalEarnedText.text = "+$earned"
        totalRedeemedText.text = "$redeemed"
        currentBalanceText.text = "$balance pts"
    }
}
