package com.thedavelopers.eventqr.features.qrcredentials.model.entity;

import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.QrDeliveryStatus;
import com.thedavelopers.eventqr.shared.constants.QrDisplayStatus;
import com.thedavelopers.eventqr.shared.entity.BaseEntity;
import com.thedavelopers.eventqr.shared.port.QrCredentialPort.QrCredentialSnapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "qr_credentials")
public class QrCredential extends BaseEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String qrValue;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID attendeeUserId;

    @Column(nullable = false, unique = true)
    private UUID registrationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QrDisplayStatus displayStatus = QrDisplayStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QrDeliveryStatus deliveryStatus = QrDeliveryStatus.PENDING;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean downloaded;

    public QrCredentialSnapshot toSnapshot() {
        return new QrCredentialSnapshot(getId(), eventId, attendeeUserId, registrationId, qrValue, active,
                displayStatus, deliveryStatus, downloaded);
    }
}