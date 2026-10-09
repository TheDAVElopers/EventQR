package com.thedavelopers.eventqr.shared.utils;

/**
 * Helpers for redacting PII in application logs.
 */
public final class LogRedaction {

    private LogRedaction() {
    }

    /**
     * Masks an email address for safe logging, e.g. {@code a***@example.com}.
     * Preserves the domain so the log stays useful for debugging while hiding
     * the account identifier. A null/blank/illegal value passes through unchanged.
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0 || at == trimmed.length() - 1) {
            return trimmed;
        }
        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at + 1);
        String maskedLocal = local.charAt(0) + "***";
        return maskedLocal + "@" + domain;
    }

    private static final java.util.regex.Pattern EMAIL_IN_TEXT =
            java.util.regex.Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+)");

    /**
     * Masks every email address embedded in free text (e.g. an exception message echoing a provider's
     * response) the same way as {@link #maskEmail(String)}. Null passes through unchanged.
     */
    public static String redactEmails(String text) {
        if (text == null || text.indexOf('@') < 0) {
            return text;
        }
        return EMAIL_IN_TEXT.matcher(text).replaceAll("$1***@$2");
    }

    /**
     * Copy of a throwable chain that is safe to hand to a logger: every message has its email addresses masked
     * (and names the original exception class), while each stack trace is kept as-is. Cycles are cut.
     */
    public static Throwable redactedCopy(Throwable throwable) {
        return redactedCopy(throwable, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    private static Throwable redactedCopy(Throwable throwable, java.util.Set<Throwable> seen) {
        if (throwable == null || !seen.add(throwable)) {
            return null;
        }
        String message = throwable.getClass().getName()
                + (throwable.getMessage() == null ? "" : ": " + redactEmails(throwable.getMessage()));
        RedactedThrowable copy = new RedactedThrowable(message, redactedCopy(throwable.getCause(), seen));
        copy.setStackTrace(throwable.getStackTrace());
        return copy;
    }

    /** Carrier for a redacted message; the original class name is the first part of the message. */
    static final class RedactedThrowable extends RuntimeException {
        RedactedThrowable(String message, Throwable cause) {
            super(message, cause, false, true);
        }
    }
}
