package com.thedavelopers.eventqr.features.auth.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.security.JwtService;
import com.thedavelopers.eventqr.shared.utils.PasswordValidator;

@Service
@Transactional
public class ChangePasswordService {

    static final String CHANGED_MESSAGE = "Password has been changed";

    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;

    public ChangePasswordService(UserProfileRepository userProfileRepository,
                                 PasswordEncoder passwordEncoder,
                                 RefreshTokenService refreshTokenService,
                                 JwtService jwtService) {
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
    }

    /**
     * Changes the password, ends every existing session (all refresh tokens and every access token issued until
     * now, including the one used for this call) and returns a fresh session for the calling device, so the caller
     * stays signed in while every other device is signed out. The new tokens are issued strictly after the
     * revocation marker, so they are valid immediately. Clients that ignore the returned session (app builds from
     * before this change) are signed out immediately, because the access token they keep using was revoked.
     */
    public LoginResponse changePassword(UUID userId, String currentPassword, String newPassword, String confirmPassword) {
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IncorrectCurrentPasswordException();
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BadRequestException("Passwords do not match");
        }
        PasswordValidator.requireValid(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userProfileRepository.save(user);
        Instant revokedAt = refreshTokenService.revokeAllForUser(userId);

        String accessToken = jwtService.createTokenIssuedAfter(user.getId(), user.getEmail(), user.getRole(), revokedAt);
        String refreshToken = refreshTokenService.issueForLoginAfter(user.getId(), revokedAt);
        return new LoginResponse(accessToken, user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                CHANGED_MESSAGE, refreshToken);
    }
}
