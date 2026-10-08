package com.thedavelopers.eventqr.shared.security;

import java.net.InetAddress;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.thedavelopers.eventqr.shared.exceptions.TooManyRequestsException;

/**
 * Brute-force defense for {@code POST /api/v1/auth/login}.
 *
 * <p>Uses reserve-then-refund: {@link #acquire} runs before the credential check and atomically
 * records an attempt under up to three sliding 15-minute windows, or throws
 * {@link TooManyRequestsException} without recording anything when any of them is full:
 * <ol>
 *   <li><b>(email, IP) pair</b>, default 6 failures. Trips first, so one attacker IP can lock a
 *       victim out only from <em>that</em> IP; the victim logging in from elsewhere is unaffected.</li>
 *   <li><b>email only</b>, default 30 failures. Backstop against distributed guessing; a
 *       distributed attacker needs 30 requests to lock this ceiling (and with it the account's
 *       logins from every IP) for the window.</li>
 *   <li><b>IP only</b>, default 50 failures, bounding what one source can try across many emails.</li>
 * </ol>
 * When the client IP is unknown (or not a parseable IP literal) the pair and IP tiers are skipped
 * and only the email ceiling applies.
 *
 * <p>A successful login (or one that fails only because the account is disabled, which proves the
 * password was right) calls {@link #onSuccess}, which drops the email counter and the caller's pair
 * counter and refunds only this attempt's slot in the IP window, so one valid login cannot be used
 * to wipe an IP's failure history. Unknown emails are counted exactly like known ones, the 429 text
 * is identical for every tripped tier, and no database access happens here, so throttling does not
 * reveal whether an account exists.
 *
 * <p>The email key deliberately does not use {@code EmailNormalizer.canonicalize}: login matches the
 * stored email exactly (case-insensitively), so canonical aliases are different accounts. It is
 * NFKC-normalised and lower-cased so Unicode look-alike variants share a bucket. IPv6 clients are
 * bucketed by /64 prefix, since a single subscriber controls that whole range.
 *
 * <p>Window arithmetic uses a monotonic time source ({@link System#nanoTime}), so wall-clock steps
 * (NTP, DST) cannot shorten or extend a lockout.
 *
 * <p>The caches are size-bounded: under a flood of distinct keys W-TinyLFU eviction can drop an
 * active entry (resetting that key's count), an accepted trade-off since the bound is what prevents
 * memory exhaustion.
 *
 * <p>State is in-memory and per instance: with N instances the effective limits multiply by N. If
 * the service scales out, move this behind an interface backed by a shared store (e.g. Redis).
 */
@Component
public class LoginRateLimiter {

    static final long WINDOW_MS = Duration.ofMinutes(15).toMillis();
    static final String BLOCKED_MESSAGE = "Too many login attempts. Please try again later.";
    static final int DEFAULT_MAX_PER_EMAIL = 30;
    static final int DEFAULT_MAX_PER_PAIR = 6;
    static final int DEFAULT_MAX_PER_IP = 50;
    private static final int MAX_EMAIL_KEY_LENGTH = 254;
    /** Bound the input handed to the Unicode normaliser; NFKC can expand some code points. */
    private static final int MAX_EMAIL_INPUT_LENGTH = 1024;
    private static final String UNKNOWN_IP = "unknown";

    /** Handle returned by {@link #acquire}; pass it to {@link #onSuccess} to refund the attempt. */
    public record Permit(String emailKey, String ipKey, String pairKey, long timestamp, long sequence) {
    }

    /** One recorded attempt; the sequence makes every slot unique so a refund removes exactly its own. */
    private record Slot(long time, long sequence) {
    }

    private final AtomicLong sequence = new AtomicLong();

    private final Cache<String, Deque<Slot>> byEmail = Caffeine.newBuilder()
            .maximumSize(20_000).expireAfterAccess(Duration.ofMinutes(15)).build();
    private final Cache<String, Deque<Slot>> byPair = Caffeine.newBuilder()
            .maximumSize(20_000).expireAfterAccess(Duration.ofMinutes(15)).build();
    private final Cache<String, Deque<Slot>> byIp = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterAccess(Duration.ofMinutes(15)).build();
    /** Guards every deque mutation so the multi-key reserve is atomic. Held only for small in-memory work. */
    private final Object lock = new Object();
    private final LongSupplier monotonicMillis;
    private final int maxPerEmail;
    private final int maxPerPair;
    private final int maxPerIp;

    @Autowired
    public LoginRateLimiter(@Value("${app.login-rate-limit.max-per-email:30}") int maxPerEmail,
                            @Value("${app.login-rate-limit.max-per-pair:6}") int maxPerPair,
                            @Value("${app.login-rate-limit.max-per-ip:50}") int maxPerIp) {
        this(() -> System.nanoTime() / 1_000_000L, maxPerEmail, maxPerPair, maxPerIp);
    }

    /** Package-private constructor for tests that need time control. */
    LoginRateLimiter(LongSupplier monotonicMillis) {
        this(monotonicMillis, DEFAULT_MAX_PER_EMAIL, DEFAULT_MAX_PER_PAIR, DEFAULT_MAX_PER_IP);
    }

    LoginRateLimiter(LongSupplier monotonicMillis, int maxPerEmail, int maxPerPair, int maxPerIp) {
        this.monotonicMillis = monotonicMillis;
        this.maxPerEmail = maxPerEmail;
        this.maxPerPair = maxPerPair;
        this.maxPerIp = maxPerIp;
    }

    /**
     * Reserves one attempt. Throws {@link TooManyRequestsException} (with a Retry-After hint covering
     * every exceeded window) when any window is full; in that case nothing is recorded.
     */
    public Permit acquire(HttpServletRequest request, String email) {
        String emailKey = emailKey(email);
        String ipKey = ipKey(ClientIp.from(request));
        String pairKey = ipKey == null ? null : emailKey + '|' + ipKey;
        long now = monotonicMillis.getAsLong();
        synchronized (lock) {
            Deque<Slot> emailWindow = window(byEmail, emailKey, now);
            Deque<Slot> pairWindow = pairKey == null ? null : window(byPair, pairKey, now);
            Deque<Slot> ipWindow = ipKey == null ? null : window(byIp, ipKey, now);
            long retryAfter = 0;
            boolean blocked = false;
            if (emailWindow.size() >= maxPerEmail) {
                blocked = true;
                retryAfter = Math.max(retryAfter, secondsUntilFree(emailWindow, now));
            }
            if (pairWindow != null && pairWindow.size() >= maxPerPair) {
                blocked = true;
                retryAfter = Math.max(retryAfter, secondsUntilFree(pairWindow, now));
            }
            if (ipWindow != null && ipWindow.size() >= maxPerIp) {
                blocked = true;
                retryAfter = Math.max(retryAfter, secondsUntilFree(ipWindow, now));
            }
            if (blocked) {
                dropIfEmpty(byEmail, emailKey, emailWindow);
                dropIfEmpty(byPair, pairKey, pairWindow);
                dropIfEmpty(byIp, ipKey, ipWindow);
                throw new TooManyRequestsException(BLOCKED_MESSAGE, Math.max(1, retryAfter));
            }
            long seq = sequence.incrementAndGet();
            Slot slot = new Slot(now, seq);
            emailWindow.addLast(slot);
            if (pairWindow != null) {
                pairWindow.addLast(slot);
            }
            if (ipWindow != null) {
                ipWindow.addLast(slot);
            }
            return new Permit(emailKey, ipKey, pairKey, now, seq);
        }
    }

    /**
     * Clears the email counter and the caller's (email, IP) counter, and refunds only this permit's
     * slot in the IP window.
     */
    public void onSuccess(Permit permit) {
        if (permit == null) {
            return;
        }
        synchronized (lock) {
            byEmail.invalidate(permit.emailKey());
            if (permit.pairKey() != null) {
                byPair.invalidate(permit.pairKey());
            }
            if (permit.ipKey() != null) {
                Deque<Slot> ipWindow = byIp.getIfPresent(permit.ipKey());
                if (ipWindow != null) {
                    ipWindow.removeFirstOccurrence(new Slot(permit.timestamp(), permit.sequence()));
                    if (ipWindow.isEmpty()) {
                        byIp.invalidate(permit.ipKey());
                    }
                }
            }
        }
    }

    /**
     * Returns only this permit's slot in each window (used when the attempt failed for a reason that is
     * not a password guess, e.g. a server error). Unlike {@link #onSuccess} it does not clear any counter.
     */
    public void refund(Permit permit) {
        if (permit == null) {
            return;
        }
        synchronized (lock) {
            removeSlot(byEmail, permit.emailKey(), permit);
            removeSlot(byPair, permit.pairKey(), permit);
            removeSlot(byIp, permit.ipKey(), permit);
        }
    }

    private static void removeSlot(Cache<String, Deque<Slot>> cache, String key, Permit permit) {
        if (key == null) {
            return;
        }
        Deque<Slot> window = cache.getIfPresent(key);
        if (window != null) {
            window.removeFirstOccurrence(new Slot(permit.timestamp(), permit.sequence()));
            if (window.isEmpty()) {
                cache.invalidate(key);
            }
        }
    }

    /** Forces pending cache evictions; used by tests to assert bounded size. */
    void cleanUp() {
        byEmail.cleanUp();
        byPair.cleanUp();
        byIp.cleanUp();
    }

    long emailKeyCount() {
        return byEmail.estimatedSize();
    }

    long pairKeyCount() {
        return byPair.estimatedSize();
    }

    long ipKeyCount() {
        return byIp.estimatedSize();
    }

    private static Deque<Slot> window(Cache<String, Deque<Slot>> cache, String key, long now) {
        Deque<Slot> window = cache.get(key, k -> new ArrayDeque<>());
        prune(window, now);
        return window;
    }

    private static void dropIfEmpty(Cache<String, Deque<Slot>> cache, String key, Deque<Slot> window) {
        if (key != null && window != null && window.isEmpty()) {
            cache.invalidate(key);
        }
    }

    private static long secondsUntilFree(Deque<Slot> window, long now) {
        Slot oldest = window.peekFirst();
        if (oldest == null) {
            return 1;
        }
        long remainingMs = oldest.time() + WINDOW_MS - now;
        return Math.max(1, (remainingMs + 999) / 1000);
    }

    private static void prune(Deque<Slot> window, long now) {
        while (!window.isEmpty() && window.peekFirst().time() <= now - WINDOW_MS) {
            window.removeFirst();
        }
    }

    static String emailKey(String email) {
        String raw = email == null ? "" : email.trim();
        if (raw.length() > MAX_EMAIL_INPUT_LENGTH) {
            raw = raw.substring(0, MAX_EMAIL_INPUT_LENGTH);
        }
        String key = Normalizer.normalize(raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return key.length() > MAX_EMAIL_KEY_LENGTH ? key.substring(0, MAX_EMAIL_KEY_LENGTH) : key;
    }

    /**
     * Returns the IP bucket key (IPv6 collapsed to its /64), or null when the address is unknown or is
     * not a well-formed IP literal. Never resolves names: only literals are parsed, so no DNS lookup
     * can be triggered and an arbitrary string can never become a bucket key.
     */
    static String ipKey(String ip) {
        if (ip == null || UNKNOWN_IP.equals(ip)) {
            return null;
        }
        byte[] bytes = ClientIp.parseLiteral(ip.trim());
        if (bytes == null) {
            return null;
        }
        try {
            // getByAddress(byte[]) performs no lookup; it folds IPv4-mapped IPv6 into IPv4.
            InetAddress address = InetAddress.getByAddress(bytes);
            byte[] raw = address.getAddress();
            if (raw.length == 16) {
                return "v6:" + HexFormat.of().formatHex(raw, 0, 8);
            }
            return address.getHostAddress();
        } catch (java.net.UnknownHostException e) {
            return null;
        }
    }
}
