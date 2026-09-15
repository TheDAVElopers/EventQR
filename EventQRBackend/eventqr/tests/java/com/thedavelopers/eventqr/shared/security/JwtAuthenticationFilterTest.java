package com.thedavelopers.eventqr.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.thedavelopers.eventqr.shared.constants.AccountRole;

import jakarta.servlet.FilterChain;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRET = "01234567890123456789012345678901"; // 32 bytes (HS256 min)

    @Mock
    private UserTokenRevocationChecker userTokenRevocationChecker;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static String bearerToken(JwtService service, UUID userId) {
        return "Bearer " + service.createToken(userId, "user@example.com", AccountRole.ATTENDEE);
    }

    private static FilterChain noOpChain() {
        return (request, response) -> {
            // no-op
        };
    }

    @Test
    void revokedTokenDoesNotSetAuthentication() throws Exception {
        JwtService jwtService = new JwtService(SECRET, 86400000L);
        UUID userId = UUID.randomUUID();
        String bearer = bearerToken(jwtService, userId);
        jwtService.revoke(bearer);

        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(jwtService, userTokenRevocationChecker);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", bearer);
        filter.doFilter(request, new MockHttpServletResponse(), noOpChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userTokenRevocationChecker, never()).isAccessAllowed(any(), any());
    }

    @Test
    void validTokenWithAllowedAccessSetsAuthentication() throws Exception {
        JwtService jwtService = new JwtService(SECRET, 86400000L);
        UUID userId = UUID.randomUUID();
        String bearer = bearerToken(jwtService, userId);
        when(userTokenRevocationChecker.isAccessAllowed(eq(userId), any(Instant.class))).thenReturn(true);

        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(jwtService, userTokenRevocationChecker);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", bearer);
        filter.doFilter(request, new MockHttpServletResponse(), noOpChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userId);
    }

    @Test
    void validTokenWithDeniedAccessDoesNotSetAuthentication() throws Exception {
        JwtService jwtService = new JwtService(SECRET, 86400000L);
        UUID userId = UUID.randomUUID();
        String bearer = bearerToken(jwtService, userId);
        when(userTokenRevocationChecker.isAccessAllowed(eq(userId), any(Instant.class))).thenReturn(false);

        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(jwtService, userTokenRevocationChecker);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", bearer);
        filter.doFilter(request, new MockHttpServletResponse(), noOpChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingHeaderLeavesContextClear() throws Exception {
        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(new JwtService(SECRET, 86400000L), userTokenRevocationChecker);
        MockHttpServletRequest request = new MockHttpServletRequest();
        filter.doFilter(request, new MockHttpServletResponse(), noOpChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidTokenClearsContext() throws Exception {
        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(new JwtService(SECRET, 86400000L), userTokenRevocationChecker);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not.a.valid.jwt");
        filter.doFilter(request, new MockHttpServletResponse(), noOpChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}