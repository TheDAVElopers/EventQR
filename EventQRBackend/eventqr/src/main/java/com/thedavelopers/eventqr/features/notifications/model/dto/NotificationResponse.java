package com.thedavelopers.eventqr.features.notifications.model.dto;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.NotificationStatus;
import com.thedavelopers.eventqr.shared.constants.NotificationType;

public record NotificationResponse(UUID notificationId, UUID eventId, UUID recipientUserId, String title,
                                    String message, NotificationStatus status, UUID relatedTransactionId,
                                    UUID relatedRewardRedemptionId, Instant readAt, NotificationType notificationType,
                                    Instant createdAt) {
}