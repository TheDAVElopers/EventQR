package com.thedavelopers.eventqr.features.notifications.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationRequest;
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse;
import com.thedavelopers.eventqr.features.notifications.model.entity.Notification;
import com.thedavelopers.eventqr.features.notifications.repository.NotificationRepository;
import com.thedavelopers.eventqr.shared.constants.NotificationStatus;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.event.TransactionRecordedEvent;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public NotificationResponse create(NotificationRequest request) {
        Notification notification = new Notification();
        notification.setEventId(request.eventId());
        notification.setRecipientUserId(request.recipientUserId());
        notification.setTitle(request.title());
        notification.setMessage(request.message());
        return toResponse(notificationRepository.save(notification));
    }

    public List<NotificationResponse> findByRecipient(UUID recipientUserId) {
        return notificationRepository.findByRecipientUserId(recipientUserId).stream().map(this::toResponse).toList();
    }

    public List<NotificationResponse> findByEvent(UUID eventId) {
        return notificationRepository.findByEventId(eventId).stream().map(this::toResponse).toList();
    }

    public NotificationResponse markRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setStatus(NotificationStatus.READ);
        notification.setReadAt(Instant.now());
        return toResponse(notificationRepository.save(notification));
    }

    @EventListener
    public void onTransactionRecorded(TransactionRecordedEvent event) {
        Notification notification = new Notification();
        notification.setEventId(event.eventId());
        notification.setRecipientUserId(event.attendeeUserId());
        notification.setTitle(event.transactionResult() == TransactionResult.APPROVED ? "Scan approved" : "Scan rejected");
        notification.setMessage(event.reason() == null ? "Your QR transaction was processed." : event.reason());
        notification.setRelatedTransactionId(event.transactionId());
        notificationRepository.save(notification);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getEventId(), notification.getRecipientUserId(),
                notification.getTitle(), notification.getMessage(), notification.getStatus(), notification.getRelatedTransactionId(),
                notification.getRelatedRewardRedemptionId(), notification.getReadAt());
    }
}