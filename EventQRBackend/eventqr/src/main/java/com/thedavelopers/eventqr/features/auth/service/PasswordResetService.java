package com.thedavelopers.eventqr.features.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.thedavelopers.eventqr.features.auth.model.entity.PasswordResetToken;
import com.thedavelopers.eventqr.features.auth.repository.PasswordResetTokenRepository;
import com.thedavelopers.eventqr.features.qremail.service.EmailGatewayService;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.utils.EmailNormalizer;
import com.thedavelopers.eventqr.shared.utils.PasswordValidator;

@Service
@Transactional
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration RESET_TTL = Duration.ofMinutes(15);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;
    static final int MAX_FAILED_ATTEMPTS = 5;
    private static final String INVALID_CODE_MESSAGE = "Reset code is invalid or expired";
    private static final long PURGE_INTERVAL_MS = 3_600_000L; // hourly

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailGatewayService emailGatewayService;

    private final RefreshTokenService refreshTokenService;

    public PasswordResetService(PasswordResetTokenRepository passwordResetTokenRepository,
                                UserProfileRepository userProfileRepository,
                                PasswordEncoder passwordEncoder,
                                EmailGatewayService emailGatewayService,
                                RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailGatewayService = emailGatewayService;
    }

    /**
     * Runs on {@code eventTaskExecutor} so the caller returns the same neutral response, in the same time,
     * whether or not the account exists (no DB writes or Brevo call on the request thread). The proxy applies
     * the class-level transaction inside the async thread, so token invalidation and persistence stay in one
     * transaction. Runs on the dedicated {@code passwordResetExecutor} (caller-runs when saturated, so mail is
     * never dropped). The whole body is guarded: failures log only userId (when known) + exception class, never
     * the email or token, and never reach the global async handler.
     */
    @Async("passwordResetExecutor")
    public void requestReset(String email) {
        AtomicReference<UUID> userId = new AtomicReference<>();
        try {
            doRequestReset(email, userId);
        } catch (Exception e) {
            log.error("Password reset request failed userId={} cause={}", userId.get(), e.getClass().getSimpleName());
        }
    }

    private void doRequestReset(String email, AtomicReference<UUID> userIdOut) {
        Optional<UserProfile> userOpt = findUser(email);
        if (userOpt.isEmpty()) {
            log.debug("Password reset requested for unknown email, returning silently");
            return;
        }
        UserProfile user = userOpt.get();
        userIdOut.set(user.getId());
        passwordResetTokenRepository.invalidateAllUnusedByUserId(user.getId());
        String code = generateCode();
        Instant expiresAt = Instant.now().plus(RESET_TTL);
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(user.getId());
        // Only a hash bound to the user id is stored: a leaked row cannot be replayed as a code, and the
        // UNIQUE constraint on token stays workable because equal codes of different users hash differently.
        resetToken.setToken(hashCode(user.getId(), code));
        resetToken.setExpiresAt(expiresAt);
        resetToken.setUsed(false);
        resetToken.setFailedAttempts(0);
        resetToken.setCreatedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);
        String subject = "EventQR — Your password reset code";
        String html = """
                <!doctype html>
                <html><body>
                <p>Hello %s,</p>
                <p>We received a request to reset your password. Enter this code in the EventQR app to set a new password. It expires in 15 minutes.</p>
                <p style="font-size:28px;font-weight:bold;letter-spacing:6px;">%s</p>
                <p>If you did not request this, you can safely ignore this email.</p>
                <p>— The EventQR Team</p>
                </body></html>
                """.formatted(user.getFullName(), code);
        sendAfterCommit(user.getId(), user.getEmail(), subject, html);
    }

    /**
     * The token row only exists for real once the surrounding transaction commits. Sending inside the
     * transaction could email a code that is then rolled back (dead code), so the mail goes out
     * from afterCommit. Without an active transaction (direct calls) it is sent immediately.
     */
    private void sendAfterCommit(UUID userId, String toEmail, String subject, String html) {
        Runnable send = () -> {
            try {
                emailGatewayService.sendSimple(toEmail, subject, html);
            } catch (Exception e) {
                log.error("Failed to send password reset email for userId={} cause={}", userId, e.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    /**
     * noRollbackFor: a wrong guess throws {@link BadRequestException} but the incremented attempt counter (and the
     * lockout at {@link #MAX_FAILED_ATTEMPTS}) must still commit. The token row is read with a write lock so
     * concurrent guesses cannot all see the same counter. Password-policy errors are raised before the code is
     * examined, so they never burn an attempt.
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public void resetPassword(String email, String code, String newPassword, String confirmPassword) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw new BadRequestException(INVALID_CODE_MESSAGE);
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("New password is required");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BadRequestException("Passwords do not match");
        }
        PasswordValidator.requireValid(newPassword);
        CheckedCode checked = checkCode(email, code);
        UserProfile user = checked.user();
        PasswordResetToken resetToken = checked.token();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userProfileRepository.save(user);
        // Resetting a forgotten password is also how a stolen session gets cut off.
        refreshTokenService.revokeAllForUser(user.getId());
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }

    /**
     * Step one of the two-step reset: confirms the code is currently valid for the email without consuming it
     * (the client resubmits email + code with the new password). A wrong guess counts against the same
     * {@link #MAX_FAILED_ATTEMPTS} budget as {@link #resetPassword} and commits despite the exception. Every failure
     * (unknown email, no active code, expired, locked, wrong) gives the same generic error.
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public void verifyCode(String email, String code) {
        checkCode(email, code);
    }

    private record CheckedCode(UserProfile user, PasswordResetToken token) {
    }

    /**
     * The single code-check path shared by verify and reset: pessimistic-lock lookup of the latest active token,
     * constant-time hash compare, attempt counting and lockout. Must run inside a transaction that commits on
     * {@link BadRequestException}.
     */
    private CheckedCode checkCode(String email, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw new BadRequestException(INVALID_CODE_MESSAGE);
        }
        UserProfile user = findUser(email)
                .orElseThrow(() -> new BadRequestException(INVALID_CODE_MESSAGE));
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findLatestActiveForUpdate(user.getId(), Instant.now())
                .orElseThrow(() -> new BadRequestException(INVALID_CODE_MESSAGE));
        byte[] expected = resetToken.getToken().getBytes(StandardCharsets.UTF_8);
        byte[] presented = hashCode(user.getId(), code.trim()).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, presented)) {
            int attempts = resetToken.getFailedAttempts() + 1;
            resetToken.setFailedAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                resetToken.setUsed(true);
            }
            passwordResetTokenRepository.save(resetToken);
            throw new BadRequestException(INVALID_CODE_MESSAGE);
        }
        return new CheckedCode(user, resetToken);
    }

    private static String hashCode(UUID userId, String code) {
        return RefreshTokenService.sha256(userId + ":" + code);
    }

    private static String generateCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(CODE_BOUND));
    }

    private Optional<UserProfile> findUser(String email) {
        String normalizedEmail = normalizeEmail(email);
        // Exact (trimmed, case-insensitive) address first: profiles created by event registration are
        // stored exactly as typed, so a dotted or +tagged address would not match its canonical form.
        // The canonical form remains a fallback so alias spellings still reach the real account.
        Optional<UserProfile> userOpt = userProfileRepository.findByEmailIgnoreCase(email.trim());
        if (userOpt.isEmpty()) {
            userOpt = userProfileRepository.findByEmailIgnoreCase(normalizedEmail);
        }
        return userOpt;
    }

    /**
     * Hourly sweep that deletes expired reset tokens, consumed or not. Prevents the
     * password_reset_tokens table from growing unbounded (a per-user request can only
     * invalidate the user's own unused tokens; used + expired rows would otherwise
     * accumulate forever). Single-instance scheduler (see {@code EventStatusScheduler}).
     */
    @Scheduled(fixedDelay = PURGE_INTERVAL_MS)
    @Transactional
    public void purgeExpiredTokens() {
        int purged = passwordResetTokenRepository.deleteByExpiresAtBefore(Instant.now());
        if (purged > 0) {
            log.info("Purged {} expired password reset token(s)", purged);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required");
        }
        // Use the same canonical form as the forgot-password rate limiter so the recipient
        // that actually receives the reset email matches the rate-limit key, and so Gmail
        // +tag/dot aliases resolve to the user's real account inbox.
        return EmailNormalizer.canonicalize(email);
    }
}
