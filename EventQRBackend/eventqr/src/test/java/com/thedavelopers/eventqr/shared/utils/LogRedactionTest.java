package com.thedavelopers.eventqr.shared.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogRedactionTest {

    @Test
    void maskEmailKeepsFirstCharacterAndDomain() {
        assertThat(LogRedaction.maskEmail("jane.doe@example.com")).isEqualTo("j***@example.com");
    }

    @Test
    void redactEmailsMasksEveryAddressInFreeText() {
        String text = "HTTP 400: {\"message\":\"jane.doe@example.com and bob+tag@mail.example.org are invalid\"}";

        assertThat(LogRedaction.redactEmails(text))
                .isEqualTo("HTTP 400: {\"message\":\"j***@example.com and b***@mail.example.org are invalid\"}")
                .doesNotContain("jane.doe", "bob+tag");
    }

    @Test
    void redactEmailsLeavesTextWithoutAddressesAndNullUntouched() {
        assertThat(LogRedaction.redactEmails("Connection reset")).isEqualTo("Connection reset");
        assertThat(LogRedaction.redactEmails(null)).isNull();
    }

    @Test
    void redactedCopyMasksEveryMessageInTheChainButKeepsStackTraces() {
        IllegalArgumentException cause = new IllegalArgumentException("bad address bob@mail.example.org");
        IllegalStateException failure = new IllegalStateException("failed for jane.doe@example.com", cause);

        Throwable copy = LogRedaction.redactedCopy(failure);

        assertThat(copy.getMessage())
                .isEqualTo("java.lang.IllegalStateException: failed for j***@example.com");
        assertThat(copy.getStackTrace()).isEqualTo(failure.getStackTrace());
        assertThat(copy.getCause().getMessage())
                .isEqualTo("java.lang.IllegalArgumentException: bad address b***@mail.example.org");
        assertThat(copy.getCause().getStackTrace()).isEqualTo(cause.getStackTrace());
        assertThat(LogRedaction.redactedCopy(null)).isNull();
    }
}
