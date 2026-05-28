package com.thedavelopers.eventqr.features.transactions.service;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
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
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.event.TransactionRecordedEvent;
import com.thedavelopers.eventqr.shared.exception.ForbiddenException;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.port.AttendeeDirectoryPort;
import com.thedavelopers.eventqr.shared.port.EventLookupPort;
import com.thedavelopers.eventqr.shared.port.QrCredentialPort;
import com.thedavelopers.eventqr.shared.port.RegistrationCommandPort;
import com.thedavelopers.eventqr.shared.port.RegistrationLookupPort;
import com.thedavelopers.eventqr.shared.port.ScanPurposePort;

@Service
@Transactional
public class TransactionService {

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

    public TransactionService(TransactionLogRepository transactionLogRepository,
                              TransactionRuleRepository transactionRuleRepository,
                              EventLookupPort eventLookupPort,
                              ScanPurposePort scanPurposePort,
                              QrCredentialPort qrCredentialPort,
                              RegistrationLookupPort registrationLookupPort,
                              RegistrationCommandPort registrationCommandPort,
                              AttendeeDirectoryPort attendeeDirectoryPort,
                              EventStaffAssignmentRepository eventStaffAssignmentRepository,
                              ApplicationEventPublisher applicationEventPublisher) {
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
    }

    @Transactional(readOnly = true)
    public ScanVerificationResponse verify(TransactionRequest request) {
        var eventSnapshot = eventLookupPort.requireEvent(request.eventId());
        var purpose = scanPurposePort.requireActive(request.scanPurposeId());
        if (!eventSnapshot.eventId().equals(purpose.eventId())) {
            throw new ForbiddenException("Scan purpose does not belong to the event");
        }
        TransactionRule rule = loadRule(request.eventId(), request.scanPurposeId());
        validateStaff(request.eventId(), request.staffUserId(), rule.isRequiresStaffAssignment());
        var qrSnapshot = qrCredentialPort.findByQrValue(request.qrValue())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid QR credential"));
        if (!eventSnapshot.eventId().equals(qrSnapshot.eventId())) {
            throw new ForbiddenException("Wrong event QR");
        }
        var registration = registrationLookupPort.findByQrCredentialId(qrSnapshot.qrCredentialId())
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found for QR credential"));
        return new ScanVerificationResponse(eventSnapshot.eventId(), registration.attendeeUserId(), registration.registrationId(),
                qrSnapshot.qrCredentialId(), qrSnapshot.qrValue(), registration.attendeeName(), registration.attendeeEmail(),
                registration.status(), purpose.scanPurposeId(), purpose.code(), qrSnapshot.active(),
                "QR credential verified", Instant.now());
    }

    public TransactionResponse record(TransactionRequest request) {
        var eventSnapshot = eventLookupPort.requireEvent(request.eventId());
        if (eventSnapshot.status() == EventStatus.REJECTED || eventSnapshot.status() == EventStatus.CANCELLED) {
            throw new ForbiddenException("Event is not available for scan transactions");
        }

        var purpose = scanPurposePort.requireActive(request.scanPurposeId());
        validateStaff(request.eventId(), request.staffUserId());

        var qrSnapshot = qrCredentialPort.findByQrValue(request.qrValue())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid QR credential"));
        if (!qrSnapshot.active()) {
            return reject(eventSnapshot.eventId(), qrSnapshot.attendeeUserId(), qrSnapshot.registrationId(),
                    qrSnapshot.qrCredentialId(), purpose.scanPurposeId(), request.staffUserId(), "Inactive QR credential",
                    TransactionType.valueOf(purpose.code().name()), 0);
        }
        if (!eventSnapshot.eventId().equals(qrSnapshot.eventId())) {
            return reject(eventSnapshot.eventId(), qrSnapshot.attendeeUserId(), qrSnapshot.registrationId(),
                    qrSnapshot.qrCredentialId(), purpose.scanPurposeId(), request.staffUserId(), "Wrong event QR",
                    TransactionType.valueOf(purpose.code().name()), 0);
        }

        var registration = registrationLookupPort.findByQrCredentialId(qrSnapshot.qrCredentialId())
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found for QR credential"));
    validateStaff(request.eventId(), request.staffUserId(), rule.isRequiresStaffAssignment());

    String duplicateReason = determineDuplicateReason(purpose.code().name(), registration, rule);
        if (duplicateReason != null) {
            return reject(eventSnapshot.eventId(), registration.attendeeUserId(), registration.registrationId(),
                    registration.qrCredentialId(), purpose.scanPurposeId(), request.staffUserId(), duplicateReason,
                    TransactionType.valueOf(purpose.code().name()), 0);
        }

        TransactionType transactionType = TransactionType.valueOf(purpose.code().name());
        int pointsDelta = purpose.trackingOnly() ? 0 : Math.max(0, rule.getPointsAwarded());
        TransactionLog log = createLog(eventSnapshot.eventId(), registration.attendeeUserId(), registration.registrationId(),
                registration.qrCredentialId(), purpose.scanPurposeId(), request.staffUserId(), TransactionResult.APPROVED,
                transactionType, pointsDelta, null);

        applyTransactionEffects(transactionType, registration.registrationId());

        TransactionLog saved = transactionLogRepository.save(log);
        applicationEventPublisher.publishEvent(new TransactionRecordedEvent(saved.getId(), saved.getEventId(),
                saved.getAttendeeUserId(), saved.getRegistrationId(), saved.getQrCredentialId(), saved.getScanPurposeId(),
                saved.getTransactionType(), saved.getTransactionResult(), saved.getPointsDelta(), saved.getStaffUserId(),
                saved.getReason()));
        return toResponse(saved);
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

    private TransactionRule loadRule(UUID eventId, UUID scanPurposeId) {
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
                || (staff.role() != AccountRole.STAFF && staff.role() != AccountRole.ORGANIZER && staff.role() != AccountRole.ADMIN)) {
            throw new ForbiddenException("Staff user is not authorized for this scan");
        }
        if (requiresStaffAssignment && staff.role() == AccountRole.STAFF && !eventStaffAssignmentRepository.existsByEventIdAndStaffUserId(eventId, staffUserId)) {
            throw new ForbiddenException("Staff user is not assigned to this event");
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

    private String determineDuplicateReason(String purposeCode, RegistrationLookupPort.RegistrationSnapshot registration,
                                            TransactionRule rule) {
        List<TransactionLog> history = transactionLogRepository.findByRegistrationIdAndScanPurposeIdOrderByScannedAtDesc(
                registration.registrationId(), rule.getScanPurposeId());
        long approvedUses = history.stream().filter(log -> log.getTransactionResult() == TransactionResult.APPROVED).count();
        if (rule.getMaxUsesPerRegistration() > 0 && approvedUses >= rule.getMaxUsesPerRegistration()) {
            return "Scan limit reached for this registration";
        }
        if (rule.getDuplicateWindowMinutes() > 0 && !history.isEmpty()) {
            Instant latestScan = history.get(0).getScannedAt();
            if (latestScan != null && latestScan.isAfter(Instant.now().minus(Duration.ofMinutes(rule.getDuplicateWindowMinutes())))) {
                return "Duplicate scan is not allowed within the configured window";
            }
        }
        if (rule.isAllowDuplicate()) {
            return null;
        }
        if ("ENTRY".equals(purposeCode) && registration.status() == RegistrationStatus.ENTERED) {
            return "Duplicate entry is not allowed";
        }
        if ("EXIT".equals(purposeCode) && registration.status() == RegistrationStatus.EXITED) {
            return "Duplicate exit is not allowed";
        }
        if ("ATTENDANCE".equals(purposeCode) && registration.attendedAt() != null) {
            return "Duplicate attendance is not allowed";
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

    private TransactionResponse reject(UUID eventId, UUID attendeeUserId, UUID registrationId, UUID qrCredentialId,
                                       UUID scanPurposeId, UUID staffUserId, String reason,
                                       TransactionType transactionType, int pointsDelta) {
        TransactionLog log = createLog(eventId, attendeeUserId, registrationId, qrCredentialId, scanPurposeId, staffUserId,
                TransactionResult.REJECTED, transactionType, pointsDelta, reason);
        TransactionLog saved = transactionLogRepository.save(log);
        applicationEventPublisher.publishEvent(new TransactionRecordedEvent(saved.getId(), saved.getEventId(),
                saved.getAttendeeUserId(), saved.getRegistrationId(), saved.getQrCredentialId(), saved.getScanPurposeId(),
                saved.getTransactionType(), saved.getTransactionResult(), saved.getPointsDelta(), saved.getStaffUserId(),
                saved.getReason()));
        return toResponse(saved);
    }

    private TransactionLog createLog(UUID eventId, UUID attendeeUserId, UUID registrationId, UUID qrCredentialId,
                                     UUID scanPurposeId, UUID staffUserId, TransactionResult result,
                                     TransactionType transactionType, int pointsDelta, String reason) {
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
        log.setScannedAt(Instant.now());
        return log;
    }

    private TransactionResponse toResponse(TransactionLog log) {
        String eventTitle = eventLookupPort.findById(log.getEventId()).map(EventLookupPort.EventSnapshot::title).orElse(null);
        return new TransactionResponse(log.getId(), log.getEventId(), log.getAttendeeUserId(), log.getRegistrationId(),
                log.getQrCredentialId(), log.getScanPurposeId(), log.getTransactionType(), log.getTransactionResult(),
            log.getPointsDelta(), log.getReason(), log.getScannedAt(), eventTitle);
    }
}