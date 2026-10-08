package com.thedavelopers.eventqr.shared.utils;

import java.nio.charset.StandardCharsets;

import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;

/**
 * Single password policy for every place a password is SET (never for login, so legacy weak
 * passwords keep working): at least 8 code points and at most 72 UTF-8 bytes, with at least one
 * uppercase letter, one lowercase letter, one digit and one special character.
 *
 * <p>The maximum is 72 UTF-8 BYTES, not characters: BCrypt's encode() throws above 72 bytes, so longer
 * input must be rejected before it reaches the encoder (non-ASCII characters use 2-4 bytes each).
 *
 * <p>"Special" means any character that is NOT a letter or digit, i.e. {@code !Character.isLetterOrDigit(c)}
 * (whitespace and symbols count). This deliberately matches the mobile client's {@code !it.isLetterOrDigit()}
 * and replaces the old {@code [^A-Za-z0-9]} regex. Evaluated per Unicode code point.
 */
public final class PasswordValidator {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    public static final String FAILURE_MESSAGE =
            "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a number, and a special character";

    public static final String TOO_LONG_MESSAGE =
            "Password must be at most 72 bytes long (about 72 characters; fewer if it uses non-English characters or emoji)";

    private PasswordValidator() {
    }

    public static boolean isValid(String password) {
        return violation(password) == null;
    }

    /** The user-facing message for the first violated rule, or null when the password is acceptable. */
    public static String violation(String password) {
        if (password == null) {
            return FAILURE_MESSAGE;
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return TOO_LONG_MESSAGE;
        }
        return hasRequiredClasses(password) ? null : FAILURE_MESSAGE;
    }

    /** Service-level guard: call before every PasswordEncoder.encode of a user-supplied password. */
    public static void requireValid(String password) {
        String message = violation(password);
        if (message != null) {
            throw new BadRequestException(message);
        }
    }

    private static boolean hasRequiredClasses(String password) {
        if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
            return false;
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (int i = 0; i < password.length(); ) {
            int cp = password.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isUpperCase(cp)) {
                upper = true;
            } else if (Character.isLowerCase(cp)) {
                lower = true;
            } else if (Character.isDigit(cp)) {
                digit = true;
            } else if (!Character.isLetterOrDigit(cp)) {
                special = true;
            }
        }
        return upper && lower && digit && special;
    }
}
