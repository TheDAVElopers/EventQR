package com.thedavelopers.eventqr.features.idprinting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse;
import com.thedavelopers.eventqr.features.idprinting.model.entity.IdPrintLog;
import com.thedavelopers.eventqr.features.idprinting.model.entity.IdTemplate;
import com.thedavelopers.eventqr.features.idprinting.repository.IdPrintLogRepository;
import com.thedavelopers.eventqr.features.idprinting.repository.IdTemplateRepository;
import com.thedavelopers.eventqr.shared.constants.IdPrintStatus;
import com.thedavelopers.eventqr.shared.constants.QrDeliveryStatus;
import com.thedavelopers.eventqr.shared.constants.QrDisplayStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort.QrCredentialSnapshot;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort.RegistrationSnapshot;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdPrintingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T08:00:00Z");

    @Mock
    private IdTemplateRepository idTemplateRepository;
    @Mock
    private IdPrintLogRepository idPrintLogRepository;
    @Mock
    private QrCredentialPort qrCredentialPort;
    @Mock
    private RegistrationLookupPort registrationLookupPort;
    @Mock
    private IdTemplateConfigurationService templateConfigurationService;

    private IdPrintingService service;

    private final UUID eventId = UUID.randomUUID();
    private final UUID attendeeId = UUID.randomUUID();
    private final UUID registrationId = UUID.randomUUID();
    private final UUID qrId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new IdPrintingService(idTemplateRepository, idPrintLogRepository, qrCredentialPort,
                registrationLookupPort, templateConfigurationService, Duration.ofMinutes(10),
                Clock.fixed(NOW, ZoneOffset.UTC));

        RegistrationSnapshot registration = registration(attendeeId, registrationId, qrId, RegistrationStatus.REGISTERED);
        given(registrationLookupPort.listByEventId(eventId)).willReturn(List.of(registration));
        given(registrationLookupPort.requireById(registrationId)).willReturn(registration);
        given(qrCredentialPort.findById(qrId)).willReturn(Optional.of(qr(qrId, attendeeId, registrationId)));

        IdTemplate template = new IdTemplate();
        template.setId(templateId);
        template.setEventId(eventId);
        given(templateConfigurationService.getOrCreateActiveTemplate(eventId)).willReturn(template);

        given(idPrintLogRepository.save(any(IdPrintLog.class))).willAnswer(invocation -> {
            IdPrintLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(UUID.randomUUID());
                log.setCreatedAt(NOW);
            }
            return log;
        });
    }

    // ---------------------------------------------------------------- print

    @Test
    void print_createsPendingLog_withDefaultTemplateFallback_andCallerAsPrinter() {
        IdPrintResponse response = service.printForAttendee(eventId, attendeeId, staffId);

        ArgumentCaptor<IdPrintLog> saved = ArgumentCaptor.forClass(IdPrintLog.class);
        verify(idPrintLogRepository).save(saved.capture());
        IdPrintLog log = saved.getValue();
        assertThat(log.getStatus()).isEqualTo(IdPrintStatus.PENDING);
        assertThat(log.isSuccess()).isFalse();
        assertThat(log.getPrintedAt()).isNull();
        assertThat(log.getConfirmedAt()).isNull();
        assertThat(log.getPrintedByUserId()).isEqualTo(staffId);
        assertThat(log.getTemplateId()).isEqualTo(templateId);
        assertThat(log.isReprint()).isFalse();
        verify(templateConfigurationService).getOrCreateActiveTemplate(eventId);

        assertThat(response.printLogId()).isEqualTo(log.getId());
        assertThat(response.status()).isEqualTo(IdPrintStatus.PENDING);
        assertThat(response.success()).isFalse();
        assertThat(response.printedAt()).isNull();
        assertThat(response.confirmedAt()).isNull();
    }

    @Test
    void print_isReprint_onlyWhenAttendeeHasEffectivelyPrintedId() {
        given(idPrintLogRepository.existsEffectivelyPrinted(eventId, attendeeId, NOW.minus(Duration.ofMinutes(10))))
                .willReturn(true);

        IdPrintResponse response = service.printForAttendee(eventId, attendeeId, staffId);

        assertThat(response.reprint()).isTrue();
        assertThat(response.message()).startsWith("Reprint logged");
    }

    @Test
    void print_afterOnlyCancelledAttempts_isFirstPrint() {
        given(idPrintLogRepository.existsEffectivelyPrinted(any(), any(), any())).willReturn(false);

        IdPrintResponse response = service.printForAttendee(eventId, attendeeId, staffId);

        assertThat(response.reprint()).isFalse();
        assertThat(response.message()).startsWith("Print logged");
    }

    @Test
    void print_cancelledRegistration_isConflict() {
        given(registrationLookupPort.requireById(registrationId))
                .willReturn(registration(attendeeId, registrationId, qrId, RegistrationStatus.CANCELLED));

        assertThatThrownBy(() -> service.printForAttendee(eventId, attendeeId, staffId))
                .isInstanceOf(ConflictException.class);
        verify(idPrintLogRepository, never()).save(any());
    }

    @Test
    void printBatch_readsRegistrationsOnce_andLogsOnePendingRowPerCard() {
        List<IdPrintResponse> responses = service.printBatch(eventId, List.of(attendeeId, attendeeId), staffId);

        assertThat(responses).hasSize(2).allMatch(r -> r.status() == IdPrintStatus.PENDING);
        verify(registrationLookupPort, times(1)).listByEventId(eventId);
        verify(idPrintLogRepository, times(2)).save(any());
    }

    @Test
    void printBatch_unknownAttendee_isNotFound_andLogsNothing() {
        assertThatThrownBy(() -> service.printBatch(eventId, List.of(attendeeId, UUID.randomUUID()), staffId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(idPrintLogRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- confirm

    @Test
    void confirm_printed_setsSuccessPrintedAtAndConfirmedAt() {
        IdPrintLog log = pendingLog(staffId, NOW.minusSeconds(30));
        given(idPrintLogRepository.findByIdAndEventIdForUpdate(log.getId(), eventId)).willReturn(Optional.of(log));

        IdPrintResponse response = service.confirm(eventId, log.getId(), IdPrintStatus.PRINTED, staffId, false);

        assertThat(log.getStatus()).isEqualTo(IdPrintStatus.PRINTED);
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getPrintedAt()).isEqualTo(NOW);
        assertThat(log.getConfirmedAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo(IdPrintStatus.PRINTED);
        assertThat(response.success()).isTrue();
        assertThat(response.printedAt()).isEqualTo(NOW);
        assertThat(response.confirmedAt()).isEqualTo(NOW);
        verify(idPrintLogRepository).boundLockWaits();
    }

    @Test
    void confirm_cancelled_keepsSuccessFalse_andStampsConfirmedAt() {
        IdPrintLog log = pendingLog(staffId, NOW.minusSeconds(30));
        given(idPrintLogRepository.findByIdAndEventIdForUpdate(log.getId(), eventId)).willReturn(Optional.of(log));

        IdPrintResponse response = service.confirm(eventId, log.getId(), IdPrintStatus.CANCELLED, staffId, false);

        assertThat(log.getStatus()).isEqualTo(IdPrintStatus.CANCELLED);
        assertThat(log.isSuccess()).isFalse();
        assertThat(log.getPrintedAt()).isNull();
        assertThat(log.getConfirmedAt()).isEqualTo(NOW);
        assertThat(response.success()).isFalse();
        assertThat(response.printedAt()).isNull();
        assertThat(response.status()).isEqualTo(IdPrintStatus.CANCELLED);
    }

    @Test
    void confirm_failed_keepsSuccessFalse() {
        IdPrintLog log = pendingLog(staffId, NOW.minusSeconds(30));
        given(idPrintLogRepository.findByIdAndEventIdForUpdate(log.getId(), eventId)).willReturn(Optional.of(log));

        IdPrintResponse response = service.confirm(eventId, log.getId(), IdPrintStatus.FAILED, staffId, false);

        assertThat(response.status()).isEqualTo(IdPrintStatus.FAILED);
        assertThat(log.isSuccess()).isFalse();
        assertThat(log.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void confirm_pendingPastGrace_isConflict_becauseItAlreadyReadsAsPrinted() {
        IdPrintLog log = pendingLog(staffId, NOW.minus(Duration.ofMinutes(11)));
        given(idPrintLogRepository.findByIdAndEventIdForUpdate(log.getId(), eventId)).willReturn(Optional.of(log));

        assertThatThrownBy(() -> service.confirm(eventId, log.getId(), IdPrintStatus.CANCELLED, staffId, false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("confirmation window expired");
        assertThat(log.getStatus()).isEqualTo(IdPrintStatus.PENDING);
        verify(idPrintLogRepository, never()).save(any());
    }

    @Test
    void confirm_pendingJustInsideGrace_isAllowed() {
        IdPrintLog log = pendingLog(staffId, NOW.minus(Duration.ofMinutes(9)));
        given(idPrintLogRepository.findByIdAndEventIdForUpdate(log.getId(), eventId)).willReturn(Optional.of(log));

        assertThat(service.confirm(eventId, log.getId(), IdPrintStatus.CANCELLED, staffId, false).status())
                .isEqualTo(IdPrintStatus.CANCELLED);
    }

    @Test
    void confirm_alreadyFinal_isConflict() {
        IdPrintLog log = pendingLog(staffId, NOW.minusSeconds(30));
        log.setStatus(IdPrintStatus.PRINTED);
        given(idPrintLogRepository.findByIdAndEventIdForUpdate(log.getId(), eventId)).willReturn(Optional.of(log));

        assertThatThrownBy(() -> service.confirm(eventId, log.getId(), IdPrintStatus.CANCELLED, staffId, false))
                .isInstanceOf(ConflictException.class);
        verify(idPrintLogRepository, never()).save(any());
    }

    @Test
    void confirm_logNotInEvent_isNotFound() {
        UUID logId = UUID.randomUUID();
        given(idPrintLogRepository.findByIdAndEventIdForUpdate(logId, eventId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(eventId, logId, IdPrintStatus.PRINTED, staffId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void confirm_byOtherStaff_isForbidden() {
        IdPrintLog log = pendingLog(staffId, NOW.minusSeconds(30));
        given(idPrintLogRepository.findByIdAndEventIdForUpdate(log.getId(), eventId)).willReturn(Optional.of(log));

        assertThatThrownBy(() -> service.confirm(eventId, log.getId(), IdPrintStatus.PRINTED, UUID.randomUUID(), false))
                .isInstanceOf(ForbiddenException.class);
        assertThat(log.getStatus()).isEqualTo(IdPrintStatus.PENDING);
        verify(idPrintLogRepository, never()).save(any());
    }

    @Test
    void confirm_byEventManager_mayConfirmSomeoneElsesLog() {
        IdPrintLog log = pendingLog(staffId, NOW.minusSeconds(30));
        given(idPrintLogRepository.findByIdAndEventIdForUpdate(log.getId(), eventId)).willReturn(Optional.of(log));

        IdPrintResponse response = service.confirm(eventId, log.getId(), IdPrintStatus.PRINTED, UUID.randomUUID(), true);

        assertThat(response.status()).isEqualTo(IdPrintStatus.PRINTED);
    }

    @Test
    void confirm_pendingOutcome_isBadRequest() {
        assertThatThrownBy(() -> service.confirm(eventId, UUID.randomUUID(), IdPrintStatus.PENDING, staffId, false))
                .isInstanceOf(BadRequestException.class);
        verify(idPrintLogRepository, never()).findByIdAndEventIdForUpdate(any(), any());
    }

    // ---------------------------------------------------------------- reads

    @Test
    void findByEvent_reportsEffectiveStatus_andKeepsCancelledAndFailedVisible() {
        IdPrintLog freshPending = pendingLog(staffId, NOW.minusSeconds(60));
        IdPrintLog stalePending = pendingLog(staffId, NOW.minus(Duration.ofMinutes(11)));
        IdPrintLog cancelled = pendingLog(staffId, NOW.minusSeconds(120));
        cancelled.setStatus(IdPrintStatus.CANCELLED);
        cancelled.setConfirmedAt(NOW.minusSeconds(100));
        IdPrintLog failed = pendingLog(staffId, NOW.minusSeconds(120));
        failed.setStatus(IdPrintStatus.FAILED);
        IdPrintLog printed = pendingLog(staffId, NOW.minusSeconds(300));
        printed.setStatus(IdPrintStatus.PRINTED);
        printed.setSuccess(true);
        printed.setPrintedAt(NOW.minusSeconds(290));
        printed.setConfirmedAt(NOW.minusSeconds(290));
        given(idPrintLogRepository.findByEventId(eventId))
                .willReturn(List.of(freshPending, stalePending, cancelled, failed, printed));

        List<IdPrintResponse> responses = service.findByEvent(eventId);

        assertThat(responses).extracting(IdPrintResponse::status).containsExactly(IdPrintStatus.PENDING,
                IdPrintStatus.PRINTED, IdPrintStatus.CANCELLED, IdPrintStatus.FAILED, IdPrintStatus.PRINTED);
        assertThat(responses).extracting(IdPrintResponse::success).containsExactly(false, true, false, false, true);

        IdPrintResponse stale = responses.get(1);
        assertThat(stale.printedAt()).isEqualTo(stalePending.getCreatedAt());
        assertThat(stale.confirmedAt()).isNull();
        assertThat(responses.get(0).printedAt()).isNull();
        assertThat(responses.get(2).printedAt()).isNull();
        assertThat(responses.get(2).confirmedAt()).isEqualTo(NOW.minusSeconds(100));
        assertThat(responses.get(4).printedAt()).isEqualTo(NOW.minusSeconds(290));
    }

    @Test
    void hasPrintedId_usesGraceCutoff() {
        given(idPrintLogRepository.existsEffectivelyPrinted(eq(eventId), eq(attendeeId), any())).willReturn(true);

        assertThat(service.hasPrintedId(eventId, attendeeId)).isTrue();
        verify(idPrintLogRepository).existsEffectivelyPrinted(eventId, attendeeId, NOW.minus(Duration.ofMinutes(10)));
    }

    @Test
    void getTemplate_fallsBackToDefaultTemplate() {
        assertThat(service.getTemplate(eventId).getId()).isEqualTo(templateId);
        verify(templateConfigurationService).getOrCreateActiveTemplate(eventId);
    }

    // ---------------------------------------------------------------- fixtures

    private IdPrintLog pendingLog(UUID printedBy, Instant createdAt) {
        IdPrintLog log = new IdPrintLog();
        log.setId(UUID.randomUUID());
        log.setCreatedAt(createdAt);
        log.setEventId(eventId);
        log.setAttendeeUserId(attendeeId);
        log.setRegistrationId(registrationId);
        log.setQrCredentialId(qrId);
        log.setTemplateId(templateId);
        log.setPrintedByUserId(printedBy);
        log.setStatus(IdPrintStatus.PENDING);
        log.setSuccess(false);
        log.setMessage("Print logged (no physical printer connected)");
        return log;
    }

    private RegistrationSnapshot registration(UUID attendee, UUID registration, UUID qr, RegistrationStatus status) {
        return new RegistrationSnapshot(registration, eventId, attendee, "a@example.com", "Attendee", status, qr,
                NOW, null, null, null, 0, 1);
    }

    private QrCredentialSnapshot qr(UUID qr, UUID attendee, UUID registration) {
        return new QrCredentialSnapshot(qr, eventId, attendee, registration, "EVQR-TEST", true,
                QrDisplayStatus.values()[0], QrDeliveryStatus.values()[0], false);
    }
}
