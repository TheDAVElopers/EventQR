package com.thedavelopers.eventqr.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.thedavelopers.eventqr.features.auth.model.entity.RefreshToken;
import com.thedavelopers.eventqr.features.auth.repository.RefreshTokenRepository;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.features.users.repository.UserTokenRevocationRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;

/**
 * Real-Postgres coverage for what mocks cannot prove: Flyway applies every migration (V42 id_print_logs status /
 * printed_by_user_id, V43 refresh_token replaced_by_hash / family_created_at) and Hibernate validates the entity
 * mapping against the result; the native {@code INSERT ... ON CONFLICT ... RETURNING} marker upsert works and never
 * moves the marker backwards. Skipped (not failed) when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TokenRevocationPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private UserProfileRepository users;
    @Autowired
    private UserTokenRevocationRepository revocations;
    @Autowired
    private RefreshTokenRepository refreshTokens;

    private UserProfile user() {
        UserProfile user = new UserProfile();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setFullName("Token Test");
        user.setPhoneNumber("+639171234567");
        user.setRole(AccountRole.STAFF);
        user.setStatus(AccountStatus.ACTIVE);
        user.setPasswordHash("{noop}unused");
        return users.saveAndFlush(user);
    }

    @Test
    void markerUpsertInsertsThenOnlyMovesForward() {
        UUID userId = user().getId();
        Instant first = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        assertThat(revocations.upsertNotBackwards(userId, first)).isEqualTo(first.toEpochMilli());
        Instant later = first.plusSeconds(10);
        assertThat(revocations.upsertNotBackwards(userId, later)).isEqualTo(later.toEpochMilli());
        assertThat(revocations.upsertNotBackwards(userId, first)).as("never backwards").isEqualTo(later.toEpochMilli());
        assertThat(revocations.findByUserId(userId)).get()
                .extracting(r -> r.getRevokedAt().toEpochMilli()).isEqualTo(later.toEpochMilli());
    }

    @Test
    void refreshTokenV43ColumnsRoundTrip() {
        UUID userId = user().getId();
        Instant familyStart = Instant.now().minusSeconds(3600).truncatedTo(ChronoUnit.MICROS);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        RefreshToken token = new RefreshToken(userId, UUID.randomUUID(), "a".repeat(64), now, now.plusSeconds(60),
                familyStart);
        token.setReplacedByHash("b".repeat(64));
        refreshTokens.saveAndFlush(token);

        RefreshToken loaded = refreshTokens.findByTokenHash("a".repeat(64)).orElseThrow();
        assertThat(loaded.getFamilyCreatedAt()).isEqualTo(familyStart);
        assertThat(loaded.getReplacedByHash()).isEqualTo("b".repeat(64));
    }
}
