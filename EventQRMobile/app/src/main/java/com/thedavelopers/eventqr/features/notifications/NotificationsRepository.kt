package com.thedavelopers.eventqr.features.notifications

import android.content.Context
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.safeApiCall

class NotificationsRepository(context: Context) {
    private val apiService = ApiClient.getService(context)

    /** The list screen wants the whole history; the decision sweep asks for `unreadOnly` only. */
    suspend fun getMyNotifications(unreadOnly: Boolean? = null) =
        safeApiCall { apiService.getMyNotifications(unreadOnly = unreadOnly) }

    suspend fun markNotificationRead(notificationId: String) =
        safeApiCall { apiService.markNotificationRead(notificationId) }

    suspend fun markAllNotificationsRead() = safeApiCall { apiService.markAllNotificationsRead() }
}
