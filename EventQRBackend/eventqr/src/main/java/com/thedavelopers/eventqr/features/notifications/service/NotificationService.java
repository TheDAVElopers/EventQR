package com.thedavelopers.eventqr.features.notifications.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse;
import com.thedavelopers.eventqr.features.notifications.model.entity.Notification;
import com.thedavelopers.eventqr.features.notifications.repository.NotificationRepository;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.shared.constants.NotificationStatus;
import com.thedavelopers.eventqr.shared.constants.NotificationType;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.interfaces.TransactionRecordedEvent;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EventRegistrationRepository registrationRepository;
    private final EventStaffAssignmentRepository staffAssignmentRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               EventRegistrationRepository registrationRepository,
                               EventStaffAssignmentRepository staffAssignmentRepository) {
        this.notificationRepository = notificationRepository;
        this.registrationRepository = registrationRepository;
        this.staffAssignmentRepository = staffAssignmentRepository;
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

        if (event.transactionResult() == TransactionResult.REJECTED) {
            List<UUID> staffRecipients = new ArrayList<>();
            if (event.staffUserId() != null) {
                staffRecipients.add(event.staffUserId());
            }
            staffAssignmentRepository.findByEventIdAndActiveTrue(event.eventId())
                .forEach(assignment -> staffRecipients.add(assignment.getStaffUserId()));
            staffRecipients.stream().distinct().forEach(recipientUserId ->
                    createScanRejectedStaffNotification(event, recipientUserId));
        }
    }

    private void createScanRejectedStaffNotification(TransactionRecordedEvent event, UUID recipientUserId) {
        Notification n = new Notification();
        n.setEventId(event.eventId());
        n.setRecipientUserId(recipientUserId);
        n.setTitle("Scan rejected");
        n.setMessage(event.reason() == null ? "A scanner rejected a QR transaction." : event.reason());
        n.setRelatedTransactionId(event.transactionId());
        n.setNotificationType(NotificationType.SCAN_REJECTED);
        n.setStatus(NotificationStatus.SENT);
        notificationRepository.save(n);
    }

    public void createNewRegistrationNotification(UUID eventId, UUID organizerUserId, String eventTitle, String attendeeName) {
        Notification n = new Notification();
        n.setEventId(eventId);
        n.setRecipientUserId(organizerUserId);
        n.setNotificationType(NotificationType.REGISTRATION_NEW);
        n.setTitle("New registration: " + eventTitle);
n.setMessage("Attendee " + attendeeName + " registered for " + eventTitle);
        n.setStatus(NotificationStatus.SENT);
        notificationRepository.save(n);
    }

    public void createCapacityWarningNotification(UUID eventId, UUID organizerUserId, String eventTitle, int count, int capacity) {
        Notification n = new Notification();
        n.setEventId(eventId);
        n.setRecipientUserId(organizerUserId);
        n.setNotificationType(NotificationType.CAPACITY_WARNING);
        n.setTitle("Capacity warning: " + eventTitle);
n.setMessage("Event " + eventTitle + " is at " + count + "/" + capacity + " (>=80%)");
        n.setStatus(NotificationStatus.SENT);
        notificationRepository.save(n);
    }

    public void createCapacityFullNotification(UUID eventId, UUID organizerUserId, String eventTitle, int count, int capacity) {
        Notification n = new Notification();
        n.setEventId(eventId);
        n.setRecipientUserId(organizerUserId);
        n.setNotificationType(NotificationType.CAPACITY_FULL);
        n.setTitle("Capacity full: " + eventTitle);
n.setMessage("Event " + eventTitle + " is full at " + count + "/" + capacity);
        n.setStatus(NotificationStatus.SENT);
        notificationRepository.save(n);
    }

    public void createRewardExhaustedNotification(UUID eventId, UUID organizerUserId, String eventTitle, String rewardName) {
        Notification n = new Notification();
        n.setEventId(eventId);
        n.setRecipientUserId(organizerUserId);
        n.setNotificationType(NotificationType.REWARD_EXHAUSTED);
        n.setTitle("Reward exhausted: " + eventTitle);
n.setMessage("Reward " + rewardName + " has been fully redeemed for " + eventTitle);
        n.setStatus(NotificationStatus.SENT);
        notificationRepository.save(n);
    }

    public void createPointsAdjustedNotification(UUID eventId, UUID organizerUserId, String eventTitle, String attendeeName, int points, String reason, boolean credited) {
        Notification n = new Notification();
        n.setEventId(eventId);
        n.setRecipientUserId(organizerUserId);
        n.setNotificationType(NotificationType.POINTS_ADJUSTED);
        n.setTitle("Points adjusted: " + eventTitle);
n.setMessage((credited ? "Credits: +" : "Deduction: -") + points + " points for " + attendeeName + ". Reason: " + reason);
        n.setStatus(NotificationStatus.SENT);
        notificationRepository.save(n);
    }

    public void createEventApprovedNotification(UUID eventId, UUID requesterUserId, String eventName) {
        Notification n = new Notification();
        n.setEventId(eventId);
        n.setRecipientUserId(requesterUserId);
        n.setNotificationType(NotificationType.EVENT_APPROVED);
        n.setTitle("Event approved: " + eventName);
n.setMessage("Your event request '" + eventName + "' has been approved.");
        n.setStatus(NotificationStatus.SENT);
        notificationRepository.save(n);
    }

    public void createEventRejectedNotification(UUID eventId, UUID requesterUserId, String eventName, String remarks) {
        Notification n = new Notification();
        n.setEventId(eventId);
        n.setRecipientUserId(requesterUserId);
        n.setNotificationType(NotificationType.EVENT_REJECTED);
        n.setTitle("Event rejected: " + eventName);
n.setMessage("Your event request '" + eventName + "' was rejected. Remarks: " + remarks);
        n.setStatus(NotificationStatus.SENT);
        notificationRepository.save(n);
    }

public void createEventStartingSoonNotifications(List<com.thedavelopers.eventqr.features.events.model.entity.Event> events) {
        for (com.thedavelopers.eventqr.features.events.model.entity.Event event : events) {
            save(NotificationType.EVENT_STARTING_SOON, event.getId(), event.getOrganizerUserId(),
                    "Starting soon: " + event.getTitle(), "Event '" + event.getTitle() + "' starts soon.");
            for (UUID attendeeId : attendeeIds(event.getId())) {
                save(NotificationType.EVENT_STARTING_SOON, event.getId(), attendeeId,
                        "Starting soon: " + event.getTitle(), "Event '" + event.getTitle() + "' starts soon.");
            }
        }
    }

    public void createEventCompletedNotifications(List<com.thedavelopers.eventqr.features.events.model.entity.Event> events) {
        for (com.thedavelopers.eventqr.features.events.model.entity.Event event : events) {
            save(NotificationType.EVENT_COMPLETED, event.getId(), event.getOrganizerUserId(),
                    "Completed: " + event.getTitle(), "Event '" + event.getTitle() + "' has completed.");
            for (UUID attendeeId : attendeeIds(event.getId())) {
                save(NotificationType.EVENT_COMPLETED, event.getId(), attendeeId,
                        "Completed: " + event.getTitle(), "Event '" + event.getTitle() + "' has completed.");
            }
        }
    }

    public void createPointsAdjustedForAttendeeNotification(UUID eventId, UUID attendeeUserId, String eventTitle, int points, String reason, boolean credited) {
        Notification n = new Notification();
        n.setEventId(eventId);
        n.setRecipientUserId(attendeeUserId);
        n.setNotificationType(NotificationType.POINTS_ADJUSTED);
        n.setTitle("Points " + (credited ? "credited" : "deducted") + ": " + eventTitle);
        n.setMessage((credited ? "You earned +" : "You were deducted ") + points + " points for " + eventTitle
                + (reason == null || reason.isBlank() ? "" : ". Reason: " + reason));
        n.setStatus(NotificationStatus.SENT);
        notificationRepository.save(n);
    }

    public void createRewardRedeemedNotification(UUID eventId, UUID attendeeUserId, String eventTitle, String rewardName, int pointsRequired) {
        Notification n = new Notification();
        n.setEventId(eventId);
        n.setRecipientUserId(attendeeUserId);
        n.setNotificationType(NotificationType.REWARD_REDEEMED);
        n.setTitle("Reward redeemed: " + eventTitle);
        n.setMessage("You redeemed " + rewardName + " for " + eventTitle + " (-" + pointsRequired + " points)");
        n.setStatus(NotificationStatus.SENT);
        notificationRepository.save(n);
    }

    public void createRewardExhaustedAttendeeNotifications(List<com.thedavelopers.eventqr.features.events.model.entity.Event> events) {
        for (com.thedavelopers.eventqr.features.events.model.entity.Event event : events) {
            for (UUID attendeeId : attendeeIds(event.getId())) {
                Notification n = new Notification();
                n.setEventId(event.getId());
                n.setRecipientUserId(attendeeId);
                n.setNotificationType(NotificationType.REWARD_EXHAUSTED);
                n.setTitle("Reward sold out: " + event.getTitle());
                n.setMessage("A reward for '" + event.getTitle() + "' is sold out. Check the rewards list for alternatives.");
                n.setStatus(NotificationStatus.SENT);
                notificationRepository.save(n);
            }
        }
    }

    public void createRegistrationConfirmationNotification(UUID eventId, UUID attendeeUserId, String eventTitle) {
        Notification n = new Notification();
        n.setEventId(eventId);
        n.setRecipientUserId(attendeeUserId);
        n.setNotificationType(NotificationType.REGISTRATION_NEW);
        n.setTitle("Registered: " + eventTitle);
        n.setMessage("You're registered for '" + eventTitle + "'. Your QR pass is ready.");
        n.setStatus(NotificationStatus.SENT);
        notificationRepository.save(n);
    }

    private List<UUID> attendeeIds(UUID eventId) {
        return registrationRepository.findByEventId(eventId).stream()
                .map(registration -> registration.getAttendeeUserId())
                .distinct()
                .toList();
    }

    private void save(NotificationType type, UUID eventId, UUID recipientUserId, String title, String message) {
        Notification n = new Notification();
        n.setEventId(eventId);
        n.setRecipientUserId(recipientUserId);
        n.setNotificationType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setStatus(NotificationStatus.SENT);
        notificationRepository.save(n);
    }

    public List<NotificationResponse> findByRecipientFiltered(UUID recipientUserId, NotificationStatus status, UUID eventId, NotificationType notificationType) {
        List<Notification> results = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId);
        if (status != null) {
            results = results.stream().filter(n -> n.getStatus() == status).toList();
        }
        if (eventId != null) {
            results = results.stream().filter(n -> eventId.equals(n.getEventId())).toList();
        }
        if (notificationType != null) {
            results = results.stream().filter(n -> notificationType == n.getNotificationType()).toList();
        }
        return results.stream().map(this::toResponse).toList();
    }
    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getEventId(), notification.getRecipientUserId(),
                notification.getTitle(), notification.getMessage(), notification.getStatus(), notification.getRelatedTransactionId(),
                notification.getRelatedRewardRedemptionId(), notification.getReadAt(), notification.getNotificationType(),
                notification.getCreatedAt());
    }
}
