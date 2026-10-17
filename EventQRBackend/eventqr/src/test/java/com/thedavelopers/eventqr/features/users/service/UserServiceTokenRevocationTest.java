package com.thedavelopers.eventqr.features.users.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.security.AccessTokenRevoker;
import com.thedavelopers.eventqr.shared.security.UserTokenRevocationChecker;

/**
 * Disable/suspend/soft-delete move the revocation marker through {@link AccessTokenRevoker} (race-safe, never
 * backwards, millisecond precision, cache dropped after commit). Re-enabling never touches it.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTokenRevocationTest {

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private EventRegistrationRepository eventRegistrationRepository;
    @Mock
    private TransactionLogRepository transactionLogRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AccessTokenRevoker accessTokenRevoker;
    @Mock
    private UserTokenRevocationChecker userTokenRevocationChecker;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userProfileRepository, eventRegistrationRepository,
                transactionLogRepository, passwordEncoder, accessTokenRevoker, userTokenRevocationChecker);
    }

    private UserProfile activeUser(UUID userId) {
        UserProfile profile = new UserProfile();
        profile.setId(userId);
        profile.setStatus(AccountStatus.ACTIVE);
        profile.setEmail("user@" + userId + ".com");
        profile.setFullName("User " + userId);
        return profile;
    }

    @Test
    void disablingRecordsRevocationMarker() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateStatus(userId, AccountStatus.INACTIVE);

        verify(accessTokenRevoker).revokeAllIssuedUntilNow(userId);
    }

    @Test
    void suspendingRecordsRevocationMarker() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateStatus(userId, AccountStatus.SUSPENDED);

        verify(accessTokenRevoker).revokeAllIssuedUntilNow(userId);
    }

    @Test
    void enablingDoesNotTouchRevocationMarker() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateStatus(userId, AccountStatus.ACTIVE);

        verify(accessTokenRevoker, never()).revokeAllIssuedUntilNow(any());
    }

    @Test
    void softDeleteRecordsRevocationMarker() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.softDelete(userId);

        verify(accessTokenRevoker).revokeAllIssuedUntilNow(userId);
    }
}
