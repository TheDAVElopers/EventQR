package com.thedavelopers.eventqr.features.users.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.model.entity.UserTokenRevocation;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.features.users.repository.UserTokenRevocationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort.AttendeeSnapshot;

@Service
@Transactional
public class UserService implements AttendeeDirectoryPort {

    private static final String UNUSABLE_HASH_PREFIX = "{UNUSABLE}";

    private final UserProfileRepository userProfileRepository;
    private final EventRegistrationRepository eventRegistrationRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserTokenRevocationRepository userTokenRevocationRepository;

    public UserService(UserProfileRepository userProfileRepository,
                       EventRegistrationRepository eventRegistrationRepository,
                       TransactionLogRepository transactionLogRepository,
                       PasswordEncoder passwordEncoder,
                       UserTokenRevocationRepository userTokenRevocationRepository) {
        this.userProfileRepository = userProfileRepository;
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.userTokenRevocationRepository = userTokenRevocationRepository;
    }
public UserResponse create(UserRequest request) {
        String email = request.email().trim().toLowerCase();
        
        // First, try to find existing user
        UserProfile existingUser = userProfileRepository.findByEmailIgnoreCase(email).orElse(null);
        
        if (existingUser != null) {
            // User exists, check if they have a real password
            if (hasRealPassword(existingUser)) {
                throw new ConflictException("User already exists for email " + email);
            }
            // User exists but doesn't have a real password (e.g., created via findOrCreateAttendee)
            // Update the existing user
            existingUser.setFullName(request.fullName().trim());
            existingUser.setPhoneNumber(request.phoneNumber());
            existingUser.setRole(request.role());
            existingUser.setStatus(AccountStatus.ACTIVE);
            existingUser.setPasswordHash(passwordEncoder.encode(request.password()));
            return toResponse(userProfileRepository.save(existingUser));
        }
        
        // No existing user found, try to create new one
        UserProfile newUser = new UserProfile();
        newUser.setEmail(email);
        newUser.setFullName(request.fullName().trim());
        newUser.setPhoneNumber(request.phoneNumber());
        newUser.setRole(request.role());
        newUser.setStatus(AccountStatus.ACTIVE);
        newUser.setPasswordHash(passwordEncoder.encode(request.password()));
        
        try {
            return toResponse(userProfileRepository.save(newUser));
        } catch (DataIntegrityViolationException e) {
            // Handle race condition: another thread created the user while we were processing
            // Check if it's due to email uniqueness constraint
            if (e.getRootCause() != null && 
                (e.getRootCause().getMessage().contains("user_profiles_email_key") ||
                 e.getRootCause().getMessage().contains("duplicate key") ||
                 e.getRootCause().getMessage().contains("Unique index"))) {
                // Retry as update - the user was created by another thread
                UserProfile retryUser = userProfileRepository.findByEmailIgnoreCase(email)
                        .orElseThrow(() -> new ConflictException("User already exists for email " + email));
                
                if (hasRealPassword(retryUser)) {
                    throw new ConflictException("User already exists for email " + email);
                }
                
                retryUser.setFullName(request.fullName().trim());
                retryUser.setPhoneNumber(request.phoneNumber());
                retryUser.setRole(request.role());
                retryUser.setStatus(AccountStatus.ACTIVE);
                retryUser.setPasswordHash(passwordEncoder.encode(request.password()));
                return toResponse(userProfileRepository.save(retryUser));
            }
            // If it's not a duplicate key error, rethrow
            throw e;
        }
    }

    public Page<UserResponse> findAllUsers(Pageable pageable) {
        return userProfileRepository.findAll(pageable).map(this::toResponse);
    }

    public Page<UserResponse> findByRole(AccountRole role, Pageable pageable) {
        return userProfileRepository.findByRole(role, pageable).map(this::toResponse);
    }

    /** Admin-scoped listing: excludes privileged roles the caller must never see. */
    public Page<UserResponse> findByRoleNotIn(Collection<AccountRole> excludedRoles, Pageable pageable) {
        return userProfileRepository.findByRoleNotIn(excludedRoles, pageable).map(this::toResponse);
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
        String normalizedAvatarFileId = avatarFileId == null || avatarFileId.isBlank() ? null : avatarFileId.trim();
        userProfile.setAvatarFileId(normalizedAvatarFileId);
        userProfile.setAvatarPath(normalizedAvatarFileId == null ? null : "files/" + normalizedAvatarFileId + "/content");
        return toResponse(userProfileRepository.save(userProfile));
    }

    public UserResponse updateStatus(UUID userId, AccountStatus status) {
        UserProfile userProfile = requireUser(userId);
        userProfile.setStatus(status);
        UserProfile saved = userProfileRepository.save(userProfile);
        if (status == AccountStatus.INACTIVE || status == AccountStatus.SUSPENDED) {
            // Kill existing sessions immediately: no 24h grace for a disabled/suspended
            // account. The marker is DB-backed and intentionally retained, so tokens
            // issued before this disable stay dead even after a later re-enable.
            upsertTokenRevocation(userId, Instant.now());
        }
        // ACTIVE is deliberately a no-op for the revocation table — enabling must never
        // resurrect pre-disable tokens.
        return toResponse(saved);
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
        upsertTokenRevocation(userId, Instant.now());
    }

    /**
     * Records (or refreshes) the revocation marker for a user. The row survives
     * re-enable on purpose: {@code iat <= revoked_at} tokens must never come back.
     */
    private void upsertTokenRevocation(UUID userId, Instant revokedAt) {
        UserTokenRevocation revocation = userTokenRevocationRepository.findByUserId(userId)
                .orElseGet(() -> new UserTokenRevocation(userId, revokedAt));
        revocation.setRevokedAt(revokedAt);
        userTokenRevocationRepository.save(revocation);
    }

    @Transactional(readOnly = true)
    public boolean hasDependentRecords(UUID userId) {
        // Check for registrations
        boolean hasRegistrations = !eventRegistrationRepository.findByAttendeeUserId(userId).isEmpty();
        // Check for transactions
        boolean hasTransactions = !transactionLogRepository.findByAttendeeUserId(userId).isEmpty();
        return hasRegistrations || hasTransactions;
    }

    public void hardDelete(UUID userId) {
        UserProfile userProfile = requireUser(userId);
        if (hasDependentRecords(userId)) {
            throw new BadRequestException("Account has transaction history, cannot be deleted");
        }
        userProfileRepository.delete(userProfile);
    }

    public UserResponse changeRoleResponse(UUID callerUserId, AccountRole callerRole, UUID userId, AccountRole role) {
        if (role == null) {
            throw new BadRequestException("Target role is required");
        }
        UserProfile target = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // SUPER_ADMIN is the only role that may assign ADMIN/SUPER_ADMIN roles at all.
        if (callerRole == AccountRole.SUPER_ADMIN) {
            return toResponse(saveRole(target, role));
        }

        // A plain ADMIN must never be able to promote anyone (including themselves) to
        // ADMIN/SUPER_ADMIN, manage another admin account, or change their own role.
        if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            throw new ForbiddenException("Only super admins can assign admin roles");
        }
        if (target.getRole() == AccountRole.ADMIN || target.getRole() == AccountRole.SUPER_ADMIN) {
            throw new ForbiddenException("Admins cannot manage admin accounts");
        }
        if (target.getId().equals(callerUserId)) {
            throw new ForbiddenException("Admins cannot change their own role");
        }
        return toResponse(saveRole(target, role));
    }

    private UserProfile saveRole(UserProfile target, AccountRole role) {
        target.setRole(role);
        return userProfileRepository.save(target);
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
                userProfile.getPhoneNumber(), userProfile.getRole(), userProfile.getStatus(), userProfile.getAvatarFileId(),
                resolveAvatarPath(userProfile));
    }

    private String resolveAvatarPath(UserProfile userProfile) {
        if (userProfile.getAvatarPath() != null && !userProfile.getAvatarPath().isBlank()) {
            return userProfile.getAvatarPath().trim();
        }
        if (userProfile.getAvatarFileId() == null || userProfile.getAvatarFileId().isBlank()) {
            return null;
        }
        return "files/" + userProfile.getAvatarFileId().trim() + "/content";
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
