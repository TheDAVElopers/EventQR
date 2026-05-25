package com.thedavelopers.eventqr.features.notifications.model.dto;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.NotificationStatus;

public record NotificationResponse(UUID notificationId, UUID eventId, UUID recipientUserId, String title,
                                   String message, NotificationStatus status, UUID relatedTransactionId,
                                   UUID relatedRewardRedemptionId, Instant readAt) {
}