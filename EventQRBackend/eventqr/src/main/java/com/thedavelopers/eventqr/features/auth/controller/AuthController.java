package com.thedavelopers.eventqr.features.auth.controller;

import java.util.Map;
import java.util.UUID;

import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.auth.model.dto.LoginRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.LogoutRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.RefreshRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse;
import com.thedavelopers.eventqr.features.auth.model.dto.ChangePasswordRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.ForgotPasswordRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.RegisterRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.ResetPasswordRequest;
import com.thedavelopers.eventqr.features.auth.service.AuthService;
import com.thedavelopers.eventqr.features.auth.service.ChangePasswordService;
import com.thedavelopers.eventqr.features.auth.service.PasswordResetService;
import com.thedavelopers.eventqr.features.users.model.dto.PasswordChangeRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.features.users.service.UserService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.ForgotPasswordRateLimiter;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.exceptions.TooManyRequestsException;
import com.thedavelopers.eventqr.shared.exceptions.UnauthorizedException;
import com.thedavelopers.eventqr.shared.security.JwtService;
import com.thedavelopers.eventqr.shared.security.LoginRateLimiter;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordResetService passwordResetService;
    private final ChangePasswordService changePasswordService;
    private final ForgotPasswordRateLimiter forgotPasswordRateLimiter;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(AuthService authService, UserService userService, JwtService jwtService,
                          PasswordResetService passwordResetService, ChangePasswordService changePasswordService,
                          ForgotPasswordRateLimiter forgotPasswordRateLimiter,
                          LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordResetService = passwordResetService;
        this.changePasswordService = changePasswordService;
        this.forgotPasswordRateLimiter = forgotPasswordRateLimiter;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserRequest userRequest = new UserRequest(request.email(), request.fullName(), request.phoneNumber(), request.password(), AccountRole.ATTENDEE);
        return ResponseEntity.ok(ApiResponse.success("Registration completed", userService.register(userRequest)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(HttpServletRequest httpRequest,
                                                            @Valid @RequestBody LoginRequest request) {
        LoginRateLimiter.Permit permit = loginRateLimiter.acquire(httpRequest, request.email());
        try {
            LoginResponse response = authService.login(request);
            loginRateLimiter.onSuccess(permit);
            return ResponseEntity.ok(ApiResponse.success("Login processed", response));
        } catch (ForbiddenException e) {
            // Disabled account: the password was correct, so this is not a guess; refund the attempt.
            loginRateLimiter.onSuccess(permit);
            throw e;
        } catch (UnauthorizedException e) {
            // A wrong password / unknown email is a real guess: the attempt stays counted.
            throw e;
        } catch (RuntimeException e) {
            // Refund only faults GlobalExceptionHandler maps to 5xx (DB down, bug): not the caller's guess.
            // Client errors (4xx) stay counted so they cannot be used as free probes. Only this permit's
            // slots are returned; counters are not cleared. A 429 from acquire() is thrown above this try
            // block, so it is never recorded or refunded.
            if (isServerFault(e)) {
                loginRateLimiter.refund(permit);
            }
            throw e;
        }
    }

    /**
     * True when {@code GlobalExceptionHandler} would answer with 5xx. Mirrors its mapping: the types it maps
     * to 400/401/403/404/409/429 are client errors; everything else falls to the generic 500 handler.
     */
    private static boolean isServerFault(RuntimeException e) {
        return !(e instanceof ResourceNotFoundException
                || e instanceof BadRequestException
                || e instanceof ConflictException
                || e instanceof IllegalArgumentException
                || e instanceof DataIntegrityViolationException
                || e instanceof PersistenceException
                || e instanceof JpaSystemException
                || e instanceof TransactionSystemException
                || e instanceof ForbiddenException
                || e instanceof TooManyRequestsException
                || e instanceof UnauthorizedException);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(HttpServletRequest request) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success(userService.findOne(userId)));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(HttpServletRequest request) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success("Session refreshed", authService.refreshToken(userId)));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<UserResponse>> changePassword(HttpServletRequest request,
                                                                    @Valid @RequestBody PasswordChangeRequest body) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        return ResponseEntity.ok(ApiResponse.success("Password updated", userService.changePassword(userId, body.currentPassword(), body.newPassword())));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePasswordWithConfirm(HttpServletRequest request,
                                                                       @Valid @RequestBody ChangePasswordRequest body) {
        UUID userId = jwtService.extractUserIdFromBearer(request.getHeader("Authorization"));
        changePasswordService.changePassword(userId, body.currentPassword(), body.newPassword(), body.confirmPassword());
        return ResponseEntity.ok(ApiResponse.success("Password has been changed", null));
    }

    /** Public: the access token has typically expired, the refresh token is the credential. */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Session refreshed", authService.refresh(body.refreshToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request,
                                                    @RequestBody(required = false) LogoutRequest body) {
        jwtService.revoke(request.getHeader("Authorization"));
        if (body != null) {
            authService.endSession(body.refreshToken());
        }
        return ResponseEntity.ok(ApiResponse.success("Logout processed", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(HttpServletRequest request,
                                                            @Valid @RequestBody ForgotPasswordRequest forgotPassword) {
        if (!forgotPasswordRateLimiter.allow(request, forgotPassword.email())) {
            throw new TooManyRequestsException(
                    "Too many password reset requests. Please try again later.");
        }
        // Asynchronous on the dedicated passwordResetExecutor: lookup, token write and email send happen off the
        // request thread so response time does not reveal whether the account exists. That executor's
        // caller-runs fallback means the task is never rejected (so there is nothing to swallow here).
        passwordResetService.requestReset(forgotPassword.email());
        return ResponseEntity.ok(ApiResponse.success("If an account with that email exists, a reset link has been sent", null));
    }

    @GetMapping("/reset-password/validate")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> validateResetToken(@RequestParam String token) {
        boolean valid = passwordResetService.validateToken(token);
        if (!valid) {
            return ResponseEntity.badRequest().body(ApiResponse.success("Token is invalid or expired", Map.of("valid", false)));
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of("valid", true)));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword(), request.confirmPassword());
        return ResponseEntity.ok(ApiResponse.success("Password has been reset", null));
    }
}
