package com.thedavelopers.eventqr.features.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.thedavelopers.eventqr.features.monitor.service.SensitiveNames;

class SensitiveNamesTest {

    @ParameterizedTest
    @ValueSource(strings = { "passwordHash", "email", "phoneNumber", "qrValue", "role", "status", "fullName",
            "attendeeEmail", "attendeeName", "contactEmail", "contactNumber", "EMAIL", "resetPassword", "accessToken",
            "clientSecret", "tokenHash", "fileHASH", "Password" })
    void sensitiveNamesAreMasked(String name) {
        assertThat(SensitiveNames.isSensitive(name)).isTrue();
        assertThat(SensitiveNames.display(name)).isEqualTo("<sensitive>");
    }

    @ParameterizedTest
    @ValueSource(strings = { "title", "capacity", "eventStartAt", "currentAttendeeCount", "updatedAt", "label" })
    void ordinaryNamesAreShown(String name) {
        assertThat(SensitiveNames.isSensitive(name)).isFalse();
        assertThat(SensitiveNames.display(name)).isEqualTo(name);
    }

    @Test
    void nullIsMasked() {
        assertThat(SensitiveNames.display(null)).isEqualTo("<sensitive>");
    }
}
