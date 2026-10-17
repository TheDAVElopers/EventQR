package com.thedavelopers.eventqr.features.idprinting.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintRequest;
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdPrintResponse;
import com.thedavelopers.eventqr.features.idprinting.model.dto.IdTemplateRequest;
import com.thedavelopers.eventqr.features.idprinting.model.entity.IdPrintLog;
import com.thedavelopers.eventqr.features.idprinting.model.entity.IdTemplate;
import com.thedavelopers.eventqr.features.idprinting.repository.IdPrintLogRepository;
import com.thedavelopers.eventqr.features.idprinting.repository.IdTemplateRepository;
import com.thedavelopers.eventqr.shared.constants.IdPrintStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort.RegistrationSnapshot;

/**
 * ID printing is two-phase. print-id / reprint-id / print-id-batch insert a PENDING log (success=false,
 * printed_at=null) before the client opens the print dialog; the client then reports the dialog outcome through
 * {@link #confirm}. Only PRINTED counts as a printed ID.
 *
 * Legacy rule: app builds that predate confirmation never call {@link #confirm}, so their logs would stay PENDING
 * forever. A PENDING log older than {@code app.id-printing.pending-confirmation-grace} (default 10 minutes) is
 * therefore reported as PRINTED (success=true, printedAt=createdAt) by every reader; this matches what those builds
 * logged before. Once a log reads as PRINTED this way, {@link #confirm} refuses it with 409 instead of flipping it,
 * so what readers already counted as a printed ID never changes afterwards. The row itself stays PENDING in the DB.
 */
@Service
@Transactional
public class IdPrintingService {

    static final Duration DEFAULT_PENDING_CONFIRMATION_GRACE = Duration.ofMinutes(10);

    private final IdTemplateRepository idTemplateRepository;
    private final IdPrintLogRepository idPrintLogRepository;
    private final QrCredentialPort qrCredentialPort;
    private final RegistrationLookupPort registrationLookupPort;
    private final IdTemplateConfigurationService templateConfigurationService;
    private final Duration pendingConfirmationGrace;
    private final Clock clock;

    @Autowired
    public IdPrintingService(IdTemplateRepository idTemplateRepository, IdPrintLogRepository idPrintLogRepository,
                             QrCredentialPort qrCredentialPort, RegistrationLookupPort registrationLookupPort,
                             IdTemplateConfigurationService templateConfigurationService,
                             @Value("${app.id-printing.pending-confirmation-grace:PT10M}") Duration pendingConfirmationGrace) {
        this(idTemplateRepository, idPrintLogRepository, qrCredentialPort, registrationLookupPort,
                templateConfigurationService, pendingConfirmationGrace, Clock.systemUTC());
    }

    IdPrintingService(IdTemplateRepository idTemplateRepository, IdPrintLogRepository idPrintLogRepository,
                      QrCredentialPort qrCredentialPort, RegistrationLookupPort registrationLookupPort,
                      IdTemplateConfigurationService templateConfigurationService, Duration pendingConfirmationGrace,
                      Clock clock) {
        this.idTemplateRepository = idTemplateRepository;
        this.idPrintLogRepository = idPrintLogRepository;
        this.qrCredentialPort = qrCredentialPort;
        this.registrationLookupPort = registrationLookupPort;
        this.templateConfigurationService = templateConfigurationService;
        this.pendingConfirmationGrace = pendingConfirmationGrace == null || pendingConfirmationGrace.isNegative()
                ? DEFAULT_PENDING_CONFIRMATION_GRACE : pendingConfirmationGrace;
        this.clock = clock;
    }

    /**
     * Inserts a PENDING print log for the request. {@code request.staffUserId()} is the authenticated caller and is
     * recorded as printed_by_user_id. Falls back to the event's default template when none is configured.
     *
     * The logged {@code reprint} flag is decided here from history, not taken from the route: it is true exactly when
     * the attendee already has an effectively PRINTED ID, so a print-id after only cancelled/failed attempts is a
     * first print and a stale client calling print-id after a real print still logs a reprint.
     */
    public IdPrintResponse print(IdPrintRequest request) {
        var qr = qrCredentialPort.findById(request.qrCredentialId())
                .orElseThrow(() -> new ResourceNotFoundException("QR credential not found"));
        if (!qr.eventId().equals(request.eventId())) {
            throw new ConflictException("QR credential does not belong to the event");
        }

        var registration = registrationLookupPort.requireById(qr.registrationId());
        if (registration.status() == RegistrationStatus.CANCELLED) {
            throw new ConflictException("Cannot print an ID for a cancelled registration");
        }
        IdTemplate template = templateConfigurationService.getOrCreateActiveTemplate(request.eventId());
        boolean reprint = hasPrintedId(request.eventId(), registration.attendeeUserId());

        IdPrintLog log = new IdPrintLog();
        log.setEventId(request.eventId());
        log.setAttendeeUserId(registration.attendeeUserId());
        log.setRegistrationId(registration.registrationId());
        log.setQrCredentialId(qr.qrCredentialId());
        log.setTemplateId(template.getId());
        log.setPrintedByUserId(request.staffUserId());
        log.setReprint(reprint);
        log.setStatus(IdPrintStatus.PENDING);
        log.setSuccess(false);
        log.setPrintedAt(null);
        log.setMessage(reprint ? "Reprint logged (no physical printer connected)" : "Print logged (no physical printer connected)");
        return toResponse(idPrintLogRepository.save(log));
    }

    /**
     * Records the outcome of a PENDING print. The caller must already have passed the event-level access check;
     * {@code callerMayConfirmAnyLog} is true for the event's organizer and admins, false for staff, who may only
     * confirm logs they created. Only PENDING moves to a final status: 404 if the log is not in this event, 403 for
     * someone else's log, 409 if it is already final or is a PENDING log past the confirmation grace (it already
     * reads as PRINTED).
     */
    public IdPrintResponse confirm(UUID eventId, UUID logId, IdPrintStatus outcome, UUID callerId,
                                   boolean callerMayConfirmAnyLog) {
        if (outcome == null || !outcome.isFinal()) {
            throw new BadRequestException("outcome must be PRINTED, CANCELLED or FAILED");
        }
        idPrintLogRepository.boundLockWaits();
        IdPrintLog log = idPrintLogRepository.findByIdAndEventIdForUpdate(logId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Print log not found"));
        if (!callerMayConfirmAnyLog && (log.getPrintedByUserId() == null || !log.getPrintedByUserId().equals(callerId))) {
            throw new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException(
                    "Only the staff member who started this print can confirm it");
        }
        if (log.getStatus() != IdPrintStatus.PENDING) {
            throw new ConflictException("Print log is already " + log.getStatus());
        }
        if (isStale(log)) {
            throw new ConflictException("Print log is already PRINTED (confirmation window expired)");
        }

        Instant now = clock.instant();
        log.setStatus(outcome);
        log.setConfirmedAt(now);
        switch (outcome) {
            case PRINTED -> {
                log.setSuccess(true);
                log.setPrintedAt(now);
                log.setMessage(log.isReprint() ? "Reprint confirmed" : "Print confirmed");
            }
            case CANCELLED -> {
                log.setSuccess(false);
                log.setPrintedAt(null);
                log.setMessage(log.isReprint() ? "Reprint cancelled" : "Print cancelled");
            }
            default -> {
                log.setSuccess(false);
                log.setPrintedAt(null);
                log.setMessage(log.isReprint() ? "Reprint failed" : "Print failed");
            }
        }
        return toResponse(idPrintLogRepository.save(log));
    }

    /**
     * Every log of the event, including CANCELLED and FAILED ones, each with its effective status. Clients deciding
     * "print vs reprint" must look for status PRINTED (equivalently success=true), not merely for a row.
     */
    @Transactional(readOnly = true)
    public List<IdPrintResponse> findByEvent(UUID eventId) {
        return idPrintLogRepository.findByEventId(eventId).stream().map(this::toResponse).toList();
    }

    /** True when the attendee has at least one effectively PRINTED log for the event (CANCELLED/FAILED ignored). */
    @Transactional(readOnly = true)
    public boolean hasPrintedId(UUID eventId, UUID attendeeUserId) {
        return idPrintLogRepository.existsEffectivelyPrinted(eventId, attendeeUserId,
                clock.instant().minus(pendingConfirmationGrace));
    }

    /** The status readers should see: a PENDING log past the confirmation grace counts as PRINTED (legacy rule). */
    IdPrintStatus effectiveStatus(IdPrintLog log) {
        IdPrintStatus status = log.getStatus() == null ? IdPrintStatus.PRINTED : log.getStatus();
        if (status == IdPrintStatus.PENDING && isStale(log)) {
            return IdPrintStatus.PRINTED;
        }
        return status;
    }

    private boolean isStale(IdPrintLog log) {
        return log.getCreatedAt() != null
                && log.getCreatedAt().isBefore(clock.instant().minus(pendingConfirmationGrace));
    }

    private IdPrintResponse toResponse(IdPrintLog log) {
        IdPrintStatus status = effectiveStatus(log);
        boolean printed = status == IdPrintStatus.PRINTED;
        Instant printedAt = printed ? (log.getPrintedAt() != null ? log.getPrintedAt() : log.getCreatedAt()) : null;
        return new IdPrintResponse(log.getId(), log.getEventId(), log.getAttendeeUserId(), log.getRegistrationId(),
                log.getQrCredentialId(), log.getTemplateId(), log.isReprint(), printed, log.getMessage(), printedAt,
                status, log.getConfirmedAt());
    }

    public List<IdTemplate> listTemplates(UUID eventId) {
        return idTemplateRepository.findByEventId(eventId);
    }

    /** The active template, or the event's default one when none has been configured yet. */
    public IdTemplate getTemplate(UUID eventId) {
        return templateConfigurationService.getOrCreateActiveTemplate(eventId);
    }

    public IdTemplate saveTemplate(UUID eventId, IdTemplateRequest request) {
        idTemplateRepository.boundLockWaits();
        idTemplateRepository.acquireTemplateLock(IdTemplateConfigurationService.TEMPLATE_LOCK_NAMESPACE, eventId.hashCode());
        IdTemplate template = idTemplateRepository.findFirstByEventIdAndActiveTrue(eventId).orElseGet(IdTemplate::new);
        template.setEventId(eventId);
        template.setName(request.name());
        template.setTemplateJson(request.templateJson());
        template.setActive(request.active());
        return idTemplateRepository.save(template);
    }

    /** print-id and reprint-id: both insert a PENDING log; the reprint flag is derived (see {@link #print}). */
    public IdPrintResponse printForAttendee(UUID eventId, UUID attendeeUserId, UUID actorUserId) {
        var registration = findRegistrationForAttendee(eventId, attendeeUserId);
        return print(new IdPrintRequest(eventId, registration.qrCredentialId(), actorUserId));
    }

    /**
     * Prints a sheet that may contain multiple ID cards. One {@code id_print_logs}
     * entry is written per attendee entry (i.e. per printed card), so an 8-up sheet
     * with 8 entries produces 8 individual log rows rather than one per sheet. Each
     * row starts PENDING and is confirmed individually.
     */
    public List<IdPrintResponse> printBatch(UUID eventId, List<UUID> attendeeUserIds, UUID actorUserId) {
        // One registration read for the whole sheet instead of one per card.
        java.util.Map<UUID, RegistrationSnapshot> byAttendee = new java.util.HashMap<>();
        for (RegistrationSnapshot snapshot : registrationLookupPort.listByEventId(eventId)) {
            byAttendee.putIfAbsent(snapshot.attendeeUserId(), snapshot);
        }
        List<RegistrationSnapshot> registrations = attendeeUserIds.stream()
                .map(attendeeUserId -> {
                    RegistrationSnapshot snapshot = byAttendee.get(attendeeUserId);
                    if (snapshot == null) {
                        throw new ResourceNotFoundException("Registration not found for attendee");
                    }
                    return snapshot;
                })
                .toList();
        return registrations.stream()
                .map(registration -> print(new IdPrintRequest(eventId, registration.qrCredentialId(), actorUserId)))
                .toList();
    }

    private RegistrationSnapshot findRegistrationForAttendee(UUID eventId, UUID attendeeUserId) {
        return registrationLookupPort.listByEventId(eventId).stream()
                .filter(item -> item.attendeeUserId().equals(attendeeUserId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found for attendee"));
    }
}
