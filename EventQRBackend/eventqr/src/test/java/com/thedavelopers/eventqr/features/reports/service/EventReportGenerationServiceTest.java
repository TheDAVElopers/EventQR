package com.thedavelopers.eventqr.features.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.reports.model.ReportType;
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportDtos.EventReportFilters;
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportDtos.EventReportResponse;
import com.thedavelopers.eventqr.features.reports.model.dto.EventReportDtos.EventReportSummaryResponse;
import com.thedavelopers.eventqr.features.rewards.model.entity.PointTransaction;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;

class EventReportGenerationServiceTest {

    private final UUID eventId = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private EventRegistrationRepository registrations;
    private TransactionLogRepository logs;
    private PointTransactionRepository points;
    private EventReportGenerationService service;

    @BeforeEach
    void setUp() {
        EventRepository events = mock(EventRepository.class);
        registrations = mock(EventRegistrationRepository.class);
        logs = mock(TransactionLogRepository.class);
        points = mock(PointTransactionRepository.class);
        service = new EventReportGenerationService(events, registrations, logs, points, new ObjectMapper());
        Event event = new Event();
        event.setId(eventId);
        event.setTitle("Expo");
        event.setOrganizerUserId(owner);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
    }

    private EventRegistration reg(UUID user, String name, String email, Integer number, RegistrationStatus status) {
        EventRegistration r = new EventRegistration();
        r.setId(UUID.randomUUID());
        r.setEventId(eventId);
        r.setAttendeeUserId(user);
        r.setAttendeeName(name);
        r.setAttendeeEmail(email);
        r.setRegistrationNumber(number);
        r.setStatus(status);
        r.setRegisteredAt(Instant.now());
        return r;
    }

    private PointTransaction pt(UUID user, int delta, String reason) {
        PointTransaction p = new PointTransaction();
        p.setEventId(eventId);
        p.setAttendeeUserId(user);
        p.setPointsChanged(delta);
        p.setReason(reason);
        p.setOccurredAt(Instant.now());
        return p;
    }

    private static EventReportFilters none() {
        return new EventReportFilters(null, null, null, null);
    }

    @Test
    void nonOrganizerIsForbiddenEvenRightAfterOwnerGeneratedReport() {
        when(registrations.findByEventId(eventId)).thenReturn(List.of(reg(UUID.randomUUID(), "A", "a@x.io", 1, RegistrationStatus.REGISTERED)));
        assertThat(service.generate(owner, eventId, ReportType.ROSTER, none()).rows()).hasSize(1);
        assertThat(service.summary(owner, eventId).registeredCount()).isEqualTo(1);

        assertThatThrownBy(() -> service.generate(stranger, eventId, ReportType.ROSTER, none()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.summary(stranger, eventId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminsAndSuperAdminsMayReadAnyEventButOtherRolesStillNeedOwnership() {
        when(registrations.findByEventId(eventId)).thenReturn(List.of(reg(UUID.randomUUID(), "A", "a@x.io", 1, RegistrationStatus.REGISTERED)));

        for (var role : List.of(com.thedavelopers.eventqr.shared.constants.AccountRole.ADMIN,
                com.thedavelopers.eventqr.shared.constants.AccountRole.SUPER_ADMIN)) {
            assertThat(service.generate(stranger, role, eventId, ReportType.ROSTER, none()).rows()).hasSize(1);
            assertThat(service.summary(stranger, role, eventId).registeredCount()).isEqualTo(1);
        }
        for (var role : List.of(com.thedavelopers.eventqr.shared.constants.AccountRole.ORGANIZER,
                com.thedavelopers.eventqr.shared.constants.AccountRole.STAFF,
                com.thedavelopers.eventqr.shared.constants.AccountRole.ATTENDEE)) {
            assertThatThrownBy(() -> service.generate(stranger, role, eventId, ReportType.ROSTER, none()))
                    .isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> service.summary(stranger, role, eventId)).isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void sameCallTwiceReturnsFreshData() {
        UUID u = UUID.randomUUID();
        when(registrations.findByEventId(eventId))
                .thenReturn(List.of(reg(u, "A", "a@x.io", 1, RegistrationStatus.REGISTERED)))
                .thenReturn(List.of(reg(u, "A", "a@x.io", 1, RegistrationStatus.REGISTERED),
                        reg(UUID.randomUUID(), "B", "b@x.io", 2, RegistrationStatus.REGISTERED)));

        EventReportResponse first = service.generate(owner, eventId, ReportType.ROSTER, none());
        EventReportResponse second = service.generate(owner, eventId, ReportType.ROSTER, none());

        assertThat(first.rows()).hasSize(1);
        assertThat(second.rows()).hasSize(2);

        when(registrations.findByEventId(eventId))
                .thenReturn(List.of(reg(u, "A", "a@x.io", 1, RegistrationStatus.REGISTERED)))
                .thenReturn(List.of());
        EventReportSummaryResponse s1 = service.summary(owner, eventId);
        EventReportSummaryResponse s2 = service.summary(owner, eventId);
        assertThat(s1.registeredCount()).isEqualTo(1);
        assertThat(s2.registeredCount()).isZero();
    }

    @Test
    void pointsChartSumsPositivePointsNotRows() {
        UUID u = UUID.randomUUID();
        when(registrations.findByEventId(eventId)).thenReturn(List.of(reg(u, "A", "a@x.io", 1, RegistrationStatus.ENTERED)));
        when(points.findByEventId(eventId)).thenReturn(List.of(
                pt(u, 50, "Booth"), pt(u, 30, "Booth"), pt(u, 10, "Quiz"), pt(u, -40, "Reward redemption")));

        EventReportResponse report = service.generate(owner, eventId, ReportType.POINTS, none());

        assertThat(report.chartSeries()).containsEntry("Booth", 80L).containsEntry("Quiz", 10L)
                .doesNotContainKey("Reward redemption");
        assertThat(report.total()).isEqualTo(90L);
        assertThat(report.columns()).containsExactly("Name", "Points (+/-)", "Source Activity");
        assertThat(report.rows()).extracting(r -> r.values().get(1)).contains("-40", "50", "30", "10");
    }

    @Test
    void chartKeepsTopFiveAndBucketsRestIntoOtherSoTotalMatches() {
        List<EventRegistration> regs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            regs.add(reg(UUID.randomUUID(), "N" + i, "n" + i + "@x.io", i + 1, RegistrationStatus.REGISTERED));
        }
        when(registrations.findByEventId(eventId)).thenReturn(regs);
        List<PointTransaction> pts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            pts.add(pt(regs.get(i).getAttendeeUserId(), 10 + i, "Activity " + i));
        }
        when(points.findByEventId(eventId)).thenReturn(pts);

        EventReportResponse report = service.generate(owner, eventId, ReportType.POINTS, none());

        assertThat(report.chartSeries()).hasSize(6).containsKey("Other");
        long expected = 0;
        for (int i = 0; i < 8; i++) {
            expected += 10 + i;
        }
        assertThat(report.chartSeries().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(expected);
        assertThat(report.total()).isEqualTo(expected);
        // Other = three smallest: 10 + 11 + 12
        assertThat(report.chartSeries().get("Other")).isEqualTo(33L);
    }

    @Test
    void attendeeSearchMatchesNameRegistrationNumberWithAndWithoutHashAndEmail() {
        when(registrations.findByEventId(eventId)).thenReturn(List.of(
                reg(UUID.randomUUID(), "Alice Cruz", "alice@mail.com", 42, RegistrationStatus.REGISTERED),
                reg(UUID.randomUUID(), "Bob Reyes", "bob@other.org", 7, RegistrationStatus.REGISTERED)));

        assertThat(search("alice")).containsExactly("Alice Cruz");
        assertThat(search("42")).containsExactly("Alice Cruz");
        assertThat(search("#42")).containsExactly("Alice Cruz");
        assertThat(search("OTHER.ORG")).containsExactly("Bob Reyes");
        assertThat(search("zzz")).isEmpty();
    }

    @Test
    void attendeeSearchExactNameOrEmailExcludesLongerSiblingsButPartialStillSubstring() {
        when(registrations.findByEventId(eventId)).thenReturn(List.of(
                reg(UUID.randomUUID(), "QA Tester", "qa@mail.com", 1, RegistrationStatus.REGISTERED),
                reg(UUID.randomUUID(), "QA Tester2", "qa2@mail.com", 2, RegistrationStatus.REGISTERED)));

        assertThat(search("QA Tester")).containsExactly("QA Tester");
        assertThat(search("  qa tester  ")).containsExactly("QA Tester");
        assertThat(search("qa2@mail.com")).containsExactly("QA Tester2");
        assertThat(search("QA Tester2")).containsExactly("QA Tester2");
        assertThat(search("QA Test")).containsExactlyInAnyOrder("QA Tester", "QA Tester2");
    }

    private List<String> search(String query) {
        return service.generate(owner, eventId, ReportType.ROSTER, new EventReportFilters(null, null, query, null))
                .rows().stream().map(r -> r.values().get(0)).toList();
    }

    @Test
    void chartTieBreakIsDeterministicByNameAscending() {
        List<EventRegistration> regs = new ArrayList<>();
        List<PointTransaction> pts = new ArrayList<>();
        // 7 equal-valued categories listed in reverse order: top 5 must be A..E, Other = F + G
        for (char c = 'G'; c >= 'A'; c--) {
            EventRegistration r = reg(UUID.randomUUID(), "N" + c, "n" + c + "@x.io", c - 'A' + 1, RegistrationStatus.REGISTERED);
            regs.add(r);
            pts.add(pt(r.getAttendeeUserId(), 10, "Act " + c));
        }
        when(registrations.findByEventId(eventId)).thenReturn(regs);
        when(points.findByEventId(eventId)).thenReturn(pts);

        EventReportResponse report = service.generate(owner, eventId, ReportType.POINTS, none());

        assertThat(report.chartSeries().keySet())
                .containsExactly("Act A", "Act B", "Act C", "Act D", "Act E", "Other");
        assertThat(report.chartSeries().get("Other")).isEqualTo(20L);
    }

    @Test
    void attendeeQueryLongerThan100CharsIsRejected() {
        assertThatThrownBy(() -> search("x".repeat(101)))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.BadRequestException.class);
        assertThat(search("x".repeat(100))).isEmpty();
    }
}
