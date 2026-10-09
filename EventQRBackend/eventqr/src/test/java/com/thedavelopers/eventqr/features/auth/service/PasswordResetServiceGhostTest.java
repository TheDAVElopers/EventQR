package com.thedavelopers.eventqr.features.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.thedavelopers.eventqr.features.auth.model.entity.PasswordResetToken;
import com.thedavelopers.eventqr.features.auth.repository.PasswordResetTokenRepository;
import com.thedavelopers.eventqr.features.qremail.service.EmailGatewayService;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;

/**
 * The legitimate owner of a passwordless ("ghost") profile gets a password through forgot/reset, which
 * proves control of the inbox. Signup is not allowed to do this (see UserServiceRegisterTest).
 */
class PasswordResetServiceGhostTest {

    private PasswordResetTokenRepository tokens;
    private UserProfileRepository users;
    private PasswordEncoder encoder;
    private EmailGatewayService email;
    private RefreshTokenService refreshTokens;
    private PasswordResetService service;
    private UserProfile ghost;

    @BeforeEach
    void setUp() {
        tokens = mock(PasswordResetTokenRepository.class);
        users = mock(UserProfileRepository.class);
        encoder = mock(PasswordEncoder.class);
        email = mock(EmailGatewayService.class);
        refreshTokens = mock(RefreshTokenService.class);
        service = new PasswordResetService(tokens, users, encoder, email, refreshTokens, "https://eventqr.app");
        ghost = new UserProfile();
        ghost.setId(UUID.randomUUID());
        ghost.setEmail("jane.doe+evt@gmail.com");
        ghost.setFullName("Jane");
        ghost.setRole(AccountRole.ATTENDEE);
        ghost.setStatus(AccountStatus.ACTIVE);
        ghost.setPasswordHash("{UNUSABLE}$2a$10$ghost");
        when(tokens.save(any(PasswordResetToken.class))).thenAnswer(i -> i.getArgument(0));
        when(encoder.encode(anyString())).thenAnswer(i -> "enc(" + i.getArgument(0) + ")");
    }

    @Test
    void forgotPasswordForAPasswordlessProfileSendsAResetLinkToItsStoredAddress() {
        // Stored exactly as typed at event registration; the canonical form would strip dots and the +tag.
        when(users.findByEmailIgnoreCase("Jane.Doe+evt@gmail.com")).thenReturn(Optional.of(ghost));

        service.requestReset("  Jane.Doe+evt@gmail.com ");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(email).sendSimple(eq("jane.doe+evt@gmail.com"), anyString(), html.capture());
        assertThat(html.getValue()).contains("https://eventqr.app/reset-password?token=");
        verify(tokens).save(any(PasswordResetToken.class));
    }

    @Test
    void onlyTheHashOfTheEmailedTokenIsStored() {
        when(users.findByEmailIgnoreCase("jane.doe+evt@gmail.com")).thenReturn(Optional.of(ghost));

        service.requestReset("jane.doe+evt@gmail.com");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(email).sendSimple(anyString(), anyString(), html.capture());
        java.util.regex.Matcher link = java.util.regex.Pattern.compile("token=([0-9a-f]{64})").matcher(html.getValue());
        assertThat(link.find()).isTrue();
        String rawToken = link.group(1);
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().getToken())
                .isNotEqualTo(rawToken)
                .isEqualTo(RefreshTokenService.sha256(rawToken));
    }

    @Test
    void anAliasSpellingStillFindsTheAccountThroughTheCanonicalFallback() {
        UserProfile canonical = new UserProfile();
        canonical.setId(UUID.randomUUID());
        canonical.setEmail("janedoe@gmail.com");
        canonical.setFullName("Jane");
        when(users.findByEmailIgnoreCase("jane.doe+x@gmail.com")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("janedoe@gmail.com")).thenReturn(Optional.of(canonical));

        service.requestReset("jane.doe+x@gmail.com");

        verify(email).sendSimple(eq("janedoe@gmail.com"), anyString(), anyString());
    }

    @Test
    void anUnknownEmailReturnsSilentlyWithoutATokenOrAnEmail() {
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        service.requestReset("nobody@example.com");

        verify(tokens, never()).save(any());
        verify(email, never()).sendSimple(anyString(), anyString(), anyString());
    }

    @Test
    void resettingThroughTheEmailedTokenSetsARealPasswordOnTheGhost() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(ghost.getId());
        token.setToken(RefreshTokenService.sha256("tok"));
        token.setExpiresAt(Instant.now().plusSeconds(600));
        // Looked up by the hash of the presented token, never by the raw value.
        when(tokens.findByTokenAndUsedFalseAndExpiresAtAfter(eq(RefreshTokenService.sha256("tok")), any()))
                .thenReturn(Optional.of(token));
        when(users.findById(ghost.getId())).thenReturn(Optional.of(ghost));

        service.resetPassword("tok", "Str0ngPass!word", "Str0ngPass!word");

        assertThat(ghost.getPasswordHash()).isEqualTo("enc(Str0ngPass!word)");
        assertThat(ghost.getPasswordHash()).doesNotStartWith("{UNUSABLE}");
        assertThat(token.isUsed()).isTrue();
        verify(refreshTokens).revokeAllForUser(ghost.getId());
    }

    @Test
    void theResetEmailIsOnlySentAfterTheTokenTransactionCommits() {
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(ghost));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.requestReset("jane.doe+evt@gmail.com");

            verify(email, never()).sendSimple(anyString(), anyString(), anyString());

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            verify(email).sendSimple(eq(ghost.getEmail()), anyString(), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void aRolledBackTokenTransactionNeverEmailsADeadLink() {
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(ghost));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.requestReset("jane.doe+evt@gmail.com");
            // Rollback: Spring runs afterCompletion(STATUS_ROLLED_BACK) and never afterCommit.
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(email, never()).sendSimple(anyString(), anyString(), anyString());
    }
}
