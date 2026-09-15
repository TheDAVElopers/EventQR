package com.thedavelopers.eventqr.features.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.model.entity.UserTokenRevocation;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.features.users.repository.UserTokenRevocationRepository;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;

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
    private UserTokenRevocationRepository userTokenRevocationRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userProfileRepository, eventRegistrationRepository,
                transactionLogRepository, passwordEncoder, userTokenRevocationRepository);
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
        when(userTokenRevocationRepository.findByUserId(userId)).thenReturn(Optional.empty());

        userService.updateStatus(userId, AccountStatus.INACTIVE);

        ArgumentCaptor<UserTokenRevocation> captor = ArgumentCaptor.forClass(UserTokenRevocation.class);
        verify(userTokenRevocationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getRevokedAt()).isNotNull();
    }

    @Test
    void suspendingRefreshesExistingRevocationMarker() {
        UUID userId = UUID.randomUUID();
        Instant previousRevocation = Instant.now().minusSeconds(3600);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userTokenRevocationRepository.findByUserId(userId))
                .thenReturn(Optional.of(new UserTokenRevocation(userId, previousRevocation)));

        userService.updateStatus(userId, AccountStatus.SUSPENDED);

        ArgumentCaptor<UserTokenRevocation> captor = ArgumentCaptor.forClass(UserTokenRevocation.class);
        verify(userTokenRevocationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getRevokedAt()).isAfter(previousRevocation);
    }

    @Test
    void enablingDoesNotTouchRevocationMarker() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateStatus(userId, AccountStatus.ACTIVE);

        verify(userTokenRevocationRepository, never()).save(any());
        verify(userTokenRevocationRepository, never()).findByUserId(any());
    }

    @Test
    void softDeleteRecordsRevocationMarker() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userTokenRevocationRepository.findByUserId(userId)).thenReturn(Optional.empty());

        userService.softDelete(userId);

        ArgumentCaptor<UserTokenRevocation> captor = ArgumentCaptor.forClass(UserTokenRevocation.class);
        verify(userTokenRevocationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getRevokedAt()).isNotNull();
    }
}