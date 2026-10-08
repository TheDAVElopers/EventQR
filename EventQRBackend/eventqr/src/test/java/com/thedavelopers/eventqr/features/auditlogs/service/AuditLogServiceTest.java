package com.thedavelopers.eventqr.features.auditlogs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.thedavelopers.eventqr.features.auditlogs.model.dto.AuditLogResponse;
import com.thedavelopers.eventqr.features.auditlogs.model.entity.AuditLog;
import com.thedavelopers.eventqr.features.auditlogs.repository.AuditLogRepository;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;

class AuditLogServiceTest {

    private AuditLogRepository auditLogRepository;
    private UserProfileRepository userProfileRepository;
    private AuditLogService service;

    private final UUID adminId = UUID.randomUUID();
    private final UUID aliceId = UUID.randomUUID();
    private final UUID bobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        userProfileRepository = mock(UserProfileRepository.class);
        service = new AuditLogService(auditLogRepository, userProfileRepository);
    }

    private AuditLog entry(UUID target) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setAction("ACCOUNT_UPDATED");
        log.setDetails("details");
        log.setPerformedByUserId(adminId);
        log.setPerformedByFullName("Admin");
        log.setTargetUserId(target);
        return log;
    }

    private UserProfile user(UUID id, String name) {
        UserProfile user = new UserProfile();
        user.setId(id);
        user.setFullName(name);
        return user;
    }

    @Test
    void targetNamesAreResolvedWithOneBatchedLookupPerPage() {
        List<AuditLog> logs = List.of(entry(aliceId), entry(bobId), entry(aliceId), entry(null));
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(logs));
        when(userProfileRepository.findAllById(anyIterable())).thenReturn(List.of(user(aliceId, "Alice"), user(bobId, "Bob")));

        Page<AuditLogResponse> page = service.findAll(0, 20);

        assertThat(page.getContent()).extracting(AuditLogResponse::targetUserFullName)
                .containsExactly("Alice", "Bob", "Alice", null);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<UUID>> ids = ArgumentCaptor.forClass(Iterable.class);
        verify(userProfileRepository, times(1)).findAllById(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(aliceId, bobId);
        assertThat(page.getContent().get(0).targetUserId()).isEqualTo(aliceId);
        assertThat(page.getContent().get(0).details()).isEqualTo("details");
    }

    @Test
    void aTargetThatNoLongerExistsYieldsANullName() {
        when(auditLogRepository.findByEventIdOrderByCreatedAtDesc(any())).thenReturn(List.of(entry(aliceId)));
        when(userProfileRepository.findAllById(anyIterable())).thenReturn(List.of());

        assertThat(service.findByEvent(UUID.randomUUID())).singleElement()
                .satisfies(r -> assertThat(r.targetUserFullName()).isNull());
    }

    @Test
    void noLookupHappensWhenNoRowHasATarget() {
        when(auditLogRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(entry(null)));

        service.findAll();

        verify(userProfileRepository, never()).findAllById(anyIterable());
    }

    @Test
    void pagingIsClampedAndSortedNewestFirstWithAStableTiebreak() {
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.findAll(-3, 5000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort()).containsExactly(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

        service.findAll(2, 0);
        verify(auditLogRepository, times(2)).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
    }

    private UserProfile user(UUID id, String name, AccountRole role) {
        UserProfile user = user(id, name);
        user.setRole(role);
        return user;
    }

    @Test
    void aPlainAdminDoesNotSeeNamesOfAdminTargetsButSuperAdminDoes() {
        UUID superId = UUID.randomUUID();
        List<AuditLog> logs = List.of(entry(aliceId), entry(bobId), entry(superId));
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(logs));
        when(userProfileRepository.findAllById(anyIterable())).thenReturn(List.of(
                user(aliceId, "Alice", AccountRole.STAFF), user(bobId, "Bob", AccountRole.ADMIN),
                user(superId, "Root", AccountRole.SUPER_ADMIN)));

        Page<AuditLogResponse> asAdmin = service.findAll(0, 20, null, AccountRole.ADMIN);
        assertThat(asAdmin.getContent()).extracting(AuditLogResponse::targetUserFullName).containsExactly("Alice", null, null);
        assertThat(asAdmin.getContent()).extracting(AuditLogResponse::targetUserId).containsExactly(aliceId, bobId, superId);

        Page<AuditLogResponse> asSuper = service.findAll(0, 20, null, AccountRole.SUPER_ADMIN);
        assertThat(asSuper.getContent()).extracting(AuditLogResponse::targetUserFullName).containsExactly("Alice", "Bob", "Root");
        verify(userProfileRepository, times(2)).findAllById(anyIterable());
    }

    @Test
    void actionPrefixUsesTheDerivedPrefixQueryWithTheSameSort() {
        when(auditLogRepository.findByActionStartingWith(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.findAll(1, 500, "ACCOUNT_", AccountRole.ADMIN);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findByActionStartingWith(org.mockito.ArgumentMatchers.eq("ACCOUNT_"), pageable.capture());
        verify(auditLogRepository, never()).findAll(any(Pageable.class));
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getSort()).containsExactly(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    }

    @Test
    void blankPrefixMeansNoFilter() {
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.findAll(0, 20, "   ", AccountRole.ADMIN);

        verify(auditLogRepository).findAll(any(Pageable.class));
        verify(auditLogRepository, never()).findByActionStartingWith(any(), any());
    }

    @Test
    void aPrefixLongerThan64CharactersIsRejectedAndExactly64IsAccepted() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.findAll(0, 20, "A".repeat(65), AccountRole.ADMIN))
                .isInstanceOf(BadRequestException.class);
        when(auditLogRepository.findByActionStartingWith(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.findAll(0, 20, "A".repeat(64), AccountRole.ADMIN);
        verify(auditLogRepository).findByActionStartingWith(any(), any(Pageable.class));
    }
}
