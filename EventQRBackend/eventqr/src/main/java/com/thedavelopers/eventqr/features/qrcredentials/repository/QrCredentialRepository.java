package com.thedavelopers.eventqr.features.qrcredentials.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.qrcredentials.model.entity.QrCredential;

public interface QrCredentialRepository extends JpaRepository<QrCredential, UUID> {

    Optional<QrCredential> findByRegistrationId(UUID registrationId);

    Optional<QrCredential> findByQrValueIgnoreCase(String qrValue);
}