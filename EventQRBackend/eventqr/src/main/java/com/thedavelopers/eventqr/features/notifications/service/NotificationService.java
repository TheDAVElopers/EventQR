package com.thedavelopers.eventqr.features.notifications.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse;
import com.thedavelopers.eventqr.features.notifications.model.entity.Notification;
import com.thedavelopers.eventqr.features.notifications.repository.NotificationRepository;
import com.thedavelopers.eventqr.shared.constants.NotificationStatus;
import com.thedavelopers.eventqr.shared.constants.NotificationType;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.interfaces.TransactionRecordedEvent;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<NotificationResponse> findByRecipient(UUID recipientUserId) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId).stream().map(this::toResponse).toList();
    }

    public List<NotificationResponse> findByRecipientAndStatus(UUID recipientUserId, NotificationStatus status) {
        return notificationRepository.findByRecipientUserIdAndStatusOrderByCreatedAtDesc(recipientUserId, status).stream().map(this::toResponse).toList();
    }

    public NotificationResponse createStaffAssignmentNotification(UUID eventId, UUID recipientUserId, String eventTitle, String organizerName) {
        Notification notification = new Notification();
        notification.setEventId(eventId);
        notification.setRecipientUserId(recipientUserId);
        notification.setNotificationType(NotificationType.STAFF_ASSIGNMENT);
        notification.setTitle("Assigned to " + eventTitle);
        notification.setMessage("You've been assigned staff for " + eventTitle + " by " + organizerName);
        notification.setStatus(NotificationStatus.SENT);
        return toResponse(notificationRepository.save(notification));
    }

    public NotificationResponse createStaffRemovalNotification(UUID eventId, UUID recipientUserId, String eventTitle, String organizerName) {
        Notification notification = new Notification();
        notification.setEventId(eventId);
        notification.setRecipientUserId(recipientUserId);
        notification.setNotificationType(NotificationType.STAFF_ASSIGNMENT);
        notification.setTitle("Removed from " + eventTitle);
        notification.setMessage("You've been removed as staff for " + eventTitle + " by " + organizerName);
        notification.setStatus(NotificationStatus.SENT);
        return toResponse(notificationRepository.save(notification));
    }

    public NotificationResponse findOne(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        return toResponse(notification);
    }

    public NotificationResponse markRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setStatus(NotificationStatus.READ);
        notification.setReadAt(Instant.now());
        return toResponse(notificationRepository.save(notification));
    }

    public void markAllRead(UUID recipientUserId) {
        notificationRepository.findByRecipientUserId(recipientUserId).forEach(notification -> {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        });
    }

    public void delete(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notificationRepository.delete(notification);
    }

    @EventListener
    public void onTransactionRecorded(TransactionRecordedEvent event) {
        Notification notification = new Notification();
        notification.setEventId(event.eventId());
        notification.setRecipientUserId(event.attendeeUserId());
        notification.setTitle(event.transactionResult() == TransactionResult.APPROVED ? "Scan approved" : "Scan rejected");
        notification.setMessage(event.reason() == null ? "Your QR transaction was processed." : event.reason());
        notification.setRelatedTransactionId(event.transactionId());
        notification.setNotificationType(event.transactionResult() == TransactionResult.APPROVED
                ? NotificationType.SCAN_APPROVED : NotificationType.SCAN_REJECTED);
        notificationRepository.save(notification);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getEventId(), notification.getRecipientUserId(),
                notification.getTitle(), notification.getMessage(), notification.getStatus(), notification.getRelatedTransactionId(),
                notification.getRelatedRewardRedemptionId(), notification.getReadAt(), notification.getNotificationType(),
                notification.getCreatedAt());
    }
}
