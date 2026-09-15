package com.thedavelopers.eventqr.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.model.entity.UserTokenRevocation;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.features.users.repository.UserTokenRevocationRepository;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;

@ExtendWith(MockitoExtension.class)
class UserTokenRevocationCheckerTest {

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserTokenRevocationRepository userTokenRevocationRepository;

    private UserTokenRevocationChecker checker;

    @BeforeEach
    void setUp() {
        checker = new UserTokenRevocationChecker(userProfileRepository, userTokenRevocationRepository);
    }

    private UserProfile profile(AccountStatus status) {
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID());
        profile.setStatus(status);
        return profile;
    }

    @Test
    void activeUserWithoutRevocationMarkerIsAllowed() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile(AccountStatus.ACTIVE)));
        when(userTokenRevocationRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThat(checker.isAccessAllowed(userId, Instant.now())).isTrue();
    }

    @Test
    void tokenIssuedBeforeRevocationIsRejected() {
        UUID userId = UUID.randomUUID();
        Instant revokedAt = Instant.now().minusSeconds(60);
        Instant tokenIssuedAt = Instant.now().minusSeconds(120);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile(AccountStatus.ACTIVE)));
        when(userTokenRevocationRepository.findByUserId(userId))
                .thenReturn(Optional.of(new UserTokenRevocation(userId, revokedAt)));

        assertThat(checker.isAccessAllowed(userId, tokenIssuedAt)).isFalse();
    }

    @Test
    void tokenIssuedAfterRevocationIsAccepted() {
        UUID userId = UUID.randomUUID();
        Instant revokedAt = Instant.now().minusSeconds(120);
        Instant tokenIssuedAt = Instant.now().minusSeconds(60);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile(AccountStatus.ACTIVE)));
        when(userTokenRevocationRepository.findByUserId(userId))
                .thenReturn(Optional.of(new UserTokenRevocation(userId, revokedAt)));

        assertThat(checker.isAccessAllowed(userId, tokenIssuedAt)).isTrue();
    }

    @Test
    void reEnabledAccountDoesNotResurrectPreDisableTokens() {
        UUID userId = UUID.randomUUID();
        Instant revokedAt = Instant.now().minusSeconds(60);
        Instant preDisableToken = Instant.now().minusSeconds(120);
        // Status is ACTIVE again (re-enabled), but the revocation row is retained.
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile(AccountStatus.ACTIVE)));
        when(userTokenRevocationRepository.findByUserId(userId))
                .thenReturn(Optional.of(new UserTokenRevocation(userId, revokedAt)));

        assertThat(checker.isAccessAllowed(userId, preDisableToken)).isFalse();
    }

    @Test
    void inactiveAccountIsRejected() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile(AccountStatus.INACTIVE)));

        assertThat(checker.isAccessAllowed(userId, Instant.now())).isFalse();
    }

    @Test
    void suspendedAccountIsRejected() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile(AccountStatus.SUSPENDED)));

        assertThat(checker.isAccessAllowed(userId, Instant.now())).isFalse();
    }

    @Test
    void unknownUserIsRejected() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(checker.isAccessAllowed(userId, Instant.now())).isFalse();
        verifyNoMoreInteractions(userTokenRevocationRepository);
    }

    @Test
    void tokenWithoutIssuedAtIsRejected() {
        UUID userId = UUID.randomUUID();
        // No repo interaction happens at all: a missing 'iat' is rejected up front.

        assertThat(checker.isAccessAllowed(userId, null)).isFalse();
        verifyNoMoreInteractions(userProfileRepository, userTokenRevocationRepository);
    }

    @Test
    void lookupIsCachedWithinTtlWindow() {
        // Documents the 30s per-JVM cache window: a status flip is NOT visible until
        // the entry expires, which is the documented propagation bound per instance.
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.of(profile(AccountStatus.ACTIVE)));
        when(userTokenRevocationRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThat(checker.isAccessAllowed(userId, Instant.now())).isTrue();
        assertThat(checker.isAccessAllowed(userId, Instant.now())).isTrue();

        // Second call served from the cache: DB consulted exactly once despite the
        // (hypothetical) status flip in between.
        verify(userProfileRepository, org.mockito.Mockito.times(1)).findById(any());
    }
}