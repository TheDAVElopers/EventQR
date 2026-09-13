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

        return new DashboardSummary(availableEventsCount, registeredCount, transactionLogRepository.countByAttendeeUserId(userId),
                pointsCount, unreadNotifications, profile.getFullName(), upcomingEvents);
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
                (int) eventRegistrationRepository.countByEventId(event.getId()),
                userId != null && eventRegistrationRepository.existsByEventIdAndAttendeeUserId(event.getId(), userId)
            ))
            .toList();
    }
}

