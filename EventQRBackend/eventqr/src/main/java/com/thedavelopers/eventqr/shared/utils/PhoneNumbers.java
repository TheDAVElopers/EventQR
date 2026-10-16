package com.thedavelopers.eventqr.shared.utils;

import java.util.regex.Pattern;

import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;

/**
 * Philippine phone numbers as accepted on write: {@code +63} or {@code 63} followed by exactly 10 digits, with
 * surrounding whitespace ignored and no separators. This mirrors the Android client's
 * {@code Validators.isValidPhoneNumber}, so every value the app can send is accepted. Stored values are always the
 * canonical {@code +63XXXXXXXXXX}. Reads never validate, so legacy rows stay readable.
 */
public final class PhoneNumbers {

    public static final String MESSAGE = "Phone number must be a Philippine number in the format +63XXXXXXXXXX";

    private static final Pattern PH = Pattern.compile("^\\+?63\\d{10}$");

    private PhoneNumbers() {
    }

    public static boolean isValid(String raw) {
        return raw != null && PH.matcher(raw.trim()).matches();
    }

    /** Canonical {@code +63XXXXXXXXXX}; null stays null. An invalid value is a 400 (DTOs validate first). */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!PH.matcher(trimmed).matches()) {
            throw new BadRequestException(MESSAGE);
        }
        return trimmed.startsWith("+") ? trimmed : "+" + trimmed;
    }
}
