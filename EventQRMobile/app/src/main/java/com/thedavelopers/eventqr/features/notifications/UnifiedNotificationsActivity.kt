package com.thedavelopers.eventqr.features.notifications

import android.os.Bundle
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.ui.components.setContentWithDetailHeader
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.AccountRole
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.core.api.dto.NotificationType
import com.thedavelopers.eventqr.core.session.SessionManager
import com.thedavelopers.eventqr.core.session.SessionRefresher
import com.thedavelopers.eventqr.core.util.RoleMapper
import com.thedavelopers.eventqr.features.attendee.EXTRA_EVENT_ID
import com.thedavelopers.eventqr.features.attendee.EventDetailActivity
import com.thedavelopers.eventqr.features.attendee.MyEventRequestsActivity
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.features.organizer.EXTRA_EVENT_ID as ORGANIZER_EVENT_ID
import com.thedavelopers.eventqr.features.organizer.events.EventManagementHubActivity
import com.thedavelopers.eventqr.features.organizer.events.OrganizerEventDetailsActivity
import com.thedavelopers.eventqr.features.organizer.openOrganizerPage
import com.thedavelopers.eventqr.ui.theme.EventQrTheme
import com.thedavelopers.eventqr.ui.theme.dimenOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

open class UnifiedNotificationsActivity : AppCompatActivity() {

    private lateinit var repository: NotificationsRepository
    private val _notifications = MutableStateFlow<List<NotificationResponse>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    /** A failed read/write of the list itself (§6.8 row 21); Retry re-runs [lastFailedOp]. */
    private val actionFailure = NotificationActionFailure()
    private var lastFailedOp: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = NotificationsRepository(this)

        val header = setContentWithDetailHeader("Notifications") {
            EventQrTheme {
                val notifications = _notifications.collectAsStateWithLifecycle().value
                val isLoading = _isLoading.collectAsStateWithLifecycle().value
                val errorMessage = _errorMessage.collectAsStateWithLifecycle().value
                val actionFailureCount = actionFailure.count.collectAsStateWithLifecycle().value

                NotificationsScreen(
                    notifications = notifications,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    actionFailureCount = actionFailureCount,
                    onNotificationClick = { notification ->
                        if (notification.status != NotificationStatus.READ && notification.readAt == null) {
                            markRead(notification.notificationId)
                        }
                        openNotification(notification)
                    },
                    onRetryClick = { loadNotifications() },
                    onActionRetry = { lastFailedOp?.invoke() },
                    navigatesOnTap = { notification ->
                        notificationDestinationFor(notification.notificationType, notification.eventId?.toString()) != null
                    },
                )
            }
        }

        val markAllRead = TextView(this).apply {
            text = getString(R.string.ui_notifications_mark_all_read)
            setTextAppearance(R.style.TextAppearance_EventQR_Type_Label)
            gravity = android.view.Gravity.CENTER
            minHeight = dimenOffset(R.dimen.ui_touch_min)
            setPadding(dimenOffset(R.dimen.space_12), 0, dimenOffset(R.dimen.space_12), 0)
            setOnClickListener { markAllRead() }
        }
        header.endSlot.addView(markAllRead)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                _notifications.collect { list ->
                    val hasUnread = list.any { it.status != NotificationStatus.READ && it.readAt == null }
                    markAllRead.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                    markAllRead.isEnabled = hasUnread
                    markAllRead.setTextColor(getColor(if (hasUnread) R.color.ui_primary else R.color.ui_text_disabled))
                }
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

    private fun markRead(notificationId: UUID) {
        val previous = _notifications.value
        _notifications.value = withReadState(previous, setOf(notificationId))
        lifecycleScope.launch {
            // The shade copy only leaves once the server stored the read, so a failed mark keeps it.
            val result = EventDecisionNotifier.markRead(this@UnifiedNotificationsActivity, listOf(notificationId.toString())) {
                repository.markNotificationRead(notificationId.toString())
            }
            when (result) {
                is NetworkResult.Success -> clearActionFailure()
                is NetworkResult.Error -> {
                    _notifications.value = previous
                    showActionFailure(result.message) { markRead(notificationId) }
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    private fun markAllRead() {
        val previous = _notifications.value
        _notifications.value = withReadState(previous, previous.map { it.notificationId }.toSet())
        val ids = previous.map { it.notificationId.toString() }
        lifecycleScope.launch {
            val result = EventDecisionNotifier.markRead(this@UnifiedNotificationsActivity, ids) {
                repository.markAllNotificationsRead()
            }
            when (result) {
                is NetworkResult.Success -> clearActionFailure()
                is NetworkResult.Error -> {
                    _notifications.value = previous
                    showActionFailure(result.message) { markAllRead() }
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    /**
     * §6.8 row 21: a failed read reports in the strip above the list, and Retry re-runs the same
     * read. The row state was already reverted, so the list never claims an optimistic write.
     * The server text goes to the log, never into the strip (§7.5).
     */
    private fun showActionFailure(message: String, retry: () -> Unit) {
        Log.w(TAG, "notification read failed: $message")
        lastFailedOp = retry
        // The screen announces the sentence from a LaunchedEffect on every new failure (§5.16). The
        // sentence never changes, so the counter carries the repeat: an equal StateFlow value would
        // be conflated and the strip would look dead after Retry.
        actionFailure.fail()
    }

    private fun clearActionFailure() {
        lastFailedOp = null
        actionFailure.clear()
    }

    private fun openNotification(notification: NotificationResponse) {
        val eventId = notification.eventId?.toString()
        if (notification.notificationType == NotificationType.EVENT_APPROVED) {
            openApprovedRequest(eventId)
            return
        }
        when (resolvedDestination(this, notification.notificationType, eventId)) {
            NotificationDestination.ORGANIZER_HUB -> openOrganizerPage(EventManagementHubActivity::class.java, eventId)
            NotificationDestination.ORGANIZER_EVENT_DETAILS -> startActivity(
                Intent(this, OrganizerEventDetailsActivity::class.java).putExtra(ORGANIZER_EVENT_ID, eventId),
            )
            NotificationDestination.ATTENDEE_EVENT_DETAIL -> startActivity(
                Intent(this, EventDetailActivity::class.java).putExtra(EXTRA_EVENT_ID, eventId),
            )
            NotificationDestination.ATTENDEE_REQUESTS -> startActivity(Intent(this, MyEventRequestsActivity::class.java))
            null -> Unit
        }
    }

    /**
     * Approval grants the organizer role in the same admin action, so the cached role can lag one
     * refresh behind. Refresh once here; a failed refresh keeps the cached role's fallback.
     */
    private fun openApprovedRequest(eventId: String?) {
        if (eventId == null) return
        val session = SessionManager(this)
        if (RoleMapper.isAtLeast(session.getUserRole(), AccountRole.ORGANIZER)) {
            openOrganizerPage(EventManagementHubActivity::class.java, eventId)
            return
        }
        lifecycleScope.launch {
            runCatching { SessionRefresher.refreshNow(this@UnifiedNotificationsActivity) }
            if (RoleMapper.isAtLeast(session.getUserRole(), AccountRole.ORGANIZER)) {
                openOrganizerPage(EventManagementHubActivity::class.java, eventId)
            } else {
                startActivity(Intent(this@UnifiedNotificationsActivity, EventDetailActivity::class.java).putExtra(EXTRA_EVENT_ID, eventId))
            }
        }
    }

    private companion object {
        const val TAG = "Notifications"
    }
}

/**
 * Local read state for the rows. Ids compare as UUIDs, so a differently-typed id can never silently
 * miss and leave a tapped row looking unread.
 */
internal fun withReadState(
    source: List<NotificationResponse>,
    readIds: Set<UUID>,
    readAt: Instant = Instant.now(),
): List<NotificationResponse> = source.map { notification ->
    if (notification.notificationId in readIds) {
        notification.copy(status = NotificationStatus.READ, readAt = readAt)
    } else {
        notification
    }
}

/**
 * §6.8 row 21 strip state. The sentence never changes, and a StateFlow conflates equal values, so a
 * repeat failure after Retry must bump a counter - only that re-emits, re-renders and re-announces.
 */
internal class NotificationActionFailure {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    fun fail() {
        _count.value += 1
    }

    fun clear() {
        _count.value = 0
    }
}
