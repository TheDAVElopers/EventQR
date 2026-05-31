package com.thedavelopers.eventqr.features.uploads.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.thedavelopers.eventqr.features.uploads.model.dto.StoredFileResponse;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;

@Service
public class FileStorageService {

    private final Path storageRoot;

    public FileStorageService(@Value("${eventqr.upload-dir:uploads}") String uploadDir) {
        this.storageRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize upload directory", exception);
        }
    }

    public StoredFileResponse store(UUID ownerId, String purpose, MultipartFile file) {
        validateFile(file);
        try {
            UUID fileId = UUID.randomUUID();
            byte[] content = file.getBytes();
            StoredFileRecord record = new StoredFileRecord(
                    fileId,
                    ownerId,
                    purpose,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    content.length,
                    Instant.now()
            );
            Files.write(filePath(fileId), content, StandardOpenOption.CREATE_NEW);
            return record.toResponse("STORED", content);
        } catch (IOException exception) {
            throw new BadRequestException("Unable to read uploaded file");
        }
    }

    public StoredFileResponse find(UUID fileId) {
        try {
            Path path = filePath(fileId);
            if (!Files.exists(path)) {
                throw new ResourceNotFoundException("File not found: " + fileId);
            }
            byte[] content = Files.readAllBytes(path);
            StoredFileRecord record = new StoredFileRecord(fileId, null, null, fileId.toString(), null, content.length, Instant.now());
            return record.toResponse("AVAILABLE", content);
        } catch (IOException exception) {
            throw new ResourceNotFoundException("File not found: " + fileId);
        }
    }

    public StoredFileResponse delete(UUID fileId) {
        StoredFileResponse existing = find(fileId);
        try {
            Files.deleteIfExists(filePath(fileId));
        } catch (IOException exception) {
            throw new BadRequestException("Unable to delete file");
        }
        return new StoredFileResponse(existing.fileId(), existing.ownerId(), existing.purpose(), existing.fileName(),
                existing.contentType(), existing.size(), "DELETED", existing.storedAt(), existing.contentBase64());
    }

    private Path filePath(UUID fileId) {
        return storageRoot.resolve(fileId.toString() + ".bin").normalize();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new BadRequestException("Only image uploads are supported");
        }
    }

    private record StoredFileRecord(UUID fileId, UUID ownerId, String purpose, String fileName,
                                    String contentType, long size, Instant storedAt) {
        StoredFileResponse toResponse(String status, byte[] content) {
            return new StoredFileResponse(fileId, ownerId, purpose, fileName,
                    contentType, size, status, storedAt, encode(content));
        }
    }

    private static String encode(byte[] content) {
        return Base64.getEncoder().encodeToString(content == null ? new byte[0] : content);
    }
}
