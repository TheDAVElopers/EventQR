package com.thedavelopers.eventqr.features.qremail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import brevoApi.TransactionalEmailsApi;
import brevoModel.SendSmtpEmail;

class EmailGatewayServiceTest {

    @Test
    void sendAddsQrImageAttachmentWithMatchingContentIdName() throws Exception {
        TransactionalEmailsApi transactionalEmailsApi = mock(TransactionalEmailsApi.class);
        EmailGatewayService gatewayService = new EmailGatewayService(transactionalEmailsApi, "sender@example.com");
        byte[] qrImageBytes = { 10, 20, 30, 40 };
        EmailTemplateBuilder.EmailContent content = new EmailTemplateBuilder.EmailContent(
                "Subject", "<img src=\"cid:qrImage.png\">", qrImageBytes);

        gatewayService.send("attendee@example.com", content);

        var emailCaptor = org.mockito.ArgumentCaptor.forClass(SendSmtpEmail.class);
        verify(transactionalEmailsApi).sendTransacEmail(emailCaptor.capture());
        SendSmtpEmail sentEmail = emailCaptor.getValue();

        assertThat(sentEmail.getAttachment()).hasSize(1);
        assertThat(sentEmail.getAttachment().get(0).getName()).isEqualTo("qrImage.png");
        assertThat(sentEmail.getAttachment().get(0).getContent()).isNotNull();
        assertThat(Arrays.equals(qrImageBytes, sentEmail.getAttachment().get(0).getContent())).isTrue();
        assertThat(sentEmail.getHtmlContent()).contains("cid:qrImage.png");
        verify(transactionalEmailsApi).sendTransacEmail(eq(sentEmail));
    }
}
