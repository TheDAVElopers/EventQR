package com.thedavelopers.eventqr.features.users.service;

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
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.security.AccessTokenRevoker;
import com.thedavelopers.eventqr.shared.security.UserTokenRevocationChecker;
import org.springframework.dao.DataIntegrityViolationException;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.utils.PasswordValidator;
import com.thedavelopers.eventqr.shared.utils.PhoneNumbers;
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
    private final AccessTokenRevoker accessTokenRevoker;
    private final UserTokenRevocationChecker userTokenRevocationChecker;

    public UserService(UserProfileRepository userProfileRepository,
                       EventRegistrationRepository eventRegistrationRepository,
                       TransactionLogRepository transactionLogRepository,
                       PasswordEncoder passwordEncoder,
                       AccessTokenRevoker accessTokenRevoker,
                       UserTokenRevocationChecker userTokenRevocationChecker) {
        this.userTokenRevocationChecker = userTokenRevocationChecker;
        this.userProfileRepository = userProfileRepository;
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenRevoker = accessTokenRevoker;
    }

    /**
     * Public self-service signup ({@code POST /auth/register}). Unlike {@link #create}, this never
     * claims an existing profile: a profile with no usable password (a "ghost" created by
     * {@link #findOrCreateAttendee} when someone was registered for an event by email) belongs to
     * whoever controls that inbox, and signup cannot prove that. Any existing email therefore gets
     * the same 409 as a normal duplicate; the owner of a ghost profile sets a password through the
     * forgot-password / reset flow, which proves inbox ownership. The role is always ATTENDEE.
     */
    public UserResponse register(UserRequest request) {
        requireStrongPassword(request.password());
        String email = request.email().trim().toLowerCase();
        if (userProfileRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ConflictException("User already exists for email " + email);
        }
        UserProfile newUser = new UserProfile();
        newUser.setEmail(email);
        newUser.setFullName(request.fullName().trim());
        newUser.setPhoneNumber(PhoneNumbers.normalize(request.phoneNumber()));
        newUser.setRole(AccountRole.ATTENDEE);
        newUser.setStatus(AccountStatus.ACTIVE);
        newUser.setPasswordHash(passwordEncoder.encode(request.password()));
        try {
            return toResponse(userProfileRepository.saveAndFlush(newUser));
        } catch (DataIntegrityViolationException e) {
            // saveAndFlush forces the INSERT here (a UUID id defers it to commit), so the race surfaces in this catch.
            // Lost a race with a concurrent signup / ghost creation for the same email: never claim it.
            throw new ConflictException("User already exists for email " + email);
        }
    }

    /**
     * Privileged creation for admin-authorized callers only; may claim an existing passwordless
     * profile (the admin sets its password and role). Public signup must use {@link #register}.
     */
    public UserResponse create(UserRequest request) {
        // Covers every branch below (new, ghost-claim, race-retry) since they all encode request.password().
        requireStrongPassword(request.password());
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
            existingUser.setPhoneNumber(PhoneNumbers.normalize(request.phoneNumber()));
            existingUser.setRole(request.role());
            existingUser.setStatus(AccountStatus.ACTIVE);
            existingUser.setPasswordHash(passwordEncoder.encode(request.password()));
            UserProfile claimed = userProfileRepository.save(existingUser);
            userTokenRevocationChecker.invalidateAfterCommit(existingUser.getId());
            return toResponse(claimed);
        }
        
        // No existing user found, try to create new one
        UserProfile newUser = new UserProfile();
        newUser.setEmail(email);
        newUser.setFullName(request.fullName().trim());
        newUser.setPhoneNumber(PhoneNumbers.normalize(request.phoneNumber()));
        newUser.setRole(request.role());
        newUser.setStatus(AccountStatus.ACTIVE);
        newUser.setPasswordHash(passwordEncoder.encode(request.password()));
        
        try {
            return toResponse(userProfileRepository.saveAndFlush(newUser));
        } catch (DataIntegrityViolationException e) {
            // Another request created this email between the lookup and the insert. The failed flush leaves this
            // transaction rollback-only, so retrying inside it cannot succeed: report the duplicate and let the
            // caller retry (the next attempt sees the committed row and takes the normal path).
            Throwable root = e.getRootCause();
            String rootMessage = root == null ? "" : String.valueOf(root.getMessage());
            if (rootMessage.contains("user_profiles_email_key") || rootMessage.contains("duplicate key")
                    || rootMessage.contains("Unique index")) {
                throw new ConflictException("User already exists for email " + email);
            }
            throw e;
        }
    }

    private static void requireStrongPassword(String password) {
        PasswordValidator.requireValid(password);
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

    /** Search variants of the listings; {@code pattern} comes from {@code LikePatterns.contains}. */
    public Page<UserResponse> searchAllUsers(String pattern, Pageable pageable) {
        return userProfileRepository.searchAll(pattern, pageable).map(this::toResponse);
    }

    public Page<UserResponse> searchByRole(AccountRole role, String pattern, Pageable pageable) {
        return userProfileRepository.searchByRole(role, pattern, pageable).map(this::toResponse);
    }

    public Page<UserResponse> searchByRoleNotIn(Collection<AccountRole> excludedRoles, String pattern, Pageable pageable) {
        return userProfileRepository.searchByRoleNotIn(excludedRoles, pattern, pageable).map(this::toResponse);
    }

    public UserResponse findOne(UUID userId) {
        return toResponse(requireUser(userId));
    }

    public UserResponse updateProfile(UUID userId, String fullName, String phoneNumber) {
        UserProfile userProfile = requireUser(userId);
        if (fullName != null && !fullName.isBlank()) {
userProfile.setFullName(fullName.trim());
         }
         userProfile.setPhoneNumber(PhoneNumbers.normalize(phoneNumber));
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
            upsertTokenRevocation(userId);
        }
        // ACTIVE is deliberately a no-op for the revocation table — enabling must never
        // resurrect pre-disable tokens.
        return toResponse(saved);
    }

    public void softDelete(UUID userId) {
        UserProfile userProfile = requireUser(userId);
        userProfile.setStatus(AccountStatus.SUSPENDED);
        userProfileRepository.save(userProfile);
        upsertTokenRevocation(userId);
    }

    /**
     * Moves the user's revocation marker to now through {@link AccessTokenRevoker}: a race-safe upsert that never
     * moves it backwards, millisecond precision like the {@code iatMs} claim, and the cached access decision dropped
     * after commit. The row survives re-enable on purpose: tokens issued before it must never come back.
     */
    private void upsertTokenRevocation(UUID userId) {
        accessTokenRevoker.revokeAllIssuedUntilNow(userId);
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
            throw new BadRequestException("Account has registration or transaction history, cannot be deleted");
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

    /**
     * Role changes never write the revocation marker (that would also kill refresh tokens). The JWT filter rejects an
     * access token whose role claim differs from the stored role, so dropping the cached decision after commit makes
     * the change effective on the next request; the client's refresh then mints a token with the new role.
     */
    private UserProfile saveRole(UserProfile target, AccountRole role) {
        target.setRole(role);
        UserProfile saved = userProfileRepository.save(target);
        userTokenRevocationChecker.invalidateAfterCommit(target.getId());
        return saved;
    }

    @Override
    public AttendeeSnapshot findOrCreateAttendee(String email, String fullName, String phoneNumber, AccountRole role) {
        UserProfile userProfile = userProfileRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> {
                    UserProfile created = new UserProfile();
                    created.setEmail(email.trim().toLowerCase());
                    created.setFullName(fullName.trim());
                    created.setPhoneNumber(PhoneNumbers.normalize(phoneNumber));
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
        return saveRole(userProfile, role).toSnapshot();
    }

    private UserResponse toResponse(UserProfile userProfile) {
        return new UserResponse(userProfile.getId(), userProfile.getEmail(), userProfile.getFullName(),
                userProfile.getPhoneNumber(), userProfile.getRole(), userProfile.getStatus());
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
