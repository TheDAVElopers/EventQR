package com.thedavelopers.eventqr.features.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse;
import com.thedavelopers.eventqr.features.auth.model.entity.PasswordResetToken;
import com.thedavelopers.eventqr.features.auth.model.entity.RefreshToken;
import com.thedavelopers.eventqr.features.auth.repository.PasswordResetTokenRepository;
import com.thedavelopers.eventqr.features.auth.repository.RefreshTokenRepository;
import com.thedavelopers.eventqr.features.qremail.service.EmailGatewayService;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.model.entity.UserTokenRevocation;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.features.users.repository.UserTokenRevocationRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.exceptions.UnauthorizedException;
import com.thedavelopers.eventqr.shared.security.AccessTokenRevoker;
import com.thedavelopers.eventqr.shared.security.JwtAuthenticationFilter;
import com.thedavelopers.eventqr.shared.security.JwtService;
import com.thedavelopers.eventqr.shared.security.UserTokenRevocationChecker;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Wires the real token pieces (JwtService, JwtAuthenticationFilter, UserTokenRevocationChecker with its cache,
 * AccessTokenRevoker, RefreshTokenService, ChangePasswordService, PasswordResetService) over in-memory repositories,
 * and checks through the filter that a password change or reset kills every earlier access token immediately while
 * the session returned by change-password works.
 */
class PasswordChangeSessionRevocationTest {

    private static final String SECRET = "01234567890123456789012345678901";
    private static final String OLD_PASSWORD = "OldPassw0rd!";
    private static final String NEW_PASSWORD = "N3wPassw0rd!x";

    private final Map<UUID, UserTokenRevocation> revocations = new HashMap<>();
    private final Map<String, RefreshToken> refreshByHash = new HashMap<>();

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;
    private RefreshTokenService refreshTokenService;
    private ChangePasswordService changePasswordService;
    private PasswordResetService passwordResetService;
    private AuthService authService;
    private PasswordResetTokenRepository resetTokens;
    private UserTokenRevocationChecker checker;
    private UserProfile user;

    @BeforeEach
    void setUp() {
        user = new UserProfile();
        user.setId(UUID.randomUUID());
        user.setEmail("staff@example.com");
        user.setFullName("Sam Staff");
        user.setRole(AccountRole.STAFF);
        user.setStatus(AccountStatus.ACTIVE);
        user.setPasswordHash("enc(" + OLD_PASSWORD + ")");

        UserProfileRepository users = mock(UserProfileRepository.class);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(users.save(any(UserProfile.class))).thenAnswer(i -> i.getArgument(0));

        UserTokenRevocationRepository revocationRepository = mock(UserTokenRevocationRepository.class);
        when(revocationRepository.findByUserId(any())).thenAnswer(i -> Optional.ofNullable(revocations.get(i.<UUID>getArgument(0))));
        // Models INSERT ... ON CONFLICT DO UPDATE SET revoked_at = GREATEST(old, new) RETURNING floor(ms).
        when(revocationRepository.upsertNotBackwards(any(), any())).thenAnswer(i -> {
            UUID userId = i.getArgument(0);
            Instant requested = i.getArgument(1);
            UserTokenRevocation existing = revocations.get(userId);
            Instant effective = existing == null || existing.getRevokedAt().isBefore(requested)
                    ? requested : existing.getRevokedAt();
            revocations.put(userId, new UserTokenRevocation(userId, effective));
            return effective.toEpochMilli();
        });
        when(revocationRepository.save(any(UserTokenRevocation.class))).thenAnswer(i -> {
            UserTokenRevocation r = i.getArgument(0);
            revocations.put(r.getUserId(), r);
            return r;
        });

        RefreshTokenRepository refreshRepository = mock(RefreshTokenRepository.class);
        when(refreshRepository.save(any(RefreshToken.class))).thenAnswer(i -> {
            RefreshToken token = i.getArgument(0);
            refreshByHash.put(token.getTokenHash(), token);
            return token;
        });
        when(refreshRepository.findByTokenHash(anyString()))
                .thenAnswer(i -> Optional.ofNullable(refreshByHash.get(i.<String>getArgument(0))));
        when(refreshRepository.revokeAllForUser(any(), any())).thenAnswer(i -> {
            UUID userId = i.getArgument(0);
            Instant now = i.getArgument(1);
            refreshByHash.values().stream()
                    .filter(t -> t.getUserId().equals(userId) && t.getRevokedAt() == null)
                    .forEach(t -> t.setRevokedAt(now));
            return 0;
        });

        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenAnswer(i -> "enc(" + i.getArgument(0) + ")");
        when(encoder.matches(anyString(), anyString()))
                .thenAnswer(i -> ("enc(" + i.getArgument(0) + ")").equals(i.getArgument(1)));

        jwtService = new JwtService(SECRET, 86_400_000L);
        checker = new UserTokenRevocationChecker(users, revocationRepository);
        AccessTokenRevoker revoker = new AccessTokenRevoker(revocationRepository, checker);
        filter = new JwtAuthenticationFilter(jwtService, checker);
        refreshTokenService = new RefreshTokenService(refreshRepository, checker, revoker, 30);
        changePasswordService = new ChangePasswordService(users, encoder, refreshTokenService, jwtService);
        authService = new AuthService(users, encoder, jwtService, refreshTokenService);

        resetTokens = mock(PasswordResetTokenRepository.class);
        when(resetTokens.save(any(PasswordResetToken.class))).thenAnswer(i -> i.getArgument(0));
        passwordResetService = new PasswordResetService(resetTokens, users, encoder, mock(EmailGatewayService.class),
                refreshTokenService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private boolean authenticates(String accessToken) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });
        boolean authenticated = SecurityContextHolder.getContext().getAuthentication() != null;
        SecurityContextHolder.clearContext();
        return authenticated;
    }

    private String login() {
        return jwtService.createToken(user.getId(), user.getEmail(), user.getRole());
    }

    @Test
    void changePassword_killsEveryEarlierAccessToken_andReturnsAWorkingSession() throws Exception {
        String callerToken = login();
        String otherDeviceToken = login();
        String otherDeviceRefresh = refreshTokenService.issueForLogin(user.getId());
        // Warm the checker cache with an "allowed" decision, as a live server would have.
        assertThat(authenticates(callerToken)).isTrue();
        assertThat(authenticates(otherDeviceToken)).isTrue();

        LoginResponse session = changePasswordService.changePassword(user.getId(), OLD_PASSWORD, NEW_PASSWORD,
                NEW_PASSWORD);

        assertThat(authenticates(callerToken)).as("token used for the change").isFalse();
        assertThat(authenticates(otherDeviceToken)).as("other device").isFalse();
        assertThatThrownBy(() -> authService.refresh(otherDeviceRefresh)).isInstanceOf(UnauthorizedException.class);

        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.refreshToken()).isNotBlank();
        assertThat(session.userId()).isEqualTo(user.getId());
        assertThat(session.email()).isEqualTo(user.getEmail());
        assertThat(session.fullName()).isEqualTo(user.getFullName());
        assertThat(session.role()).isEqualTo(AccountRole.STAFF);
        assertThat(session.message()).isEqualTo("Password has been changed");
        assertThat(authenticates(session.accessToken())).as("fresh session access token").isTrue();

        // The fresh refresh token rotates and the rotation reflects the CURRENT role (role change as
        // UserService.saveRole does it: no revocation marker, just a cache invalidation).
        user.setRole(AccountRole.ORGANIZER);
        checker.invalidate(user.getId());
        assertThat(authenticates(session.accessToken())).as("stale-role access token").isFalse();
        LoginResponse refreshed = authService.refresh(session.refreshToken());
        assertThat(refreshed.role()).isEqualTo(AccountRole.ORGANIZER);
        assertThat(refreshed.refreshToken()).isNotEqualTo(session.refreshToken());
        assertThat(authenticates(refreshed.accessToken())).isTrue();
    }

    @Test
    void changePassword_newSessionIsValidEvenWhenMintedInTheSameMillisecondAsTheMarker() throws Exception {
        // Repeat to make a same-millisecond (and same-second) mint after the marker overwhelmingly likely.
        for (int i = 0; i < 25; i++) {
            String password = i % 2 == 0 ? OLD_PASSWORD : NEW_PASSWORD;
            String next = i % 2 == 0 ? NEW_PASSWORD : OLD_PASSWORD;
            LoginResponse session = changePasswordService.changePassword(user.getId(), password, next, next);
            Instant marker = revocations.get(user.getId()).getRevokedAt();

            assertThat(authenticates(session.accessToken())).isTrue();
            assertThat(jwtService.issuedAtOf(Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(session.accessToken()).getPayload())).isAfter(marker);
            assertThat(refreshByHash.get(RefreshTokenService.sha256(session.refreshToken())).getCreatedAt())
                    .isAfter(marker);
        }
    }

    @Test
    void resetPassword_killsEveryEarlierAccessToken() throws Exception {
        String token = login();
        assertThat(authenticates(token)).isTrue();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(user.getId());
        resetToken.setToken(RefreshTokenService.sha256(user.getId() + ":123456"));
        resetToken.setExpiresAt(Instant.now().plusSeconds(600));
        when(resetTokens.findLatestActiveForUpdate(eq(user.getId()), any())).thenReturn(Optional.of(resetToken));

        passwordResetService.resetPassword(user.getEmail(), "123456", NEW_PASSWORD, NEW_PASSWORD);

        assertThat(authenticates(token)).isFalse();
        // Signing in again afterwards works.
        Thread.sleep(2);
        assertThat(authenticates(login())).isTrue();
    }

    @Test
    void legacySecondPrecisionTokenFromTheMarkerSecondIsRejected() throws Exception {
        Instant marker = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        revocations.put(user.getId(), new UserTokenRevocation(user.getId(), marker));
        // A token minted before iatMs existed: only whole-second iat, same second as the marker.
        String legacy = Jwts.builder()
                .subject(user.getId().toString())
                .claim("userId", user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(marker))
                .expiration(Date.from(marker.plusSeconds(3600)))
                .signWith(key())
                .compact();

        assertThat(authenticates(legacy)).isFalse();
    }

    @Test
    void tokenMintedAtTheMarkerMillisecondIsRejected_andOneMillisecondLaterIsAccepted() {
        Instant marker = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UserProfileRepository users = mock(UserProfileRepository.class);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        UserTokenRevocationRepository repo = mock(UserTokenRevocationRepository.class);
        when(repo.findByUserId(user.getId())).thenReturn(Optional.of(new UserTokenRevocation(user.getId(), marker)));
        UserTokenRevocationChecker checker = new UserTokenRevocationChecker(users, repo);

        assertThat(checker.isAccessAllowed(user.getId(), marker)).isFalse();
        assertThat(checker.isAccessAllowed(user.getId(), marker.plusMillis(1))).isTrue();
    }

    @Test
    void demotedAdmin_oldAccessTokenIs401_refreshYieldsTheNewRole_andRefreshTokensSurvive() throws Exception {
        user.setRole(AccountRole.ADMIN);
        String adminToken = login();
        String refresh = refreshTokenService.issueForLogin(user.getId());
        assertThat(authenticates(adminToken)).isTrue();

        user.setRole(AccountRole.ATTENDEE);
        checker.invalidateAfterCommit(user.getId()); // no transaction here: invalidates immediately

        assertThat(authenticates(adminToken)).as("demoted admin's token").isFalse();
        assertThat(revocations).as("role change writes no revocation marker").doesNotContainKey(user.getId());
        LoginResponse rotated = authService.refresh(refresh);
        assertThat(rotated.role()).isEqualTo(AccountRole.ATTENDEE);
        assertThat(authenticates(rotated.accessToken())).isTrue();
    }

    @Test
    void disabledAccount_stillRejectsEvenAFreshToken() throws Exception {
        String token = login();
        user.setStatus(AccountStatus.SUSPENDED);
        assertThat(authenticates(token)).isFalse();
    }

    private static SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
