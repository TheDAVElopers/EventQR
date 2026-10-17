package com.thedavelopers.eventqr.features.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.thedavelopers.eventqr.features.auth.service.RefreshTokenService;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.features.users.repository.UserTokenRevocationRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;

/** Every phone write path stores the canonical +63XXXXXXXXXX; legacy stored values are never re-validated on read. */
class UserServicePhoneNormalizationTest {

    private UserProfileRepository repository;
    private UserService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserProfileRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenAnswer(i -> "enc(" + i.getArgument(0) + ")");
        when(repository.save(any(UserProfile.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.saveAndFlush(any(UserProfile.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        service = new UserService(repository, mock(EventRegistrationRepository.class),
                mock(TransactionLogRepository.class), encoder, mock(com.thedavelopers.eventqr.shared.security.AccessTokenRevoker.class),
                org.mockito.Mockito.mock(com.thedavelopers.eventqr.shared.security.UserTokenRevocationChecker.class));
    }

    private UserProfile existing(String phone) {
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("jane@example.com");
        profile.setFullName("Jane");
        profile.setPhoneNumber(phone);
        profile.setRole(AccountRole.ATTENDEE);
        profile.setStatus(AccountStatus.ACTIVE);
        when(repository.findById(profile.getId())).thenReturn(Optional.of(profile));
        return profile;
    }

    @Test
    void registerStoresTheCanonicalForm() {
        var response = service.register(
                new UserRequest("jane@example.com", "Jane", "639171234567", "Passw0rd!!", AccountRole.ATTENDEE));
        assertThat(response.phoneNumber()).isEqualTo("+639171234567");
    }

    @Test
    void createStoresTheCanonicalForm() {
        var response = service.create(
                new UserRequest("admin@example.com", "Admin", "639171234567", "Passw0rd!!", AccountRole.ADMIN));
        assertThat(response.phoneNumber()).isEqualTo("+639171234567");
    }

    @Test
    void updateProfileStoresTheCanonicalForm() {
        UserProfile profile = existing("+639170000000");

        service.updateProfile(profile.getId(), "Jane", " 639171234567 ");

        assertThat(profile.getPhoneNumber()).isEqualTo("+639171234567");
    }

    @Test
    void legacyStoredPhoneDoesNotBlockANameOnlyUpdate() {
        // Pre-existing behaviour: an omitted phone (null) clears it; validation is skipped for null.
        UserProfile profile = existing("9171234567");

        service.updateProfile(profile.getId(), "Jane Doe", null);

        assertThat(profile.getFullName()).isEqualTo("Jane Doe");
        assertThat(profile.getPhoneNumber()).isNull();
    }

    @Test
    void ghostAttendeeCreatedByEventRegistrationStoresTheCanonicalForm() {
        var snapshot = service.findOrCreateAttendee("new@example.com", "New", "639171234567", AccountRole.ATTENDEE);
        assertThat(snapshot.phoneNumber()).isEqualTo("+639171234567");
    }
}
