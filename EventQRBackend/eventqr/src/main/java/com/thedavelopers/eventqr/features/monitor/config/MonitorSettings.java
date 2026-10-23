package com.thedavelopers.eventqr.features.monitor.config;

import java.util.regex.Pattern;

/** Validation rules for the monitor's token and browser-origin settings. */
public final class MonitorSettings {

    /** Shorter tokens are treated as "not configured" so a weak shared secret can never enable the endpoint. */
    public static final int MIN_TOKEN_LENGTH = 32;

    // scheme://host[:port] with http/https only: no path, trailing slash, query, fragment, user-info or wildcard.
    private static final Pattern ORIGIN = Pattern.compile(
            "^https?://[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*(:[0-9]{1,5})?$");

    /**
     * Opt-in public mode ({@code app.monitor.public}, env {@code LOG_VIEWER_PUBLIC}): the feed is readable without
     * any token. Off by default.
     */
    public static final String PUBLIC_PROPERTY = "app.monitor.public";

    private MonitorSettings() {
    }

    /**
     * Single parser for {@code app.monitor.public}: true only for the text "true" (any case, surrounding spaces
     * ignored). Blank, "1", "yes", a typo or anything else is false, so a bad value can never fail startup and can
     * never open the feed by accident.
     */
    public static boolean isPublic(String value) {
        return value != null && "true".equalsIgnoreCase(value.trim());
    }

    /** True when the raw value is neither blank nor "true"/"false": likely a typo worth one startup warning. */
    public static boolean isUnrecognisedPublicValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return !"true".equalsIgnoreCase(trimmed) && !"false".equalsIgnoreCase(trimmed);
    }

    /** True when {@code app.monitor.public} is set to true in the environment (read as a plain string). */
    public static boolean isPublic(org.springframework.core.env.Environment environment) {
        return isPublic(environment.getProperty(PUBLIC_PROPERTY));
    }

    /** The monitor exists when it is public or protected by a usable token. */
    public static boolean isEnabled(String token, boolean publicMode) {
        return publicMode || isUsableToken(token);
    }

    /** True when the (trimmed) token is long enough to enable the monitor. */
    public static boolean isUsableToken(String token) {
        return token != null && token.trim().length() >= MIN_TOKEN_LENGTH;
    }

    /** True for an exact browser origin such as {@code https://viewer.example.com} or {@code http://localhost:5173}. */
    public static boolean isValidOrigin(String origin) {
        return origin != null && ORIGIN.matcher(origin.trim()).matches();
    }
}
