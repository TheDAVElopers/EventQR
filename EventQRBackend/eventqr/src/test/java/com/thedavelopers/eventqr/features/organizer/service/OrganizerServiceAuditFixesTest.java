package com.thedavelopers.eventqr.features.organizer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.features.events.model.dto.EventRequest;
import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.idprinting.repository.IdTemplateRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerAttendeeResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerDashboardResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerStaffResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.StaffAssignmentRequest;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.StaffAssignmentUpdateRequest;
import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.rewards.model.entity.RewardRedemption;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.scanning.repository.ScanPurposeRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionRuleRepository;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.RedemptionStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;

class OrganizerServiceAuditFixesTest {

    private final UUID eventId = UUID.randomUUID();
    private final UUID organizerId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();
    private final Instant start = Instant.parse("2030-01-01T00:00:00Z");
    private EventRegistrationRepository registrations;
    private RewardRedemptionRepository redemptions;
    private EventStaffAssignmentRepository staffRepo;
    private UserProfileRepository users;
    private EventRepository events;
    private Event event;
    private NotificationService notificationService;
    private com.thedavelopers.eventqr.features.registrations.service.RegistrationService registrationService;
    private OrganizerService service;

    @BeforeEach
    void setUp() {
        events = mock(EventRepository.class);
        users = mock(UserProfileRepository.class);
        registrations = mock(EventRegistrationRepository.class);
        redemptions = mock(RewardRedemptionRepository.class);
        staffRepo = mock(EventStaffAssignmentRepository.class);
        notificationService = mock(NotificationService.class);
        registrationService = mock(com.thedavelopers.eventqr.features.registrations.service.RegistrationService.class);
        service = new OrganizerService(events, registrations, mock(TransactionLogRepository.class),
                mock(ScanPurposeRepository.class), mock(TransactionRuleRepository.class), redemptions,
                mock(PointTransactionRepository.class), staffRepo, users, mock(IdTemplateRepository.class),
                notificationService, registrationService);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "entityManager", mock(jakarta.persistence.EntityManager.class));
        event = new Event();
        event.setId(eventId);
        event.setTitle("Expo");
        event.setOrganizerUserId(organizerId);
        event.setStatus(EventStatus.APPROVED);
        event.setEventStartAt(start);
        event.setCapacity(10);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(events.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
        when(users.findById(organizerId)).thenReturn(Optional.of(user(organizerId, AccountRole.ORGANIZER)));
        when(users.findById(strangerId)).thenReturn(Optional.of(user(strangerId, AccountRole.ORGANIZER)));
    }

    private UserProfile user(UUID id, AccountRole role) {
        UserProfile u = new UserProfile();
        u.setId(id);
        u.setFullName("User " + id.toString().substring(0, 4));
        u.setEmail(id + "@x.io");
        u.setRole(role);
        return u;
    }

    private EventRegistration reg(RegistrationStatus status, boolean attended) {
        EventRegistration r = new EventRegistration();
        r.setId(UUID.randomUUID());
        r.setEventId(eventId);
        r.setAttendeeUserId(UUID.randomUUID());
        r.setAttendeeName("n");
        r.setAttendeeEmail("n@x.io");
        r.setStatus(status);
        r.setRegisteredAt(Instant.now());
        r.setAttendedAt(attended ? Instant.now() : null);
        return r;
    }

    private EventRequest capacityRequest(int capacity) {
        return new EventRequest("Expo", null, null, null, null, null, null, start, null, capacity, false);
    }

    @Test
    void capacityEditCountsOnlyRegisteredStatuses() {
        when(registrations.findByEventId(eventId)).thenReturn(List.of(
                reg(RegistrationStatus.REGISTERED, false), reg(RegistrationStatus.ENTERED, true),
                reg(RegistrationStatus.EXITED, true), reg(RegistrationStatus.CANCELLED, false),
                reg(RegistrationStatus.NO_SHOW, false)));

        // 3 counted as registered: capacity 3 is fine even though 5 rows exist
        service.updateEvent(organizerId, eventId, AccountRole.ORGANIZER, capacityRequest(3));
        assertThat(event.getCapacity()).isEqualTo(3);

        assertThatThrownBy(() -> service.updateEvent(organizerId, eventId, AccountRole.ORGANIZER, capacityRequest(2)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Capacity cannot be less than current registered attendees (3)");
    }

    @Test
    void attendeeStatusDerivesFromRegistrationStatusAndExitedIsNotCheckedIn() {
        when(registrations.findByEventId(eventId)).thenReturn(List.of(
                reg(RegistrationStatus.ENTERED, true), reg(RegistrationStatus.EXITED, true),
                reg(RegistrationStatus.CANCELLED, false), reg(RegistrationStatus.NO_SHOW, false),
                reg(RegistrationStatus.REGISTERED, false)));

        List<OrganizerAttendeeResponse> result = service.attendees(organizerId, eventId, AccountRole.ORGANIZER);

        assertThat(result).extracting(OrganizerAttendeeResponse::currentEventStatus)
                .containsExactly("Checked In", "Exited", "Cancelled", "No Show", "Registered");
        assertThat(result).extracting(OrganizerAttendeeResponse::countedAsRegistered)
                .containsExactly(true, true, false, false, true);
    }

    @Test
    void dashboardExposesRegistrationsRedemptionsAndPoints() {
        when(events.findByOrganizerUserId(organizerId)).thenReturn(List.of(event));
        when(registrations.findByEventId(eventId)).thenReturn(List.of(
                reg(RegistrationStatus.REGISTERED, false), reg(RegistrationStatus.CANCELLED, false)));
        RewardRedemption redeemed = new RewardRedemption();
        redeemed.setStatus(RedemptionStatus.REDEEMED);
        RewardRedemption pending = new RewardRedemption();
        pending.setStatus(RedemptionStatus.PENDING);
        when(redemptions.findByEventId(eventId)).thenReturn(List.of(redeemed, pending));

        OrganizerDashboardResponse d = service.dashboard(organizerId);

        assertThat(d.totalRegistrations()).isEqualTo(1);
        assertThat(d.totalAttendees()).isEqualTo(1);
        assertThat(d.rewardRedemptions()).isEqualTo(1);
        assertThat(d.totalPointsAwarded()).isZero();
    }

    @Test
    void addStaffSetsExplicitFlagsAndReportsPromotion() {
        UserProfile attendee = user(UUID.randomUUID(), AccountRole.ATTENDEE);
        when(users.findById(attendee.getId())).thenReturn(Optional.of(attendee));
        when(staffRepo.findByEventIdAndStaffUserId(eventId, attendee.getId())).thenReturn(Optional.empty());
        when(staffRepo.save(any(EventStaffAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizerStaffResponse r = service.addStaff(organizerId, eventId, AccountRole.ORGANIZER,
                new StaffAssignmentRequest(attendee.getId(), null, null, null, null, true, true, false, null));

        assertThat(r.promotedToStaff()).isTrue();
        assertThat(attendee.getRole()).isEqualTo(AccountRole.STAFF);
        assertThat(r.canScan()).isTrue();
        assertThat(r.canPrintId()).isTrue();
        assertThat(r.canViewLogs()).isTrue();
        assertThat(r.canManageRewards()).isFalse();
    }

    @Test
    void addStaffDefaultsAndNoPromotionForExistingStaff() {
        UserProfile staff = user(UUID.randomUUID(), AccountRole.STAFF);
        when(users.findById(staff.getId())).thenReturn(Optional.of(staff));
        when(staffRepo.findByEventIdAndStaffUserId(eventId, staff.getId())).thenReturn(Optional.empty());
        when(staffRepo.save(any(EventStaffAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizerStaffResponse r = service.addStaff(organizerId, eventId, AccountRole.ORGANIZER,
                new StaffAssignmentRequest(staff.getId(), null, null, null, null, null, null, null, null));

        assertThat(r.promotedToStaff()).isFalse();
        assertThat(r.canScan()).isTrue();
        assertThat(r.canPrintId()).isFalse();
        assertThat(r.canViewLogs()).isFalse();
        assertThat(r.canManageRewards()).isFalse();
    }

    @Test
    void updateStaffPermissionsAllowedForOwnerAndForbiddenForOtherOrganizer() {
        UUID assignmentId = UUID.randomUUID();
        EventStaffAssignment assignment = new EventStaffAssignment();
        assignment.setId(assignmentId);
        assignment.setEventId(eventId);
        assignment.setStaffUserId(UUID.randomUUID());
        assignment.setCanScan(true);
        when(staffRepo.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(staffRepo.save(any(EventStaffAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizerStaffResponse r = service.updateStaff(organizerId, eventId, AccountRole.ORGANIZER, assignmentId,
                new StaffAssignmentUpdateRequest(null, null, null, true, true, true, null));
        assertThat(r.canScan()).isTrue();
        assertThat(r.canPrintId()).isTrue();
        assertThat(r.canViewLogs()).isTrue();
        assertThat(r.canManageRewards()).isTrue();

        assertThatThrownBy(() -> service.updateStaff(strangerId, eventId, AccountRole.ORGANIZER, assignmentId,
                new StaffAssignmentUpdateRequest(null, null, null, false, false, false, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    private EventStaffAssignment existingAssignment(UUID assignmentId) {
        EventStaffAssignment a = new EventStaffAssignment();
        a.setId(assignmentId);
        a.setEventId(eventId);
        a.setStaffUserId(UUID.randomUUID());
        a.setCanScan(true);
        when(staffRepo.findById(assignmentId)).thenReturn(Optional.of(a));
        when(staffRepo.save(any(EventStaffAssignment.class))).thenAnswer(inv -> inv.getArgument(0));
        return a;
    }

    @Test
    void unknownOrSubstringPermissionTokenIsRejectedWith400() {
        UUID id = UUID.randomUUID();
        EventStaffAssignment a = existingAssignment(id);
        for (String bad : List.of("catalog", "scanner", "Rewards", "")) {
            assertThatThrownBy(() -> service.updateStaff(organizerId, eventId, AccountRole.ORGANIZER, id,
                    new StaffAssignmentUpdateRequest(null, null, null, null, null, null, List.of(bad))))
                    .isInstanceOf(BadRequestException.class);
        }
        assertThat(a.isCanViewLogs()).isFalse();
        assertThat(a.isCanScan()).isTrue();
    }

    @Test
    void addStaffWithUnknownTokenIs400AndSavesNothing() {
        UserProfile staff = user(UUID.randomUUID(), AccountRole.STAFF);
        when(users.findById(staff.getId())).thenReturn(Optional.of(staff));
        assertThatThrownBy(() -> service.addStaff(organizerId, eventId, AccountRole.ORGANIZER,
                new StaffAssignmentRequest(staff.getId(), null, null, null, null, null, null, null, List.of("catalog"))))
                .isInstanceOf(BadRequestException.class);
        org.mockito.Mockito.verify(staffRepo, org.mockito.Mockito.never()).save(any(EventStaffAssignment.class));
    }

    @Test
    void listOnlyUpdateNeverClearsCanScanAndMapsKnownTokens() {
        UUID id = UUID.randomUUID();
        EventStaffAssignment a = existingAssignment(id);
        service.updateStaff(organizerId, eventId, AccountRole.ORGANIZER, id,
                new StaffAssignmentUpdateRequest(null, null, null, null, null, null,
                        List.of("print id", "Manage Rewards", "View attendee details")));
        assertThat(a.isCanScan()).isTrue();
        assertThat(a.isCanPrintId()).isTrue();
        assertThat(a.isCanViewLogs()).isFalse();
        assertThat(a.isCanManageRewards()).isTrue();
    }

    @Test
    void explicitFlagWinsOverPermissionList() {
        UUID id = UUID.randomUUID();
        EventStaffAssignment a = existingAssignment(id);
        service.updateStaff(organizerId, eventId, AccountRole.ORGANIZER, id,
                new StaffAssignmentUpdateRequest(null, null, false, false, null, null, List.of("Scan QR", "Print ID")));
        assertThat(a.isCanScan()).isFalse();
        assertThat(a.isCanPrintId()).isFalse();
    }

    @Test
    void organizerCanCancelARegisteredAttendeeOfAnActiveEventUsingTheOrganizerPath() {
        event.setStatus(EventStatus.ACTIVE);
        event.setEventStartAt(Instant.now().minusSeconds(3_600));
        EventRegistration r = reg(RegistrationStatus.REGISTERED, false);
        when(registrations.findByEventId(eventId)).thenReturn(List.of(r));
        when(registrations.findById(r.getId())).thenReturn(Optional.of(r));

        service.updateAttendeeStatus(organizerId, eventId, AccountRole.ORGANIZER, r.getAttendeeUserId(), "CANCELLED");

        org.mockito.Mockito.verify(registrationService).cancelAsOrganizer(r.getId());
        org.mockito.Mockito.verify(registrationService, org.mockito.Mockito.never()).cancel(any(), any());
    }

    @Test
    void organizerCancellingAttendeeSendsNotification() {
        EventRegistration r = reg(RegistrationStatus.REGISTERED, false);
        when(registrations.findByEventId(eventId)).thenReturn(List.of(r));
        when(registrations.findById(r.getId())).thenReturn(Optional.of(r));

        service.updateAttendeeStatus(organizerId, eventId, AccountRole.ORGANIZER, r.getAttendeeUserId(), "CANCELLED");

        org.mockito.Mockito.verify(registrationService).cancelAsOrganizer(r.getId());
        org.mockito.Mockito.verify(notificationService).createRegistrationCancelledByOrganizerNotification(
                eventId, r.getAttendeeUserId(), "Expo");
    }
}
