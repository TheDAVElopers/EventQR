package com.thedavelopers.eventqr.features.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private static final Duration RESET_TTL = Duration.ofMinutes(30);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final long PURGE_INTERVAL_MS = 3_600_000L; // hourly

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailGatewayService emailGatewayService;
    private final String frontendBaseUrl;

    private final RefreshTokenService refreshTokenService;

    public PasswordResetService(PasswordResetTokenRepository passwordResetTokenRepository,
                                UserProfileRepository userProfileRepository,
                                PasswordEncoder passwordEncoder,
                                EmailGatewayService emailGatewayService,
                                RefreshTokenService refreshTokenService,
                                @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.refreshTokenService = refreshTokenService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailGatewayService = emailGatewayService;
        this.frontendBaseUrl = frontendBaseUrl;
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
        String normalizedEmail = normalizeEmail(email);
        // Exact (trimmed, case-insensitive) address first: profiles created by event registration are
        // stored exactly as typed, so a dotted or +tagged address would not match its canonical form.
        // The canonical form remains a fallback so alias spellings still reach the real account.
        Optional<UserProfile> userOpt = userProfileRepository.findByEmailIgnoreCase(email.trim());
        if (userOpt.isEmpty()) {
            userOpt = userProfileRepository.findByEmailIgnoreCase(normalizedEmail);
        }
        if (userOpt.isEmpty()) {
            log.debug("Password reset requested for unknown email, returning silently");
            return;
        }
        UserProfile user = userOpt.get();
        userIdOut.set(user.getId());
        passwordResetTokenRepository.invalidateAllUnusedByUserId(user.getId());
        String token = generateToken();
        Instant expiresAt = Instant.now().plus(RESET_TTL);
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(user.getId());
        // Only the SHA-256 of the token is stored (like refresh tokens): a leaked table row cannot be used
        // as a reset link, and lookups match on the hash rather than comparing secrets in application code.
        resetToken.setToken(RefreshTokenService.sha256(token));
        resetToken.setExpiresAt(expiresAt);
        resetToken.setUsed(false);
        resetToken.setCreatedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);
        String resetLink = frontendBaseUrl + "/reset-password?token=" + token;
        String subject = "EventQR — Reset your password";
        String html = """
                <!doctype html>
                <html><body>
                <p>Hello %s,</p>
                <p>We received a request to reset your password. Click the link below to set a new password. This link expires in 30 minutes.</p>
                <p><a href="%s" style="display:inline-block;padding:10px 20px;background:#6C63FF;color:#FFFFFF;text-decoration:none;border-radius:6px;">Reset Password</a></p>
                <p>If you did not request this, you can safely ignore this email.</p>
                <p>— The EventQR Team</p>
                </body></html>
                """.formatted(user.getFullName(), resetLink);
        sendAfterCommit(user.getId(), user.getEmail(), subject, html);
    }

    /**
     * The token row only exists for real once the surrounding transaction commits. Sending inside the
     * transaction could email a link whose token is then rolled back (dead link), so the mail goes out
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

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return passwordResetTokenRepository
                .findByTokenAndUsedFalseAndExpiresAtAfter(RefreshTokenService.sha256(token.trim()), Instant.now())
                .isPresent();
    }

    public void resetPassword(String token, String newPassword, String confirmPassword) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Reset token is required");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("New password is required");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BadRequestException("Passwords do not match");
        }
        PasswordValidator.requireValid(newPassword);
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenAndUsedFalseAndExpiresAtAfter(RefreshTokenService.sha256(token.trim()), Instant.now())
                .orElseThrow(() -> new BadRequestException("Reset token is invalid or expired"));
        UserProfile user = userProfileRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new BadRequestException("User account not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userProfileRepository.save(user);
        // Resetting a forgotten password is also how a stolen session gets cut off.
        refreshTokenService.revokeAllForUser(user.getId());
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
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
