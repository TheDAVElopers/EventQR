package com.thedavelopers.eventqr.features.notifications.model.dto

import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.core.api.dto.NotificationType
import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val notificationId: UUID,
    val eventId: UUID,
    val recipientUserId: UUID,
    val title: String,
    val message: String,
    val status: NotificationStatus,
    val relatedTransactionId: UUID? = null,
    val relatedRewardRedemptionId: UUID? = null,
    val readAt: Instant? = null,
    val createdAt: Instant? = null,
    val notificationType: NotificationType? = null,
)
