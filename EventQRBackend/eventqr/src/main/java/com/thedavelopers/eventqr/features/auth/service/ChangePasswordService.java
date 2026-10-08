package com.thedavelopers.eventqr.features.auth.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.utils.PasswordValidator;

@Service
@Transactional
public class ChangePasswordService {

    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    private final RefreshTokenService refreshTokenService;

    public ChangePasswordService(UserProfileRepository userProfileRepository,
                                 PasswordEncoder passwordEncoder,
                                 RefreshTokenService refreshTokenService) {
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword, String confirmPassword) {
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BadRequestException("Passwords do not match");
        }
        PasswordValidator.requireValid(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userProfileRepository.save(user);
        refreshTokenService.revokeAllForUser(userId);
    }
}
