package com.thedavelopers.eventqr.features.organizer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.idprinting.repository.IdTemplateRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerTransactionRuleResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.TransactionRuleRequest;
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

import jakarta.persistence.EntityManager;

class OrganizerTransactionRuleSaveTest {

    private final UUID organizerId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID purposeId = UUID.randomUUID();

    private TransactionRuleRepository rules;
    private EntityManager entityManager;
    private OrganizerService service;

    @BeforeEach
    void setUp() {
        EventRepository events = mock(EventRepository.class);
        UserProfileRepository users = mock(UserProfileRepository.class);
        ScanPurposeRepository purposes = mock(ScanPurposeRepository.class);
        rules = mock(TransactionRuleRepository.class);
        entityManager = mock(EntityManager.class);
        service = new OrganizerService(events, mock(EventRegistrationRepository.class),
                mock(TransactionLogRepository.class), purposes, rules, mock(RewardRedemptionRepository.class),
                mock(PointTransactionRepository.class), mock(EventStaffAssignmentRepository.class), users,
                mock(IdTemplateRepository.class), mock(NotificationService.class), mock(RegistrationService.class));
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerUserId(organizerId);
        event.setStatus(EventStatus.ACTIVE);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(users.findById(organizerId)).thenReturn(Optional.of(new UserProfile()));
        ScanPurpose purpose = new ScanPurpose();
        purpose.setEventId(eventId);
        when(purposes.findById(purposeId)).thenReturn(Optional.of(purpose));
        when(rules.findByEventIdAndScanPurposeId(eventId, purposeId)).thenReturn(Optional.empty());
        when(rules.saveAndFlush(any(TransactionRule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(entityManager.contains(any())).thenReturn(true);
    }

    @Test
    void customLimitsArePersistedAndRefreshedWhenDuplicatesAllowed() {
        OrganizerTransactionRuleResponse response = service.saveTransactionRule(organizerId, eventId,
                AccountRole.ORGANIZER, new TransactionRuleRequest(purposeId, true, true, 15, 3, true, 0));

        assertThat(response.maxUsesPerRegistration()).isEqualTo(3);
        assertThat(response.duplicateWindowMinutes()).isEqualTo(15);
        verify(entityManager).refresh(any(TransactionRule.class));
    }

    @Test
    void duplicatesDisallowedForcesSingleUse() {
        OrganizerTransactionRuleResponse response = service.saveTransactionRule(organizerId, eventId,
                AccountRole.ORGANIZER, new TransactionRuleRequest(purposeId, true, false, 15, 3, true, 0));

        assertThat(response.maxUsesPerRegistration()).isEqualTo(1);
    }
}
