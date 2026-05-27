package com.thedavelopers.eventqr.features.auth.controller;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.auth.model.dto.LoginRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse;
import com.thedavelopers.eventqr.features.auth.model.dto.ForgotPasswordRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.RegisterRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.ResetPasswordRequest;
import com.thedavelopers.eventqr.features.auth.service.AuthService;
import com.thedavelopers.eventqr.features.auth.service.PasswordResetService;
import com.thedavelopers.eventqr.features.users.model.dto.PasswordChangeRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.features.users.service.UserService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, UserService userService, JwtService jwtService,
                          PasswordResetService passwordResetService) {
        this.authService = authService;
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserRequest userRequest = new UserRequest(request.email(), request.fullName(), request.phoneNumber(), request.password(), AccountRole.ATTENDEE);
        return ResponseEntity.ok(ApiResponse.success("Registration completed", userService.create(userRequest)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login processed", authService.login(request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(HttpServletRequest request) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success(userService.findOne(userId)));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<UserResponse>> changePassword(HttpServletRequest request,
                                                                    @Valid @RequestBody PasswordChangeRequest body) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success("Password updated", userService.changePassword(userId, body.currentPassword(), body.newPassword())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok(ApiResponse.success("Logout processed", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        PasswordResetService.PasswordResetResponse response = passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().body(ApiResponse.success("Password reset token generated", response.resetToken()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.email(), request.resetToken(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success("Password reset completed", null));
    }
}