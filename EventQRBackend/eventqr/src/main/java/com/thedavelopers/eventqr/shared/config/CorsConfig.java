package com.thedavelopers.eventqr.shared.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.thedavelopers.eventqr.features.monitor.config.MonitorSettings;

@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    @Value("${app.frontend-base-url:https://eventqr.app}")
    private String frontendBaseUrl;

    /** Origin of the read-only log viewer (app.monitor.allowed-origin); blank = no monitor CORS entry. */
    @Value("${app.monitor.allowed-origin:}")
    private String monitorAllowedOrigin;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendBaseUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Explicit allow-list instead of "*": the API only consumes these headers, and a
        // wildcard would pre-approve any future custom/security-sensitive header.
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // UrlBasedCorsConfigurationSource returns the FIRST registered pattern that matches, so the more specific
        // monitor entry must be registered before the catch-all "/**" below.
        if (monitorAllowedOrigin != null && !monitorAllowedOrigin.isBlank()
                && !MonitorSettings.isValidOrigin(monitorAllowedOrigin)) {
            log.warn("app.monitor.allowed-origin is not a valid scheme://host[:port] origin; "
                    + "no CORS entry registered for the monitor endpoint");
        } else if (monitorAllowedOrigin != null && !monitorAllowedOrigin.isBlank()) {
            CorsConfiguration monitor = new CorsConfiguration();
            monitor.setAllowedOrigins(List.of(monitorAllowedOrigin.trim()));
            monitor.setAllowedMethods(List.of("GET", "OPTIONS"));
            monitor.setAllowedHeaders(List.of("Authorization", "Accept", "Content-Type"));
            monitor.setExposedHeaders(List.of("Retry-After"));
            monitor.setAllowCredentials(false);
            monitor.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/v1/monitor/**", monitor);
        }
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}