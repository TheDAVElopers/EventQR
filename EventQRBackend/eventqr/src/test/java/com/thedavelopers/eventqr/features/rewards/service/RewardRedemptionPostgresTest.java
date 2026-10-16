package com.thedavelopers.eventqr.features.rewards.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.thedavelopers.eventqr.features.events.model.entity.Event;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionGrantRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResultResponse;
import com.thedavelopers.eventqr.features.rewards.model.entity.AttendeePointBalance;
import com.thedavelopers.eventqr.features.rewards.model.entity.Reward;
import com.thedavelopers.eventqr.features.rewards.repository.AttendeePointBalanceRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRepository;
import com.thedavelopers.eventqr.features.scanning.model.entity.ScanPurpose;
import com.thedavelopers.eventqr.features.scanning.repository.ScanPurposeRepository;
import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionLog;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.RedemptionStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.constants.RewardStatus;
import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort;

/**
 * Real-Postgres coverage for what mocks cannot prove: Flyway applies every migration through V41 on an empty
 * database (and Hibernate validates the mapping against it), the V40 trigger issues random EVQR- credentials, and
 * the scan-log row lock plus the V41 partial unique index let exactly one of two concurrent redemptions spend a scan.
 *
 * Skipped (not failed) when Docker is unavailable. Each step commits on its own (NOT_SUPPORTED), because the
 * concurrency check needs two independent transactions.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RewardRedemptionService.class, DuplicateRewardClaimChecker.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RewardRedemptionPostgresTest {

    private static final String QR_FORMAT = "^EVQR-[0-9A-F]{32}$";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "6");
    }

    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private RegistrationLookupPort registrationLookupPort;

    @Autowired
    private RewardRedemptionService rewardRedemptionService;
    @Autowired
    private UserProfileRepository users;
    @Autowired
    private EventRepository events;
    @Autowired
    private EventRegistrationRepository registrations;
    @Autowired
    private ScanPurposeRepository scanPurposes;
    @Autowired
    private RewardRepository rewards;
    @Autowired
    private AttendeePointBalanceRepository balances;
    @Autowired
    private TransactionLogRepository transactionLogs;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DataSource dataSource;

    private TransactionTemplate tx;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        jdbc = new JdbcTemplate(dataSource);
        // The real implementation, backed by the migrated schema (RegistrationService itself has many unrelated deps).
        when(registrationLookupPort.hasActiveRegistration(any(), any())).thenAnswer(inv ->
                registrations.existsActiveByEventIdAndAttendeeUserId(inv.getArgument(0), inv.getArgument(1)));
    }

    @Test
    void flywayReachesV41AndTheRegistrationTriggerIssuesARandomQrValue() {
        assertThat(jdbc.queryForObject(
                "select max(version::int) from flyway_schema_history where success", Integer.class))
                .isGreaterThanOrEqualTo(41);
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_indexes where indexname = 'ux_reward_redemptions_scan_log_redeemed'",
                Integer.class)).isEqualTo(1);

        Seed seed = seed();

        String qrValue = jdbc.queryForObject("select q.qr_value from public.qr_credentials q "
                + "join public.event_registrations r on r.qr_credential_id = q.id where r.id = ?",
                String.class, seed.registrationId());
        assertThat(qrValue).matches(QR_FORMAT);
        assertThat(qrValue).doesNotContain(seed.eventId().toString());
    }

    @Test
    void twoConcurrentRedemptionsOfOneScanYieldExactlyOneRedeemed() throws Exception {
        Seed seed = seed();
        RewardRedemptionGrantRequest request = new RewardRedemptionGrantRequest(seed.eventId(), seed.attendeeId(),
                seed.rewardId(), seed.staffId(), seed.scanLogId());

        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> attempt = () -> {
            start.await();
            try {
                return rewardRedemptionService.redeem(request);
            } catch (RuntimeException e) {
                return e;
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Object> outcomes = new ArrayList<>();
        try {
            Future<Object> first = pool.submit(attempt);
            Future<Object> second = pool.submit(attempt);
            start.countDown();
            outcomes.add(first.get(30, TimeUnit.SECONDS));
            outcomes.add(second.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(outcomes).filteredOn(o -> o instanceof RewardRedemptionResultResponse r
                && r.status() == RedemptionStatus.REDEEMED).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o instanceof ConflictException c
                && GlobalExceptionHandler.REDEMPTION_SCAN_ALREADY_USED_MESSAGE.equals(c.getMessage())).hasSize(1);

        assertThat(jdbc.queryForObject("select count(*) from public.reward_redemptions "
                + "where redemption_scan_log_id = ? and status = 'REDEEMED'", Integer.class, seed.scanLogId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select points_balance from public.attendee_point_balances "
                + "where event_id = ? and attendee_user_id = ?", Integer.class, seed.eventId(), seed.attendeeId()))
                .isEqualTo(90);
    }

    // --- seeding ------------------------------------------------------------

    private record Seed(UUID eventId, UUID attendeeId, UUID staffId, UUID registrationId, UUID rewardId,
                        UUID scanLogId) {
    }

    private Seed seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID organizerId = tx.execute(s -> user("org-" + suffix + "@example.com", AccountRole.ORGANIZER).getId());
        UUID attendeeId = tx.execute(s -> user("att-" + suffix + "@example.com", AccountRole.ATTENDEE).getId());
        UUID staffId = tx.execute(s -> user("staff-" + suffix + "@example.com", AccountRole.STAFF).getId());

        UUID eventId = tx.execute(s -> {
            Event event = new Event();
            event.setTitle("Expo " + suffix);
            event.setLocation("Hall");
            event.setCapacity(100);
            event.setStatus(EventStatus.APPROVED);
            event.setRewardsEnabled(true);
            event.setOrganizerUserId(organizerId);
            event.setEventStartAt(Instant.now().plusSeconds(3600));
            event.setEventEndAt(Instant.now().plusSeconds(7200));
            return events.saveAndFlush(event).getId();
        });

        // Committed on its own so the AFTER INSERT trigger's qr_credentials row and back-reference are visible.
        UUID registrationId = tx.execute(s -> {
            EventRegistration registration = new EventRegistration();
            registration.setEventId(eventId);
            registration.setAttendeeUserId(attendeeId);
            registration.setAttendeeEmail("att-" + suffix + "@example.com");
            registration.setAttendeeName("Attendee");
            registration.setStatus(RegistrationStatus.REGISTERED);
            registration.setRegisteredAt(Instant.now());
            return registrations.saveAndFlush(registration).getId();
        });
        UUID qrCredentialId = jdbc.queryForObject(
                "select qr_credential_id from public.event_registrations where id = ?", UUID.class, registrationId);

        UUID purposeId = tx.execute(s -> {
            ScanPurpose purpose = new ScanPurpose();
            purpose.setEventId(eventId);
            purpose.setName("Reward Redemption");
            purpose.setCode(ScanPurposeCode.REWARD_REDEMPTION_SCAN);
            purpose.setActive(true);
            return scanPurposes.saveAndFlush(purpose).getId();
        });

        UUID rewardId = tx.execute(s -> {
            Reward reward = new Reward();
            reward.setEventId(eventId);
            reward.setName("Tote");
            reward.setPointsRequired(10);
            reward.setStatus(RewardStatus.ACTIVE);
            reward.setStockQuantity(null);
            // Duplicate claims allowed, so only the single-use scan rule can stop the second redemption.
            reward.setAllowDuplicateClaims(true);
            return rewards.saveAndFlush(reward).getId();
        });

        tx.executeWithoutResult(s -> {
            AttendeePointBalance balance = new AttendeePointBalance();
            balance.setEventId(eventId);
            balance.setAttendeeUserId(attendeeId);
            balance.setPointsBalance(100);
            balances.saveAndFlush(balance);
        });

        UUID scanLogId = tx.execute(s -> {
            TransactionLog scan = new TransactionLog();
            scan.setEventId(eventId);
            scan.setAttendeeUserId(attendeeId);
            scan.setRegistrationId(registrationId);
            scan.setQrCredentialId(qrCredentialId);
            scan.setScanPurposeId(purposeId);
            scan.setStaffUserId(staffId);
            scan.setTransactionType(TransactionType.REWARD_REDEMPTION_SCAN);
            scan.setTransactionResult(TransactionResult.APPROVED);
            scan.setScannedAt(Instant.now());
            return transactionLogs.saveAndFlush(scan).getId();
        });

        return new Seed(eventId, attendeeId, staffId, registrationId, rewardId, scanLogId);
    }

    private UserProfile user(String email, AccountRole role) {
        UserProfile user = new UserProfile();
        user.setEmail(email);
        user.setFullName(role.name());
        user.setPhoneNumber("+639171234567");
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        user.setPasswordHash("{noop}unused");
        return users.saveAndFlush(user);
    }
}
