package com.thedavelopers.eventqr.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.thedavelopers.eventqr.shared.exceptions.TooManyRequestsException;

class LoginRateLimiterTest {

    private static final long WINDOW_MS = 15 * 60_000L;
    private static final String BLOCKED = "Too many login attempts. Please try again later.";

    private TestMillis clock;
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new TestMillis();
        limiter = new LoginRateLimiter(clock);
    }

    private static MockHttpServletRequest from(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", ip);
        return request;
    }

    private static MockHttpServletRequest unknownIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("");
        return request;
    }

    private void attempts(MockHttpServletRequest request, String email, int times) {
        for (int i = 0; i < times; i++) {
            limiter.acquire(request, email);
        }
    }

    private TooManyRequestsException blockedBy(MockHttpServletRequest request, String email) {
        TooManyRequestsException e = catchThrowableOfType(TooManyRequestsException.class,
                () -> limiter.acquire(request, email));
        assertThat(e).as("expected a 429").isNotNull();
        return e;
    }

    // ----- pair tier (email + IP), the first to trip -----

    @Test
    void sixFailuresFromOnePairAreAllowedAndTheSeventhIsBlockedWithRetryAfter() {
        MockHttpServletRequest request = from("203.0.113.9");
        attempts(request, "a@x.com", 6);

        TooManyRequestsException blocked = blockedBy(request, "a@x.com");

        assertThat(blocked.getRetryAfterSeconds()).isEqualTo(900);
        assertThat(blocked.getMessage()).isEqualTo(BLOCKED);
    }

    @Test
    void anAttackerIpCannotLockTheVictimOutFromAnotherIp() {
        MockHttpServletRequest attacker = from("203.0.113.9");
        MockHttpServletRequest victim = from("198.51.100.20");
        attempts(attacker, "victim@x.com", 6);
        blockedBy(attacker, "victim@x.com");

        // The victim's own IP has a separate pair bucket, so they can still log in (and retry).
        attempts(victim, "victim@x.com", 6);
        assertThat(limiter.emailKeyCount()).isEqualTo(1);
    }

    @Test
    void aDistributedAttackerNeedsThirtyRequestsToTripTheEmailCeiling() {
        // 5 attacker IPs x 6 attempts = 30 failures on the email, each IP staying within its pair cap.
        for (int ip = 1; ip <= 5; ip++) {
            attempts(from("203.0.113." + ip), "victim@x.com", 6);
        }

        TooManyRequestsException blocked = blockedBy(from("198.51.100.20"), "victim@x.com");
        assertThat(blocked.getMessage()).isEqualTo(BLOCKED);
        assertThat(blocked.getRetryAfterSeconds()).isEqualTo(900);
        // Another account from the victim's IP is unaffected.
        assertThat(limiter.acquire(from("198.51.100.20"), "someone-else@x.com")).isNotNull();
    }

    @Test
    void theEmailCeilingIsExactlyThirty() {
        // 29 failures spread over fresh IPs (1 each): still allowed; the 31st is blocked.
        for (int i = 0; i < 30; i++) {
            limiter.acquire(from("203.0.113." + (i + 1)), "victim@x.com");
        }
        blockedBy(from("203.0.113.200"), "victim@x.com");
    }

    @Test
    void retryAfterShrinksAndTheKeyFreesAfterFifteenMinutes() {
        MockHttpServletRequest request = from("203.0.113.9");
        attempts(request, "a@x.com", 6);

        clock.advance(300_000L);
        assertThat(blockedBy(request, "a@x.com").getRetryAfterSeconds()).isEqualTo(600);

        clock.advance(WINDOW_MS - 300_000L);
        assertThat(limiter.acquire(request, "a@x.com")).isNotNull();
    }

    @Test
    void retryAfterIsRoundedUpAndNeverBelowOne() {
        MockHttpServletRequest request = from("203.0.113.9");
        attempts(request, "a@x.com", 6);

        clock.advance(WINDOW_MS - 1L);
        assertThat(blockedBy(request, "a@x.com").getRetryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void retryAfterIsTheMaxOverAllExceededKeys() {
        LoginRateLimiter small = new LoginRateLimiter(clock, 7, 6, 50);
        small.acquire(from("198.51.100.1"), "a@x.com");   // email oldest at t0, pair B only
        clock.advance(300_000L);                           // t0 + 5 min
        for (int i = 0; i < 6; i++) {
            small.acquire(from("203.0.113.9"), "a@x.com"); // pair A full (oldest t0+5), email full (7)
        }
        TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
                () -> small.acquire(from("203.0.113.9"), "a@x.com"));
        // email frees in 10 min (600s), pair in 15 min (900s): the larger wins.
        assertThat(blocked.getRetryAfterSeconds()).isEqualTo(900);
    }

    @Test
    void ipRetryAfterIsIncludedInTheMax() {
        MockHttpServletRequest request = from("203.0.113.9");
        limiter.acquire(request, "first@x.com");           // IP oldest at t0
        clock.advance(600_000L);
        for (int i = 0; i < 49; i++) {
            limiter.acquire(request, "u" + i + "@x.com");
        }
        assertThat(blockedBy(request, "fresh@x.com").getRetryAfterSeconds()).isEqualTo(300);
    }

    // ----- IP tier -----

    @Test
    void fiftyFailuresFromOneIpAcrossManyEmailsBlockTheIp() {
        MockHttpServletRequest request = from("203.0.113.9");
        for (int i = 0; i < 50; i++) {
            limiter.acquire(request, "user" + i + "@x.com");
        }

        TooManyRequestsException blocked = blockedBy(request, "fresh@x.com");
        assertThat(blocked.getRetryAfterSeconds()).isPositive();
        assertThat(blocked.getMessage()).isEqualTo(BLOCKED);
    }

    // ----- success / refund semantics -----

    @Test
    void onSuccessClearsTheEmailAndPairCounters() {
        MockHttpServletRequest request = from("203.0.113.9");
        attempts(request, "a@x.com", 5);
        LoginRateLimiter.Permit permit = limiter.acquire(request, "a@x.com");

        limiter.onSuccess(permit);

        // Pair counter is gone: six more attempts fit, the seventh does not.
        attempts(request, "a@x.com", 6);
        blockedBy(request, "a@x.com");
    }

    @Test
    void onSuccessOnlyClearsTheCallersPairNotAnotherIpsPair() {
        MockHttpServletRequest attacker = from("203.0.113.9");
        MockHttpServletRequest victim = from("198.51.100.20");
        attempts(attacker, "victim@x.com", 6);
        limiter.onSuccess(limiter.acquire(victim, "victim@x.com"));

        // The attacker's pair history is untouched by the victim's successful login.
        // (Email key was cleared by the success, but the attacker's pair is still full.)
        blockedBy(attacker, "victim@x.com");
    }

    @Test
    void onSuccessRefundsOnlyOneIpEntryAndDoesNotClearTheIpCounter() {
        MockHttpServletRequest request = from("203.0.113.9");
        for (int i = 0; i < 49; i++) {
            limiter.acquire(request, "user" + i + "@x.com");
        }
        LoginRateLimiter.Permit permit = limiter.acquire(request, "winner@x.com");
        limiter.onSuccess(permit);

        // 49 entries remain: exactly one more fits, then the IP is full again.
        assertThat(limiter.acquire(request, "next@x.com")).isNotNull();
        blockedBy(request, "another@x.com");
    }

    @Test
    void refundReturnsOnlyThisPermitsSlotsAndKeepsEarlierFailures() {
        MockHttpServletRequest request = from("203.0.113.9");
        attempts(request, "a@x.com", 5);
        LoginRateLimiter.Permit serverError = limiter.acquire(request, "a@x.com");

        limiter.refund(serverError);

        // 5 genuine failures remain: one more fits, then the pair is full.
        assertThat(limiter.acquire(request, "a@x.com")).isNotNull();
        blockedBy(request, "a@x.com");
    }

    @Test
    void aBlockedAcquireRecordsNothing() {
        MockHttpServletRequest request = from("203.0.113.9");
        attempts(request, "a@x.com", 6);

        for (int i = 0; i < 20; i++) {
            blockedBy(request, "a@x.com");
        }

        // After the window frees, nothing from the blocked calls remains: exactly 6 fit again.
        clock.advance(WINDOW_MS);
        attempts(request, "a@x.com", 6);
        blockedBy(request, "a@x.com");
    }

    @Test
    void aBlockedPairDoesNotConsumeIpBudget() {
        MockHttpServletRequest request = from("203.0.113.9");
        attempts(request, "a@x.com", 6);
        for (int i = 0; i < 30; i++) {
            blockedBy(request, "a@x.com");
        }
        // 6 IP entries used; exactly 44 more distinct emails still fit.
        for (int i = 0; i < 44; i++) {
            limiter.acquire(request, "other" + i + "@x.com");
        }
        blockedBy(request, "last@x.com");
    }

    @Test
    void theBlockedMessageIsIdenticalForEveryTrippedTier() {
        MockHttpServletRequest request = from("203.0.113.9");
        attempts(request, "pair@x.com", 6);
        String pair = blockedBy(request, "pair@x.com").getMessage();

        for (int ip = 1; ip <= 5; ip++) {
            attempts(from("192.0.2." + ip), "email@x.com", 6);
        }
        String email = blockedBy(from("192.0.2.99"), "email@x.com").getMessage();

        MockHttpServletRequest busy = from("198.51.100.1");
        for (int i = 0; i < 50; i++) {
            limiter.acquire(busy, "u" + i + "@x.com");
        }
        String ip = blockedBy(busy, "new@x.com").getMessage();

        assertThat(pair).isEqualTo(BLOCKED).isEqualTo(email).isEqualTo(ip);
    }

    // ----- keys -----

    @Test
    void emailCaseAndWhitespaceShareABucketButPlusAddressesDoNot() {
        assertThat(LoginRateLimiter.emailKey("A@x.com")).isEqualTo("a@x.com");
        assertThat(LoginRateLimiter.emailKey(" a@x.com ")).isEqualTo("a@x.com");
        assertThat(LoginRateLimiter.emailKey("a+1@x.com")).isNotEqualTo("a@x.com");

        MockHttpServletRequest request = from("203.0.113.9");
        limiter.acquire(request, "A@x.com");
        limiter.acquire(request, " a@x.com ");
        attempts(request, "a@x.com", 4);
        blockedBy(request, "A@X.COM");
        assertThat(limiter.acquire(request, "a+1@x.com")).isNotNull();
    }

    @Test
    void unicodeVariantsFoldIntoTheSameEmailKey() {
        String plain = "admin@example.com";
        // Fullwidth Latin letters, Kelvin sign, circled letter and a ligature all NFKC-fold to ASCII.
        assertThat(LoginRateLimiter.emailKey("ａｄｍｉｎ@example.com")).isEqualTo(plain);
        assertThat(LoginRateLimiter.emailKey("ＡDMIN@Example.com")).isEqualTo(plain);
        assertThat(LoginRateLimiter.emailKey("K@example.com")).isEqualTo("k@example.com");
        assertThat(LoginRateLimiter.emailKey("ⓐ@example.com")).isEqualTo("a@example.com");
        assertThat(LoginRateLimiter.emailKey("ﬁsh@example.com")).isEqualTo("fish@example.com");

        MockHttpServletRequest request = from("203.0.113.9");
        limiter.acquire(request, plain);
        limiter.acquire(request, "ａdmin@example.com");
        attempts(request, "ADMIN@example.com", 4);
        blockedBy(request, "ＡＤＭＩＮ@example.com");
    }

    @Test
    void theEmailKeyIsTruncatedTo254CharactersEvenForHugeInput() {
        assertThat(LoginRateLimiter.emailKey("a".repeat(400) + "@x.com")).hasSize(254);
        assertThat(LoginRateLimiter.emailKey("a".repeat(2_000_000))).hasSize(254);
        assertThat(LoginRateLimiter.emailKey(null)).isEmpty();
    }

    @Test
    void ipv6AddressesInTheSame64ShareABucket() {
        for (int i = 0; i < 25; i++) {
            limiter.acquire(from("2001:db8:1:2:aaaa::" + (i + 1)), "u" + i + "@x.com");
        }
        for (int i = 25; i < 50; i++) {
            limiter.acquire(from("2001:db8:1:2:bbbb::" + (i + 1)), "u" + i + "@x.com");
        }
        blockedBy(from("2001:db8:1:2:cccc::1"), "z@x.com");
        // A different /64 is unaffected.
        assertThat(limiter.acquire(from("2001:db8:1:3::1"), "z@x.com")).isNotNull();
    }

    @Test
    void ipKeyCollapsesIpv6ToThePrefixAndLeavesIpv4Alone() {
        assertThat(LoginRateLimiter.ipKey("2001:db8:1:2:aaaa::1")).isEqualTo(LoginRateLimiter.ipKey("2001:db8:1:2::ffff"));
        assertThat(LoginRateLimiter.ipKey("2001:db8:1:2::1")).isNotEqualTo(LoginRateLimiter.ipKey("2001:db8:1:3::1"));
        assertThat(LoginRateLimiter.ipKey("203.0.113.9")).isEqualTo("203.0.113.9");
        assertThat(LoginRateLimiter.ipKey("::ffff:203.0.113.9")).isEqualTo("203.0.113.9");
        assertThat(LoginRateLimiter.ipKey("::1")).isEqualTo("v6:0000000000000000");
    }

    @Test
    void ipKeyTreatsAnythingThatIsNotAnIpLiteralAsUnknown() {
        String[] garbage = {
                "1:2:3", "evil.example.com", "localhost", "example.com:80", "::1%eth0", "1.2.3", "1.2.3.4.5",
                "256.1.1.1", "1.2.3.-4", "0x7f.0.0.1", "１２７.0.0.1", ":::", "1::2::3", "12345::1",
                "g::1", "1:2:3:4:5:6:7:8:9", "", " ", "a".repeat(100), "a".repeat(100_000), ":".repeat(5_000),
                "1.".repeat(5_000), "[::1]", "2001:db8::1/64"
        };
        for (String value : garbage) {
            assertThat(LoginRateLimiter.ipKey(value)).as("ipKey(%s)", value.length() > 40 ? "<long>" : value).isNull();
        }
        assertThat(LoginRateLimiter.ipKey(null)).isNull();
        assertThat(LoginRateLimiter.ipKey("unknown")).isNull();
    }

    @Test
    void aGarbageRemoteAddrSkipsTheIpAndPairTiersAndNeverBecomesAKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("evil.example.com");

        LoginRateLimiter.Permit permit = limiter.acquire(request, "a@x.com");

        assertThat(permit.ipKey()).isNull();
        assertThat(permit.pairKey()).isNull();
        assertThat(limiter.ipKeyCount()).isZero();
        assertThat(limiter.pairKeyCount()).isZero();
    }

    @Test
    void anUnknownIpSkipsPairAndIpTiersAndReliesOnTheEmailCeiling() {
        MockHttpServletRequest request = unknownIp();
        for (int i = 0; i < 60; i++) {
            LoginRateLimiter.Permit permit = limiter.acquire(request, "user" + i + "@x.com");
            assertThat(permit.ipKey()).isNull();
            assertThat(permit.pairKey()).isNull();
        }
        assertThat(limiter.ipKeyCount()).isZero();
        assertThat(limiter.pairKeyCount()).isZero();

        // Pair cap (6) does not apply without an IP; the 30-per-email ceiling does.
        attempts(request, "same@x.com", 30);
        blockedBy(request, "same@x.com");
    }

    @Test
    void emailAndIpLimitsAreIndependent() {
        MockHttpServletRequest first = from("203.0.113.9");
        attempts(first, "a@x.com", 6);
        // Same email from another IP is still allowed (separate pair, below the email ceiling).
        assertThat(limiter.acquire(from("198.51.100.1"), "a@x.com")).isNotNull();
        // A different email from the first IP is fine.
        assertThat(limiter.acquire(first, "b@x.com")).isNotNull();
    }

    // ----- time source -----

    @Test
    void windowsWorkOnAMonotonicSourceWithAnArbitraryNegativeOrigin() {
        TestMillis negative = new TestMillis(-9_000_000_000L);
        LoginRateLimiter l = new LoginRateLimiter(negative);
        MockHttpServletRequest request = from("203.0.113.9");
        for (int i = 0; i < 6; i++) {
            l.acquire(request, "a@x.com");
        }
        negative.advance(120_000L);
        TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
                () -> l.acquire(request, "a@x.com"));
        assertThat(blocked.getRetryAfterSeconds()).isEqualTo(780);

        negative.advance(WINDOW_MS - 120_000L);
        assertThat(l.acquire(request, "a@x.com")).isNotNull();
    }

    @Test
    void theDefaultConstructorUsesTheRealMonotonicSource() {
        LoginRateLimiter real = new LoginRateLimiter(30, 6, 50);
        MockHttpServletRequest request = from("203.0.113.9");
        for (int i = 0; i < 6; i++) {
            real.acquire(request, "a@x.com");
        }
        TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
                () -> real.acquire(request, "a@x.com"));
        assertThat(blocked.getRetryAfterSeconds()).isBetween(898L, 900L);
    }

    @Test
    void thresholdsAreConfigurable() {
        LoginRateLimiter custom = new LoginRateLimiter(clock, 3, 2, 4);
        MockHttpServletRequest request = from("203.0.113.9");
        custom.acquire(request, "a@x.com");
        custom.acquire(request, "a@x.com");
        assertThatThrownBy(() -> custom.acquire(request, "a@x.com")).isInstanceOf(TooManyRequestsException.class);
    }

    // ----- concurrency / bounds -----

    @Test
    void oneHundredParallelAcquiresLetExactlySixThroughPerPair() throws Exception {
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        limiter.acquire(from("203.0.113.9"), "race@x.com");
                        allowed.incrementAndGet();
                    } catch (TooManyRequestsException expected) {
                        // blocked, as intended
                    }
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(allowed.get()).isEqualTo(6);
    }

    @Test
    void cacheSizeStaysBounded() {
        for (int i = 0; i < 25_000; i++) {
            limiter.acquire(from("10." + (i / 65_536 % 256) + "." + (i / 256 % 256) + "." + (i % 256)),
                    "user" + i + "@x.com");
        }
        limiter.cleanUp();
        assertThat(limiter.emailKeyCount()).isLessThanOrEqualTo(20_000);
        assertThat(limiter.pairKeyCount()).isLessThanOrEqualTo(20_000);
        assertThat(limiter.ipKeyCount()).isLessThanOrEqualTo(10_000);
    }

    @Test
    void refundingOneOfTwoSameMillisecondPermitsLeavesTheOtherCounted() {
        MockHttpServletRequest request = from("203.0.113.9");
        LoginRateLimiter.Permit first = limiter.acquire(request, "a@x.com");
        LoginRateLimiter.Permit second = limiter.acquire(request, "a@x.com");
        assertThat(first.timestamp()).isEqualTo(second.timestamp());

        limiter.refund(second);
        limiter.refund(second); // a repeated refund must not remove the other request's slot either

        // 1 slot (first) remains: 5 more fit in the pair budget of 6, then it is full.
        attempts(request, "a@x.com", 5);
        blockedBy(request, "a@x.com");
    }

    @Test
    void retryAfterStillFollowsTheOldestSlotAfterASameMillisecondRefund() {
        MockHttpServletRequest request = from("203.0.113.9");
        LoginRateLimiter.Permit first = limiter.acquire(request, "a@x.com");
        clock.advance(60_000L);
        LoginRateLimiter.Permit later = limiter.acquire(request, "a@x.com");
        attempts(request, "a@x.com", 4);
        limiter.refund(later);
        attempts(request, "a@x.com", 1);

        TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
                () -> limiter.acquire(request, "a@x.com"));
        // The oldest remaining slot is `first`, recorded 60s ago, so 15 min - 60 s remain.
        assertThat(blocked.getRetryAfterSeconds()).isEqualTo(840);
        assertThat(first).isNotNull();
    }
}
