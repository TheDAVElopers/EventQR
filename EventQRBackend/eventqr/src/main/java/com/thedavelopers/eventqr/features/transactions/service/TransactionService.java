package com.thedavelopers.eventqr.features.transactions.service;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest;
import com.thedavelopers.eventqr.features.transactions.model.dto.ScanVerificationResponse;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse;
import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionLog;
import com.thedavelopers.eventqr.features.transactions.model.entity.TransactionRule;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionRuleRepository;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountRoles;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.interfaces.TransactionRecordedEvent;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.interfaces.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.shared.interfaces.EventLookupPort;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationCommandPort;
import com.thedavelopers.eventqr.shared.interfaces.RegistrationLookupPort;
import com.thedavelopers.eventqr.shared.interfaces.ScanPurposePort;

@Service
@Transactional
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final String DEFAULT_METADATA = "{}";

    private final TransactionLogRepository transactionLogRepository;
    private final TransactionRuleRepository transactionRuleRepository;
    private final EventLookupPort eventLookupPort;
    private final ScanPurposePort scanPurposePort;
    private final QrCredentialPort qrCredentialPort;
    private final RegistrationLookupPort registrationLookupPort;
    private final RegistrationCommandPort registrationCommandPort;
    private final AttendeeDirectoryPort attendeeDirectoryPort;
    private final EventStaffAssignmentRepository eventStaffAssignmentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Business timezone for "today" boundaries; configurable via app.timezone (default Asia/Manila). */
    private final ZoneId businessZone;

    public TransactionService(TransactionLogRepository transactionLogRepository,
                              TransactionRuleRepository transactionRuleRepository,
                              EventLookupPort eventLookupPort,
                              ScanPurposePort scanPurposePort,
                              QrCredentialPort qrCredentialPort,
                              RegistrationLookupPort registrationLookupPort,
                              RegistrationCommandPort registrationCommandPort,
                              AttendeeDirectoryPort attendeeDirectoryPort,
                              EventStaffAssignmentRepository eventStaffAssignmentRepository,
                              ApplicationEventPublisher applicationEventPublisher,
                              @Value("${app.timezone:Asia/Manila}") String timezone) {
        this.transactionLogRepository = transactionLogRepository;
        this.transactionRuleRepository = transactionRuleRepository;
        this.eventLookupPort = eventLookupPort;
        this.scanPurposePort = scanPurposePort;
        this.qrCredentialPort = qrCredentialPort;
        this.registrationLookupPort = registrationLookupPort;
        this.registrationCommandPort = registrationCommandPort;
        this.attendeeDirectoryPort = attendeeDirectoryPort;
        this.eventStaffAssignmentRepository = eventStaffAssignmentRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.businessZone = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public ScanVerificationResponse verify(TransactionRequest request) {
        var eventSnapshot = eventLookupPort.requireEvent(request.eventId());
        if (eventSnapshot.status() == EventStatus.ENDED) {
            throw new ForbiddenException("Event has ended. Scanning is disabled.");
        }
        var purpose = scanPurposePort.requireActive(request.scanPurposeId());
        if (!eventSnapshot.eventId().equals(purpose.eventId())) {
            throw new ForbiddenException("Scan purpose does not belong to the event");
        }
        TransactionRule rule = loadRule(request.eventId(), request.scanPurposeId());
        validateStaff(request.eventId(), request.staffUserId(), rule.isRequiresStaffAssignment());

        // Resolve QR credential — either by short Attendee ID or by raw QR value
        QrCredentialPort.QrCredentialSnapshot qrSnapshot;
        if (request.hasShortId()) {
            Integer regNum = request.parsedShortId();
            var registration = registrationLookupPort.findByEventIdAndRegistrationNumber(eventSnapshot.eventId(), regNum)
                    .orElseThrow(() -> new ResourceNotFoundException("Attendee ID #" + regNum + " not found for this event"));
            if (registration.qrCredentialId() == null) {
                throw new ResourceNotFoundException("No QR credential assigned to Attendee ID #" + regNum);
            }
            qrSnapshot = qrCredentialPort.findById(registration.qrCredentialId())
                    .orElseThrow(() -> new ResourceNotFoundException("QR credential not found for Attendee ID #" + regNum));
            if (!eventSnapshot.eventId().equals(qrSnapshot.eventId())) {
                throw new ForbiddenException("Wrong event QR");
            }
            if (isNotScannable(registration.status())) {
                throw new ForbiddenException("Registration is not active");
            }
            return new ScanVerificationResponse(eventSnapshot.eventId(), registration.attendeeUserId(), registration.registrationId(),
                    qrSnapshot.qrCredentialId(), qrSnapshot.qrValue(), registration.attendeeName(), registration.attendeeEmail(),
                    registration.status(), purpose.scanPurposeId(), purpose.code(), qrSnapshot.active(),
                    "Attendee ID #" + regNum + " verified", Instant.now());
        }

        if (request.qrValue() == null || request.qrValue().isBlank()) {
            throw new ResourceNotFoundException("QR value or Attendee ID is required");
        }
        qrSnapshot = qrCredentialPort.findByQrValue(request.qrValue())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid QR credential"));
        if (!qrSnapshot.active()) {
            throw new ForbiddenException("Inactive QR credential");
        }
        if (!eventSnapshot.eventId().equals(qrSnapshot.eventId())) {
            throw new ForbiddenException("Wrong event QR");
        }
        var registration = registrationLookupPort.findByQrCredentialId(qrSnapshot.qrCredentialId())
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found for QR credential"));
        if (!eventSnapshot.eventId().equals(registration.eventId())) {
            throw new ForbiddenException("Registration does not belong to selected event");
        }
        if (isNotScannable(registration.status())) {
            throw new ForbiddenException("Registration is not active");
        }
        return new ScanVerificationResponse(eventSnapshot.eventId(), registration.attendeeUserId(), registration.registrationId(),
                qrSnapshot.qrCredentialId(), qrSnapshot.qrValue(), registration.attendeeName(), registration.attendeeEmail(),
                registration.status(), purpose.scanPurposeId(), purpose.code(), qrSnapshot.active(),
                "QR credential verified", Instant.now());
    }

    @CacheEvict(cacheNames = "transaction-rules", key = "#request.eventId()")
    public TransactionResponse record(TransactionRequest request) {
        UUID clientRequestId = request.clientRequestId();
        if (clientRequestId == null) {
            return recordNew(request);
        }
        // Retry of a scan the server may already have logged (e.g. the response was lost):
        // return the original outcome, approved or rejected, without logging it again.
        var existing = transactionLogRepository.findByClientRequestId(clientRequestId);
        if (existing.isPresent()) {
            if (!existing.get().getEventId().equals(request.eventId())) {
                throw new BadRequestException("clientRequestId was already used for a different event");
            }
            return toResponse(existing.get());
        }
        TransactionResponse response = recordNew(request);
        transactionLogRepository.findById(response.transactionId())
                .ifPresent(saved -> saved.setClientRequestId(clientRequestId));
        return response;
    }

    // Package-private so tests can exercise the idempotency wrapper without the full scan chain.
    TransactionResponse recordNew(TransactionRequest request) {
        var eventSnapshot = eventLookupPort.requireEvent(request.eventId());
        if (eventSnapshot.status() == EventStatus.ENDED) {
            throw new ForbiddenException("Event has ended. Scanning is disabled.");
        }
        if (eventSnapshot.status() == EventStatus.REJECTED || eventSnapshot.status() == EventStatus.CANCELLED) {
            throw new ForbiddenException("Event is not available for scan transactions");
        }

        var purpose = scanPurposePort.requireActive(request.scanPurposeId());
        if (!eventSnapshot.eventId().equals(purpose.eventId())) {
            throw new ForbiddenException("Scan purpose does not belong to the event");
        }
        TransactionRule rule = loadRule(request.eventId(), request.scanPurposeId());
        validateStaff(request.eventId(), request.staffUserId(), rule.isRequiresStaffAssignment());

        var qrSnapshot = qrCredentialPort.findByQrValue(request.qrValue())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid QR credential"));
        TransactionType transactionType = resolveTransactionType(purpose.code().name());
        if (!qrSnapshot.active()) {
            return reject(eventSnapshot.eventId(), qrSnapshot.attendeeUserId(), qrSnapshot.registrationId(),
                    qrSnapshot.qrCredentialId(), purpose.scanPurposeId(), request.staffUserId(), "Inactive QR credential",
                    transactionType, 0, request.notes(), request.qrValue(), purpose.code().name(), purpose.name());
        }
        if (!eventSnapshot.eventId().equals(qrSnapshot.eventId())) {
            return reject(eventSnapshot.eventId(), qrSnapshot.attendeeUserId(), qrSnapshot.registrationId(),
                    qrSnapshot.qrCredentialId(), purpose.scanPurposeId(), request.staffUserId(), "Wrong event QR",
                    transactionType, 0, request.notes(), request.qrValue(), purpose.code().name(), purpose.name());
        }

        var registration = registrationLookupPort.findByQrCredentialId(qrSnapshot.qrCredentialId())
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found for QR credential"));
        if (!eventSnapshot.eventId().equals(registration.eventId())) {
            return reject(eventSnapshot.eventId(), registration.attendeeUserId(), registration.registrationId(),
                registration.qrCredentialId(), purpose.scanPurposeId(), request.staffUserId(),
                "Registration does not belong to selected event",
                transactionType, 0, request.notes(), request.qrValue(), purpose.code().name(), purpose.name());
        }

        if (isNotScannable(registration.status())) {
            return reject(eventSnapshot.eventId(), registration.attendeeUserId(), registration.registrationId(),
                registration.qrCredentialId(), purpose.scanPurposeId(), request.staffUserId(),
                "Registration is not active",
                transactionType, 0, request.notes(), request.qrValue(), purpose.code().name(), purpose.name());
        }

        boolean rewardRedemptionScan = purpose.code() == ScanPurposeCode.REWARD_REDEMPTION_SCAN;
        String duplicateReason = rewardRedemptionScan ? null : determineDuplicateReason(registration, rule);
        if (duplicateReason != null) {
            return reject(eventSnapshot.eventId(), registration.attendeeUserId(), registration.registrationId(),
                    registration.qrCredentialId(), purpose.scanPurposeId(), request.staffUserId(), duplicateReason,
                transactionType, 0, request.notes(), request.qrValue(), purpose.code().name(), purpose.name());
        }

        int pointsDelta = purpose.trackingOnly() ? 0 : Math.max(0, rule.getPointsAwarded());
        String metadata = buildMetadata(request.qrValue(), purpose.code().name(), purpose.name(), request.notes(), "staff-scan");
        log.debug("Transaction save request eventId={} registrationId={} qrCredentialId={} scanPurposeId={} staffUserId={} transactionType={} metadata={}",
                eventSnapshot.eventId(), registration.registrationId(), registration.qrCredentialId(), purpose.scanPurposeId(),
                request.staffUserId(), transactionType, metadata);
        TransactionLog transactionLog = createLog(eventSnapshot.eventId(), registration.attendeeUserId(), registration.registrationId(),
                registration.qrCredentialId(), purpose.scanPurposeId(), request.staffUserId(), TransactionResult.APPROVED,
                transactionType, pointsDelta, null, metadata);

        applyTransactionEffects(transactionType, registration.registrationId());

        TransactionLog saved = transactionLogRepository.save(transactionLog);
        log.debug("Transaction saved transactionLogId={} eventId={} scanPurposeId={} result={}",
                saved.getId(), saved.getEventId(), saved.getScanPurposeId(), saved.getTransactionResult());
        applicationEventPublisher.publishEvent(new TransactionRecordedEvent(saved.getId(), saved.getEventId(),
                saved.getAttendeeUserId(), saved.getRegistrationId(), saved.getQrCredentialId(), saved.getScanPurposeId(),
                saved.getTransactionType(), saved.getTransactionResult(), saved.getPointsDelta(), saved.getStaffUserId(),
                saved.getReason()));
        return toResponse(saved);
    }

    private static boolean isNotScannable(RegistrationStatus status) {
        return status == RegistrationStatus.CANCELLED || status == RegistrationStatus.NO_SHOW;
    }

    @Transactional(readOnly = true)
    public TransactionResponse latest(UUID eventId) {
        TransactionLog log = transactionLogRepository.findFirstByEventIdOrderByScannedAtDesc(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No transactions found for event"));
        return toResponse(log);
    }

    public List<TransactionResponse> findByEvent(UUID eventId) {
        return transactionLogRepository.findByEventId(eventId).stream().map(this::toResponse).toList();
    }

    public Page<TransactionResponse> findByEvent(UUID eventId, Pageable pageable) {
        return transactionLogRepository.findByEventId(eventId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findByEventToday(UUID eventId) {
        Instant startOfToday = LocalDate.now(businessZone).atStartOfDay(businessZone).toInstant();
        return transactionLogRepository.findByEventIdAndScannedAtGreaterThanEqual(eventId, startOfToday)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findByAttendee(UUID attendeeUserId) {
        return transactionLogRepository.findByAttendeeUserId(attendeeUserId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findByEventAndAttendee(UUID eventId, UUID attendeeUserId) {
        return transactionLogRepository.findByEventId(eventId).stream()
                .filter(log -> log.getAttendeeUserId().equals(attendeeUserId))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findForStaff(UUID staffUserId, UUID eventId, UUID scanPurposeId) {
        List<TransactionLog> logs;
        if (eventId != null && scanPurposeId != null) {
            logs = transactionLogRepository.findByStaffUserIdAndEventIdAndScanPurposeIdOrderByScannedAtDesc(staffUserId, eventId, scanPurposeId);
        } else if (eventId != null) {
            logs = transactionLogRepository.findByStaffUserIdAndEventIdOrderByScannedAtDesc(staffUserId, eventId);
        } else if (scanPurposeId != null) {
            logs = transactionLogRepository.findByStaffUserIdAndScanPurposeIdOrderByScannedAtDesc(staffUserId, scanPurposeId);
        } else {
            logs = transactionLogRepository.findByStaffUserIdOrderByScannedAtDesc(staffUserId);
        }
        return logs.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findForStaffToday(UUID staffUserId) {
        Instant startOfToday = LocalDate.now(businessZone).atStartOfDay(businessZone).toInstant();
        return transactionLogRepository.findByStaffUserIdAndScannedAtGreaterThanEqual(staffUserId, startOfToday)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findRecentByEventAndAttendee(UUID eventId, UUID attendeeUserId, int limit) {
        return transactionLogRepository.findTop5ByEventIdAndAttendeeUserIdOrderByScannedAtDesc(eventId, attendeeUserId)
                .stream()
                .limit(limit)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponse findOne(UUID transactionId) {
        TransactionLog log = transactionLogRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        return toResponse(log);
    }

    @Transactional(readOnly = true)
    public TransactionResponse findOneForEvent(UUID eventId, UUID transactionId) {
        TransactionLog log = transactionLogRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (!log.getEventId().equals(eventId)) {
            throw new ResourceNotFoundException("Transaction not found for event");
        }
        return toResponse(log);
    }

    @Cacheable(cacheNames = "transaction-rules", key = "#eventId + ':' + #scanPurposeId")
    public TransactionRule loadRule(UUID eventId, UUID scanPurposeId) {
        return transactionRuleRepository.findByEventIdAndScanPurposeId(eventId, scanPurposeId)
                .orElseGet(() -> defaultRule(eventId, scanPurposeId));
    }

    private void validateStaff(UUID eventId, UUID staffUserId, boolean requiresStaffAssignment) {
        if (staffUserId == null) {
            throw new ForbiddenException("Staff user is required for scan transactions");
        }
        var staff = attendeeDirectoryPort.findById(staffUserId)
                .orElseThrow(() -> new ForbiddenException("Staff user not found"));
        if (staff.status() != com.thedavelopers.eventqr.shared.constants.AccountStatus.ACTIVE
                || !AccountRoles.isAtLeast(staff.role(), AccountRole.STAFF)) {
            throw new ForbiddenException("Staff user is not authorized for this scan");
        }
        if (requiresStaffAssignment && staff.role() == AccountRole.STAFF) {
            var assignment = eventStaffAssignmentRepository.findByEventIdAndStaffUserIdAndActiveTrue(eventId, staffUserId)
                    .orElseThrow(() -> new ForbiddenException("Staff user is not assigned to this event"));
            if (!assignment.isCanScan()) {
                throw new ForbiddenException("Staff user is not allowed to scan for this event");
            }
        }
    }

    private TransactionRule defaultRule(UUID eventId, UUID scanPurposeId) {
        TransactionRule rule = new TransactionRule();
        rule.setEventId(eventId);
        rule.setScanPurposeId(scanPurposeId);
        rule.setActive(true);
        rule.setAllowDuplicate(false);
        rule.setDuplicateWindowMinutes(0);
        rule.setMaxUsesPerRegistration(1);
        rule.setRequiresStaffAssignment(true);
        rule.setPointsAwarded(0);
        return rule;
    }

    private String determineDuplicateReason(RegistrationLookupPort.RegistrationSnapshot registration,
                                            TransactionRule rule) {
        List<TransactionLog> history = transactionLogRepository.findByRegistrationIdAndScanPurposeIdOrderByScannedAtDesc(
                registration.registrationId(), rule.getScanPurposeId());
        List<TransactionLog> approvedHistory = history.stream()
                .filter(log -> log.getTransactionResult() == TransactionResult.APPROVED)
                .toList();
        long approvedUses = approvedHistory.size();
        if (approvedUses == 0) {
            return null;
        }
        if (!rule.isAllowDuplicate()) {
            return "Duplicate scan is not allowed for this scan purpose";
        }
        if (rule.getMaxUsesPerRegistration() > 0 && approvedUses >= rule.getMaxUsesPerRegistration()) {
            return "Scan limit reached for this scan purpose";
        }
        if (rule.getDuplicateWindowMinutes() > 0) {
            Instant latestScan = approvedHistory.get(0).getScannedAt();
            if (latestScan != null && latestScan.isAfter(Instant.now().minus(Duration.ofMinutes(rule.getDuplicateWindowMinutes())))) {
                return "Duplicate scan is not allowed within the configured window";
            }
        }
        return null;
    }

    private void applyTransactionEffects(TransactionType transactionType, UUID registrationId) {
        if (transactionType == TransactionType.ENTRY) {
            registrationCommandPort.markEntered(registrationId);
            return;
        }
        if (transactionType == TransactionType.EXIT) {
            registrationCommandPort.markExited(registrationId);
            return;
        }
        if (transactionType == TransactionType.ATTENDANCE) {
            registrationCommandPort.markAttended(registrationId);
        }
    }

    private TransactionType resolveTransactionType(String scanPurposeCode) {
        if ("REGISTRATION_LOOKUP".equals(scanPurposeCode)) {
            return TransactionType.REGISTRATION;
        }
        return TransactionType.valueOf(scanPurposeCode);
    }

    private TransactionResponse reject(UUID eventId, UUID attendeeUserId, UUID registrationId, UUID qrCredentialId,
                                       UUID scanPurposeId, UUID staffUserId, String reason,
                                       TransactionType transactionType, int pointsDelta, String notes, String qrValue,
                                       String scanPurposeCode, String scanPurposeLabel) {
        String metadata = buildMetadata(qrValue, scanPurposeCode, scanPurposeLabel, notes, "staff-scan");
        log.debug("Transaction reject save request eventId={} registrationId={} qrCredentialId={} scanPurposeId={} staffUserId={} transactionType={} metadata={} reason={}",
                eventId, registrationId, qrCredentialId, scanPurposeId, staffUserId, transactionType, metadata, reason);
        TransactionLog transactionLog = createLog(eventId, attendeeUserId, registrationId, qrCredentialId, scanPurposeId, staffUserId,
                TransactionResult.REJECTED, transactionType, pointsDelta, reason, metadata);
        TransactionLog saved = transactionLogRepository.save(transactionLog);
        log.debug("Transaction saved transactionLogId={} eventId={} scanPurposeId={} result={}",
                saved.getId(), saved.getEventId(), saved.getScanPurposeId(), saved.getTransactionResult());
        applicationEventPublisher.publishEvent(new TransactionRecordedEvent(saved.getId(), saved.getEventId(),
                saved.getAttendeeUserId(), saved.getRegistrationId(), saved.getQrCredentialId(), saved.getScanPurposeId(),
                saved.getTransactionType(), saved.getTransactionResult(), saved.getPointsDelta(), saved.getStaffUserId(),
                saved.getReason()));
        return toResponse(saved);
    }

    private TransactionLog createLog(UUID eventId, UUID attendeeUserId, UUID registrationId, UUID qrCredentialId,
                                     UUID scanPurposeId, UUID staffUserId, TransactionResult result,
                                     TransactionType transactionType, int pointsDelta, String reason, String metadata) {
        TransactionLog log = new TransactionLog();
        log.setEventId(eventId);
        log.setAttendeeUserId(attendeeUserId);
        log.setRegistrationId(registrationId);
        log.setQrCredentialId(qrCredentialId);
        log.setScanPurposeId(scanPurposeId);
        log.setStaffUserId(staffUserId);
        log.setTransactionType(transactionType);
        log.setTransactionResult(result);
        log.setPointsDelta(pointsDelta);
        log.setReason(reason);
        log.setMetadata(metadata == null || metadata.isBlank() ? DEFAULT_METADATA : metadata);
        log.setScannedAt(Instant.now());
        return log;
    }

    private String buildMetadata(String qrValue, String scanPurposeCode, String scanPurposeLabel,
                                 String notes, String source) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("qrValue", qrValue);
        payload.put("scanPurposeCode", scanPurposeCode);
        payload.put("scanPurposeLabel", scanPurposeLabel);
        payload.put("source", source);
        if (notes != null && !notes.isBlank()) {
            payload.put("notes", notes);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.warn("Unable to serialize transaction metadata; using default", exception);
            return DEFAULT_METADATA;
        }
    }

    private TransactionResponse toResponse(TransactionLog log) {
        String eventTitle = eventLookupPort.findById(log.getEventId())
                .map(EventLookupPort.EventSnapshot::title)
                .orElse(null);
        String attendeeName = attendeeDirectoryPort.findById(log.getAttendeeUserId())
                .map(AttendeeDirectoryPort.AttendeeSnapshot::fullName)
                .orElse(null);
        String registrationStatus = registrationLookupPort.findById(log.getRegistrationId())
                .map(snapshot -> snapshot.status().name())
                .orElse(null);
        String scanPurposeName = scanPurposePort.findById(log.getScanPurposeId())
                .map(ScanPurposePort.ScanPurposeSnapshot::name)
                .orElse(null);
        return new TransactionResponse(log.getId(), log.getEventId(), eventTitle, log.getAttendeeUserId(), attendeeName,
                log.getRegistrationId(), registrationStatus, log.getQrCredentialId(), log.getScanPurposeId(), scanPurposeName,
                log.getTransactionType(), log.getTransactionResult(), log.getPointsDelta(), log.getReason(), log.getScannedAt());
    }
}