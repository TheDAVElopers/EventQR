package com.thedavelopers.eventqr.features.notifications

import android.content.Context
import com.thedavelopers.eventqr.core.api.ApiClient
import com.thedavelopers.eventqr.core.api.safeApiCall

class NotificationsRepository(context: Context) {
    private val apiService = ApiClient.getService(context)

    suspend fun getMyNotifications() = safeApiCall { apiService.getMyNotifications() }

    suspend fun markNotificationRead(notificationId: String) =
        safeApiCall { apiService.markNotificationRead(notificationId) }

    suspend fun markAllNotificationsRead() = safeApiCall { apiService.markAllNotificationsRead() }
}
