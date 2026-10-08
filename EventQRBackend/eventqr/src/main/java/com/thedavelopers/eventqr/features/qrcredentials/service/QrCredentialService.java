package com.thedavelopers.eventqr.features.qrcredentials.service;

import java.util.Optional;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.qrcredentials.model.entity.QrCredential;
import com.thedavelopers.eventqr.features.qrcredentials.repository.QrCredentialRepository;
import com.thedavelopers.eventqr.shared.constants.QrDeliveryStatus;
import com.thedavelopers.eventqr.shared.constants.QrDisplayStatus;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort;
import com.thedavelopers.eventqr.shared.interfaces.QrCredentialPort.QrCredentialSnapshot;
import com.thedavelopers.eventqr.shared.utils.QrValueGenerator;

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
    public QrCredentialSnapshot reissueCredential(UUID eventId, UUID attendeeUserId, UUID registrationId, String attendeeEmail) {
        QrCredential existing = qrCredentialRepository.findByRegistrationId(registrationId).orElse(null);
        if (existing == null) {
            return issueCredential(eventId, attendeeUserId, registrationId, attendeeEmail);
        }
        existing.setQrValue(generateUniqueQrValue());
        existing.setDisplayStatus(QrDisplayStatus.PENDING);
        existing.setDeliveryStatus(QrDeliveryStatus.QUEUED);
        existing.setDownloaded(false);
        existing.setActive(true);
        return qrCredentialRepository.save(existing).toSnapshot();
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
    public byte[] renderQrImage(String qrValue) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(qrValue, BarcodeFormat.QR_CODE, 320, 320,
                    Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name()));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("QR image could not be generated", exception);
        }
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

    @Override
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

    @Override
    public QrCredentialSnapshot markEmailSent(UUID qrCredentialId) {
        QrCredential qrCredential = load(qrCredentialId);
        qrCredential.setDeliveryStatus(QrDeliveryStatus.SENT);
        return qrCredentialRepository.save(qrCredential).toSnapshot();
    }

    @Override
    public QrCredentialSnapshot markEmailFailed(UUID qrCredentialId) {
        QrCredential qrCredential = load(qrCredentialId);
        qrCredential.setDeliveryStatus(QrDeliveryStatus.FAILED);
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
