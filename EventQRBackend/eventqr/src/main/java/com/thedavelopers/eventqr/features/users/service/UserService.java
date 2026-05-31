package com.thedavelopers.eventqr.features.users.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.users.model.dto.UserRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort.AttendeeSnapshot;

@Service
@Transactional
public class UserService implements AttendeeDirectoryPort {

    private static final String UNUSABLE_HASH_PREFIX = "{UNUSABLE}";

    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserProfileRepository userProfileRepository, PasswordEncoder passwordEncoder) {
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse create(UserRequest request) {
        UserProfile userProfile = userProfileRepository.findByEmailIgnoreCase(request.email()).orElseGet(UserProfile::new);
        if (hasRealPassword(userProfile)) {
            throw new ConflictException("User already exists for email " + request.email());
        }
        userProfile.setEmail(request.email().trim().toLowerCase());
        userProfile.setFullName(request.fullName().trim());
        userProfile.setPhoneNumber(request.phoneNumber());
        userProfile.setRole(request.role());
        userProfile.setStatus(AccountStatus.ACTIVE);
        userProfile.setPasswordHash(passwordEncoder.encode(request.password()));
        return toResponse(userProfileRepository.save(userProfile));
    }

    public List<UserResponse> findAllUsers() {
        return userProfileRepository.findAll().stream().map(this::toResponse).toList();
    }

    public UserResponse findOne(UUID userId) {
        return toResponse(requireUser(userId));
    }

    public UserResponse updateProfile(UUID userId, String fullName, String phoneNumber) {
        UserProfile userProfile = requireUser(userId);
        if (fullName != null && !fullName.isBlank()) {
            userProfile.setFullName(fullName.trim());
        }
        userProfile.setPhoneNumber(phoneNumber);
        return toResponse(userProfileRepository.save(userProfile));
    }

    public UserResponse updateAvatar(UUID userId, String avatarFileId) {
        UserProfile userProfile = requireUser(userId);
        userProfile.setAvatarFileId(avatarFileId == null || avatarFileId.isBlank() ? null : avatarFileId.trim());
        return toResponse(userProfileRepository.save(userProfile));
    }

    public UserResponse updateStatus(UUID userId, AccountStatus status) {
        UserProfile userProfile = requireUser(userId);
        userProfile.setStatus(status);
        return toResponse(userProfileRepository.save(userProfile));
    }

    public UserResponse changePassword(UUID userId, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("New password is required");
        }
        UserProfile userProfile = requireUser(userId);
        if (!passwordEncoder.matches(currentPassword, userProfile.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        userProfile.setPasswordHash(passwordEncoder.encode(newPassword));
        return toResponse(userProfileRepository.save(userProfile));
    }

    public void softDelete(UUID userId) {
        UserProfile userProfile = requireUser(userId);
        userProfile.setStatus(AccountStatus.SUSPENDED);
        userProfileRepository.save(userProfile);
    }

    public UserResponse changeRoleResponse(UUID userId, AccountRole role) {
        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        userProfile.setRole(role);
        return toResponse(userProfileRepository.save(userProfile));
    }

    @Override
    public AttendeeSnapshot findOrCreateAttendee(String email, String fullName, String phoneNumber, AccountRole role) {
        UserProfile userProfile = userProfileRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> {
                    UserProfile created = new UserProfile();
                    created.setEmail(email.trim().toLowerCase());
                    created.setFullName(fullName.trim());
                    created.setPhoneNumber(phoneNumber);
                    created.setRole(role);
                    created.setStatus(AccountStatus.ACTIVE);
                    created.setPasswordHash(createUnusablePasswordHash());
                    return userProfileRepository.save(created);
                });
        if (userProfile.getPasswordHash() == null || userProfile.getPasswordHash().isBlank()) {
            userProfile.setPasswordHash(createUnusablePasswordHash());
            userProfileRepository.save(userProfile);
        }
        return userProfile.toSnapshot();
    }

    @Override
    public java.util.Optional<AttendeeSnapshot> findById(UUID userId) {
        return userProfileRepository.findById(userId).map(UserProfile::toSnapshot);
    }

    @Override
    public java.util.Optional<AttendeeSnapshot> findByEmail(String email) {
        return userProfileRepository.findByEmailIgnoreCase(email).map(UserProfile::toSnapshot);
    }

    @Override
    public List<AttendeeSnapshot> listAll() {
        return userProfileRepository.findAll().stream().map(UserProfile::toSnapshot).toList();
    }

    @Override
    public AttendeeSnapshot changeRole(UUID userId, AccountRole role) {
        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        userProfile.setRole(role);
        return userProfileRepository.save(userProfile).toSnapshot();
    }

    private UserResponse toResponse(UserProfile userProfile) {
        return new UserResponse(userProfile.getId(), userProfile.getEmail(), userProfile.getFullName(),
                userProfile.getPhoneNumber(), userProfile.getRole(), userProfile.getStatus(), userProfile.getAvatarFileId());
    }

    private UserProfile requireUser(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private boolean hasRealPassword(UserProfile userProfile) {
        return userProfile.getPasswordHash() != null
                && !userProfile.getPasswordHash().isBlank()
                && !userProfile.getPasswordHash().startsWith(UNUSABLE_HASH_PREFIX);
    }

    private String createUnusablePasswordHash() {
        return UNUSABLE_HASH_PREFIX + passwordEncoder.encode(UUID.randomUUID().toString());
    }
}
