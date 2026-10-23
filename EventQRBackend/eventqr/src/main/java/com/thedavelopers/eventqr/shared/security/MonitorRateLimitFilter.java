package com.thedavelopers.eventqr.shared.security;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thedavelopers.eventqr.shared.response.ErrorResponse;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A small per-client token bucket for {@code /api/v1/monitor/**} only, in addition to the global
 * {@link RateLimitFilter}. The feed can be anonymous, so one client must not be able to hammer it: the default is
 * 5 requests/second with a burst of 15, far above the viewer's ~0.7 requests/second poll.
 *
 * <p>Clients are keyed by {@link ClientIp}; IPv6 clients are keyed by their /64 prefix so rotating through the
 * addresses of one subnet does not buy extra buckets. State is in memory (single-instance deployment), bounded
 * and self-expiring, and nothing here touches the database. Other paths pass straight through.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class MonitorRateLimitFilter implements Filter {

    static final String PATH_PREFIX = "/api/v1/monitor";
    private static final int MAX_KEYS = 10_000;
    private static final Duration IDLE_EXPIRY = Duration.ofMinutes(5);

    private final double burst;
    private final double ratePerSecond;
    private final LongSupplier clockMillis;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAX_KEYS)
            .expireAfterAccess(IDLE_EXPIRY)
            .build();

    private final CorsConfigurationSource corsSource;

    @Autowired
    public MonitorRateLimitFilter(@Value("${app.monitor.rate-limit.burst:15}") int burst,
                                  @Value("${app.monitor.rate-limit.per-second:5}") double ratePerSecond,
                                  @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource) {
        this(burst, ratePerSecond, System::currentTimeMillis, corsSource);
    }

    MonitorRateLimitFilter(int burst, double ratePerSecond, LongSupplier clockMillis) {
        this(burst, ratePerSecond, clockMillis, null);
    }

    MonitorRateLimitFilter(int burst, double ratePerSecond, LongSupplier clockMillis,
                           CorsConfigurationSource corsSource) {
        this.burst = Math.max(1, burst);
        this.ratePerSecond = ratePerSecond > 0 ? ratePerSecond : 5;
        this.clockMillis = clockMillis;
        this.corsSource = corsSource;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        if (!isMonitorPath(request)) {
            chain.doFilter(req, res);
            return;
        }
        long now = clockMillis.getAsLong();
        Bucket bucket = buckets.get(clientKey(ClientIp.from(request)), k -> new Bucket(burst, now));
        long retryAfterSeconds = bucket.tryConsume(now, burst, ratePerSecond);
        if (retryAfterSeconds == 0) {
            chain.doFilter(req, res);
            return;
        }
        HttpServletResponse response = (HttpServletResponse) res;
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        addCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(new ErrorResponse(Instant.now(), HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), "Rate limit exceeded. Please try again later.",
                request.getRequestURI()).toJson());
    }

    /**
     * This filter runs before Spring Security's CorsFilter, so a 429 would reach the browser without CORS headers
     * and the viewer could neither read the status nor {@code Retry-After}. For an origin the CORS configuration of
     * the path allows, add the same headers CorsFilter would: Allow-Origin, Vary and Expose-Headers. Disallowed
     * origins get nothing.
     */
    private void addCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (corsSource == null || origin == null) {
            return;
        }
        try {
            CorsConfiguration config = corsSource.getCorsConfiguration(request);
            if (config == null || config.checkOrigin(origin) == null) {
                return;
            }
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.addHeader("Vary", "Origin");
            if (config.getExposedHeaders() != null && !config.getExposedHeaders().isEmpty()) {
                response.setHeader("Access-Control-Expose-Headers", String.join(", ", config.getExposedHeaders()));
            }
        } catch (RuntimeException e) {
            // Best effort only: the 429 itself must still be sent.
        }
    }

    /** Matches on the raw URI and on the container-decoded servlet path so an encoded prefix cannot slip past. */
    static boolean isMonitorPath(HttpServletRequest request) {
        return underMonitor(request.getRequestURI()) || underMonitor(request.getServletPath())
                || underMonitor(request.getPathInfo());
    }

    /** The monitor path itself or anything below it; "/api/v1/monitoring" is a different path. */
    private static boolean underMonitor(String path) {
        if (path == null || !path.startsWith(PATH_PREFIX)) {
            return false;
        }
        return path.length() == PATH_PREFIX.length() || path.charAt(PATH_PREFIX.length()) == '/'
                || path.charAt(PATH_PREFIX.length()) == ';';
    }

    /** IPv4 (and "unknown") as-is; IPv6 reduced to its /64 network prefix. */
    static String clientKey(String clientIp) {
        if (clientIp == null || clientIp.indexOf(':') < 0) {
            return clientIp;
        }
        try {
            // ClientIp only ever returns validated literals, so this performs no DNS lookup.
            byte[] address = InetAddress.getByName(clientIp).getAddress();
            if (address.length != 16) {
                return clientIp;
            }
            return "v6/64:" + HexFormat.of().formatHex(address, 0, 8);
        } catch (java.net.UnknownHostException | RuntimeException e) {
            return clientIp;
        }
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillMillis;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefillMillis = now;
        }

        /** @return 0 when a token was taken, otherwise the whole seconds to wait (at least 1). */
        synchronized long tryConsume(long now, double burst, double ratePerSecond) {
            long elapsed = now - lastRefillMillis;
            if (elapsed > 0) {
                tokens = Math.min(burst, tokens + elapsed / 1000.0 * ratePerSecond);
                lastRefillMillis = now;
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return 0;
            }
            return Math.max(1, (long) Math.ceil((1.0 - tokens) / ratePerSecond));
        }
    }
}
