package com.thedavelopers.eventqr.features.scanpurposes.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeRequest;
import com.thedavelopers.eventqr.features.scanpurposes.model.dto.ScanPurposeResponse;
import com.thedavelopers.eventqr.features.scanpurposes.model.entity.ScanPurpose;
import com.thedavelopers.eventqr.features.scanpurposes.repository.ScanPurposeRepository;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.port.ScanPurposePort;
import com.thedavelopers.eventqr.shared.port.ScanPurposePort.ScanPurposeSnapshot;

@Service
@Transactional
public class ScanPurposeService implements ScanPurposePort {

    private final ScanPurposeRepository scanPurposeRepository;

    public ScanPurposeService(ScanPurposeRepository scanPurposeRepository) {
        this.scanPurposeRepository = scanPurposeRepository;
    }

    public ScanPurposeResponse create(ScanPurposeRequest request) {
        ScanPurpose scanPurpose = new ScanPurpose();
        scanPurpose.setEventId(request.eventId());
        scanPurpose.setName(request.name());
        scanPurpose.setCode(request.code());
        scanPurpose.setActive(request.active());
        scanPurpose.setTrackingOnly(request.trackingOnly());
        scanPurpose.setDescription(request.description());
        return toResponse(scanPurposeRepository.save(scanPurpose));
    }

    public List<ScanPurposeResponse> findByEventId(UUID eventId) {
        return scanPurposeRepository.findByEventId(eventId).stream().map(this::toResponse).toList();
    }

    @Override
    public ScanPurposeSnapshot requireActive(UUID scanPurposeId) {
        ScanPurpose scanPurpose = scanPurposeRepository.findById(scanPurposeId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan purpose not found: " + scanPurposeId));
        if (!scanPurpose.isActive()) {
            throw new ResourceNotFoundException("Scan purpose is inactive: " + scanPurposeId);
        }
        return scanPurpose.toSnapshot();
    }

    @Override
    public java.util.Optional<ScanPurposeSnapshot> findById(UUID scanPurposeId) {
        return scanPurposeRepository.findById(scanPurposeId).map(ScanPurpose::toSnapshot);
    }

    @Override
    public List<ScanPurposeSnapshot> listByEventId(UUID eventId) {
        return scanPurposeRepository.findByEventId(eventId).stream().map(ScanPurpose::toSnapshot).toList();
    }

    private ScanPurposeResponse toResponse(ScanPurpose scanPurpose) {
        return new ScanPurposeResponse(scanPurpose.getId(), scanPurpose.getEventId(), scanPurpose.getName(),
                scanPurpose.getCode(), scanPurpose.isActive(), scanPurpose.isTrackingOnly(), scanPurpose.getDescription());
    }
}