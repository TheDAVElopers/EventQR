package com.thedavelopers.eventqr.features.organizer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.idprinting.repository.IdTemplateRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerStaffResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.StaffAssignmentRequest;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.StaffAssignmentUpdateRequest;
import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.scanning.repository.ScanPurposeRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionRuleRepository;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.constants.EventStatus;

class OrganizerServiceStaffDemotionTest {

    private final UUID eventId = UUID.randomUUID();
    private final UUID organizerId = UUID.randomUUID();
    private final UUID staffUserId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();

    private EventRepository eventRepository;
    private EventStaffAssignmentRepository staffRepo;
    private UserProfileRepository userRepo;
    private TransactionLogRepository transactionLogRepo;
    private NotificationService notificationService;
    private OrganizerService organizerService;

    private Event event;
    private UserProfile organizer;
    private UserProfile staffUser;
    private EventStaffAssignment assignment;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        staffRepo = mock(EventStaffAssignmentRepository.class);
        userRepo = mock(UserProfileRepository.class);
        transactionLogRepo = mock(TransactionLogRepository.class);
        notificationService = mock(NotificationService.class);

        organizerService = new OrganizerService(
                eventRepository,
                mock(EventRegistrationRepository.class),
                transactionLogRepo,
                mock(ScanPurposeRepository.class),
                mock(TransactionRuleRepository.class),
                mock(RewardRedemptionRepository.class),
                mock(PointTransactionRepository.class),
                staffRepo,
                userRepo,
                mock(IdTemplateRepository.class),
                notificationService, mock(com.thedavelopers.eventqr.features.registrations.service.RegistrationService.class)
        );

        event = new Event();
        event.setId(eventId);
        event.setTitle("Tech Summit 2026");
        event.setOrganizerUserId(organizerId);
        event.setStatus(EventStatus.APPROVED);
        event.setEventStartAt(Instant.now().plusSeconds(3600));

        organizer = new UserProfile();
        organizer.setId(organizerId);
        organizer.setFullName("Organizer Dave");
        organizer.setEmail("dave@eventqr.io");
        organizer.setRole(AccountRole.ORGANIZER);
        organizer.setStatus(AccountStatus.ACTIVE);

        staffUser = new UserProfile();
        staffUser.setId(staffUserId);
        staffUser.setFullName("Staff Alice");
        staffUser.setEmail("alice@eventqr.io");
        staffUser.setRole(AccountRole.STAFF);
        staffUser.setStatus(AccountStatus.ACTIVE);

        assignment = new EventStaffAssignment();
        assignment.setId(assignmentId);
        assignment.setEventId(eventId);
        assignment.setStaffUserId(staffUserId);
        assignment.setActive(true);
        assignment.setCanScan(true);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(userRepo.findById(organizerId)).thenReturn(Optional.of(organizer));
        when(userRepo.findById(staffUserId)).thenReturn(Optional.of(staffUser));
        when(userRepo.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        when(staffRepo.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(staffRepo.save(any(EventStaffAssignment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void removeStaff_whenUserIsStaffAndHasNoOtherActiveAssignments_demotesToAttendee() {
        // Given: staff user has no other active assignments
        when(staffRepo.existsByStaffUserIdAndActiveTrue(staffUserId)).thenReturn(false);

        // When
        organizerService.removeStaff(organizerId, eventId, AccountRole.ORGANIZER, assignmentId);

        // Then: assignment deactivated
        assertThat(assignment.isActive()).isFalse();
        verify(staffRepo).save(assignment);

        // User role demoted from STAFF to ATTENDEE
        assertThat(staffUser.getRole()).isEqualTo(AccountRole.ATTENDEE);
        verify(userRepo).save(staffUser);

        // Past transactions must never be modified or deleted
        verify(transactionLogRepo, never()).deleteAll(any());
    }

    @Test
    void removeStaff_whenUserHasAnotherActiveAssignment_retainsStaffRole() {
        // Given: staff user still has another active assignment elsewhere
        when(staffRepo.existsByStaffUserIdAndActiveTrue(staffUserId)).thenReturn(true);

        // When
        organizerService.removeStaff(organizerId, eventId, AccountRole.ORGANIZER, assignmentId);

        // Then: assignment deactivated
        assertThat(assignment.isActive()).isFalse();
        verify(staffRepo).save(assignment);

        // User remains STAFF
        assertThat(staffUser.getRole()).isEqualTo(AccountRole.STAFF);
        verify(userRepo, never()).save(staffUser);
    }

    @Test
    void removeStaff_whenUserIsOrganizerOrAdmin_neverDemotedToAttendee() {
        // Given: user assigned as staff is an ORGANIZER
        staffUser.setRole(AccountRole.ORGANIZER);
        when(staffRepo.existsByStaffUserIdAndActiveTrue(staffUserId)).thenReturn(false);

        // When
        organizerService.removeStaff(organizerId, eventId, AccountRole.ORGANIZER, assignmentId);

        // Then
        assertThat(assignment.isActive()).isFalse();
        assertThat(staffUser.getRole()).isEqualTo(AccountRole.ORGANIZER);
        verify(userRepo, never()).save(staffUser);
    }

    @Test
    void updateStaff_deactivatingAssignment_whenNoOtherActiveAssignments_demotesToAttendee() {
        // Given
        when(staffRepo.existsByStaffUserIdAndActiveTrue(staffUserId)).thenReturn(false);

        // When: updating active to false
        StaffAssignmentUpdateRequest request = new StaffAssignmentUpdateRequest(
                false, null, null, null, null, null, null);
        OrganizerStaffResponse response = organizerService.updateStaff(
                organizerId, eventId, AccountRole.ORGANIZER, assignmentId, request);

        // Then
        assertThat(response.active()).isFalse();
        assertThat(staffUser.getRole()).isEqualTo(AccountRole.ATTENDEE);
        verify(userRepo).save(staffUser);
    }

    @Test
    void updateStaff_reactivatingAssignment_promotesAttendeeBackToStaff() {
        // Given: user was demoted to ATTENDEE
        staffUser.setRole(AccountRole.ATTENDEE);
        assignment.setActive(false);
        when(staffRepo.existsByStaffUserIdAndActiveTrue(staffUserId)).thenReturn(true);

        // When: updating active to true
        StaffAssignmentUpdateRequest request = new StaffAssignmentUpdateRequest(
                true, null, null, null, null, null, null);
        OrganizerStaffResponse response = organizerService.updateStaff(
                organizerId, eventId, AccountRole.ORGANIZER, assignmentId, request);

        // Then
        assertThat(response.active()).isTrue();
        assertThat(staffUser.getRole()).isEqualTo(AccountRole.STAFF);
        verify(userRepo).save(staffUser);
    }

    @Test
    void addStaff_whenAddingAttendee_promotesToStaff() {
        // Given: user is currently an ATTENDEE and was previously inactive on event
        staffUser.setRole(AccountRole.ATTENDEE);
        assignment.setActive(false);
        when(userRepo.findByEmailIgnoreCase("alice@eventqr.io")).thenReturn(Optional.of(staffUser));
        when(staffRepo.findByEventIdAndStaffUserId(eventId, staffUserId)).thenReturn(Optional.of(assignment));

        // When
        StaffAssignmentRequest request = new StaffAssignmentRequest(
                staffUserId, "alice@eventqr.io", "Alice", "Scanner", true, false, false, false, null);
        OrganizerStaffResponse response = organizerService.addStaff(
                organizerId, eventId, AccountRole.ORGANIZER, request);

        // Then: promotedToStaff flag is true and role updated to STAFF
        assertThat(response.promotedToStaff()).isTrue();
        assertThat(staffUser.getRole()).isEqualTo(AccountRole.STAFF);
        verify(userRepo).save(staffUser);
    }
}
