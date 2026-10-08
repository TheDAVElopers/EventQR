package com.thedavelopers.eventqr.features.dashboard.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardSummary;
import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardSummary.DashboardUpcomingEvent;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.notifications.repository.NotificationRepository;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.rewards.repository.AttendeePointBalanceRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.NotificationStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final List<EventStatus> PUBLIC_EVENT_STATUSES = List.of(EventStatus.APPROVED, EventStatus.ACTIVE);

    private final EventRepository eventRepository;
    private final EventRegistrationRepository eventRegistrationRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final AttendeePointBalanceRepository attendeePointBalanceRepository;
    private final NotificationRepository notificationRepository;
    private final UserProfileRepository userProfileRepository;

    public DashboardService(EventRepository eventRepository,
                            EventRegistrationRepository eventRegistrationRepository,
                            TransactionLogRepository transactionLogRepository,
                            AttendeePointBalanceRepository attendeePointBalanceRepository,
                            NotificationRepository notificationRepository,
                            UserProfileRepository userProfileRepository) {
        this.eventRepository = eventRepository;
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.attendeePointBalanceRepository = attendeePointBalanceRepository;
        this.notificationRepository = notificationRepository;
        this.userProfileRepository = userProfileRepository;
    }

    public DashboardSummary summary(UUID userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        Instant now = Instant.now();
        List<EventRegistration> registrations = eventRegistrationRepository.findByAttendeeUserId(userId);

        // Registered = registrations excluding CANCELLED and NO_SHOW — canonical count,
        // shared by OrganizerService and EventReportGenerationService via RegistrationStatus.isCountedAsRegistered()
        long registeredCount = registrations.stream()
                .filter(reg -> reg.getStatus().isCountedAsRegistered())
                .count();
        long availableEventsCount = eventRepository.countByStatusIn(PUBLIC_EVENT_STATUSES);
        long pointsCount = attendeePointBalanceRepository.sumPointsByAttendeeUserId(userId);
        List<DashboardUpcomingEvent> upcomingEvents = loadUpcomingEvents(now, userId);
        long unreadNotifications = notificationRepository.countByRecipientUserIdAndStatusNot(userId, NotificationStatus.READ);

        long completedCount = countCompleted(registrations);
        long approvedTransactions = transactionLogRepository.countByAttendeeUserIdAndTransactionResult(
                userId, TransactionResult.APPROVED);

        return new DashboardSummary(availableEventsCount, registeredCount, approvedTransactions,
                pointsCount, unreadNotifications, profile.getFullName(), upcomingEvents,
                completedCount, unreadNotifications, pointsCount, availableEventsCount);
    }

    /** Counted registrations whose event has ENDED (by status or end time) or that were attended. */
    private long countCompleted(List<EventRegistration> registrations) {
        List<EventRegistration> counted = registrations.stream()
                .filter(reg -> reg.getStatus().isCountedAsRegistered()).toList();
        if (counted.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        java.util.Map<UUID, com.thedavelopers.eventqr.features.events.model.entity.Event> events = new java.util.HashMap<>();
        eventRepository.findAllById(counted.stream().map(EventRegistration::getEventId).distinct().toList())
                .forEach(e -> events.put(e.getId(), e));
        return counted.stream().filter(reg -> {
            if (reg.getAttendedAt() != null) {
                return true;
            }
            var event = events.get(reg.getEventId());
            return event != null && (event.getStatus() == EventStatus.ENDED
                    || (event.getEventEndAt() != null && !event.getEventEndAt().isAfter(now)));
        }).count();
    }

    private List<DashboardUpcomingEvent> loadUpcomingEvents(Instant now, UUID userId) {
        return eventRepository.findTop3ByStatusInAndEventStartAtAfterOrderByEventStartAtAsc(PUBLIC_EVENT_STATUSES, now)
            .stream()
            .map(event -> new DashboardUpcomingEvent(
                event.getId(),
                null,
                event.getTitle() == null || event.getTitle().isBlank() ? "Untitled event" : event.getTitle(),
                event.getLocation(),
                event.getEventStartAt(),
                "Upcoming",
                event.getCategory(),
                event.getDescription(),
                event.getEventEndAt(),
                event.getCapacity(),
                event.getCurrentAttendeeCount() == null ? 0 : event.getCurrentAttendeeCount(),
                userId != null && eventRegistrationRepository.findFirstByEventIdAndAttendeeUserId(event.getId(), userId)
                    .map(r -> r.getStatus() != RegistrationStatus.CANCELLED).orElse(false)
            ))
            .toList();
    }
}

