package com.thedavelopers.eventqr.features.attendee

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.features.notifications.NotificationAdapter
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse

open class AttendeeNotificationsActivity : AppCompatActivity(), NotificationsContract.View {
    private lateinit var presenter: NotificationsPresenter
    private lateinit var adapter: NotificationAdapter
    private lateinit var recyclerNotifications: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var skeletonLoading: View
    private lateinit var layoutEmpty: View
    private lateinit var layoutError: View
    private lateinit var btnRetry: Button
    private lateinit var actionMarkAllRead: TextView
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        presenter = NotificationsPresenter(this, AttendeeRepository(this))
        adapter = NotificationAdapter { notification ->
            if (notification.status != NotificationStatus.READ) {
                presenter.markRead(notification.notificationId.toString())
            }
        }
        swipeRefresh = findViewById(R.id.swipeRefreshNotifications)
        recyclerNotifications = findViewById(R.id.recyclerNotifications)
        skeletonLoading = findViewById(R.id.skeletonLoading)
        layoutEmpty = findViewById(R.id.layoutNotificationsEmpty)
        layoutError = findViewById(R.id.layoutNotificationsError)
        btnRetry = findViewById(R.id.btnNotificationsRetry)
        actionMarkAllRead = findViewById(R.id.txtMarkAllRead)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }
        actionMarkAllRead.setOnClickListener { presenter.markAllRead() }
        btnRetry.setOnClickListener { presenter.load() }
        swipeRefresh.setOnRefreshListener { presenter.load() }

        recyclerNotifications.apply {
            layoutManager = LinearLayoutManager(this@AttendeeNotificationsActivity)
            adapter = this@AttendeeNotificationsActivity.adapter
        }

        presenter.load()
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }

    override fun showLoading(isLoading: Boolean) {
        if (!swipeRefresh.isRefreshing) {
            skeletonLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        if (isLoading) {
            recyclerNotifications.visibility = View.GONE
            layoutEmpty.visibility = View.GONE
            layoutError.visibility = View.GONE
            btnRetry.visibility = View.GONE
        } else {
            swipeRefresh.isRefreshing = false
        }
    }

    override fun showContent() {
        swipeRefresh.isRefreshing = false
        layoutError.visibility = View.GONE
        btnRetry.visibility = View.GONE
        skeletonLoading.visibility = View.GONE
    }

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showError(message: String) {
        swipeRefresh.isRefreshing = false
        recyclerNotifications.visibility = View.GONE
        layoutEmpty.visibility = View.GONE
        skeletonLoading.visibility = View.GONE

        layoutError.visibility = View.VISIBLE
        btnRetry.visibility = View.VISIBLE
        layoutError.findViewById<TextView>(R.id.txtNotificationsError).text = message
    }

    override fun setMarkAllEnabled(enabled: Boolean) {
        actionMarkAllRead.isEnabled = enabled
        actionMarkAllRead.alpha = if (enabled) 1f else 0.5f
    }

    override fun renderNotifications(items: List<NotificationResponse>) {
        swipeRefresh.isRefreshing = false
        layoutError.visibility = View.GONE
        btnRetry.visibility = View.GONE
        skeletonLoading.visibility = View.GONE

        if (items.isEmpty()) {
            recyclerNotifications.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
            return
        }

        layoutEmpty.visibility = View.GONE
        recyclerNotifications.visibility = View.VISIBLE
        adapter.submitItems(items)
    }
}
