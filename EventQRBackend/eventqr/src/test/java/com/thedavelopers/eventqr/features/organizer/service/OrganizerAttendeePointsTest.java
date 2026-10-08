package com.thedavelopers.eventqr.features.organizer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.idprinting.repository.IdTemplateRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerAttendeeResponse;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository.EarnedPointsRow;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.scanning.repository.ScanPurposeRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionRuleRepository;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;

/** Organizer attendee points come from the same derived sum as the staff registration screens. */
class OrganizerAttendeePointsTest {

    private final UUID eventId = UUID.randomUUID();
    private final UUID organizerId = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private EventRegistrationRepository registrations;
    private PointTransactionRepository points;
    private OrganizerService service;

    @BeforeEach
    void setUp() {
        EventRepository events = mock(EventRepository.class);
        UserProfileRepository users = mock(UserProfileRepository.class);
        registrations = mock(EventRegistrationRepository.class);
        points = mock(PointTransactionRepository.class);
        service = new OrganizerService(events, registrations, mock(TransactionLogRepository.class),
                mock(ScanPurposeRepository.class), mock(TransactionRuleRepository.class), mock(RewardRedemptionRepository.class),
                points, mock(EventStaffAssignmentRepository.class), users,
                mock(IdTemplateRepository.class), mock(NotificationService.class), mock(com.thedavelopers.eventqr.features.registrations.service.RegistrationService.class));
        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerUserId(organizerId);
        event.setStatus(EventStatus.ACTIVE);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(users.findById(organizerId)).thenReturn(Optional.of(new com.thedavelopers.eventqr.features.users.model.entity.UserProfile()));
    }

    private EventRegistration registration(UUID attendee, String name) {
        EventRegistration r = new EventRegistration();
        r.setId(UUID.randomUUID());
        r.setEventId(eventId);
        r.setAttendeeUserId(attendee);
        r.setAttendeeName(name);
        r.setAttendeeEmail(name + "@example.com");
        r.setStatus(RegistrationStatus.REGISTERED);
        r.setRegisteredAt(Instant.now());
        r.setPointsEarned(77); // legacy column must be ignored
        return r;
    }

    private static EarnedPointsRow row(UUID event, UUID attendee, Long total) {
        return new EarnedPointsRow() {
            @Override public UUID getEventId() { return event; }
            @Override public UUID getAttendeeUserId() { return attendee; }
            @Override public Long getTotal() { return total; }
        };
    }

    @Test
    void attendeesShowDerivedPointsFromOneBatchedQuery() {
        when(registrations.findByEventId(eventId)).thenReturn(List.of(registration(alice, "alice"), registration(bob, "bob")));
        when(points.sumEarnedPoints(anyCollection(), anyCollection())).thenReturn(List.of(row(eventId, alice, 40L)));

        List<OrganizerAttendeeResponse> result = service.attendees(organizerId, eventId, AccountRole.ORGANIZER);

        assertThat(result).extracting(OrganizerAttendeeResponse::points).containsExactly(40, 0);
        verify(points, times(1)).sumEarnedPoints(anyCollection(), anyCollection());
    }
}
