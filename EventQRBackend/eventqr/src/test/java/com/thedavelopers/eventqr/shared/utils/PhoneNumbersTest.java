package com.thedavelopers.eventqr.shared.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;

class PhoneNumbersTest {

    @Test
    void normalizeAddsThePlusToTheLegacyForm() {
        assertThat(PhoneNumbers.normalize("639171234567")).isEqualTo("+639171234567");
    }

    @Test
    void normalizeKeepsTheCanonicalFormAndTrims() {
        assertThat(PhoneNumbers.normalize("+639171234567")).isEqualTo("+639171234567");
        assertThat(PhoneNumbers.normalize("  639171234567 ")).isEqualTo("+639171234567");
    }

    @Test
    void normalizeKeepsNull() {
        assertThat(PhoneNumbers.normalize(null)).isNull();
    }

    @Test
    void normalizeRejectsInvalidValues() {
        for (String bad : new String[] {"", "9171234567", "+63 9171234567", "+63917123456"}) {
            assertThatThrownBy(() -> PhoneNumbers.normalize(bad))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage(PhoneNumbers.MESSAGE);
        }
    }

    @Test
    void isValidMatchesTheValidator() {
        assertThat(PhoneNumbers.isValid("+639171234567")).isTrue();
        assertThat(PhoneNumbers.isValid(null)).isFalse();
        assertThat(PhoneNumbers.isValid("09171234567")).isFalse();
    }
}
