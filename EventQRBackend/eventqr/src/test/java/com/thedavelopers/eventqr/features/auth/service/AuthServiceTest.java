package com.thedavelopers.eventqr.features.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.thedavelopers.eventqr.features.auth.model.dto.LoginRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.UnauthorizedException;
import com.thedavelopers.eventqr.shared.security.JwtService;

class AuthServiceTest {

    private static final String SECRET = "01234567890123456789012345678901"; // 32 bytes (HS256 min)
    private static final String PASSWORD = "Passw0rd!!";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final JwtService jwtService = new JwtService(SECRET, 3_600_000L);
    private UserProfileRepository userRepository;
    private RefreshTokenService refreshTokenService;
    private AuthService service;
    private UserProfile user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserProfileRepository.class);
        refreshTokenService = mock(RefreshTokenService.class);
        service = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService);

        user = new UserProfile();
        user.setId(UUID.randomUUID());
        user.setEmail("jane@example.com");
        user.setFullName("Jane Doe");
        user.setRole(AccountRole.ATTENDEE);
        user.setStatus(AccountStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokenService.issueForLogin(user.getId())).thenReturn("refresh-1");
    }

    private LoginRequest login(String password) {
        return new LoginRequest("jane@example.com", password);
    }

    // ----- login -----

    @Test
    void aCorrectLoginReturnsAnAccessTokenARefreshTokenAndTheProfile() {
        LoginResponse response = service.login(login(PASSWORD));

        assertThat(response.userId()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.fullName()).isEqualTo("Jane Doe");
        assertThat(response.role()).isEqualTo(AccountRole.ATTENDEE);
        assertThat(response.refreshToken()).isEqualTo("refresh-1");
        assertThat(jwtService.extractUserIdFromBearer("Bearer " + response.accessToken())).isEqualTo(user.getId());
        assertThat(jwtService.extractRoleFromBearer("Bearer " + response.accessToken())).isEqualTo(AccountRole.ATTENDEE);
    }

    @Test
    void anUnknownEmailAndAWrongPasswordGiveTheSameMessageSoAccountsCannotBeProbed() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        Throwable unknown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.login(new LoginRequest("nobody@example.com", PASSWORD)));
        Throwable wrong = org.assertj.core.api.Assertions.catchThrowable(() -> service.login(login("wrong-password")));

        assertThat(unknown).isInstanceOf(UnauthorizedException.class);
        assertThat(wrong).isInstanceOf(UnauthorizedException.class);
        assertThat(unknown.getMessage()).isEqualTo(wrong.getMessage()).isEqualTo("Invalid email or password");
    }

    @Test
    void unknownEmailsAndUnusableHashesStillSpendAPasswordHashVerification() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthService timed = new AuthService(userRepository, encoder, jwtService, refreshTokenService);
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());
        user.setPasswordHash("{UNUSABLE}abc");

        assertThatThrownBy(() -> timed.login(new LoginRequest("nobody@example.com", PASSWORD)))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid email or password");
        assertThatThrownBy(() -> timed.login(login(PASSWORD)))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid email or password");

        org.mockito.ArgumentCaptor<String> hash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(encoder, org.mockito.Mockito.times(2)).matches(org.mockito.ArgumentMatchers.eq(PASSWORD), hash.capture());
        // A real, well-formed strength-10 BCrypt hash, so the verification costs the same as a real one.
        assertThat(hash.getAllValues()).allSatisfy(h -> {
            assertThat(h).startsWith("$2a$10$").hasSize(60);
            assertThat(new BCryptPasswordEncoder().matches(PASSWORD, h)).isFalse();
        });
    }

    @Test
    void accountsWithoutAUsablePasswordCannotLogIn() {
        for (String hash : new String[] {null, "", "   ", "{UNUSABLE}abc"}) {
            user.setPasswordHash(hash);

            assertThatThrownBy(() -> service.login(login(PASSWORD)))
                    .as("hash [%s]", hash)
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid email or password");
        }
    }

    @Test
    void disabledAndSuspendedAccountsCannotLogInEvenWithTheRightPassword() {
        for (AccountStatus status : new AccountStatus[] {AccountStatus.INACTIVE, AccountStatus.SUSPENDED}) {
            user.setStatus(status);

            assertThatThrownBy(() -> service.login(login(PASSWORD)))
                    .as("status %s", status)
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Account is disabled. Contact support.");
        }
    }

    @Test
    void disabledAccountWithWrongPasswordStillGets401NotTheDisabledSignal() {
        user.setStatus(AccountStatus.SUSPENDED);

        assertThatThrownBy(() -> service.login(login("wrong-password")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void aFailedLoginNeverIssuesARefreshToken() {
        assertThatThrownBy(() -> service.login(login("wrong-password"))).isInstanceOf(UnauthorizedException.class);

        org.mockito.Mockito.verify(refreshTokenService, org.mockito.Mockito.never()).issueForLogin(org.mockito.ArgumentMatchers.any());
    }

    // ----- refresh -----

    @Test
    void refreshingIssuesNewTokensUsingTheCurrentRoleFromTheDatabase() {
        user.setRole(AccountRole.ORGANIZER); // promoted since the refresh token was issued
        when(refreshTokenService.rotate("refresh-1"))
                .thenReturn(new RefreshTokenService.Rotated(user.getId(), "refresh-2"));

        LoginResponse response = service.refresh("refresh-1");

        assertThat(response.refreshToken()).isEqualTo("refresh-2");
        assertThat(response.role()).isEqualTo(AccountRole.ORGANIZER);
        assertThat(jwtService.extractRoleFromBearer("Bearer " + response.accessToken())).isEqualTo(AccountRole.ORGANIZER);
    }

    @Test
    void aRejectedRefreshTokenPropagatesAsUnauthorized() {
        when(refreshTokenService.rotate("bad")).thenThrow(new UnauthorizedException("Invalid or expired session"));

        assertThatThrownBy(() -> service.refresh("bad")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshingForADisabledAccountIsRefused() {
        user.setStatus(AccountStatus.SUSPENDED);
        when(refreshTokenService.rotate("refresh-1"))
                .thenReturn(new RefreshTokenService.Rotated(user.getId(), "refresh-2"));

        assertThatThrownBy(() -> service.refresh("refresh-1")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshingForADeletedUserIsRefused() {
        UUID gone = UUID.randomUUID();
        when(userRepository.findById(gone)).thenReturn(Optional.empty());
        when(refreshTokenService.rotate("refresh-1")).thenReturn(new RefreshTokenService.Rotated(gone, "refresh-2"));

        assertThatThrownBy(() -> service.refresh("refresh-1")).isInstanceOf(UnauthorizedException.class);
    }

    // ----- the older access-token based re-issue, still used by the app on resume -----

    @Test
    void reissuingASessionPicksUpTheCurrentRoleWithoutANewRefreshToken() {
        user.setRole(AccountRole.STAFF);

        LoginResponse response = service.refreshToken(user.getId());

        assertThat(response.role()).isEqualTo(AccountRole.STAFF);
        assertThat(response.refreshToken()).isNull();
    }

    @Test
    void reissuingForADisabledAccountIsRefused() {
        user.setStatus(AccountStatus.INACTIVE);

        assertThatThrownBy(() -> service.refreshToken(user.getId())).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void endingASessionRevokesItsRefreshFamily() {
        service.endSession("refresh-1");

        verify(refreshTokenService).revokeFamilyOf("refresh-1");
    }
}
