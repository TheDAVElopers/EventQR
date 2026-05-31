package com.thedavelopers.eventqr.features.uploads.service;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.thedavelopers.eventqr.features.uploads.model.dto.StoredFileResponse;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;

@Service
public class FileStorageService {

    private final Map<UUID, StoredFileRecord> storage = new ConcurrentHashMap<>();

    public StoredFileResponse store(UUID ownerId, String purpose, MultipartFile file) {
        validateFile(file);
        try {
            UUID fileId = UUID.randomUUID();
            StoredFileRecord record = new StoredFileRecord(fileId, ownerId, purpose, file.getOriginalFilename(),
                    file.getContentType(), file.getBytes(), Instant.now());
            storage.put(fileId, record);
            return record.toResponse("STORED");
        } catch (IOException exception) {
            throw new BadRequestException("Unable to read uploaded file");
        }
    }

    public StoredFileResponse find(UUID fileId) {
        return require(fileId).toResponse("AVAILABLE");
    }

    public StoredFileResponse delete(UUID fileId) {
        StoredFileRecord record = require(fileId);
        storage.remove(fileId);
        return record.toResponse("DELETED");
    }

    private StoredFileRecord require(UUID fileId) {
        StoredFileRecord record = storage.get(fileId);
        if (record == null) {
            throw new ResourceNotFoundException("File not found: " + fileId);
        }
        return record;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
    }

    private record StoredFileRecord(UUID fileId, UUID ownerId, String purpose, String fileName,
                                    String contentType, byte[] content, Instant storedAt) {
        StoredFileResponse toResponse(String status) {
            return new StoredFileResponse(fileId, ownerId, purpose, fileName,
                    contentType, content == null ? 0 : content.length, status, storedAt, encode(content));
        }
    }

    @SuppressWarnings("unused")
    private static String encode(byte[] content) {
        return Base64.getEncoder().encodeToString(content == null ? new byte[0] : Arrays.copyOf(content, content.length));
    }
}
