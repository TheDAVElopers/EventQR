package com.thedavelopers.eventqr.features.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.features.auth.model.entity.RefreshToken;
import com.thedavelopers.eventqr.features.auth.repository.RefreshTokenRepository;
import com.thedavelopers.eventqr.shared.exceptions.UnauthorizedException;
import com.thedavelopers.eventqr.shared.security.UserTokenRevocationChecker;

import static org.mockito.Mockito.mock;

class RefreshTokenServiceTest {

    private final Map<String, RefreshToken> byHash = new HashMap<>();
    private RefreshTokenRepository repository;
    private UserTokenRevocationChecker checker;
    private RefreshTokenService service;
    private com.thedavelopers.eventqr.shared.security.AccessTokenRevoker revoker;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        checker = mock(UserTokenRevocationChecker.class);
        when(checker.isAccessAllowed(any(), any())).thenReturn(true);
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            byHash.put(token.getTokenHash(), token);
            return token;
        });
        when(repository.findByTokenHash(any())).thenAnswer(invocation ->
                Optional.ofNullable(byHash.get(invocation.<String>getArgument(0))));
        revoker = mock(com.thedavelopers.eventqr.shared.security.AccessTokenRevoker.class);
        service = new RefreshTokenService(repository, checker, revoker, 30);
    }

    private RefreshToken stored(String raw) {
        return byHash.get(RefreshTokenService.sha256(raw));
    }

    @Test
    void issuedTokenIsStoredAsAHashNotTheRawValue() {
        String raw = service.issueForLogin(userId);

        assertThat(byHash).doesNotContainKey(raw);
        assertThat(stored(raw)).isNotNull();
        assertThat(stored(raw).getTokenHash()).hasSize(64);
    }

    @Test
    void rotationMarksTheOldTokenUsedAndIssuesASuccessorInTheSameFamily() {
        String first = service.issueForLogin(userId);

        RefreshTokenService.Rotated rotated = service.rotate(first);

        assertThat(rotated.userId()).isEqualTo(userId);
        assertThat(rotated.refreshToken()).isNotEqualTo(first);
        assertThat(stored(first).getUsedAt()).isNotNull();
        assertThat(stored(rotated.refreshToken()).getFamilyId()).isEqualTo(stored(first).getFamilyId());
    }

    @Test
    void resendingATokenWithinTheRetryWindowIsTreatedAsARetry() {
        String first = service.issueForLogin(userId);
        service.rotate(first);

        RefreshTokenService.Rotated retry = service.rotate(first);

        assertThat(retry.refreshToken()).isNotBlank();
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void reusingATokenAfterTheRetryWindowRevokesTheWholeFamily() {
        String first = service.issueForLogin(userId);
        service.rotate(first);
        RefreshToken old = stored(first);
        old.setUsedAt(Instant.now().minus(service.retryWindow()).minusSeconds(5));

        assertThatThrownBy(() -> service.rotate(first)).isInstanceOf(UnauthorizedException.class);

        verify(repository).revokeFamily(any(UUID.class), any(Instant.class));
    }

    @Test
    void unknownBlankAndNullTokensAreRejected() {
        assertThatThrownBy(() -> service.rotate("not-a-token")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.rotate("  ")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.rotate(null)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void expiredAndRevokedTokensAreRejected() {
        String expiredRaw = service.issueForLogin(userId);
        RefreshToken expired = stored(expiredRaw);
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        String revokedRaw = service.issueForLogin(userId);
        stored(revokedRaw).setRevokedAt(Instant.now());

        assertThatThrownBy(() -> service.rotate(expiredRaw)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.rotate(revokedRaw)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void aDisabledAccountCannotRefresh() {
        String raw = service.issueForLogin(userId);
        when(checker.isAccessAllowed(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.rotate(raw)).isInstanceOf(UnauthorizedException.class);
        assertThat(stored(raw).getUsedAt()).isNull();
    }

    @Test
    void tokensLiveForTheConfiguredNumberOfDays() {
        String raw = service.issueForLogin(userId);

        Duration lifetime = Duration.between(stored(raw).getCreatedAt(), stored(raw).getExpiresAt());

        assertThat(lifetime).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void logoutRevokesTheFamilyOfTheGivenToken() {
        String raw = service.issueForLogin(userId);

        service.revokeFamilyOf(raw);

        verify(repository).revokeFamily(any(UUID.class), any(Instant.class));
    }

    @Test
    void logoutWithoutATokenDoesNothing() {
        service.revokeFamilyOf(null);
        service.revokeFamilyOf("unknown");

        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void revokingAllForAUserAlsoMovesTheAccessTokenMarker() {
        Instant marker = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        when(revoker.revokeAllIssuedUntilNow(userId)).thenReturn(marker);

        assertThat(service.revokeAllForUser(userId)).isEqualTo(marker);

        org.mockito.Mockito.verify(revoker).revokeAllIssuedUntilNow(userId);
        org.mockito.Mockito.verify(repository).revokeAllForUser(org.mockito.ArgumentMatchers.eq(userId), any());
    }

    @Test
    void aTokenIssuedAfterAMarkerIsCreatedStrictlyAfterIt() {
        Instant futureMarker = Instant.now().plusSeconds(5).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        String raw = service.issueForLoginAfter(userId, futureMarker);

        assertThat(stored(raw).getCreatedAt()).isEqualTo(futureMarker.plusMillis(1));
    }

    // ----- grace retry keeps a single live branch (V43 replaced_by_hash) -----

    @Test
    void theRetryWindowDefaultsToSixtySeconds_longerThanTheAppsNetworkTimeouts() {
        assertThat(RefreshTokenService.DEFAULT_RETRY_WINDOW).isEqualTo(Duration.ofSeconds(60));
        assertThat(service.retryWindow()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void aSlowRetryInsideTheWindow_forExampleAfterA45SecondTimeout_isStillARetry() {
        String first = service.issueForLogin(userId);
        String lostChild = service.rotate(first).refreshToken();
        stored(first).setUsedAt(Instant.now().minusSeconds(50));

        String retryChild = service.rotate(first).refreshToken();

        assertThat(retryChild).isNotBlank();
        assertThat(stored(lostChild).getRevokedAt()).isNotNull();
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void theRetryWindowIsConfigurable() {
        RefreshTokenService strict = new RefreshTokenService(repository, checker, revoker, 30, 90, Duration.ofSeconds(5));
        String first = strict.issueForLogin(userId);
        strict.rotate(first);
        stored(first).setUsedAt(Instant.now().minusSeconds(10));

        assertThatThrownBy(() -> strict.rotate(first)).isInstanceOf(UnauthorizedException.class);
        verify(repository).revokeFamily(any(UUID.class), any(Instant.class));
    }

    @Test
    void rotationRecordsTheSuccessorHash() {
        String first = service.issueForLogin(userId);

        RefreshTokenService.Rotated rotated = service.rotate(first);

        assertThat(stored(first).getReplacedByHash()).isEqualTo(RefreshTokenService.sha256(rotated.refreshToken()));
    }

    @Test
    void aGraceRetryRevokesTheEarlierSuccessorSoOnlyOneChildIsAlive() {
        String first = service.issueForLogin(userId);
        String lostChild = service.rotate(first).refreshToken();

        String retryChild = service.rotate(first).refreshToken();

        assertThat(stored(lostChild).getRevokedAt()).as("earlier successor").isNotNull();
        assertThat(stored(retryChild).getRevokedAt()).isNull();
        assertThat(stored(first).getReplacedByHash()).isEqualTo(RefreshTokenService.sha256(retryChild));
        assertThatThrownBy(() -> service.rotate(lostChild)).isInstanceOf(UnauthorizedException.class);
        assertThat(service.rotate(retryChild).refreshToken()).isNotBlank();
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void aRetryAfterTheEarlierSuccessorWasUsedIsReuse_andRevokesTheFamily() {
        String first = service.issueForLogin(userId);
        String child = service.rotate(first).refreshToken();
        service.rotate(child); // the "lost" child was actually used, so the parent cannot be a lost-response retry

        assertThatThrownBy(() -> service.rotate(first)).isInstanceOf(UnauthorizedException.class);

        verify(repository).revokeFamily(eq(stored(first).getFamilyId()), any(Instant.class));
    }

    @Test
    void aGraceRetryOfAPreV43RowWithoutASuccessorLinkStillWorks() {
        String first = service.issueForLogin(userId);
        service.rotate(first);
        stored(first).setReplacedByHash(null); // rotated before V43

        assertThat(service.rotate(first).refreshToken()).isNotBlank();
        verify(repository, never()).revokeFamily(any(), any());
    }

    // ----- absolute session lifetime (V43 family_created_at) -----

    @Test
    void familyStartIsSetOnLoginAndCarriedOverOnRotation() {
        String first = service.issueForLogin(userId);
        Instant familyStart = stored(first).getFamilyCreatedAt();
        assertThat(familyStart).isEqualTo(stored(first).getCreatedAt());

        String second = service.rotate(first).refreshToken();
        String third = service.rotate(second).refreshToken();

        assertThat(stored(second).getFamilyCreatedAt()).isEqualTo(familyStart);
        assertThat(stored(third).getFamilyCreatedAt()).isEqualTo(familyStart);
    }

    @Test
    void rotationIsRefusedOnceTheFamilyIsOlderThanTheAbsoluteLifetime() {
        String first = service.issueForLogin(userId);
        stored(first).setFamilyCreatedAt(Instant.now().minus(Duration.ofDays(90)).minusSeconds(1));

        assertThatThrownBy(() -> service.rotate(first)).isInstanceOf(UnauthorizedException.class);
        assertThat(stored(first).getUsedAt()).isNull();
    }

    @Test
    void aSuccessorNeverOutlivesTheFamilysAbsoluteLifetime() {
        String first = service.issueForLogin(userId);
        Instant familyStart = Instant.now().minus(Duration.ofDays(80));
        stored(first).setFamilyCreatedAt(familyStart);

        String next = service.rotate(first).refreshToken();

        assertThat(stored(next).getExpiresAt()).isEqualTo(familyStart.plus(Duration.ofDays(90)));
    }

    @Test
    void theAbsoluteLifetimeIsConfigurable() {
        RefreshTokenService shortLived = new RefreshTokenService(repository, checker, revoker, 30, 1);
        String first = shortLived.issueForLogin(userId);
        stored(first).setFamilyCreatedAt(Instant.now().minus(Duration.ofDays(1)).minusSeconds(1));

        assertThatThrownBy(() -> shortLived.rotate(first)).isInstanceOf(UnauthorizedException.class);
    }
}
