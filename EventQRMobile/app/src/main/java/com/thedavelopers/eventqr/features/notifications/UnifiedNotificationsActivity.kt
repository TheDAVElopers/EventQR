package com.thedavelopers.eventqr.features.notifications

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.features.attendee.AttendeeRepository
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

open class UnifiedNotificationsActivity : AppCompatActivity() {

    private lateinit var repository: AttendeeRepository
    private val _notifications = MutableStateFlow<List<NotificationResponse>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AttendeeRepository(this)

        setContent {
            EventQrTheme {
                val notifications = _notifications.collectAsStateWithLifecycle().value
                val isLoading = _isLoading.collectAsStateWithLifecycle().value
                val errorMessage = _errorMessage.collectAsStateWithLifecycle().value

                NotificationsScreen(
                    notifications = notifications,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onBackClick = { finish() },
                    onNotificationClick = { notification ->
                        if (notification.status != NotificationStatus.READ && notification.readAt == null) {
                            markRead(notification.notificationId.toString())
                        }
                    },
                    onMarkAllReadClick = { markAllRead() },
                    onRetryClick = { loadNotifications() },
                )
            }
        }

        loadNotifications()
    }

    protected fun loadNotifications() {
        _isLoading.value = true
        _errorMessage.value = null
        lifecycleScope.launch {
            when (val result = repository.getMyNotifications()) {
                is NetworkResult.Success -> {
                    _notifications.value = result.data
                    _isLoading.value = false
                }
                is NetworkResult.Error -> {
                    _isLoading.value = false
                    _errorMessage.value = result.message.ifBlank { "Unable to load notifications." }
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun markRead(notificationId: String) {
        lifecycleScope.launch {
            when (val result = repository.markNotificationRead(notificationId)) {
                is NetworkResult.Success -> loadNotifications()
                is NetworkResult.Error -> {
                    Toast.makeText(this@UnifiedNotificationsActivity, result.message.ifBlank { "Unable to mark notification as read." }, Toast.LENGTH_SHORT).show()
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun markAllRead() {
        lifecycleScope.launch {
            when (val result = repository.markAllNotificationsRead()) {
                is NetworkResult.Success -> loadNotifications()
                is NetworkResult.Error -> {
                    Toast.makeText(this@UnifiedNotificationsActivity, result.message.ifBlank { "Unable to mark notifications as read." }, Toast.LENGTH_SHORT).show()
                }
                NetworkResult.Loading -> Unit
            }
        }
    }
}
