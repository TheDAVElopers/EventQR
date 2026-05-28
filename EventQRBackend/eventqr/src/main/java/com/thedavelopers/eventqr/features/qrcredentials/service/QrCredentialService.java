package com.thedavelopers.eventqr.features.qrcredentials.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.qrcredentials.model.entity.QrCredential;
import com.thedavelopers.eventqr.features.qrcredentials.repository.QrCredentialRepository;
import com.thedavelopers.eventqr.shared.constants.QrDeliveryStatus;
import com.thedavelopers.eventqr.shared.constants.QrDisplayStatus;
import com.thedavelopers.eventqr.shared.exception.ConflictException;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.port.QrCredentialPort;
import com.thedavelopers.eventqr.shared.port.QrCredentialPort.QrCredentialSnapshot;
import com.thedavelopers.eventqr.shared.util.QrValueGenerator;

@Service
@Transactional
public class QrCredentialService implements QrCredentialPort {

    private final QrCredentialRepository qrCredentialRepository;

    public QrCredentialService(QrCredentialRepository qrCredentialRepository) {
        this.qrCredentialRepository = qrCredentialRepository;
    }

    @Override
    public QrCredentialSnapshot issueCredential(UUID eventId, UUID attendeeUserId, UUID registrationId, String attendeeEmail) {
        if (qrCredentialRepository.findByRegistrationId(registrationId).isPresent()) {
            throw new ConflictException("QR credential already issued for registration " + registrationId);
        }
        QrCredential qrCredential = new QrCredential();
        qrCredential.setEventId(eventId);
        qrCredential.setAttendeeUserId(attendeeUserId);
        qrCredential.setRegistrationId(registrationId);
        qrCredential.setQrValue(generateUniqueQrValue());
        qrCredential.setDisplayStatus(QrDisplayStatus.PENDING);
        qrCredential.setDeliveryStatus(QrDeliveryStatus.QUEUED);
        qrCredential.setDownloaded(false);
        qrCredential.setActive(true);
        return qrCredentialRepository.save(qrCredential).toSnapshot();
    }

    @Override
    public QrCredentialSnapshot issueOrReturnExisting(UUID eventId, UUID attendeeUserId, UUID registrationId, String attendeeEmail) {
        return qrCredentialRepository.findByRegistrationId(registrationId)
                .map(QrCredential::toSnapshot)
                .orElseGet(() -> issueCredential(eventId, attendeeUserId, registrationId, attendeeEmail));
    }

    @Override
    public Optional<QrCredentialSnapshot> findById(UUID qrCredentialId) {
        return qrCredentialRepository.findById(qrCredentialId).map(QrCredential::toSnapshot);
    }

    @Override
    public Optional<QrCredentialSnapshot> findByRegistrationId(UUID registrationId) {
        return qrCredentialRepository.findByRegistrationId(registrationId).map(QrCredential::toSnapshot);
    }

    @Override
    public Optional<QrCredentialSnapshot> findByQrValue(String qrValue) {
        return qrCredentialRepository.findByQrValueIgnoreCase(qrValue).map(QrCredential::toSnapshot);
    }

    @Override
    public QrCredentialSnapshot markDisplayedOnce(UUID qrCredentialId) {
        QrCredential qrCredential = load(qrCredentialId);
        if (qrCredential.getDisplayStatus() != QrDisplayStatus.SHOWN_ONCE) {
            qrCredential.setDisplayStatus(QrDisplayStatus.SHOWN_ONCE);
            qrCredential = qrCredentialRepository.save(qrCredential);
        }
        return qrCredential.toSnapshot();
    }

    @Override
    public QrCredentialSnapshot markDownloaded(UUID qrCredentialId) {
        QrCredential qrCredential = load(qrCredentialId);
        qrCredential.setDownloaded(true);
        return qrCredentialRepository.save(qrCredential).toSnapshot();
    }

    public QrCredentialSnapshot deactivate(UUID qrCredentialId) {
        QrCredential qrCredential = load(qrCredentialId);
        qrCredential.setActive(false);
        return qrCredentialRepository.save(qrCredential).toSnapshot();
    }

    @Override
    public QrCredentialSnapshot markEmailQueued(UUID qrCredentialId) {
        QrCredential qrCredential = load(qrCredentialId);
        qrCredential.setDeliveryStatus(QrDeliveryStatus.QUEUED);
        return qrCredentialRepository.save(qrCredential).toSnapshot();
    }

    private QrCredential load(UUID qrCredentialId) {
        return qrCredentialRepository.findById(qrCredentialId)
                .orElseThrow(() -> new ResourceNotFoundException("QR credential not found: " + qrCredentialId));
    }

    private String generateUniqueQrValue() {
        String candidate = QrValueGenerator.generate();
        while (qrCredentialRepository.findByQrValueIgnoreCase(candidate).isPresent()) {
            candidate = QrValueGenerator.generate();
        }
        return candidate;
    }
}