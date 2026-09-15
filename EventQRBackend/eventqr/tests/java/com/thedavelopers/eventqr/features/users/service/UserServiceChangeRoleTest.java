package com.thedavelopers.eventqr.features.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.features.users.repository.UserTokenRevocationRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;

@ExtendWith(MockitoExtension.class)
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

    private UUID adminId;
    private UUID superAdminId;
    private UUID targetAttendeeId;
    private UUID targetAdminId;
    private UUID targetSuperAdminId;

    @BeforeEach
    void setUp() {
        userService = new UserService(userProfileRepository, eventRegistrationRepository,
                transactionLogRepository, passwordEncoder, userTokenRevocationRepository);
        adminId = UUID.randomUUID();
        superAdminId = UUID.randomUUID();
        targetAttendeeId = UUID.randomUUID();
        targetAdminId = UUID.randomUUID();
        targetSuperAdminId = UUID.randomUUID();
    }

    private UserProfile user(UUID id, AccountRole role) {
        UserProfile p = new UserProfile();
        p.setId(id);
        p.setRole(role);
        p.setStatus(com.thedavelopers.eventqr.shared.constants.AccountStatus.ACTIVE);
        p.setEmail("user@" + id + ".com");
        p.setFullName("User " + id);
        return p;
    }

    private void stubTarget(UserProfile target) {
        when(userProfileRepository.findById(target.getId())).thenReturn(Optional.of(target));
    }

    private void stubSave() {
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void adminCannotAssignAdminRole() {
        UserProfile target = user(targetAttendeeId, AccountRole.ATTENDEE);
        stubTarget(target);
        assertThatThrownBy(() -> userService.changeRoleResponse(adminId, AccountRole.ADMIN, targetAttendeeId, AccountRole.ADMIN))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminCannotAssignSuperAdminRole() {
        UserProfile target = user(targetAttendeeId, AccountRole.ATTENDEE);
        stubTarget(target);
        assertThatThrownBy(() -> userService.changeRoleResponse(adminId, AccountRole.ADMIN, targetAttendeeId, AccountRole.SUPER_ADMIN))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminCannotManageAdminTarget() {
        UserProfile target = user(targetAdminId, AccountRole.ADMIN);
        stubTarget(target);
        assertThatThrownBy(() -> userService.changeRoleResponse(adminId, AccountRole.ADMIN, targetAdminId, AccountRole.ATTENDEE))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminCannotManageSuperAdminTarget() {
        UserProfile target = user(targetSuperAdminId, AccountRole.SUPER_ADMIN);
        stubTarget(target);
        assertThatThrownBy(() -> userService.changeRoleResponse(adminId, AccountRole.ADMIN, targetSuperAdminId, AccountRole.ATTENDEE))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminCannotChangeOwnRole() {
        UserProfile target = user(adminId, AccountRole.ADMIN);
        stubTarget(target);
        assertThatThrownBy(() -> userService.changeRoleResponse(adminId, AccountRole.ADMIN, adminId, AccountRole.ATTENDEE))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void superAdminCanAssignAdminRole() {
        UserProfile target = user(targetAttendeeId, AccountRole.ATTENDEE);
        stubTarget(target);
        stubSave();
        var updated = userService.changeRoleResponse(superAdminId, AccountRole.SUPER_ADMIN, targetAttendeeId, AccountRole.ADMIN);
        assertThat(updated.role()).isEqualTo(AccountRole.ADMIN);
    }

    @Test
    void superAdminCanAssignSuperAdminRole() {
        UserProfile target = user(targetAttendeeId, AccountRole.ATTENDEE);
        stubTarget(target);
        stubSave();
        var updated = userService.changeRoleResponse(superAdminId, AccountRole.SUPER_ADMIN, targetAttendeeId, AccountRole.SUPER_ADMIN);
        assertThat(updated.role()).isEqualTo(AccountRole.SUPER_ADMIN);
    }

    @Test
    void adminCanDemoteAttendeeToLowerRole() {
        UserProfile target = user(targetAttendeeId, AccountRole.ATTENDEE);
        stubTarget(target);
        stubSave();
        var updated = userService.changeRoleResponse(adminId, AccountRole.ADMIN, targetAttendeeId, AccountRole.STAFF);
        assertThat(updated.role()).isEqualTo(AccountRole.STAFF);
    }
}