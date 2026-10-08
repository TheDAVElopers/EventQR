package com.thedavelopers.eventqr.features.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.thedavelopers.eventqr.features.auth.service.RefreshTokenService;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.features.users.repository.UserTokenRevocationRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;

/**
 * The admin create path used to retry as an update inside the same transaction after a unique-key race.
 * That transaction is rollback-only after the failed flush, so the retry could never commit.
 */
class UserServiceCreateRaceTest {

    private UserProfileRepository repository;
    private UserService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserProfileRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenAnswer(i -> "enc(" + i.getArgument(0) + ")");
        service = new UserService(repository, mock(EventRegistrationRepository.class),
                mock(TransactionLogRepository.class), encoder, mock(UserTokenRevocationRepository.class),
                mock(RefreshTokenService.class));
    }

    @Test
    void aUniqueKeyRaceOnCreateIsAClean409WithoutRetryingInsideTheFailedTransaction() {
        when(repository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(UserProfile.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"user_profiles_email_key\"")));

        Throwable thrown = catchThrowable(() -> service.create(
                new UserRequest("New@Example.com", "Admin", "+639170000000", "Passw0rd!!", AccountRole.ORGANIZER)));

        assertThat(thrown).isExactlyInstanceOf(ConflictException.class)
                .hasMessage("User already exists for email new@example.com");
        verify(repository, times(1)).findByEmailIgnoreCase(any());
        verify(repository, never()).save(any());
    }

    @Test
    void anUnrelatedIntegrityViolationOnCreateIsStillRethrown() {
        when(repository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        DataIntegrityViolationException other = new DataIntegrityViolationException("bad",
                new RuntimeException("ERROR: null value in column \"full_name\" violates not-null constraint"));
        when(repository.saveAndFlush(any(UserProfile.class))).thenThrow(other);

        Throwable thrown = catchThrowable(() -> service.create(
                new UserRequest("new@example.com", "Admin", "+639170000000", "Passw0rd!!", AccountRole.ORGANIZER)));

        assertThat(thrown).isSameAs(other);
    }
}
