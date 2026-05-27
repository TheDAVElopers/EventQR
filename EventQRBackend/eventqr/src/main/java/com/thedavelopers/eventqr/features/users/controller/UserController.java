package com.thedavelopers.eventqr.features.users.controller;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.thedavelopers.eventqr.features.users.model.dto.UserRequest;
import com.thedavelopers.eventqr.features.users.model.dto.ProfileUpdateRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.features.users.service.UserService;
import com.thedavelopers.eventqr.features.uploads.model.dto.StoredFileResponse;
import com.thedavelopers.eventqr.features.uploads.service.FileStorageService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;
    private final FileStorageService fileStorageService;

    public UserController(UserService userService, JwtService jwtService, FileStorageService fileStorageService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User created", userService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(userService.findAllUsers()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(HttpServletRequest request) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success(userService.findOne(userId)));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(HttpServletRequest request,
                                                              @Valid @RequestBody ProfileUpdateRequest body) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success("Profile updated", userService.updateProfile(userId, body.fullName(), body.phoneNumber())));
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<ApiResponse<StoredFileResponse>> updateAvatar(HttpServletRequest request,
                                                                        @RequestParam("file") MultipartFile file) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success("Avatar stored", fileStorageService.store(userId, "profile-photo", file)));
    }

    @PutMapping("/{userId}/role/{role}")
    public ResponseEntity<ApiResponse<UserResponse>> changeRole(@PathVariable UUID userId,
                                                                @PathVariable AccountRole role) {
        return ResponseEntity.ok(ApiResponse.success("Role updated", userService.changeRoleResponse(userId, role)));
    }
}