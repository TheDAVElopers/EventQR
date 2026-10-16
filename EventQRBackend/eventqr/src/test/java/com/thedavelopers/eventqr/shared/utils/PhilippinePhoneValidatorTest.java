package com.thedavelopers.eventqr.shared.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** Server rule: after trim, ^\+?63\d{10}$ (exactly what the Android client accepts); null is valid, blank is not. */
class PhilippinePhoneValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    record Holder(@PhilippinePhone String phone) {
    }

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private final PhilippinePhoneValidator plain = new PhilippinePhoneValidator();

    @ParameterizedTest
    @ValueSource(strings = {"+639171234567", "639171234567", " +639171234567 ", "+632123456789"})
    void acceptsEveryFormTheAppCanSend(String value) {
        assertThat(plain.isValid(value, null)).isTrue();
        assertThat(validator.validate(new Holder(value))).isEmpty();
    }

    @Test
    void nullIsValid() {
        assertThat(plain.isValid(null, null)).isTrue();
        assertThat(validator.validate(new Holder(null))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "9171234567", "09171234567", "+63 917 123 4567", "+63-917-123-4567",
            "+63917123456", "+6391712345678", "+649171234567", "+63917123456a", "++639171234567", "12345"})
    void rejectsEverythingElse(String value) {
        assertThat(plain.isValid(value, null)).isFalse();
        Set<ConstraintViolation<Holder>> violations = validator.validate(new Holder(value));
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo(PhoneNumbers.MESSAGE);
    }

    @Test
    void rejectsAVeryLongValue() {
        String longValue = "+63" + "9".repeat(1000);
        assertThat(plain.isValid(longValue, null)).isFalse();
    }
}
