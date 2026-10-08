package com.thedavelopers.eventqr.features.organizer.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.idprinting.repository.IdTemplateRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.scanning.model.entity.ScanPurpose;
import com.thedavelopers.eventqr.features.scanning.repository.ScanPurposeRepository;
import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionRule;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionRuleRepository;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;

import jakarta.persistence.EntityManager;

class OrganizerScanPurposeDeleteTest {

    private final UUID organizerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID otherEventId = UUID.randomUUID();
    private final UUID purposeId = UUID.randomUUID();

    private EventRepository eventRepository;
    private UserProfileRepository userProfileRepository;
    private ScanPurposeRepository scanPurposeRepository;
    private TransactionRuleRepository transactionRuleRepository;
    private TransactionLogRepository transactionLogRepository;
    private OrganizerService service;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        userProfileRepository = mock(UserProfileRepository.class);
        scanPurposeRepository = mock(ScanPurposeRepository.class);
        transactionRuleRepository = mock(TransactionRuleRepository.class);
        transactionLogRepository = mock(TransactionLogRepository.class);

        service = new OrganizerService(
                eventRepository,
                mock(EventRegistrationRepository.class),
                transactionLogRepository,
                scanPurposeRepository,
                transactionRuleRepository,
                mock(RewardRedemptionRepository.class),
                mock(PointTransactionRepository.class),
                mock(EventStaffAssignmentRepository.class),
                userProfileRepository,
                mock(IdTemplateRepository.class),
                mock(NotificationService.class),
                mock(RegistrationService.class)
        );
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerUserId(organizerId);
        event.setStatus(EventStatus.ACTIVE);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        UserProfile organizer = new UserProfile();
        organizer.setId(organizerId);
        organizer.setRole(AccountRole.ORGANIZER);
        when(userProfileRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

        UserProfile otherUser = new UserProfile();
        otherUser.setId(otherUserId);
        otherUser.setRole(AccountRole.ORGANIZER);
        when(userProfileRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when scan purpose does not exist")
    void deleteScanPurpose_whenScanPurposeNotFound_throwsResourceNotFoundException() {
        when(scanPurposeRepository.findById(purposeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteScanPurpose(organizerId, eventId, AccountRole.ORGANIZER, purposeId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Scan purpose not found");

        verify(scanPurposeRepository, never()).delete(any());
        verify(transactionRuleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when scan purpose belongs to a different event")
    void deleteScanPurpose_whenBelongsToDifferentEvent_throwsResourceNotFoundException() {
        ScanPurpose purpose = new ScanPurpose();
        purpose.setId(purposeId);
        purpose.setEventId(otherEventId);
        when(scanPurposeRepository.findById(purposeId)).thenReturn(Optional.of(purpose));

        assertThatThrownBy(() -> service.deleteScanPurpose(organizerId, eventId, AccountRole.ORGANIZER, purposeId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Scan purpose not found for event");

        verify(scanPurposeRepository, never()).delete(any());
        verify(transactionRuleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should throw ForbiddenException when caller is not the event organizer or an admin")
    void deleteScanPurpose_whenCallerNotOwnerOrAdmin_throwsForbiddenException() {
        assertThatThrownBy(() -> service.deleteScanPurpose(otherUserId, eventId, AccountRole.ORGANIZER, purposeId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Organizer is not assigned to this event");

        verify(scanPurposeRepository, never()).delete(any());
        verify(transactionRuleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should throw ConflictException when transaction logs reference the scan purpose")
    void deleteScanPurpose_whenTransactionLogsExist_throwsConflictException() {
        ScanPurpose purpose = new ScanPurpose();
        purpose.setId(purposeId);
        purpose.setEventId(eventId);
        when(scanPurposeRepository.findById(purposeId)).thenReturn(Optional.of(purpose));
        when(transactionLogRepository.existsByScanPurposeId(purposeId)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteScanPurpose(organizerId, eventId, AccountRole.ORGANIZER, purposeId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Scan purpose cannot be deleted because transaction logs exist");

        verify(transactionLogRepository).existsByScanPurposeId(purposeId);
        verify(transactionRuleRepository, never()).delete(any());
        verify(scanPurposeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should delete corresponding TransactionRule and ScanPurpose when safe to delete")
    void deleteScanPurpose_whenSafeToDelete_deletesTransactionRuleAndScanPurpose() {
        ScanPurpose purpose = new ScanPurpose();
        purpose.setId(purposeId);
        purpose.setEventId(eventId);
        when(scanPurposeRepository.findById(purposeId)).thenReturn(Optional.of(purpose));
        when(transactionLogRepository.existsByScanPurposeId(purposeId)).thenReturn(false);

        TransactionRule rule = new TransactionRule();
        rule.setId(UUID.randomUUID());
        rule.setEventId(eventId);
        rule.setScanPurposeId(purposeId);
        when(transactionRuleRepository.findByEventIdAndScanPurposeId(eventId, purposeId))
                .thenReturn(Optional.of(rule));

        service.deleteScanPurpose(organizerId, eventId, AccountRole.ORGANIZER, purposeId);

        verify(transactionLogRepository).existsByScanPurposeId(purposeId);
        verify(transactionRuleRepository).delete(rule);
        verify(scanPurposeRepository).delete(purpose);
    }

    @Test
    @DisplayName("Should delete ScanPurpose successfully when safe and no TransactionRule exists")
    void deleteScanPurpose_whenSafeToDeleteAndNoRuleExists_deletesScanPurpose() {
        ScanPurpose purpose = new ScanPurpose();
        purpose.setId(purposeId);
        purpose.setEventId(eventId);
        when(scanPurposeRepository.findById(purposeId)).thenReturn(Optional.of(purpose));
        when(transactionLogRepository.existsByScanPurposeId(purposeId)).thenReturn(false);
        when(transactionRuleRepository.findByEventIdAndScanPurposeId(eventId, purposeId))
                .thenReturn(Optional.empty());

        service.deleteScanPurpose(organizerId, eventId, AccountRole.ORGANIZER, purposeId);

        verify(transactionLogRepository).existsByScanPurposeId(purposeId);
        verify(transactionRuleRepository, never()).delete(any());
        verify(scanPurposeRepository).delete(purpose);
    }
}
