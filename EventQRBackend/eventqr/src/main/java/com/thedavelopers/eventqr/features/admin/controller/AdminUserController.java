package com.thedavelopers.eventqr.features.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.users.model.dto.UserRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.features.users.service.UserService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final JwtService jwtService;

    public AdminUserController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/admins")
    public ResponseEntity<ApiResponse<UserResponse>> createAdmin(HttpServletRequest httpRequest,
                                                                 @Valid @RequestBody UserRequest request) {
        AccountRole currentRole = jwtService.extractRoleFromBearer(httpRequest.getHeader("Authorization"));
        if (currentRole != AccountRole.SUPER_ADMIN) {
            throw new ForbiddenException("Only Super Admin can create admin accounts.");
        }
        if (request.role() != AccountRole.ADMIN) {
            throw new BadRequestException("This endpoint only creates Admin accounts.");
        }
        return ResponseEntity.ok(ApiResponse.success("Admin account created", userService.create(request)));
    }
}
