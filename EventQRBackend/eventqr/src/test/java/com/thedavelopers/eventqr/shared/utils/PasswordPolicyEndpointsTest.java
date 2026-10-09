package com.thedavelopers.eventqr.shared.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.admin.controller.AdminUserController;
import com.thedavelopers.eventqr.features.auth.controller.AuthController;
import com.thedavelopers.eventqr.features.auth.model.dto.LoginRequest;
import com.thedavelopers.eventqr.features.auth.service.AuthService;
import com.thedavelopers.eventqr.features.auth.service.ChangePasswordService;
import com.thedavelopers.eventqr.features.auth.service.PasswordResetService;
import com.thedavelopers.eventqr.features.auth.service.RefreshTokenService;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.features.uploads.service.FileStorageService;
import com.thedavelopers.eventqr.features.users.controller.UserController;
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;
import com.thedavelopers.eventqr.features.users.repository.UserTokenRevocationRepository;
import com.thedavelopers.eventqr.features.users.service.UserService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.ForgotPasswordRateLimiter;
import com.thedavelopers.eventqr.shared.security.JwtService;
import com.thedavelopers.eventqr.shared.security.LoginRateLimiter;

/** The one password policy at every set-password entry point (DTO constraint + service defense). */
class PasswordPolicyEndpointsTest {

    private static final String WEAK = "PASSWORD1!"; // no lowercase
    private static final String STRONG = "Passw0rd!!";
    private static final String MSG = PasswordValidator.FAILURE_MESSAGE;

    private UserService userService;
    private PasswordResetService resetService;
    private ChangePasswordService changeService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        resetService = mock(PasswordResetService.class);
        changeService = mock(ChangePasswordService.class);
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.extractRoleFromBearer(any())).thenReturn(AccountRole.SUPER_ADMIN);
        when(jwtService.extractUserIdFromBearer(any())).thenReturn(UUID.randomUUID());
        mvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(mock(AuthService.class), userService, jwtService, resetService, changeService,
                                mock(ForgotPasswordRateLimiter.class), new LoginRateLimiter(30, 6, 50)),
                        new UserController(userService, jwtService, mock(FileStorageService.class)),
                        new AdminUserController(userService, jwtService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer t")
                .content(body.replace('\'', '"'));
    }

    private void rejected(MockHttpServletRequestBuilder request, String field) throws Exception {
        rejected(request, field, MSG);
    }

    private void rejected(MockHttpServletRequestBuilder request, String field, String msg) throws Exception {
        mvc.perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(msg))
                .andExpect(jsonPath("$.fieldErrors." + field).value(msg))
                .andExpect(jsonPath("$.path").exists());
        verify(userService, never()).register(any());
        verify(userService, never()).create(any());
        verify(userService, never()).changePassword(any(), any(), any());
        verify(resetService, never()).resetPassword(any(), any(), any(), any());
        verify(changeService, never()).changePassword(any(), any(), any(), any());
    }

    @Test
    void registerRejectsLowercaseLessPassword() throws Exception {
        rejected(json(post("/api/v1/auth/register"),
                "{'email':'a@b.com','fullName':'A','password':'" + WEAK + "'}"), "password");
    }

    @Test
    void adminCreateRejectsLowercaseLessPassword() throws Exception {
        rejected(json(post("/api/v1/admin/users/admins"),
                "{'email':'a@b.com','fullName':'A','password':'" + WEAK + "','role':'ADMIN'}"), "password");
    }

    @Test
    void userCreateRejectsLowercaseLessPassword() throws Exception {
        rejected(json(post("/api/v1/users"),
                "{'email':'a@b.com','fullName':'A','password':'" + WEAK + "','role':'ATTENDEE'}"), "password");
    }

    @Test
    void passwordChangeRejectsLowercaseLessNewPasswordButNotCurrent() throws Exception {
        rejected(json(patch("/api/v1/auth/me/password"),
                "{'currentPassword':'weak','newPassword':'" + WEAK + "'}"), "newPassword");
    }

    @Test
    void resetRejectsLowercaseLessPassword() throws Exception {
        rejected(json(post("/api/v1/auth/reset-password"),
                "{'email':'a@b.com','code':'123456','newPassword':'" + WEAK + "','confirmPassword':'" + WEAK + "'}"), "newPassword");
    }

    @Test
    void changePasswordRejectsLowercaseLessPassword() throws Exception {
        rejected(json(post("/api/v1/auth/change-password"),
                "{'currentPassword':'weak','newPassword':'" + WEAK + "','confirmPassword':'" + WEAK + "'}"),
                "newPassword");
    }

    @Test
    void oversizedPasswordIsRejected() throws Exception {
        String big = "Aa1!" + "a".repeat(69); // 73 bytes
        String multibyte = "Aa1!" + "é".repeat(40); // 84 bytes, 44 chars
        for (String pw : new String[] {big, multibyte}) {
            rejected(json(post("/api/v1/auth/register"),
                    "{'email':'a@b.com','fullName':'A','password':'" + pw + "'}"), "password",
                    PasswordValidator.TOO_LONG_MESSAGE);
            rejected(json(post("/api/v1/auth/reset-password"),
                    "{'email':'a@b.com','code':'123456','newPassword':'" + pw + "','confirmPassword':'" + pw + "'}"), "newPassword",
                    PasswordValidator.TOO_LONG_MESSAGE);
        }
    }

    @Test
    void strongPasswordsAreAccepted() throws Exception {
        mvc.perform(json(post("/api/v1/auth/register"),
                "{'email':'a@b.com','fullName':'A','password':'" + STRONG + "'}")).andExpect(status().isOk());
    }

    // ----- service defense in depth -----

    private UserService realUserService(UserProfileRepository repo) {
        return new UserService(repo, mock(EventRegistrationRepository.class), mock(TransactionLogRepository.class),
                new BCryptPasswordEncoder(4), mock(UserTokenRevocationRepository.class), mock(RefreshTokenService.class));
    }

    @Test
    void userServiceRegisterCreateAndChangePasswordRejectWeakPasswordsWithoutTouchingTheRepository() {
        UserProfileRepository repo = mock(UserProfileRepository.class);
        UserService service = realUserService(repo);
        UserRequest weak = new UserRequest("a@b.com", "A", null, WEAK, AccountRole.ATTENDEE);

        assertThatThrownBy(() -> service.register(weak)).isInstanceOf(BadRequestException.class).hasMessage(MSG);
        assertThatThrownBy(() -> service.create(weak)).isInstanceOf(BadRequestException.class).hasMessage(MSG);
        assertThatThrownBy(() -> service.changePassword(UUID.randomUUID(), "x", WEAK))
                .isInstanceOf(BadRequestException.class).hasMessage(MSG);
        verifyNoInteractions(repo);
    }

    @Test
    void tooLongPasswordsNeverReachTheEncoder() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserProfileRepository repo = mock(UserProfileRepository.class);
        UserService service = new UserService(repo, mock(EventRegistrationRepository.class),
                mock(TransactionLogRepository.class), encoder, mock(UserTokenRevocationRepository.class),
                mock(RefreshTokenService.class));
        String tooLong = "Aa1!" + "é".repeat(40);
        UserRequest req = new UserRequest("a@b.com", "A", null, tooLong, AccountRole.ATTENDEE);

        assertThatThrownBy(() -> service.register(req)).hasMessage(PasswordValidator.TOO_LONG_MESSAGE);
        assertThatThrownBy(() -> service.create(req)).hasMessage(PasswordValidator.TOO_LONG_MESSAGE);
        assertThatThrownBy(() -> service.changePassword(UUID.randomUUID(), "x", tooLong))
                .hasMessage(PasswordValidator.TOO_LONG_MESSAGE);
        com.thedavelopers.eventqr.features.auth.service.PasswordResetService reset = new com.thedavelopers.eventqr.features.auth.service.PasswordResetService(mock(
                com.thedavelopers.eventqr.features.auth.repository.PasswordResetTokenRepository.class), repo, encoder,
                mock(com.thedavelopers.eventqr.features.qremail.service.EmailGatewayService.class),
                mock(RefreshTokenService.class));
        assertThatThrownBy(() -> reset.resetPassword("a@b.com", "123456", tooLong, tooLong))
                .hasMessage(PasswordValidator.TOO_LONG_MESSAGE);
        com.thedavelopers.eventqr.features.auth.service.ChangePasswordService change =
                new com.thedavelopers.eventqr.features.auth.service.ChangePasswordService(repo, encoder,
                        mock(RefreshTokenService.class));
        UserProfile existing = new UserProfile();
        existing.setPasswordHash("h");
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(encoder.matches(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> change.changePassword(id, "cur", tooLong, tooLong))
                .hasMessage(PasswordValidator.TOO_LONG_MESSAGE);
        org.mockito.Mockito.verify(encoder, never()).encode(any());
    }

    @Test
    void userServiceCreateStillWorksForAStrongPassword() {
        UserProfileRepository repo = mock(UserProfileRepository.class);
        when(repo.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(repo.saveAndFlush(any(UserProfile.class))).thenAnswer(i -> i.getArgument(0));
        UserService service = realUserService(repo);

        assertThat(service.create(new UserRequest("a@b.com", "A", null, STRONG, AccountRole.STAFF)).email())
                .isEqualTo("a@b.com");
    }

    // ----- login must keep accepting legacy weak passwords -----

    @Test
    void loginStillAcceptsAWeakLegacyPassword() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);
        UserProfileRepository repo = mock(UserProfileRepository.class);
        RefreshTokenService refresh = mock(RefreshTokenService.class);
        UserProfile user = new UserProfile();
        user.setId(UUID.randomUUID());
        user.setEmail("old@example.com");
        user.setFullName("Old");
        user.setRole(AccountRole.ATTENDEE);
        user.setStatus(AccountStatus.ACTIVE);
        user.setPasswordHash(encoder.encode("password1"));
        when(repo.findByEmailIgnoreCase("old@example.com")).thenReturn(Optional.of(user));
        when(refresh.issueForLogin(user.getId())).thenReturn("r");
        AuthService auth = new AuthService(repo, encoder,
                new JwtService("01234567890123456789012345678901", 3_600_000L), refresh);

        assertThat(auth.login(new LoginRequest("old@example.com", "password1")).email()).isEqualTo("old@example.com");
    }
}
