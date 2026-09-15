package com.thedavelopers.eventqr.features.auth.service;

import com.thedavelopers.eventqr.features.auth.model.dto.LoginRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse;
import com.thedavelopers.eventqr.features.auth.model.dto.RegisterRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.shared.exceptions.UnauthorizedException;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.security.JwtService;
import com.thedavelopers.eventqr.features.users.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class for AuthService.
 */
@WebMvcTest(AuthService.class)
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private PasswordEncoder passwordEncoder;

    private UUID testUserId;
    private LoginRequest validLoginRequest;
    private RegisterRequest validRegisterRequest;
    private UserProfile testUserProfile;
    private UserResponse testUserResponse;
    private LoginResponse testLoginResponse;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        validLoginRequest = new LoginRequest("test@example.com", "ValidPass1!");
        validRegisterRequest = new RegisterRequest("newuser@example.com", "New User", "+1234567890", "ValidPass1!");
        
        testUserProfile = new UserProfile();
        testUserProfile.setId(testUserId);
        testUserProfile.setEmail("test@example.com");
        testUserProfile.setFullName("Test User");
        testUserProfile.setPhoneNumber("+1234567890");
        testUserProfile.setPasswordHash("$2a$10$hashed.password");
        testUserProfile.setRole(com.thedavelopers.eventqr.shared.constants.AccountRole.ATTENDEE);
        testUserProfile.setStatus(com.thedavelopers.eventqr.shared.constants.AccountStatus.ACTIVE);
        
        testUserResponse = new UserResponse(testUserId, "test@example.com", "Test User", "+1234567890", 
                com.thedavelopers.eventqr.shared.constants.AccountRole.ATTENDEE, 
                com.thedavelopers.eventqr.shared.constants.AccountStatus.ACTIVE);
        
        testLoginResponse = new LoginResponse("jwt.token.here", testUserId, "test@example.com", "Test User", com.thedavelopers.eventqr.shared.constants.AccountRole.ATTENDEE, "Login successful");
    }

    @Test
    void testLogin_Success() throws Exception {
        // Arrange
        given(userService.findByEmail("test@example.com")).willReturn(Optional.of(testUserProfile));
        given(passwordEncoder.matches("ValidPass1!", testUserProfile.getPasswordHash())).willReturn(true);
        given(jwtService.createToken(testUserId, "test@example.com", com.thedavelopers.eventqr.shared.constants.AccountRole.ATTENDEE))
                .willReturn("jwt.token.here");

        // Act
        LoginResponse result = authService.login(validLoginRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo("jwt.token.here");
        assertThat(result.user().email()).isEqualTo("test@example.com");
        then(userService).should().findByEmail("test@example.com");
        then(passwordEncoder).should().matches("ValidPass1!", testUserProfile.getPasswordHash());
        then(jwtService).should().createToken(testUserId, "test@example.com", com.thedavelopers.eventqr.shared.constants.AccountRole.ATTENDEE);
    }

    @Test
    void testLogin_UserNotFound() throws Exception {
        // Arrange
        given(userService.findByEmail("nonexistent@example.com")).willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.login(new LoginRequest("nonexistent@example.com", "ValidPass1!")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void testLogin_InvalidPassword() throws Exception {
        // Arrange
        given(userService.findByEmail("test@example.com")).willReturn(Optional.of(testUserProfile));
        given(passwordEncoder.matches("WrongPass1!", testUserProfile.getPasswordHash())).willReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.login(new LoginRequest("test@example.com", "WrongPass1!")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void testRegister_Success() throws Exception {
        // Arrange
        given(userService.findByEmailIgnoreCase("newuser@example.com")).willReturn(Optional.empty());
        given(userService.create(any())).willReturn(testUserResponse);

        // Act
        UserResponse result = authService.register(validRegisterRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo("newuser@example.com");
        assertThat(result.fullName()).isEqualTo("New User");
        then(userService).should().findByEmailIgnoreCase("newuser@example.com");
        then(userService).should().create(any());
    }

    @Test
    void testRegister_DuplicateEmail() throws Exception {
        // Arrange
        given(userService.findByEmailIgnoreCase("existing@example.com")).willReturn(Optional.of(testUserProfile));

        // Act & Assert
        assertThatThrownBy(() -> authService.register(new RegisterRequest("existing@example.com", "User", "ValidPass1!", "+1234567890", com.thedavelopers.eventqr.shared.constants.AccountRole.ATTENDEE)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("User already exists");
    }

    @Test
    void testGetProfile_Success() throws Exception {
        // Arrange
        given(userService.findOne(testUserId)).willReturn(testUserResponse);

        // Act
        UserResponse result = authService.getProfile(testUserId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo("test@example.com");
        assertThat(result.fullName()).isEqualTo("Test User");
        then(userService).should().findOne(testUserId);
    }

    @Test
    void testGetProfile_UserNotFound() throws Exception {
        // Arrange
        given(userService.findOne(any())).willThrow(new com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException("User not found"));

        // Act & Assert
        assertThatThrownBy(() -> authService.getProfile(UUID.randomUUID()))
                .isInstanceOf(com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException.class);
    }
}