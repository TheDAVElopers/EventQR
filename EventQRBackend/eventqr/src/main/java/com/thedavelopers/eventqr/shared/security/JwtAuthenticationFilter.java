package com.thedavelopers.eventqr.shared.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.UnauthorizedException;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserTokenRevocationChecker userTokenRevocationChecker;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserTokenRevocationChecker userTokenRevocationChecker) {
        this.jwtService = jwtService;
        this.userTokenRevocationChecker = userTokenRevocationChecker;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                if (jwtService.isRevoked(header)) {
                    // Per-token logout denylist hit: token was explicitly logged out.
                    SecurityContextHolder.clearContext();
                } else {
                    // Single parse + validation of the token stays inside this try/catch;
                    // any invalid/expired/malformed token clears the context below.
                    Claims claims = jwtService.extractClaimsFromBearer(header);
                    UUID userId = userIdFrom(claims);
                    AccountRole role = jwtService.extractRoleFrom(claims);
                    if (userTokenRevocationChecker.isAccessAllowed(userId, jwtService.issuedAtOf(claims), role)) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userId,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        // Account not ACTIVE, token predates the revocation marker, or its role claim is stale.
                        SecurityContextHolder.clearContext();
                    }
                }
            } catch (UnauthorizedException exception) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private UUID userIdFrom(Claims claims) {
        String userId = claims.get("userId", String.class);
        if (userId == null || userId.isBlank()) {
            userId = claims.getSubject();
        }
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("Invalid or expired session");
        }
        return UUID.fromString(userId);
    }
}