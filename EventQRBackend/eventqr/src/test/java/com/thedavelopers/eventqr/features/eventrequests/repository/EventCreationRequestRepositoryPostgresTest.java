package com.thedavelopers.eventqr.features.eventrequests.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.thedavelopers.eventqr.features.eventrequests.model.entity.EventCreationRequest;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.constants.EventRequestStatus;

/**
 * Real-Postgres proof for the admin dashboard queries: the pending badge counts every PENDING row without loading
 * them, and the preview loads at most three of the oldest ones.
 *
 * Skipped (not failed) when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventCreationRequestRepositoryPostgresTest {

    private static final Instant BASE = Instant.parse("2026-11-01T08:00:00Z");

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
    private EventCreationRequestRepository requests;
    @Autowired
    private UserProfileRepository users;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void pendingPreviewIsCappedAtThreeOldestFirstWhileTheCountCoversEveryPendingRow() {
        UserProfile requester = users.save(requester("requester@example.com"));

        List<EventCreationRequest> pending = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            pending.add(requests.save(request(requester, "Pending " + i, EventRequestStatus.PENDING)));
        }
        requests.save(request(requester, "Approved", EventRequestStatus.APPROVED));
        requests.save(request(requester, "Rejected", EventRequestStatus.REJECTED));
        requests.flush();

        // Auditing stamps created_at on persist, so pin the order after the insert to make "oldest first" provable.
        int hour = 1;
        for (EventCreationRequest request : pending) {
            jdbcTemplate.update("update event_requests set created_at = ? where id = ?",
                    OffsetDateTime.ofInstant(BASE.plus(hour++, ChronoUnit.HOURS), ZoneOffset.UTC), request.getId());
        }
        entityManager.clear();

        assertThat(requests.countByStatus(EventRequestStatus.PENDING)).isEqualTo(5);
        assertThat(requests.findTop3ByStatusOrderByCreatedAtAsc(EventRequestStatus.PENDING))
                .extracting(EventCreationRequest::getEventName)
                .containsExactly("Pending 1", "Pending 2", "Pending 3");
    }

    private UserProfile requester(String email) {
        UserProfile user = new UserProfile();
        user.setEmail(email);
        user.setFullName("Requester");
        user.setPasswordHash("x");
        user.setRole(AccountRole.ATTENDEE);
        user.setStatus(AccountStatus.ACTIVE);
        return user;
    }

    private EventCreationRequest request(UserProfile requester, String eventName, EventRequestStatus status) {
        EventCreationRequest request = new EventCreationRequest();
        request.setRequesterUserId(requester.getId());
        request.setRequesterName(requester.getFullName());
        request.setEventName(eventName);
        request.setEventDescription("Description");
        request.setEventCategory("Technology");
        request.setCapacity(100);
        request.setVenue("Main Hall");
        request.setStartDateTime(BASE.plus(30, ChronoUnit.DAYS));
        request.setEndDateTime(BASE.plus(31, ChronoUnit.DAYS));
        request.setContactEmail(requester.getEmail());
        request.setContactNumber("+639170000000");
        request.setReasonForRequest("Testing");
        request.setStatus(status);
        return request;
    }
}
