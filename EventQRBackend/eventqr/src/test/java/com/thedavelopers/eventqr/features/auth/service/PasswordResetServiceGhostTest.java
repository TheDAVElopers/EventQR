package com.thedavelopers.eventqr.features.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.thedavelopers.eventqr.features.auth.model.entity.PasswordResetToken;
import com.thedavelopers.eventqr.features.auth.repository.PasswordResetTokenRepository;
import com.thedavelopers.eventqr.features.qremail.service.EmailGatewayService;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;

/**
 * Forgot/reset by emailed 6-digit code. The legitimate owner of a passwordless ("ghost") profile gets a password
 * through it, which proves control of the inbox. Signup is not allowed to do this (see UserServiceRegisterTest).
 */
class PasswordResetServiceGhostTest {

    private static final String EMAIL = "jane.doe+evt@gmail.com";
    private static final String STRONG = "Str0ngPass!word";
    private static final String GENERIC = "Reset code is invalid or expired";

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
        service = new PasswordResetService(tokens, users, encoder, email, refreshTokens);
        ghost = new UserProfile();
        ghost.setId(UUID.randomUUID());
        ghost.setEmail(EMAIL);
        ghost.setFullName("Jane");
        ghost.setRole(AccountRole.ATTENDEE);
        ghost.setStatus(AccountStatus.ACTIVE);
        ghost.setPasswordHash("{UNUSABLE}$2a$10$ghost");
        when(tokens.save(any(PasswordResetToken.class))).thenAnswer(i -> i.getArgument(0));
        when(encoder.encode(anyString())).thenAnswer(i -> "enc(" + i.getArgument(0) + ")");
    }

    @Test
    void forgotPasswordForAPasswordlessProfileSendsAResetCodeToItsStoredAddress() {
        // Stored exactly as typed at event registration; the canonical form would strip dots and the +tag.
        when(users.findByEmailIgnoreCase("Jane.Doe+evt@gmail.com")).thenReturn(Optional.of(ghost));

        service.requestReset("  Jane.Doe+evt@gmail.com ");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(email).sendSimple(eq(EMAIL), anyString(), html.capture());
        assertThat(html.getValue()).containsPattern(">\\d{6}<").doesNotContain("href").doesNotContain("http");
        verify(tokens).save(any(PasswordResetToken.class));
    }

    @Test
    void onlyAUserBoundHashOfTheEmailedCodeIsStored() {
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(ghost));

        service.requestReset(EMAIL);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(email).sendSimple(anyString(), anyString(), html.capture());
        Matcher m = Pattern.compile(">(\\d{6})<").matcher(html.getValue());
        assertThat(m.find()).isTrue();
        String code = m.group(1);
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().getToken())
                .isNotEqualTo(code)
                .isEqualTo(RefreshTokenService.sha256(ghost.getId() + ":" + code));
        assertThat(saved.getValue().getFailedAttempts()).isZero();
        assertThat(saved.getValue().getExpiresAt())
                .isBetween(Instant.now().plusSeconds(14 * 60), Instant.now().plusSeconds(15 * 60 + 5));
        verify(tokens).invalidateAllUnusedByUserId(ghost.getId());
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

    private PasswordResetToken tokenFor(UserProfile user, String code) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setToken(RefreshTokenService.sha256(user.getId() + ":" + code));
        token.setExpiresAt(Instant.now().plusSeconds(600));
        return token;
    }

    /** The repository query only returns the latest unused, unexpired row, so null models used/expired/none. */
    private void activeToken(UserProfile user, PasswordResetToken token) {
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(tokens.findLatestActiveForUpdate(eq(user.getId()), any())).thenReturn(Optional.ofNullable(token));
    }

    @Test
    void resettingWithTheCorrectCodeSetsARealPasswordOnTheGhost() {
        PasswordResetToken token = tokenFor(ghost, "123456");
        activeToken(ghost, token);

        service.resetPassword(EMAIL, "123456", STRONG, STRONG);

        assertThat(ghost.getPasswordHash()).isEqualTo("enc(" + STRONG + ")");
        assertThat(ghost.getPasswordHash()).doesNotStartWith("{UNUSABLE}");
        assertThat(token.isUsed()).isTrue();
        verify(refreshTokens).revokeAllForUser(ghost.getId());
    }

    @Test
    void aWrongCodeIncrementsAttemptsAndTheFifthFailureLocksTheCode() {
        PasswordResetToken token = tokenFor(ghost, "123456");
        activeToken(ghost, token);

        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> service.resetPassword(EMAIL, "000000", STRONG, STRONG))
                    .isInstanceOf(BadRequestException.class).hasMessage(GENERIC);
            assertThat(token.getFailedAttempts()).isEqualTo(i);
            assertThat(token.isUsed()).isFalse();
        }
        assertThatThrownBy(() -> service.resetPassword(EMAIL, "000000", STRONG, STRONG))
                .isInstanceOf(BadRequestException.class).hasMessage(GENERIC);
        assertThat(token.getFailedAttempts()).isEqualTo(5);
        assertThat(token.isUsed()).isTrue();
        verify(users, never()).save(any());
        verify(refreshTokens, never()).revokeAllForUser(any());
    }

    @Test
    void aLockedOrUsedCodeIsRejectedGenerically() {
        activeToken(ghost, null);

        assertThatThrownBy(() -> service.resetPassword(EMAIL, "123456", STRONG, STRONG))
                .isInstanceOf(BadRequestException.class).hasMessage(GENERIC);
        verify(users, never()).save(any());
    }

    @Test
    void anExpiredCodeIsRejectedGenerically() {
        // Expired rows are excluded by the query (expiresAt > now), so none is found.
        activeToken(ghost, null);

        assertThatThrownBy(() -> service.resetPassword(EMAIL, "123456", STRONG, STRONG))
                .isInstanceOf(BadRequestException.class).hasMessage(GENERIC);
        verify(tokens, never()).save(any());
        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        verify(tokens).findLatestActiveForUpdate(eq(ghost.getId()), now.capture());
        assertThat(now.getValue()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void anUnknownEmailGetsTheSameGenericErrorAsAWrongCode() {
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("nobody@example.com", "123456", STRONG, STRONG))
                .isInstanceOf(BadRequestException.class).hasMessage(GENERIC);
        verify(tokens, never()).findLatestActiveForUpdate(any(), any());
    }

    @Test
    void anotherUsersCodeDoesNotWork() {
        UserProfile other = new UserProfile();
        other.setId(UUID.randomUUID());
        PasswordResetToken otherToken = tokenFor(other, "123456");
        PasswordResetToken victimToken = tokenFor(ghost, "654321");
        activeToken(ghost, victimToken);

        // "123456" is valid for the other user, but the victim's stored hash is bound to the victim id.
        assertThatThrownBy(() -> service.resetPassword(EMAIL, "123456", STRONG, STRONG))
                .isInstanceOf(BadRequestException.class).hasMessage(GENERIC);
        assertThat(victimToken.getFailedAttempts()).isEqualTo(1);
        assertThat(otherToken.getToken()).isNotEqualTo(RefreshTokenService.sha256(ghost.getId() + ":123456"));
        verify(users, never()).save(any());
    }

    @Test
    void passwordValidationErrorsDoNotBurnAnAttempt() {
        PasswordResetToken token = tokenFor(ghost, "123456");
        activeToken(ghost, token);

        assertThatThrownBy(() -> service.resetPassword(EMAIL, "000000", STRONG, "Different1!word"))
                .isInstanceOf(BadRequestException.class).hasMessage("Passwords do not match");
        assertThatThrownBy(() -> service.resetPassword(EMAIL, "000000", "weak", "weak"))
                .isInstanceOf(BadRequestException.class);

        assertThat(token.getFailedAttempts()).isZero();
        verify(tokens, never()).findLatestActiveForUpdate(any(), any());
    }

    @Test
    void wrongGuessAttemptsCommitDespiteTheBadRequest() throws Exception {
        Transactional tx = PasswordResetService.class
                .getMethod("resetPassword", String.class, String.class, String.class, String.class)
                .getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.noRollbackFor()).contains(BadRequestException.class);
    }

    @Test
    void theResetEmailIsOnlySentAfterTheTokenTransactionCommits() {
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(ghost));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.requestReset(EMAIL);

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
    void aRolledBackTokenTransactionNeverEmailsADeadCode() {
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(ghost));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.requestReset(EMAIL);
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
