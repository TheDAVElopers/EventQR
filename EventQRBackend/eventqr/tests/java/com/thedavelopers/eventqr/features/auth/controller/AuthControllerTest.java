package com.thedavelopers.eventqr.features.auth.controller;

import com.thedavelopers.eventqr.features.auth.dto.LoginResponse;
import com.thedavelopers.eventqr.features.auth.model.dto.ChangePasswordRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.ForgotPasswordRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.LoginRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.RegisterRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.ResetPasswordRequest;
import com.thedavelopers.eventqr.features.auth.service.AuthService;
import com.thedavelopers.eventqr.features.auth.service.ChangePasswordService;
import com.thedavelopers.eventqr.features.auth.service.PasswordResetService;
import com.thedavelopers.eventqr.features.users.dto.UserResponse;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;
import com.thedavelopers.eventqr.shared.security.ForgotPasswordRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class for AuthController.
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private ChangePasswordService changePasswordService;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private ForgotPasswordRateLimiter forgotPasswordRateLimiter;

    private String validToken = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        // Common mock setup
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(UUID.randomUUID());
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(com.thedavelopers.eventqr.shared.constants.AccountRole.ATTENDEE);
        given(forgotPasswordRateLimiter.tryConsume(any()))
                .willReturn(true);
    }

    @Test
    void testLogin_Success() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest("test@example.com", "ValidPass1!");
        LoginResponse response = new LoginResponse(
                "jwt.token.here",
                UUID.randomUUID(),
                "test@example.com",
                "Test User",
                com.thedavelopers.eventqr.shared.constants.AccountRole.ATTENDEE,
                "Login successful"
        );
        
        given(authService.login(request)).willReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""" 
                                {
                                    "email": "test@example.com",
                                    "password": "ValidPass1!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("jwt.token.here"))
                .andExpect(jsonPath("$.data.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.message").value("Login successful"));
    }

    @Test
    void testLogin_InvalidCredentials() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest("test@example.com", "wrongpass");
        given(authService.login(request)).willThrow(new com.thedavelopers.eventqr.shared.exceptions.UnauthorizedException("Invalid credentials"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@example.com",
                                    "password": "wrongpass"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testRegister_Success() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest(
                "newuser@example.com",
                "New User",
                "+1234567890",
                "ValidPass1!"
        );
        UserResponse response = new UserResponse(
                UUID.randomUUID(),
                "newuser@example.com",
                "New User",
                "+1234567890",
                com.thedavelopers.eventqr.shared.constants.AccountRole.ATTENDEE,
                com.thedavelopers.eventqr.shared.constants.AccountStatus.ACTIVE
        );
        
        given(authService.register(request)).willReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "newuser@example.com",
                                    "fullName": "New User",
                                    "phoneNumber": "+1234567890",
                                    "password": "ValidPass1!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("New User"))
                .andExpect(jsonPath("$.message").value("Registration successful"));
    }

    @Test
    void testRegister_DuplicateEmail() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest(
                "existing@example.com",
                "User",
                "+1234567890",
                "ValidPass1!"
        );
        given(authService.register(request)).willThrow(new com.thedavelopers.eventqr.shared.exceptions.ConflictException("User already exists"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "existing@example.com",
                                    "fullName": "User",
                                    "phoneNumber": "+1234567890",
                                    "password": "ValidPass1!"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void testChangePassword_Success() throws Exception {
        // Arrange
        ChangePasswordRequest request = new ChangePasswordRequest("oldPass1!", "NewPass1!", "NewPass1!");
        given(changePasswordService.changePassword(any(), any(), any())).willReturn(UUID.randomUUID());

        // Act & Assert
        mockMvc.perform(patch("/api/v1/auth/change-password")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "currentPassword": "oldPass1!",
                                    "newPassword": "NewPass1!",
                                    "confirmPassword": "NewPass1!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    @Test
    void testForgotPassword_Success() throws Exception {
        // Arrange
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@example.com"
                                }
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void testForgotPassword_RateLimited() throws Exception {
        // Arrange
        given(forgotPasswordRateLimiter.tryConsume(any())).willReturn(false);
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@example.com"
                                }
                                """))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void testResetPassword_Success() throws Exception {
        // Arrange
        ResetPasswordRequest request = new ResetPasswordRequest("reset.token.here", "NewPass1!", "NewPass1!");
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "token": "reset.token.here",
                                    "newPassword": "NewPass1!",
                                    "confirmPassword": "NewPass1!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset"));
    }

    @Test
    void testResetPassword_InvalidToken() throws Exception {
        // Arrange
        ResetPasswordRequest request = new ResetPasswordRequest("invalid.token", "NewPass1!", "NewPass1!");
        given(passwordResetService.validateToken("invalid.token")).willReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "token": "invalid.token",
                                    "newPassword": "NewPass1!",
                                    "confirmPassword": "NewPass1!"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testResetPassword_PasswordMismatch() throws Exception {
        // Arrange
        ResetPasswordRequest request = new ResetPasswordRequest("valid.token", "Pass1!", "Pass2!");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "token": "valid.token",
                                    "newPassword": "Pass1!",
                                    "confirmPassword": "Pass2!"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetProfile_Success() throws Exception {
        // Arrange
        UserResponse response = new UserResponse(
                UUID.randomUUID(),
                "test@example.com",
                "Test User",
                "+1234567890",
                com.thedavelopers.eventqr.shared.constants.AccountRole.ATTENDEE,
                com.thedavelopers.eventqr.shared.constants.AccountStatus.ACTIVE
        );
        given(authService.getProfile(UUID.randomUUID())).willReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Test User"))
                .andExpect(jsonPath("$.message").value("Profile retrieved"));
    }

    @Test
    void testGetProfile_Unauthenticated() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}