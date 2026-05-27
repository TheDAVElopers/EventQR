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
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;

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

        long registeredCount = registrations.stream()
                .filter(this::isRegistered)
                .count();
        long availableEventsCount = eventRepository.countByStatusIn(PUBLIC_EVENT_STATUSES);
        long pointsCount = attendeePointBalanceRepository.sumPointsByAttendeeUserId(userId);
        List<DashboardUpcomingEvent> upcomingEvents = loadUpcomingEvents(now);

        return new DashboardSummary(availableEventsCount, registeredCount, transactionLogRepository.count(),
                pointsCount, notificationRepository.count(), profile.getFullName(), upcomingEvents);
    }

    private boolean isRegistered(EventRegistration registration) {
        RegistrationStatus status = registration.getStatus();
        return status != RegistrationStatus.CANCELLED && status != RegistrationStatus.NO_SHOW;
    }

    private List<DashboardUpcomingEvent> loadUpcomingEvents(Instant now) {
        return eventRepository.findTop3ByStatusInAndEventStartAtAfterOrderByEventStartAtAsc(PUBLIC_EVENT_STATUSES, now)
            .stream()
            .map(event -> new DashboardUpcomingEvent(
                event.getId(),
                null,
                event.getTitle() == null || event.getTitle().isBlank() ? "Untitled event" : event.getTitle(),
                event.getLocation(),
                event.getEventStartAt(),
                "Upcoming"))
            .toList();
    }
}
