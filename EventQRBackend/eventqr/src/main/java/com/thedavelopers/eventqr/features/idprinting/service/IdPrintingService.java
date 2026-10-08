package com.thedavelopers.eventqr.features.idprinting.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintRequest;
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse;
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdTemplateRequest;
import com.thedavelopers.eventqr.features.idprinting.model.entity.IdPrintLog;
import com.thedavelopers.eventqr.features.idprinting.model.entity.IdTemplate;
import com.thedavelopers.eventqr.features.idprinting.repository.IdPrintLogRepository;
import com.thedavelopers.eventqr.features.idprinting.repository.IdTemplateRepository;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort;

@Service
@Transactional
public class IdPrintingService {

    private final IdTemplateRepository idTemplateRepository;
    private final IdPrintLogRepository idPrintLogRepository;
    private final QrCredentialPort qrCredentialPort;
    private final RegistrationLookupPort registrationLookupPort;

    public IdPrintingService(IdTemplateRepository idTemplateRepository, IdPrintLogRepository idPrintLogRepository,
                             QrCredentialPort qrCredentialPort, RegistrationLookupPort registrationLookupPort) {
        this.idTemplateRepository = idTemplateRepository;
        this.idPrintLogRepository = idPrintLogRepository;
        this.qrCredentialPort = qrCredentialPort;
        this.registrationLookupPort = registrationLookupPort;
    }

    public IdPrintResponse print(IdPrintRequest request) {
        var qr = qrCredentialPort.findById(request.qrCredentialId())
                .orElseThrow(() -> new ResourceNotFoundException("QR credential not found"));
        if (!qr.eventId().equals(request.eventId())) {
            throw new ConflictException("QR credential does not belong to the event");
        }

        IdTemplate template = idTemplateRepository.findFirstByEventIdAndActiveTrue(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Active ID template not found for event"));
        var registration = registrationLookupPort.requireById(qr.registrationId());
        if (registration.status() == com.thedavelopers.eventqr.shared.constants.RegistrationStatus.CANCELLED) {
            throw new ConflictException("Cannot print an ID for a cancelled registration");
        }

        IdPrintLog log = new IdPrintLog();
        log.setEventId(request.eventId());
        log.setAttendeeUserId(registration.attendeeUserId());
        log.setRegistrationId(registration.registrationId());
        log.setQrCredentialId(qr.qrCredentialId());
        log.setTemplateId(template.getId());
        log.setReprint(request.reprint());
        log.setSuccess(true);
        log.setPrintedAt(Instant.now());
        log.setMessage(request.reprint() ? "Reprint logged (no physical printer connected)" : "Print logged (no physical printer connected)");
        IdPrintLog saved = idPrintLogRepository.save(log);
        return new IdPrintResponse(saved.getId(), saved.getEventId(), saved.getAttendeeUserId(), saved.getRegistrationId(),
                saved.getQrCredentialId(), saved.getTemplateId(), saved.isReprint(), saved.isSuccess(), saved.getMessage(),
                saved.getPrintedAt());
    }

    public List<IdPrintResponse> findByEvent(UUID eventId) {
        return idPrintLogRepository.findByEventId(eventId).stream().map(log -> new IdPrintResponse(log.getId(), log.getEventId(),
                log.getAttendeeUserId(), log.getRegistrationId(), log.getQrCredentialId(), log.getTemplateId(), log.isReprint(),
                log.isSuccess(), log.getMessage(), log.getPrintedAt())).toList();
    }

    public List<IdTemplate> listTemplates(UUID eventId) {
        return idTemplateRepository.findByEventId(eventId);
    }

    public IdTemplate getTemplate(UUID eventId) {
        return idTemplateRepository.findFirstByEventIdAndActiveTrue(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Active ID template not found for event"));
    }

    public IdTemplate saveTemplate(UUID eventId, IdTemplateRequest request) {
        IdTemplate template = idTemplateRepository.findFirstByEventIdAndActiveTrue(eventId).orElseGet(IdTemplate::new);
        template.setEventId(eventId);
        template.setName(request.name());
        template.setTemplateJson(request.templateJson());
        template.setActive(request.active());
        return idTemplateRepository.save(template);
    }

    public IdPrintResponse previewForAttendee(UUID eventId, UUID attendeeUserId) {
        return print(new IdPrintRequest(eventId, findRegistrationForAttendee(eventId, attendeeUserId).qrCredentialId(),
                attendeeUserId, false));
    }

    public IdPrintResponse printForAttendee(UUID eventId, UUID attendeeUserId, boolean reprint) {
        var registration = findRegistrationForAttendee(eventId, attendeeUserId);
        return print(new IdPrintRequest(eventId, registration.qrCredentialId(), attendeeUserId, reprint));
    }

    /**
     * Prints a sheet that may contain multiple ID cards. One {@code id_print_logs}
     * entry is written per attendee entry (i.e. per printed card), so an 8-up sheet
     * with 8 entries produces 8 individual log rows rather than one per sheet.
     */
    public List<IdPrintResponse> printBatch(UUID eventId, List<UUID> attendeeUserIds, boolean reprint) {
        return attendeeUserIds.stream()
                .map(attendeeUserId -> findRegistrationForAttendee(eventId, attendeeUserId))
                .map(registration -> print(new IdPrintRequest(eventId, registration.qrCredentialId(),
                        registration.attendeeUserId(), reprint)))
                .toList();
    }

    private com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort.RegistrationSnapshot findRegistrationForAttendee(
            UUID eventId, UUID attendeeUserId) {
        return registrationLookupPort.listByEventId(eventId).stream()
                .filter(item -> item.attendeeUserId().equals(attendeeUserId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found for attendee"));
    }
}
