package com.thedavelopers.eventqr.features.users.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.security.AccessTokenRevoker;
import com.thedavelopers.eventqr.shared.security.UserTokenRevocationChecker;

/** Every role or status change drops the cached access decision after commit; role changes write no marker. */
class UserServiceAccessCacheInvalidationTest {

    private UserProfileRepository users;
    private AccessTokenRevoker revoker;
    private UserTokenRevocationChecker checker;
    private UserService service;
    private UserProfile target;

    @BeforeEach
    void setUp() {
        users = mock(UserProfileRepository.class);
        revoker = mock(AccessTokenRevoker.class);
        checker = mock(UserTokenRevocationChecker.class);
        service = new UserService(users, mock(EventRegistrationRepository.class), mock(TransactionLogRepository.class),
                mock(PasswordEncoder.class), revoker, checker);
        target = new UserProfile();
        target.setId(UUID.randomUUID());
        target.setEmail("t@example.com");
        target.setFullName("Target");
        target.setRole(AccountRole.ADMIN);
        target.setStatus(AccountStatus.ACTIVE);
        when(users.findById(target.getId())).thenReturn(Optional.of(target));
        when(users.save(any(UserProfile.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void superAdminDemotingAnAdmin_invalidatesWithoutWritingAMarker() {
        service.changeRoleResponse(UUID.randomUUID(), AccountRole.SUPER_ADMIN, target.getId(), AccountRole.ATTENDEE);

        verify(checker).invalidateAfterCommit(target.getId());
        verify(revoker, never()).revokeAllIssuedUntilNow(any());
    }

    @Test
    void portRoleChange_eventRequestApproval_invalidates() {
        target.setRole(AccountRole.ATTENDEE);

        service.changeRole(target.getId(), AccountRole.ORGANIZER);

        verify(checker).invalidateAfterCommit(target.getId());
        verify(revoker, never()).revokeAllIssuedUntilNow(any());
    }

    @Test
    void disablingMovesTheMarkerThroughTheRevoker() {
        // The revoker does the race-safe upsert and drops the cached decision after commit
        // (covered in AccountAccessRoleAndRevokerTest).
        service.updateStatus(target.getId(), AccountStatus.SUSPENDED);

        verify(revoker).revokeAllIssuedUntilNow(target.getId());
    }

    @Test
    void softDeleteMovesTheMarkerThroughTheRevoker() {
        service.softDelete(target.getId());

        verify(revoker).revokeAllIssuedUntilNow(target.getId());
    }
}
