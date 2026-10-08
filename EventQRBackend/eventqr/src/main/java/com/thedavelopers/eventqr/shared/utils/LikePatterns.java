package com.thedavelopers.eventqr.shared.utils;

/**
 * Builds lower-cased LIKE patterns for case-insensitive "contains" searches. The escape
 * character is {@value #ESCAPE}; queries must declare {@code escape '!'}.
 */
public final class LikePatterns {

    public static final String ESCAPE = "!";

    private LikePatterns() {
    }

    /** Returns {@code %term%} (lower-cased, wildcards escaped), or null when the term is null/blank. */
    public static String contains(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String escaped = term.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
