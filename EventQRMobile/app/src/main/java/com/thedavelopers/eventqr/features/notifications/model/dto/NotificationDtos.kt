package com.thedavelopers.eventqr.features.notifications.model.dto

import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.core.api.dto.NotificationType
import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val notificationId: UUID,
    /** Null for a decision that has no event row yet, e.g. a pending request rejected by an admin. */
    val eventId: UUID? = null,
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
