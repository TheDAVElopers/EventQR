package com.thedavelopers.eventqr.shared.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.thedavelopers.eventqr.features.monitor.config.MonitorSettings;
import com.thedavelopers.eventqr.shared.response.ErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates {@code /api/v1/monitor/**} with one shared bearer token ({@code app.monitor.token}). It is NOT a
 * Spring bean on purpose (so Boot does not also register it as a servlet filter for every path); it is created
 * and wired only into the dedicated monitor security chain.
 *
 * <ul>
 *   <li>Token not configured or shorter than {@value MonitorSettings#MIN_TOKEN_LENGTH} characters: 404 for every request, so the endpoint does not exist.</li>
 *   <li>Matching token: an authenticated principal with the single authority {@code ROLE_MONITOR} is installed.
 *       Real user JWTs never match, so they are not accepted here.</li>
 *   <li>Anything else: no authentication is set and the chain's entry point answers 401.</li>
 *   <li>Public mode ({@code app.monitor.public}): never 404; a missing or wrong header is simply ignored and the
 *       request continues unauthenticated, where the chain allows anonymous access to the single read endpoint only.</li>
 * </ul>
 *
 * <p>Comparison is constant time: both sides are SHA-256 hashed (fixed length) and compared with
 * {@link MessageDigest#isEqual}. Only the digest of the configured token is retained, and the token is never
 * logged. No database or user lookup happens here.
 */
public class MonitorTokenFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final byte[] expectedDigest;
    private final boolean publicMode;

    public MonitorTokenFilter(String configuredToken) {
        this(configuredToken, false);
    }

    /**
     * @param publicMode when true the feed is readable anonymously: the filter never answers 404/401 itself and
     *                   ignores a missing or wrong Authorization header (the chain's authorization rules decide
     *                   which requests may be anonymous). A matching token still authenticates as usual.
     */
    public MonitorTokenFilter(String configuredToken, boolean publicMode) {
        this.expectedDigest = MonitorSettings.isUsableToken(configuredToken) ? sha256(configuredToken.trim()) : null;
        this.publicMode = publicMode;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (expectedDigest == null && !publicMode) {
            writeError(request, response, HttpStatus.NOT_FOUND, "Not found");
            return;
        }
        String header = request.getHeader("Authorization");
        if (expectedDigest != null && header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            String presented = header.substring(BEARER.length()).trim();
            if (MessageDigest.isEqual(expectedDigest, sha256(presented))) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        "log-viewer", null, List.of(new SimpleGrantedAuthority("ROLE_MONITOR")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private static void writeError(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                                   String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                message, request.getRequestURI()).toJson());
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
