package com.thedavelopers.eventqr.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.features.users.repository.UserTokenRevocationRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;

/** Role gate and after-commit invalidation in UserTokenRevocationChecker, and the race-safe AccessTokenRevoker. */
class AccountAccessRoleAndRevokerTest {

    private UserProfileRepository users;
    private UserTokenRevocationRepository revocations;
    private UserTokenRevocationChecker checker;
    private UserProfile user;

    @BeforeEach
    void setUp() {
        users = mock(UserProfileRepository.class);
        revocations = mock(UserTokenRevocationRepository.class);
        checker = new UserTokenRevocationChecker(users, revocations);
        user = new UserProfile();
        user.setId(UUID.randomUUID());
        user.setStatus(AccountStatus.ACTIVE);
        user.setRole(AccountRole.ADMIN);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(revocations.findByUserId(user.getId())).thenReturn(Optional.empty());
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void tokenRoleMustMatchTheStoredRole() {
        assertThat(checker.isAccessAllowed(user.getId(), Instant.now(), AccountRole.ADMIN)).isTrue();
        assertThat(checker.isAccessAllowed(user.getId(), Instant.now(), AccountRole.ORGANIZER)).isFalse();
        // The refresh-token gate passes no role and ignores it.
        assertThat(checker.isAccessAllowed(user.getId(), Instant.now())).isTrue();
    }

    @Test
    void demotionTakesEffectOnceTheCacheIsInvalidated() {
        assertThat(checker.isAccessAllowed(user.getId(), Instant.now(), AccountRole.ADMIN)).isTrue();
        user.setRole(AccountRole.ATTENDEE);
        // Still cached: this is why every role change calls invalidateAfterCommit.
        assertThat(checker.isAccessAllowed(user.getId(), Instant.now(), AccountRole.ADMIN)).isTrue();

        checker.invalidateAfterCommit(user.getId());

        assertThat(checker.isAccessAllowed(user.getId(), Instant.now(), AccountRole.ADMIN)).isFalse();
        assertThat(checker.isAccessAllowed(user.getId(), Instant.now(), AccountRole.ATTENDEE)).isTrue();
    }

    @Test
    void invalidateAfterCommit_insideATransaction_waitsForTheCommit() {
        assertThat(checker.isAccessAllowed(user.getId(), Instant.now(), AccountRole.ADMIN)).isTrue();
        TransactionSynchronizationManager.initSynchronization();
        user.setRole(AccountRole.STAFF);

        checker.invalidateAfterCommit(user.getId());
        assertThat(checker.isAccessAllowed(user.getId(), Instant.now(), AccountRole.ADMIN))
                .as("before commit the old decision stays cached").isTrue();

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        assertThat(checker.isAccessAllowed(user.getId(), Instant.now(), AccountRole.ADMIN)).isFalse();
        verify(users, times(2)).findById(user.getId());
    }

    @Test
    void revoker_usesTheAtomicUpsert_andReturnsTheEffectiveMarker() {
        Instant later = Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MILLIS);
        when(revocations.upsertNotBackwards(eq(user.getId()), any())).thenReturn(later.toEpochMilli());
        AccessTokenRevoker revoker = new AccessTokenRevoker(revocations, checker);

        Instant marker = revoker.revokeAllIssuedUntilNow(user.getId());

        // A concurrent writer's later marker wins (GREATEST), and the caller mints tokens after that one.
        assertThat(marker).isEqualTo(later);
        verify(revocations).upsertNotBackwards(eq(user.getId()), any());
        verify(revocations, never()).save(any());
    }

    @Test
    void revoker_invalidatesTheCachedDecision() {
        assertThat(checker.isAccessAllowed(user.getId(), Instant.now().minusSeconds(5), AccountRole.ADMIN)).isTrue();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        when(revocations.upsertNotBackwards(eq(user.getId()), any())).thenReturn(now.toEpochMilli());
        when(revocations.findByUserId(user.getId())).thenReturn(Optional.of(
                new com.thedavelopers.eventqr.features.users.model.entity.UserTokenRevocation(user.getId(), now)));

        new AccessTokenRevoker(revocations, checker).revokeAllIssuedUntilNow(user.getId());

        assertThat(checker.isAccessAllowed(user.getId(), now.minusSeconds(5), AccountRole.ADMIN)).isFalse();
    }
}
