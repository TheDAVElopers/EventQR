package com.thedavelopers.eventqr.features.monitor.config;

import java.util.regex.Pattern;

/** Validation rules for the monitor's token and browser-origin settings. */
public final class MonitorSettings {

    /** Shorter tokens are treated as "not configured" so a weak shared secret can never enable the endpoint. */
    public static final int MIN_TOKEN_LENGTH = 32;

    // scheme://host[:port] with http/https only: no path, trailing slash, query, fragment, user-info or wildcard.
    private static final Pattern ORIGIN = Pattern.compile(
            "^https?://[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*(:[0-9]{1,5})?$");

    private MonitorSettings() {
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
