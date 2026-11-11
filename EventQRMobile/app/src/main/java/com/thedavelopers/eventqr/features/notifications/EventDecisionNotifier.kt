package com.thedavelopers.eventqr.features.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.NetworkResult
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.core.api.dto.NotificationType
import com.thedavelopers.eventqr.core.session.SessionRefresher
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.ui.icons.AppIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * System delivery for event decisions.
 *
 * The backend has no push transport, so unread EVENT_APPROVED / EVENT_REJECTED notifications are
 * fetched and posted locally. A posted notification id is remembered so the same decision is never
 * posted twice; opening the shade copy does not mark it read - the in-app list does that.
 *
 * shortcut: poll-based delivery (min 15 min), upgrade to FCM when instant delivery is required.
 */
object EventDecisionNotifier {

    const val CHANNEL_ID = "event_decisions"
    private const val PREFS_NAME = "event_decisions"
    private const val KEY_POSTED_IDS = "posted_notification_ids"
    private const val KEY_PERMISSION_ASKED = "permission_asked"
    private const val MAX_POSTED_IDS = 200
    private const val WORK_NAME = "event_decision_sweep"
    private const val FOREGROUND_SWEEP_WINDOW_MS = 60_000L

    private val decisionTypes = setOf(NotificationType.EVENT_APPROVED, NotificationType.EVENT_REJECTED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastSweepAt = 0L

    /** Unique periodic sweep; KEEP so every app start does not reset the 15-minute window. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<EventDecisionSweepWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        // A host without the androidx.startup initializer (Robolectric unit tests) has no WorkManager;
        // scheduling must never be the reason the process fails to start.
        runCatching {
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    /** Foreground sweep for the dashboard resumes; the worker covers the app being closed. */
    fun sweepInBackground(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSweepAt < FOREGROUND_SWEEP_WINDOW_MS) return
        lastSweepAt = now
        val appContext = context.applicationContext
        scope.launch { runCatching { sweep(appContext) } }
    }

    suspend fun sweep(context: Context) {
        if (!canPost(context)) return
        val unread = (NotificationsRepository(context).getMyNotifications(unreadOnly = true) as? NetworkResult.Success<List<NotificationResponse>>)?.data
            ?: return
        val posted = postedIds(context)
        val pending = unread.filter { shouldPost(it, posted) }
        // Approval grants ORGANIZER in the same admin action, so the cached role can lag one refresh
        // behind. Refresh here, before the shade intent is built, so both taps agree on the role.
        if (pending.any { it.notificationType == NotificationType.EVENT_APPROVED }) {
            runCatching { SessionRefresher.refreshNow(context) }
        }
        pending.forEach { post(context, it) }
    }

    /**
     * A decision is posted once: unread, of a decision type that has a destination, and not already
     * posted. A rejection with a null event id still opens the request list; an approval with no
     * event has nowhere to go and is skipped.
     */
    internal fun shouldPost(item: NotificationResponse, posted: Set<String>): Boolean =
        item.notificationType in decisionTypes &&
            item.isUnread() &&
            notificationDestinationFor(item.notificationType, item.eventId?.toString()) != null &&
            item.notificationId.toString() !in posted

    fun hasAskedForPermission(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PERMISSION_ASKED, false)

    fun markAskedForPermission(context: Context) =
        prefs(context).edit { putBoolean(KEY_PERMISSION_ASKED, true) }

    private fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun NotificationResponse.isUnread(): Boolean =
        status != NotificationStatus.READ && readAt == null

    /** One id derivation shared by post and cancel. `hashCode()` can collide across two ids; the worst case is one shade row replacing another. */
    internal fun notificationIdFor(notificationId: String): Int = notificationId.hashCode()

    /** Drops the shade copy once the in-app list has marked the same notification read. */
    fun cancel(context: Context, notificationId: String) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(notificationId))
    }

    /**
     * Runs a mark-read request and drops the shade copies only once the server accepted it: a failed
     * mark leaves the notification in the shade, so the decision is still reachable from there.
     */
    suspend fun <T> markRead(
        context: Context,
        notificationIds: Collection<String>,
        request: suspend () -> NetworkResult<T>,
    ): NetworkResult<T> {
        val result = request()
        if (result is NetworkResult.Success) notificationIds.forEach { cancel(context, it) }
        return result
    }

    @SuppressLint("MissingPermission") // canPost() checks the runtime grant and the channel toggle.
    internal fun post(context: Context, item: NotificationResponse) {
        val intent = notificationIntent(context, item.notificationType, item.eventId?.toString()) ?: return
        ensureChannel(context)
        val requestCode = notificationIdFor(item.notificationId.toString())
        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(AppIcons.Notification)
            .setContentTitle(item.title)
            .setContentText(item.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(requestCode, notification)
        rememberPosted(context, item.notificationId.toString())
    }

    /** Created on the first post, never in Application.onCreate. */
    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_decisions_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_decisions_description)
            },
        )
    }

    private fun postedIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_POSTED_IDS, emptySet()).orEmpty()

    private fun rememberPosted(context: Context, notificationId: String) {
        val ids = HashSet(postedIds(context)).apply { add(notificationId) }
        // Bounded so the pref cannot grow forever. SharedPreferences hands back an unordered set, so
        // the eviction order is arbitrary; a dropped id only re-posts a decision still unread.
        val trimmed = if (ids.size > MAX_POSTED_IDS) ids.toList().takeLast(MAX_POSTED_IDS).toSet() else ids
        prefs(context).edit { putStringSet(KEY_POSTED_IDS, trimmed) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

class EventDecisionSweepWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { EventDecisionNotifier.sweep(applicationContext) }
        return Result.success()
    }
}
