package com.thedavelopers.eventqr.features.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.features.users.repository.UserTokenRevocationRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Role-change ceilings from UserService.changeRoleResponse:
 * only SUPER_ADMIN may assign ADMIN/SUPER_ADMIN; admins cannot manage admin
 * accounts or change their own role.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceChangeRoleTest {

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private EventRegistrationRepository eventRegistrationRepository;
    @Mock
    private TransactionLogRepository transactionLogRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserTokenRevocationRepository userTokenRevocationRepository;

    private UserService userService;

    private final UUID adminUserId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userService = new UserService(userProfileRepository, eventRegistrationRepository,
                transactionLogRepository, passwordEncoder, userTokenRevocationRepository);
        given(userProfileRepository.save(any(UserProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    private UserProfile profileWithRole(AccountRole role) {
        UserProfile profile = new UserProfile();
        profile.setId(targetUserId);
        profile.setEmail("target@example.com");
        profile.setFullName("Target");
        profile.setRole(role);
        return profile;
    }

    private void givenTarget(AccountRole role) {
        given(userProfileRepository.findById(targetUserId)).willReturn(Optional.of(profileWithRole(role)));
    }

    @Test
    void adminAssigningAdminRole_isForbidden() {
        givenTarget(AccountRole.ATTENDEE);

        assertThrows(ForbiddenException.class, () ->
                userService.changeRoleResponse(adminUserId, AccountRole.ADMIN, targetUserId, AccountRole.ADMIN));
    }

    @Test
    void adminAssigningSuperAdminRole_isForbidden() {
        givenTarget(AccountRole.ATTENDEE);

        assertThrows(ForbiddenException.class, () ->
                userService.changeRoleResponse(adminUserId, AccountRole.ADMIN, targetUserId, AccountRole.SUPER_ADMIN));
    }

    @Test
    void adminManagingAnotherAdminAccount_isForbidden() {
        givenTarget(AccountRole.ADMIN);

        assertThrows(ForbiddenException.class, () ->
                userService.changeRoleResponse(adminUserId, AccountRole.ADMIN, targetUserId, AccountRole.STAFF));
    }

    @Test
    void adminChangingOwnRole_isForbidden() {
        given(userProfileRepository.findById(adminUserId))
                .willReturn(Optional.of(profileWithRole(AccountRole.ADMIN)));

        assertThrows(ForbiddenException.class, () ->
                userService.changeRoleResponse(adminUserId, AccountRole.ADMIN, adminUserId, AccountRole.ORGANIZER));
    }

    @Test
    void adminChangingAttendeeToStaff_isAllowed() {
        givenTarget(AccountRole.ATTENDEE);

        UserResponse response = userService.changeRoleResponse(adminUserId, AccountRole.ADMIN,
                targetUserId, AccountRole.STAFF);

        assertEquals(AccountRole.STAFF, response.role());
        assertEquals(targetUserId, response.userId());
    }

    @Test
    void superAdminAssigningAdminRole_isAllowed() {
        givenTarget(AccountRole.ATTENDEE);

        UserResponse response = userService.changeRoleResponse(adminUserId, AccountRole.SUPER_ADMIN,
                targetUserId, AccountRole.ADMIN);

        assertEquals(AccountRole.ADMIN, response.role());
    }

    @Test
    void superAdminAssigningSuperAdminRole_isAllowed() {
        givenTarget(AccountRole.ATTENDEE);

        UserResponse response = userService.changeRoleResponse(adminUserId, AccountRole.SUPER_ADMIN,
                targetUserId, AccountRole.SUPER_ADMIN);

        assertEquals(AccountRole.SUPER_ADMIN, response.role());
    }
}
