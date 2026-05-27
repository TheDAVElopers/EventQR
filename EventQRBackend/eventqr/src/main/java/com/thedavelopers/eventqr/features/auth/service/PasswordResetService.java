package com.thedavelopers.eventqr.features.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.exception.BadRequestException;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;

@Service
@Transactional
public class PasswordResetService {

    private static final Duration RESET_TTL = Duration.ofMinutes(30);

    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final Map<String, PasswordResetRecord> resets = new ConcurrentHashMap<>();

    public PasswordResetService(UserProfileRepository userProfileRepository, PasswordEncoder passwordEncoder) {
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public PasswordResetResponse requestReset(String email) {
        String normalizedEmail = normalizeEmail(email);
        UserProfile userProfile = userProfileRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email " + normalizedEmail));
        String resetToken = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plus(RESET_TTL);
        resets.put(resetToken, new PasswordResetRecord(userProfile.getEmail(), expiresAt));
        return new PasswordResetResponse(resetToken, expiresAt, "SIMULATED");
    }

    public void resetPassword(String email, String resetToken, String newPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (resetToken == null || resetToken.isBlank()) {
            throw new BadRequestException("Reset token is required");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("New password is required");
        }
        PasswordResetRecord record = resets.get(resetToken);
        if (record == null || record.expiresAt().isBefore(Instant.now()) || !record.email().equalsIgnoreCase(normalizedEmail)) {
            throw new BadRequestException("Reset token is invalid or expired");
        }
        UserProfile userProfile = userProfileRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email " + normalizedEmail));
        userProfile.setPasswordHash(passwordEncoder.encode(newPassword));
        userProfileRepository.save(userProfile);
        resets.remove(resetToken);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required");
        }
        return email.trim().toLowerCase();
    }

    public record PasswordResetResponse(String resetToken, Instant expiresAt, String status) {
    }

    private record PasswordResetRecord(String email, Instant expiresAt) {
    }
}