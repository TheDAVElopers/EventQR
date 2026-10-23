package com.thedavelopers.eventqr.features.monitor.service;

import java.util.Locale;
import java.util.Set;

/** Decides which entity property names are too revealing to show in an update description. */
public final class SensitiveNames {

    public static final String PLACEHOLDER = "<sensitive>";

    private static final Set<String> EXACT = Set.of(
            "passwordhash", "email", "phonenumber", "qrvalue", "role", "status", "fullname",
            "attendeeemail", "attendeename", "contactemail", "contactnumber");

    private static final String[] FRAGMENTS = { "password", "token", "secret", "hash" };

    private SensitiveNames() {
    }

    public static boolean isSensitive(String propertyName) {
        if (propertyName == null) {
            return true;
        }
        String lower = propertyName.toLowerCase(Locale.ROOT);
        if (EXACT.contains(lower)) {
            return true;
        }
        for (String fragment : FRAGMENTS) {
            if (lower.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    /** The name itself, or {@link #PLACEHOLDER} when it is sensitive. */
    public static String display(String propertyName) {
        return isSensitive(propertyName) ? PLACEHOLDER : propertyName;
    }
}
