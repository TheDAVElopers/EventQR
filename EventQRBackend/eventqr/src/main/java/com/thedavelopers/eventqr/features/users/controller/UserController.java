package com.thedavelopers.eventqr.features.users.controller;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.thedavelopers.eventqr.shared.utils.LikePatterns;
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
    public ResponseEntity<ApiResponse<UserResponse>> create(HttpServletRequest request,
                                                            @Valid @RequestBody UserRequest body) {
        requireCanCreateWithRole(request, body.role());
        return ResponseEntity.ok(ApiResponse.success("User created", userService.create(body)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserResponse>>> list(HttpServletRequest request,
                                                                @RequestParam(required = false) AccountRole role,
                                                                @RequestParam(required = false) String q,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        requireAdmin(request);
        AccountRole callerRole = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserResponse> users;
        String pattern = LikePatterns.contains(q);
        if (callerRole == AccountRole.SUPER_ADMIN) {
            if (pattern != null) {
                users = role != null ? userService.searchByRole(role, pattern, pageable) : userService.searchAllUsers(pattern, pageable);
            } else {
                users = role != null ? userService.findByRole(role, pageable) : userService.findAllUsers(pageable);
            }
        } else if (role == AccountRole.ADMIN || role == AccountRole.SUPER_ADMIN) {
            // A plain ADMIN must never list admin accounts; an empty page keeps the contract uniform.
            users = Page.empty(pageable);
        } else if (role != null) {
            users = pattern != null ? userService.searchByRole(role, pattern, pageable) : userService.findByRole(role, pageable);
        } else if (pattern != null) {
            users = userService.searchByRoleNotIn(List.of(AccountRole.ADMIN, AccountRole.SUPER_ADMIN), pattern, pageable);
        } else {
            users = userService.findByRoleNotIn(List.of(AccountRole.ADMIN, AccountRole.SUPER_ADMIN), pageable);
        }
        return ResponseEntity.ok(ApiResponse.success(users));
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

@PutMapping("/{userId}/role/{role}")
    public ResponseEntity<ApiResponse<UserResponse>> changeRole(HttpServletRequest request,
                                                                @PathVariable UUID userId,
                                                                @PathVariable AccountRole role) {
        requireAdmin(request);
        AccountRole callerRole = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success("Role updated",
                userService.changeRoleResponse(currentUserId(request), callerRole, userId, role)));
    }

    private UUID currentUserId(HttpServletRequest request) {
        return jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
    }

    private void requireAdmin(HttpServletRequest request) {
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role != AccountRole.ADMIN && role != AccountRole.SUPER_ADMIN) {
            throw new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException("Admin access required");
        }
    }

    private void requireCanCreateWithRole(HttpServletRequest request, AccountRole roleToCreate) {
        AccountRole callerRole = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (callerRole != AccountRole.ADMIN && callerRole != AccountRole.SUPER_ADMIN) {
            throw new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException("Admin access required to create users");
        }
        if (roleToCreate == AccountRole.ADMIN || roleToCreate == AccountRole.SUPER_ADMIN) {
            if (callerRole != AccountRole.SUPER_ADMIN) {
                throw new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException("Only super admins can create admin accounts");
            }
        }
    }
}