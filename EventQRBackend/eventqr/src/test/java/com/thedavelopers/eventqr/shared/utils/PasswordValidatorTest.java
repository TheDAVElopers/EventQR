package com.thedavelopers.eventqr.shared.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordValidatorTest {

    @Test
    void failureMessageIsTheAgreedText() {
        assertThat(PasswordValidator.FAILURE_MESSAGE).isEqualTo(
                "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a number, and a special character");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Passw0rd!", "Aa1!aaaa", "Abcdef1 x", "Abcdef1_x", "Abcdef1-x", "Abcdef1é£"})
    void validPasswordsPass(String password) {
        assertThat(PasswordValidator.isValid(password)).isTrue();
    }

    @Test
    void nullIsInvalid() {
        assertThat(PasswordValidator.isValid(null)).isFalse();
    }

    @Test
    void eachMissingClassFails() {
        assertThat(PasswordValidator.isValid("passw0rd!")).as("no upper").isFalse();
        assertThat(PasswordValidator.isValid("PASSW0RD!")).as("no lower").isFalse();
        assertThat(PasswordValidator.isValid("Password!")).as("no digit").isFalse();
        assertThat(PasswordValidator.isValid("Passw0rdd")).as("no special").isFalse();
    }

    @Test
    void lengthBounds() {
        assertThat(PasswordValidator.isValid("Aa1!aaa")).as("7 chars").isFalse();
        assertThat(PasswordValidator.isValid("Aa1!aaaa")).as("8 chars").isTrue();
        assertThat(PasswordValidator.violation("Aa1!aaa")).isEqualTo(PasswordValidator.FAILURE_MESSAGE);
    }

    @Test
    void maximumIs72Utf8BytesNotCharacters() {
        assertThat(PasswordValidator.isValid("Aa1!" + "a".repeat(68))).as("exactly 72 bytes").isTrue();
        assertThat(PasswordValidator.violation("Aa1!" + "a".repeat(69))).as("73 bytes")
                .isEqualTo(PasswordValidator.TOO_LONG_MESSAGE);
        // 4 ASCII + 40 x 2-byte chars = 84 bytes, only 44 characters.
        String multibyte = "Aa1!" + "é".repeat(40);
        assertThat(PasswordValidator.violation(multibyte)).isEqualTo(PasswordValidator.TOO_LONG_MESSAGE);
        // 4 + 34 x 2 = 72 bytes exactly.
        assertThat(PasswordValidator.isValid("Aa1!" + "é".repeat(34))).isTrue();
    }

    @Test
    void tooLongMessageIsTheAgreedText() {
        assertThat(PasswordValidator.TOO_LONG_MESSAGE).isEqualTo(
                "Password must be at most 72 bytes long (about 72 characters; fewer if it uses non-English characters or emoji)");
    }

    @Test
    void requireValidThrowsBadRequestWithTheMatchingMessage() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PasswordValidator.requireValid("weak"))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.BadRequestException.class)
                .hasMessage(PasswordValidator.FAILURE_MESSAGE);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PasswordValidator.requireValid("Aa1!" + "a".repeat(69)))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.BadRequestException.class)
                .hasMessage(PasswordValidator.TOO_LONG_MESSAGE);
    }

    @Test
    void unicodeLettersCountAsLettersNotSpecials() {
        // Accented upper/lower letters and a digit, no special: invalid.
        assertThat(PasswordValidator.isValid("École1éx")).isFalse();
        // Non-ASCII letters satisfy upper/lower; the symbol is the special.
        assertThat(PasswordValidator.isValid("École1é€")).isTrue();
        // Caseless CJK letters are neither upper nor lower, and not special.
        assertThat(PasswordValidator.isValid("漢字漢字Aa1x")).isFalse();
    }

    @Test
    void emojiAndWhitespaceAreSpecialAndSurrogatesCountOnce() {
        assertThat(PasswordValidator.isValid("Passw0rd😀")).isTrue();
        assertThat(PasswordValidator.isValid("Passw0rd ")).isTrue();
        // 4 code points (8 UTF-16 units) must NOT satisfy the 8 minimum.
        assertThat(PasswordValidator.isValid("A1😀😀")).isFalse();
    }
}
