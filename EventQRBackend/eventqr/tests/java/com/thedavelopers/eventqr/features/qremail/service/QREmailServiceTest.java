package com.thedavelopers.eventqr.features.qremail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.features.qremail.model.EmailDeliveryStatus;
import com.thedavelopers.eventqr.features.qremail.model.entity.EmailDeliveryLog;
import com.thedavelopers.eventqr.features.qremail.repository.EmailDeliveryLogRepository;
import com.thedavelopers.eventqr.features.qrcredentials.model.entity.QrCredential;
import com.thedavelopers.eventqr.features.qrcredentials.repository.QrCredentialRepository;
import com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort;

class QREmailServiceTest {

    @Test
    void retriesOnceAfterFirstGatewayFailure() {
        EventRegistrationRepository registrationRepository = mock(EventRegistrationRepository.class);
        QrCredentialRepository qrCredentialRepository = mock(QrCredentialRepository.class);
        EmailDeliveryLogRepository deliveryLogRepository = mock(EmailDeliveryLogRepository.class);
        QrCredentialPort qrCredentialPort = mock(QrCredentialPort.class);
        EmailGatewayService emailGatewayService = mock(EmailGatewayService.class);

        UUID registrationId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        EventRegistration registration = new EventRegistration();
        registration.setId(registrationId);
        registration.setAttendeeEmail("attendee@example.com");
        registration.setAttendeeName("Attendee");
        QrCredential credential = new QrCredential();
        credential.setId(credentialId);
        credential.setQrValue("credential-value");

        when(registrationRepository.findById(registrationId)).thenReturn(Optional.of(registration));
        when(qrCredentialRepository.findByRegistrationId(registrationId)).thenReturn(Optional.of(credential));
        when(deliveryLogRepository.save(any(EmailDeliveryLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(qrCredentialPort.renderQrImage("credential-value")).thenReturn(new byte[] { 1, 2, 3 });
        doThrow(new IllegalStateException("Brevo failed"))
                .doNothing()
                .when(emailGatewayService).send(anyString(), any(EmailTemplateBuilder.EmailContent.class));

        QREmailService service = new QREmailService(registrationRepository, qrCredentialRepository,
                deliveryLogRepository, qrCredentialPort, new EmailTemplateBuilder(), emailGatewayService);

        QREmailService.DeliveryResult result = service.sendForRegistration(registrationId);

        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.SENT);
        verify(emailGatewayService, times(2)).send(anyString(), any(EmailTemplateBuilder.EmailContent.class));
        verify(qrCredentialPort).markEmailSent(credentialId);
    }
}
