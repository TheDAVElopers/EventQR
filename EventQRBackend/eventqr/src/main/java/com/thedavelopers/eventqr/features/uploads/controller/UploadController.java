package com.thedavelopers.eventqr.features.uploads.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.thedavelopers.eventqr.features.uploads.model.dto.StoredFileResponse;
import com.thedavelopers.eventqr.features.uploads.service.FileStorageService;
import com.thedavelopers.eventqr.shared.response.ApiResponse;

@RestController
@RequestMapping("/api/v1")
public class UploadController {

    private final FileStorageService fileStorageService;

    public UploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/uploads/event-logo")
    public ResponseEntity<ApiResponse<StoredFileResponse>> uploadEventLogo(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Event poster stored", fileStorageService.store(null, "event-poster", file)));
    }

    @PostMapping("/uploads/id-template-assets")
    public ResponseEntity<ApiResponse<StoredFileResponse>> uploadTemplateAsset(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("ID template asset stored", fileStorageService.store(null, "id-template-asset", file)));
    }

    @PostMapping("/uploads/profile-photo")
    public ResponseEntity<ApiResponse<StoredFileResponse>> uploadProfilePhoto(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Profile photo stored", fileStorageService.store(null, "profile-photo", file)));
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<ApiResponse<StoredFileResponse>> getFile(@PathVariable UUID fileId) {
        return ResponseEntity.ok(ApiResponse.success(fileStorageService.find(fileId)));
    }

    @GetMapping(value = "/files/{fileId}/content", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getFileContent(@PathVariable UUID fileId) {
        StoredFileResponse file = fileStorageService.find(fileId);
        FileStorageService.StoredFileContent content = fileStorageService.readContent(fileId);
        MediaType mediaType = file.contentType() == null || file.contentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(file.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(content.content());
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<ApiResponse<StoredFileResponse>> deleteFile(@PathVariable UUID fileId) {
        return ResponseEntity.ok(ApiResponse.success("File deleted", fileStorageService.delete(fileId)));
    }
}
