package com.thedavelopers.eventqr.features.auth.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.auth.model.dto.LoginRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.UnauthorizedException;
import com.thedavelopers.eventqr.shared.security.JwtService;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final String UNUSABLE_HASH_PREFIX = "{UNUSABLE}";
    /**
     * Valid BCrypt hash (strength 10, matching the production {@code BCryptPasswordEncoder}) of a random
     * throwaway value. Verified against on the unknown-email / unusable-hash paths so latency does not
     * reveal whether an account exists.
     */
    private static final String DUMMY_HASH = "$2a$10$W4XyQAN5VLaQjhxZFfzCR.2UXzuPIN2T5nN8yXBRRAhW1phWMcs/q";
    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final String DISABLED_MESSAGE = "Account is disabled. Contact support.";

    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserProfileRepository userProfileRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserProfile userProfile = userProfileRepository.findByEmailIgnoreCase(request.email()).orElse(null);
        if (userProfile == null || userProfile.getPasswordHash() == null || userProfile.getPasswordHash().isBlank()
                || userProfile.getPasswordHash().startsWith(UNUSABLE_HASH_PREFIX)) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), userProfile.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        // Only reached after the password matched, so a distinct 403 reveals nothing to a guesser.
        if (isDisabled(userProfile)) {
            throw new ForbiddenException(DISABLED_MESSAGE);
        }
        String accessToken = jwtService.createToken(userProfile.getId(), userProfile.getEmail(), userProfile.getRole());
        String refreshToken = refreshTokenService.issueForLogin(userProfile.getId());
        return new LoginResponse(accessToken, userProfile.getId(), userProfile.getEmail(), userProfile.getFullName(),
                userProfile.getRole(), "Login successful", refreshToken);
    }

    /**
     * Exchanges a refresh token for a new access token and a new refresh token. The role comes
     * from the database, so role changes are picked up here too. Rotation must commit even when
     * a reused token makes this throw, because that is when the session family gets revoked.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public LoginResponse refresh(String rawRefreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(rawRefreshToken);
        UserProfile userProfile = userProfileRepository.findById(rotated.userId())
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired session"));
        assertUsableLogin(userProfile);
        String accessToken = jwtService.createToken(userProfile.getId(), userProfile.getEmail(), userProfile.getRole());
        return new LoginResponse(accessToken, userProfile.getId(), userProfile.getEmail(), userProfile.getFullName(),
                userProfile.getRole(), "Session refreshed", rotated.refreshToken());
    }

    /** Ends the login a refresh token belongs to, so it cannot be used to get new access tokens. */
    @Transactional
    public void endSession(String rawRefreshToken) {
        refreshTokenService.revokeFamilyOf(rawRefreshToken);
    }

    /**
     * Re-issues a session token for an already authenticated user based on the
     * user's CURRENT role in the database, rather than the (potentially stale)
     * role embedded in the presented JWT. This lets a user pick up a role change
     * (e.g. an attendee upgraded to organizer after their event request is approved)
     * without forcing a logout/login cycle.
     */
    public LoginResponse refreshToken(UUID userId) {
        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Invalid session"));
        assertUsableLogin(userProfile);
        String accessToken = jwtService.createToken(userProfile.getId(), userProfile.getEmail(), userProfile.getRole());
        return new LoginResponse(accessToken, userProfile.getId(), userProfile.getEmail(), userProfile.getFullName(),
                userProfile.getRole(), "Session refreshed");
    }

    /**
     * Rejects login/session-refresh for accounts that have been disabled or suspended.
     * PENDING is intentionally not blocked here: no current registration flow sets a
     * user to PENDING, so it never delineates an unusable account today.
     */
    private void assertUsableLogin(UserProfile userProfile) {
        if (isDisabled(userProfile)) {
            throw new UnauthorizedException(DISABLED_MESSAGE);
        }
    }

    private static boolean isDisabled(UserProfile userProfile) {
        return userProfile.getStatus() == AccountStatus.INACTIVE
                || userProfile.getStatus() == AccountStatus.SUSPENDED;
    }
}
