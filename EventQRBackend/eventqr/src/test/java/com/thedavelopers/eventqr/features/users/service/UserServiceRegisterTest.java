package com.thedavelopers.eventqr.features.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.thedavelopers.eventqr.features.auth.service.RefreshTokenService;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.features.users.repository.UserTokenRevocationRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;

/**
 * Public signup must never claim a "ghost" profile (passwordless, created when someone was registered
 * for an event by email): that would let anyone take over a victim's registrations and QR codes.
 */
class UserServiceRegisterTest {

    private static final String UNUSABLE = "{UNUSABLE}$2a$10$ghost";

    private UserProfileRepository repository;
    private PasswordEncoder encoder;
    private UserService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserProfileRepository.class);
        encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenAnswer(i -> "enc(" + i.getArgument(0) + ")");
        when(repository.save(any(UserProfile.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.saveAndFlush(any(UserProfile.class))).thenAnswer(i -> i.getArgument(0));
        service = new UserService(repository, mock(EventRegistrationRepository.class),
                mock(TransactionLogRepository.class), encoder, mock(UserTokenRevocationRepository.class),
                mock(RefreshTokenService.class));
    }

    private UserProfile ghost(AccountRole role, AccountStatus status) {
        UserProfile ghost = new UserProfile();
        ghost.setId(UUID.randomUUID());
        ghost.setEmail("victim@example.com");
        ghost.setFullName("Victim");
        ghost.setRole(role);
        ghost.setStatus(status);
        ghost.setPasswordHash(UNUSABLE);
        return ghost;
    }

    private UserRequest signup(String email, AccountRole requestedRole) {
        return new UserRequest(email, "Mallory", "+639170000000", "Passw0rd!!", requestedRole);
    }

    @Test
    void signingUpWithAGhostProfilesEmailIsRejectedLikeAnyDuplicateAndChangesNothing() {
        UserProfile ghost = ghost(AccountRole.ATTENDEE, AccountStatus.ACTIVE);
        when(repository.findByEmailIgnoreCase("victim@example.com")).thenReturn(Optional.of(ghost));

        assertThatThrownBy(() -> service.register(signup("Victim@Example.com", AccountRole.ATTENDEE)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User already exists for email victim@example.com");

        verify(repository, never()).save(any());
        assertThat(ghost.getPasswordHash()).isEqualTo(UNUSABLE);
        assertThat(ghost.getFullName()).isEqualTo("Victim");
    }

    @Test
    void theRejectionIsIdenticalForAGhostAndAProfileWithARealPassword() {
        UserProfile real = ghost(AccountRole.ATTENDEE, AccountStatus.ACTIVE);
        real.setPasswordHash("$2a$10$realhash");
        when(repository.findByEmailIgnoreCase("victim@example.com")).thenReturn(Optional.of(real));
        Throwable forReal = catchThrowable(() -> service.register(signup("victim@example.com", AccountRole.ATTENDEE)));

        when(repository.findByEmailIgnoreCase("victim@example.com"))
                .thenReturn(Optional.of(ghost(AccountRole.ATTENDEE, AccountStatus.ACTIVE)));
        Throwable forGhost = catchThrowable(() -> service.register(signup("victim@example.com", AccountRole.ATTENDEE)));

        assertThat(forGhost).isInstanceOf(ConflictException.class).hasMessage(forReal.getMessage());
        assertThat(forGhost.getClass()).isEqualTo(forReal.getClass());
    }

    @Test
    void signupCannotReactivateASuspendedGhostOrChangeItsRole() {
        UserProfile suspended = ghost(AccountRole.ORGANIZER, AccountStatus.SUSPENDED);
        when(repository.findByEmailIgnoreCase("victim@example.com")).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> service.register(signup("victim@example.com", AccountRole.ADMIN)))
                .isInstanceOf(ConflictException.class);

        assertThat(suspended.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(suspended.getRole()).isEqualTo(AccountRole.ORGANIZER);
        verify(repository, never()).save(any());
    }

    @Test
    void aUniqueKeyRaceAtFlushIsReportedAsTheSameStandard409AsANormalDuplicate() {
        when(repository.findByEmailIgnoreCase("victim@example.com")).thenReturn(Optional.empty());
        // saveAndFlush forces the INSERT inside register(), so the constraint violation surfaces in its catch.
        when(repository.saveAndFlush(any(UserProfile.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        Throwable race = catchThrowable(() -> service.register(signup("Victim@Example.com", AccountRole.ATTENDEE)));

        when(repository.findByEmailIgnoreCase("victim@example.com"))
                .thenReturn(Optional.of(ghost(AccountRole.ATTENDEE, AccountStatus.ACTIVE)));
        Throwable duplicate = catchThrowable(() -> service.register(signup("Victim@Example.com", AccountRole.ATTENDEE)));

        assertThat(race).isExactlyInstanceOf(ConflictException.class)
                .hasMessage("User already exists for email victim@example.com");
        assertThat(race.getClass()).isEqualTo(duplicate.getClass());
        assertThat(race.getMessage()).isEqualTo(duplicate.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void aFreshSignupAlwaysCreatesAnActiveAttendeeWhateverRoleTheRequestCarries() {
        when(repository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());

        for (AccountRole requested : AccountRole.values()) {
            UserResponse response = service.register(signup("new@example.com", requested));
            assertThat(response.role()).isEqualTo(AccountRole.ATTENDEE);
            assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        }

        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        verify(repository, times(AccountRole.values().length)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(p -> {
            assertThat(p.getRole()).isEqualTo(AccountRole.ATTENDEE);
            assertThat(p.getPasswordHash()).isEqualTo("enc(Passw0rd!!)");
            assertThat(p.getEmail()).isEqualTo("new@example.com");
        });
    }

    @Test
    void theAdminOnlyCreatePathStillClaimsAGhostSoOperatorsCanProvisionAccounts() {
        UserProfile ghost = ghost(AccountRole.ATTENDEE, AccountStatus.ACTIVE);
        when(repository.findByEmailIgnoreCase("victim@example.com")).thenReturn(Optional.of(ghost));

        service.create(signup("victim@example.com", AccountRole.STAFF));

        assertThat(ghost.getRole()).isEqualTo(AccountRole.STAFF);
        assertThat(ghost.getPasswordHash()).isEqualTo("enc(Passw0rd!!)");
    }
}
