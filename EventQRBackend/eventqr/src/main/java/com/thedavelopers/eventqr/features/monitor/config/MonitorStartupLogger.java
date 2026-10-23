package com.thedavelopers.eventqr.features.monitor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final String publicValue;
    private final boolean publicMode;

    public MonitorStartupLogger(String token, String allowedOrigin) {
        this(token, allowedOrigin, (String) null);
    }

    public MonitorStartupLogger(String token, String allowedOrigin, boolean publicMode) {
        this(token, allowedOrigin, String.valueOf(publicMode));
    }

    @Autowired
    public MonitorStartupLogger(@Value("${app.monitor.token:}") String token,
                                @Value("${app.monitor.allowed-origin:}") String allowedOrigin,
                                @Value("${app.monitor.public:false}") String publicValue) {
        this.token = token;
        this.allowedOrigin = allowedOrigin;
        this.publicValue = publicValue;
        this.publicMode = MonitorSettings.isPublic(publicValue);
    }

    @PostConstruct
    public void logConfiguration() {
        if (MonitorSettings.isUnrecognisedPublicValue(publicValue)) {
            // The value itself is deliberately not echoed.
            log.warn("{} is neither 'true' nor 'false'; treating the monitor feed as NOT public",
                    MonitorSettings.PUBLIC_PROPERTY);
        }
        if (publicMode) {
            log.warn("CRUD monitor feed is PUBLIC (LOG_VIEWER_PUBLIC=true): anyone with the URL can read table names, "
                    + "operations, route patterns, actor roles, timing and counts");
            if (StringUtils.hasText(token) && !MonitorSettings.isUsableToken(token)) {
                log.warn("LOG_VIEWER_TOKEN is {} characters long but at least {} are required; it is ignored",
                        token.trim().length(), MonitorSettings.MIN_TOKEN_LENGTH);
            }
            warnIfOriginBlank();
            return;
        }
        if (!StringUtils.hasText(token)) {
            return;
        }
        if (!MonitorSettings.isUsableToken(token)) {
            log.warn("CRUD monitor DISABLED: LOG_VIEWER_TOKEN is {} characters long but at least {} are required",
                    token.trim().length(), MonitorSettings.MIN_TOKEN_LENGTH);
            return;
        }
        warnIfOriginBlank();
    }

    private void warnIfOriginBlank() {
        if (!StringUtils.hasText(allowedOrigin)) {
            log.warn("CRUD monitor is enabled but LOG_VIEWER_ORIGIN is blank: browsers on another origin will be "
                    + "refused by CORS");
        }
    }
}
