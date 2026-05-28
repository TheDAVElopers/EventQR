package com.thedavelopers.eventqr.features.attendee

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.features.notifications.NotificationAdapter

open class AttendeeNotificationsActivity : AppCompatActivity(), NotificationsContract.View {
    private lateinit var presenter: NotificationsPresenter
    private lateinit var adapter: NotificationAdapter
    private lateinit var loadingText: TextView
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        presenter = NotificationsPresenter(this, AttendeeRepository(this))
        adapter = NotificationAdapter()
        loadingText = findViewById(R.id.txtNotificationsLoading)
        emptyText = findViewById(R.id.txtNotificationsEmpty)

        findViewById<RecyclerView>(R.id.recyclerNotifications).apply {
            layoutManager = LinearLayoutManager(this@AttendeeNotificationsActivity)
            adapter = this@AttendeeNotificationsActivity.adapter
        }

        val recipientUserId = SessionManager(this).getUserId().orEmpty()
        if (recipientUserId.isBlank()) {
            emptyText.text = "Sign in again to see your notifications."
            emptyText.visibility = View.VISIBLE
        } else {
            presenter.load(recipientUserId)
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

    override fun renderNotifications(items: List<com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse>) {
        emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = if (items.isEmpty()) "No notifications available." else emptyText.text
        adapter.submitItems(items)
    }
}
