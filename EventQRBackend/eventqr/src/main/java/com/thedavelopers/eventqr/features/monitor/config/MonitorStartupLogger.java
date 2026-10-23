package com.thedavelopers.eventqr.features.monitor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/** Logs (never the token value) when the monitor is misconfigured. Always present, even when disabled. */
@Component
public class MonitorStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(MonitorStartupLogger.class);

    private final String token;
    private final String allowedOrigin;

    public MonitorStartupLogger(@Value("${app.monitor.token:}") String token,
                                @Value("${app.monitor.allowed-origin:}") String allowedOrigin) {
        this.token = token;
        this.allowedOrigin = allowedOrigin;
    }

    @PostConstruct
    public void logConfiguration() {
        if (!StringUtils.hasText(token)) {
            return;
        }
        if (!MonitorSettings.isUsableToken(token)) {
            log.warn("CRUD monitor DISABLED: LOG_VIEWER_TOKEN is {} characters long but at least {} are required",
                    token.trim().length(), MonitorSettings.MIN_TOKEN_LENGTH);
            return;
        }
        if (!StringUtils.hasText(allowedOrigin)) {
            log.warn("CRUD monitor is enabled but LOG_VIEWER_ORIGIN is blank: browsers on another origin will be "
                    + "refused by CORS");
        }
    }
}
