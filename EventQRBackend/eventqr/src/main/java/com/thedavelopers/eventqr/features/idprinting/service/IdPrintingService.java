package com.thedavelopers.eventqr.features.idprinting.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintRequest;
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse;
import com.thedavelopers.eventqr.features.idprinting.model.entity.IdPrintLog;
import com.thedavelopers.eventqr.features.idprinting.model.entity.IdTemplate;
import com.thedavelopers.eventqr.features.idprinting.repository.IdPrintLogRepository;
import com.thedavelopers.eventqr.features.idprinting.repository.IdTemplateRepository;
import com.thedavelopers.eventqr.shared.exception.ConflictException;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.port.QrCredentialPort;
import com.thedavelopers.eventqr.shared.port.RegistrationLookupPort;

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

        IdPrintLog log = new IdPrintLog();
        log.setEventId(request.eventId());
        log.setAttendeeUserId(registration.attendeeUserId());
        log.setRegistrationId(registration.registrationId());
        log.setQrCredentialId(qr.qrCredentialId());
        log.setTemplateId(template.getId());
        log.setReprint(request.reprint());
        log.setSuccess(true);
        log.setPrintedAt(Instant.now());
        log.setMessage(request.reprint() ? "Reprint simulated successfully" : "Print simulated successfully");
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
}