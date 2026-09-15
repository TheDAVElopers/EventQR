package com.thedavelopers.eventqr.features.qremail.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailTemplateBuilderTest {

    @Test
    void buildUsesCidReferenceInsteadOfDataUri() {
        byte[] qrImageBytes = { 1, 2, 3 };

        EmailTemplateBuilder.EmailContent content = new EmailTemplateBuilder()
                .build("Attendee", "credential-value", qrImageBytes, null);

        assertThat(content.html()).contains("cid:qrImage.png");
        assertThat(content.html()).doesNotContain("data:image");
        assertThat(content.qrImageBytes()).isSameAs(qrImageBytes);
    }
}
