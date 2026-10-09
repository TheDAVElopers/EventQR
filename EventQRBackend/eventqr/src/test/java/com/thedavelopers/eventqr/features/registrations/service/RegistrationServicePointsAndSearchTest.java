package com.thedavelopers.eventqr.features.registrations.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.qremail.service.QREmailService;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository.EarnedPointsRow;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort.EventSnapshot;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort;
import com.thedavelopers.eventqr.shared.security.RegistrationRateLimiter;

/**
 * pointsEarned is derived from point_transactions, not from the never-written registration column. The
 * "positive only" filter lives in the JPQL of {@code sumEarnedPoints}, so these tests pin the mapping and the
 * batching; the query itself needs a database to exercise.
 */
class RegistrationServicePointsAndSearchTest {

    private EventRegistrationRepository registrations;
    private PointTransactionRepository points;
    private EventLookupPort events;
    private RegistrationService service;

    private final UUID eventId = UUID.randomUUID();
    private final UUID otherEventId = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        registrations = mock(EventRegistrationRepository.class);
        points = mock(PointTransactionRepository.class);
        events = mock(EventLookupPort.class);
        service = new RegistrationService(registrations, mock(AttendeeDirectoryPort.class), mock(NotificationService.class),
                mock(EventStaffAssignmentRepository.class), events, mock(QrCredentialPort.class), mock(EventService.class),
                mock(QREmailService.class), mock(ApplicationEventPublisher.class), new RegistrationRateLimiter(), points,
                List.of());
        for (UUID id : List.of(eventId, otherEventId)) {
            when(events.findById(id)).thenReturn(Optional.of(new EventSnapshot(id, "Event", "Hall", EventStatus.ACTIVE,
                    Instant.now(), Instant.now(), Instant.now(), Instant.now(), 100, 1, true, UUID.randomUUID())));
        }
    }

    private EventRegistration registration(UUID event, UUID attendee, String name) {
        EventRegistration r = new EventRegistration();
        r.setId(UUID.randomUUID());
        r.setEventId(event);
        r.setAttendeeUserId(attendee);
        r.setAttendeeEmail(name.toLowerCase() + "@example.com");
        r.setAttendeeName(name);
        r.setStatus(RegistrationStatus.REGISTERED);
        r.setPointsEarned(0);
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
    void anAttendeeWithNoPointTransactionsHasZeroPoints() {
        EventRegistration r = registration(eventId, alice, "Alice");
        r.setPointsEarned(99); // the legacy column is ignored
        when(registrations.findById(r.getId())).thenReturn(Optional.of(r));
        when(points.sumEarnedPoints(anyCollection(), anyCollection())).thenReturn(List.of());

        assertThat(service.findOne(r.getId()).pointsEarned()).isZero();
    }

    @Test
    void singleRegistrationUsesExactlyOneQueryScopedToItsEventAndAttendee() {
        EventRegistration r = registration(eventId, alice, "Alice");
        when(registrations.findById(r.getId())).thenReturn(Optional.of(r));
        when(points.sumEarnedPoints(anyCollection(), anyCollection())).thenReturn(List.of(row(eventId, alice, 35L)));

        assertThat(service.findOne(r.getId()).pointsEarned()).isEqualTo(35);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> eventIds = ArgumentCaptor.forClass(Collection.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> attendeeIds = ArgumentCaptor.forClass(Collection.class);
        verify(points, times(1)).sumEarnedPoints(eventIds.capture(), attendeeIds.capture());
        assertThat(Set.copyOf(eventIds.getValue())).containsExactly(eventId);
        assertThat(Set.copyOf(attendeeIds.getValue())).containsExactly(alice);
    }

    @Test
    void aPageOfRegistrationsIsResolvedWithOneBatchedQueryAndEachRowGetsItsOwnTotal() {
        EventRegistration a = registration(eventId, alice, "Alice");
        EventRegistration b = registration(eventId, bob, "Bob");
        EventRegistration c = registration(eventId, UUID.randomUUID(), "Cara");
        Pageable pageable = PageRequest.of(0, 20);
        when(registrations.findByEventId(eventId, pageable)).thenReturn(new PageImpl<>(List.of(a, b, c), pageable, 3));
        when(points.sumEarnedPoints(anyCollection(), anyCollection()))
                .thenReturn(List.of(row(eventId, alice, 50L), row(eventId, bob, 10L)));

        Page<RegistrationResponse> page = service.findByEvent(eventId, pageable);

        assertThat(page.getContent()).extracting(RegistrationResponse::pointsEarned).containsExactly(50, 10, 0);
        verify(points, times(1)).sumEarnedPoints(anyCollection(), anyCollection());
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void myRegistrationsKeepPointsSeparatePerEvent() {
        EventRegistration a = registration(eventId, alice, "Alice");
        EventRegistration b = registration(otherEventId, alice, "Alice");
        Pageable pageable = PageRequest.of(0, 20);
        when(registrations.findByAttendeeUserId(alice, pageable)).thenReturn(new PageImpl<>(List.of(a, b), pageable, 2));
        when(points.sumEarnedPoints(anyCollection(), anyCollection()))
                .thenReturn(List.of(row(eventId, alice, 20L), row(otherEventId, alice, 5L)));

        assertThat(service.findByAttendeeUserId(alice, pageable).getContent())
                .extracting(RegistrationResponse::pointsEarned).containsExactly(20, 5);
        verify(points, times(1)).sumEarnedPoints(anyCollection(), anyCollection());
    }

    @Test
    void aSumThatCameBackNullCountsAsZero() {
        EventRegistration r = registration(eventId, alice, "Alice");
        when(registrations.findById(r.getId())).thenReturn(Optional.of(r));
        when(points.sumEarnedPoints(anyCollection(), anyCollection())).thenReturn(List.of(row(eventId, alice, null)));

        assertThat(service.findOne(r.getId()).pointsEarned()).isZero();
    }

    @Test
    void anEmptyPageRunsNoPointsQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        when(registrations.findByEventId(eventId, pageable)).thenReturn(Page.empty(pageable));

        assertThat(service.findByEvent(eventId, pageable).getContent()).isEmpty();
        verify(points, never()).sumEarnedPoints(any(), any());
    }

    // ----- search -----

    @Test
    void withoutFiltersTheDefaultListingIsUsed() {
        Pageable pageable = PageRequest.of(0, 20);
        when(registrations.findByEventId(eventId, pageable)).thenReturn(Page.empty(pageable));

        service.findByEvent(eventId, "  ", null, pageable);

        verify(registrations).findByEventId(eventId, pageable);
        verify(registrations, never()).searchByEvent(any(), any(), any(), any());
    }

    @Test
    void aSearchTermBecomesAnEscapedLowerCasePatternOverAllStatuses() {
        Pageable pageable = PageRequest.of(1, 10);
        when(registrations.searchByEvent(eq(eventId), any(), any(), eq(pageable))).thenReturn(Page.empty(pageable));

        service.findByEvent(eventId, " #Ja_ne ", null, pageable);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<RegistrationStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(registrations).searchByEvent(eq(eventId), eq("%ja!_ne%"), statuses.capture(), eq(pageable));
        assertThat(Set.copyOf(statuses.getValue())).containsExactlyInAnyOrder(RegistrationStatus.values());
    }

    @Test
    void aStatusOnlyFilterMatchesEveryNameAndNarrowsTheStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        when(registrations.searchByEvent(eq(eventId), any(), any(), eq(pageable))).thenReturn(Page.empty(pageable));

        service.findByEvent(eventId, null, RegistrationStatus.CANCELLED, pageable);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<RegistrationStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(registrations).searchByEvent(eq(eventId), eq("%"), statuses.capture(), eq(pageable));
        assertThat(Set.copyOf(statuses.getValue())).containsExactly(RegistrationStatus.CANCELLED);
    }

    @Test
    void searchResultsAlsoCarryDerivedPoints() {
        EventRegistration a = registration(eventId, alice, "Alice");
        Pageable pageable = PageRequest.of(0, 20);
        when(registrations.searchByEvent(eq(eventId), eq("%ali%"), any(), eq(pageable))).thenReturn(new PageImpl<>(List.of(a), pageable, 1));
        when(points.sumEarnedPoints(anyCollection(), anyCollection())).thenReturn(List.of(row(eventId, alice, 8L)));

        assertThat(service.findByEvent(eventId, "ali", null, pageable).getContent())
                .extracting(RegistrationResponse::pointsEarned).containsExactly(8);
    }

    @Test
    void lookupSnapshotsCarryTheDerivedPointsToo() {
        EventRegistration a = registration(eventId, alice, "Alice");
        EventRegistration b = registration(eventId, bob, "Bob");
        a.setPointsEarned(500);
        when(registrations.findById(a.getId())).thenReturn(Optional.of(a));
        when(registrations.findByEventId(eventId)).thenReturn(List.of(a, b));
        when(points.sumEarnedPoints(anyCollection(), anyCollection())).thenReturn(List.of(row(eventId, alice, 12L)));

        assertThat(service.findById(a.getId())).get().extracting(s -> s.pointsEarned()).isEqualTo(12);
        assertThat(service.listByEventId(eventId)).extracting(s -> s.pointsEarned()).containsExactly(12, 0);
        verify(points, times(2)).sumEarnedPoints(anyCollection(), anyCollection());
    }
}
