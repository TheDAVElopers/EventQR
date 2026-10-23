package com.thedavelopers.eventqr.shared.config;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.thedavelopers.eventqr.features.monitor.config.MonitorSettings;
import com.thedavelopers.eventqr.shared.response.ErrorResponse;
import com.thedavelopers.eventqr.shared.security.JwtAuthenticationFilter;
import com.thedavelopers.eventqr.shared.security.MonitorTokenFilter;

/**
 * Security for the read-only CRUD monitor feed ({@code /api/v1/monitor/**}): a separate, first-ordered chain that
 * authenticates a single shared bearer token ({@code app.monitor.token}) instead of user JWTs. The main chain in
 * {@link SecurityConfig} is untouched and keeps handling every other path.
 */
@Configuration
public class MonitorSecurityConfig {

    static final String MONITOR_PATH = "/api/v1/monitor/**";
    static final String MONITOR_EVENTS_PATH = "/api/v1/monitor/events";

    @Bean
    @Order(1)
    public SecurityFilterChain monitorSecurityFilterChain(HttpSecurity http,
            @Value("${app.monitor.token:}") String monitorToken,
            @Value("${app.monitor.public:false}") String publicSetting) throws Exception {
        boolean publicMode = MonitorSettings.isPublic(publicSetting);
        http.securityMatcher(MONITOR_PATH);
        http.csrf(csrf -> csrf.disable());
        // Uses the application's CorsConfigurationSource bean (CorsConfig), which has a dedicated monitor entry.
        http.cors(Customizer.withDefaults());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(authorize -> {
            if (publicMode) {
                // Opt-in public mode: anonymous access to the one read endpoint and nothing else on this path.
                // CORS preflights are answered by the CorsFilter earlier in the chain. Every other method or
                // path still needs the monitor token and is refused (401) without it.
                authorize.requestMatchers(HttpMethod.GET, MONITOR_EVENTS_PATH).permitAll();
            }
            authorize.anyRequest().hasRole("MONITOR");
        });
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(unauthorizedEntryPoint())
                .accessDeniedHandler((request, response, denied) -> unauthorizedEntryPoint()
                        .commence(request, response, null)));
        http.addFilterBefore(new MonitorTokenFilter(monitorToken, publicMode), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * {@link JwtAuthenticationFilter} is a {@code @Component}, so Boot also registers it as a plain servlet filter
     * that runs after the security chains for EVERY request. For monitor requests that would parse the monitor
     * token as a JWT and consult the revoked-token store (a database lookup). The filter is already part of the main
     * security chain, which is the only place it must run, so the servlet-level registration is disabled.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private static AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(new ErrorResponse(
                    Instant.now(),
                    HttpStatus.UNAUTHORIZED.value(),
                    HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                    "Authentication required: missing or invalid token",
                    request.getRequestURI()).toJson());
        };
    }
}
