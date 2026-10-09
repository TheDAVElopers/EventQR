package com.thedavelopers.eventqr.features.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse;
import com.thedavelopers.eventqr.features.auth.service.AuthService;
import com.thedavelopers.eventqr.features.auth.service.ChangePasswordService;
import com.thedavelopers.eventqr.features.auth.service.PasswordResetService;
import com.thedavelopers.eventqr.features.users.model.dto.UserRequest;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.features.users.service.UserService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.exceptions.UnauthorizedException;
import com.thedavelopers.eventqr.shared.security.ForgotPasswordRateLimiter;
import com.thedavelopers.eventqr.shared.security.JwtService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private UserService userService;
    @Mock private JwtService jwtService;
    @Mock private PasswordResetService passwordResetService;
    @Mock private ChangePasswordService changePasswordService;
    @Mock private ForgotPasswordRateLimiter forgotPasswordRateLimiter;
    @Mock private com.thedavelopers.eventqr.shared.security.ResetCodeVerifyRateLimiter verifyLimiter;
    @Mock private com.thedavelopers.eventqr.shared.security.ResetPasswordRateLimiter resetLimiter;

    private MockMvc mvc;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(verifyLimiter.allow(any(), any())).thenReturn(true);
        when(resetLimiter.allow(any(), any())).thenReturn(true);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, userService, jwtService,
                        passwordResetService, changePasswordService, forgotPasswordRateLimiter,
                        new com.thedavelopers.eventqr.shared.security.LoginRateLimiter(30, 6, 50),
                        verifyLimiter, resetLimiter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private LoginResponse session(String refresh) {
        return new LoginResponse("access-token", userId, "jane@example.com", "Jane Doe", AccountRole.ATTENDEE,
                "Login successful", refresh);
    }

    private static String json(String body) {
        return body.replace('\'', '"');
    }

    // ----- login -----

    @Test
    void loginReturnsTheAccessAndRefreshTokens() throws Exception {
        when(authService.login(any())).thenReturn(session("refresh-1"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'jane@example.com','password':'Passw0rd!!'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-1"))
                .andExpect(jsonPath("$.data.role").value("ATTENDEE"));
    }

    @Test
    void wrongCredentialsAre401() throws Exception {
        when(authService.login(any())).thenThrow(new UnauthorizedException("Invalid email or password"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'jane@example.com','password':'nope'}")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void aMalformedLoginIsRejectedBeforeTheServiceIsCalled() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'not-an-email','password':''}")))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }

    // ----- register -----

    @Test
    void registeringAlwaysCreatesAnAttendeeEvenIfTheClientAsksForMore() throws Exception {
        when(userService.register(any())).thenReturn(new UserResponse(userId, "jane@example.com", "Jane Doe", null,
                AccountRole.ATTENDEE, AccountStatus.ACTIVE));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'jane@example.com','fullName':'Jane Doe','password':'Passw0rd!!','role':'SUPER_ADMIN'}")))
                .andExpect(status().isOk());

        ArgumentCaptor<UserRequest> captured = ArgumentCaptor.forClass(UserRequest.class);
        verify(userService).register(captured.capture());
        verify(userService, never()).create(any());
        org.assertj.core.api.Assertions.assertThat(captured.getValue().role()).isEqualTo(AccountRole.ATTENDEE);
    }

    @Test
    void aTooShortPasswordIsRejectedOnRegistration() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'jane@example.com','fullName':'Jane Doe','password':'short'}")))
                .andExpect(status().isBadRequest());

        verify(userService, never()).create(any());
    }

    // ----- refresh -----

    @Test
    void refreshReturnsANewTokenPair() throws Exception {
        when(authService.refresh("refresh-1")).thenReturn(session("refresh-2"));

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'refreshToken':'refresh-1'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-2"));
    }

    @Test
    void anInvalidRefreshTokenIs401() throws Exception {
        when(authService.refresh("stolen")).thenThrow(new UnauthorizedException("Invalid or expired session"));

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'refreshToken':'stolen'}")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutATokenIs400() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).refresh(any());
    }

    // ----- logout -----

    @Test
    void logoutRevokesTheAccessTokenAndEndsTheRefreshFamily() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'refreshToken':'refresh-1'}")))
                .andExpect(status().isOk());

        verify(jwtService).revoke("Bearer access-token");
        verify(authService).endSession("refresh-1");
    }

    @Test
    void logoutWithoutABodyStillRevokesTheAccessToken() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk());

        verify(jwtService).revoke("Bearer access-token");
        verify(authService, never()).endSession(any());
    }

    // ----- forgot / reset password -----

    @Test
    void forgotPasswordGivesTheSameNeutralAnswerWhetherOrNotTheAccountExists() throws Exception {
        when(forgotPasswordRateLimiter.allow(any(), eq("jane@example.com"))).thenReturn(true);

        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'jane@example.com'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account with that email exists, a reset code has been sent"));

        verify(passwordResetService).requestReset("jane@example.com");
    }

    @Test
    void forgotPasswordIsRateLimitedAndDoesNotSendAnEmail() throws Exception {
        when(forgotPasswordRateLimiter.allow(any(), any())).thenReturn(false);

        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'jane@example.com'}")))
                .andExpect(status().isTooManyRequests());

        verify(passwordResetService, never()).requestReset(any());
    }

    @Test
    void aWeakNewPasswordIsRejectedBeforeTheServiceIsCalled() throws Exception {
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'a@b.com','code':'123456','newPassword':'alllowercase1','confirmPassword':'alllowercase1'}")))
                .andExpect(status().isBadRequest());

        verify(passwordResetService, never()).resetPassword(any(), any(), any(), any());
    }

    @Test
    void mismatchedResetPasswordsFromTheServiceAre400() throws Exception {
        org.mockito.Mockito.doThrow(new BadRequestException("Passwords do not match"))
                .when(passwordResetService).resetPassword(any(), any(), any(), any());

        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'a@b.com','code':'123456','newPassword':'Passw0rd!!','confirmPassword':'Different1!'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Passwords do not match"));
    }

    @Test
    void resetPasswordPassesEmailAndCodeToTheService() throws Exception {
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'a@b.com','code':'123456','newPassword':'Passw0rd!!','confirmPassword':'Passw0rd!!'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset"));

        verify(passwordResetService).resetPassword("a@b.com", "123456", "Passw0rd!!", "Passw0rd!!");
    }

    @Test
    void aMalformedResetCodeIsRejectedBeforeTheServiceIsCalled() throws Exception {
        for (String bad : new String[] {"12345", "1234567", "12345a", " 12345", ""}) {
            mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                            .content(json("{'email':'a@b.com','code':'" + bad + "','newPassword':'Passw0rd!!','confirmPassword':'Passw0rd!!'}")))
                    .andExpect(status().isBadRequest());
        }
        verify(passwordResetService, never()).resetPassword(any(), any(), any(), any());
    }

    // ----- two-step reset: verify -----

    @Test
    void verifyResetCodePassesEmailAndCodeToTheServiceAndReturns200() throws Exception {
        mvc.perform(post("/api/v1/auth/reset-password/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'a@b.com','code':'123456'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(passwordResetService).verifyCode("a@b.com", "123456");
        verify(passwordResetService, never()).resetPassword(any(), any(), any(), any());
    }

    @Test
    void verifyWithAWrongCodeIsTheGenericBadRequest() throws Exception {
        org.mockito.Mockito.doThrow(new BadRequestException("Reset code is invalid or expired"))
                .when(passwordResetService).verifyCode(any(), any());

        mvc.perform(post("/api/v1/auth/reset-password/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'a@b.com','code':'000000'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Reset code is invalid or expired"));
    }

    @Test
    void verifyIsRateLimitedWith429AndNeverReachesTheService() throws Exception {
        when(verifyLimiter.allow(any(), any())).thenReturn(false);

        mvc.perform(post("/api/v1/auth/reset-password/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'a@b.com','code':'123456'}")))
                .andExpect(status().isTooManyRequests());

        verify(passwordResetService, never()).verifyCode(any(), any());
    }

    @Test
    void resetPasswordIsRateLimitedWith429AndNeverReachesTheService() throws Exception {
        when(resetLimiter.allow(any(), any())).thenReturn(false);

        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'a@b.com','code':'123456','newPassword':'Passw0rd!!','confirmPassword':'Passw0rd!!'}")))
                .andExpect(status().isTooManyRequests());

        verify(passwordResetService, never()).resetPassword(any(), any(), any(), any());
    }

    @Test
    void verifyBudgetIsNotSharedWithForgotPasswordOrReset() throws Exception {
        when(verifyLimiter.allow(any(), any())).thenReturn(false);
        when(forgotPasswordRateLimiter.allow(any(), any())).thenReturn(true);

        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'a@b.com'}")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'a@b.com','code':'123456','newPassword':'Passw0rd!!','confirmPassword':'Passw0rd!!'}")))
                .andExpect(status().isOk());
    }

    @Test
    void malformedVerifyInputIsRejectedBeforeLimiterAndService() throws Exception {
        for (String bad : new String[] {"{'email':'a@b.com','code':'12345'}", "{'email':'a@b.com','code':'12345a'}",
                "{'email':'a@b.com','code':''}", "{'email':'not-an-email','code':'123456'}",
                "{'email':'','code':'123456'}", "{'code':'123456'}", "{'email':'a@b.com'}"}) {
            mvc.perform(post("/api/v1/auth/reset-password/verify").contentType(MediaType.APPLICATION_JSON)
                            .content(json(bad)))
                    .andExpect(status().isBadRequest());
        }
        verify(passwordResetService, never()).verifyCode(any(), any());
        verify(verifyLimiter, never()).allow(any(), any());
    }

    @Test
    void resetValidateEndpointIsGone() throws Exception {
        mvc.perform(get("/api/v1/auth/reset-password/validate").param("token", "x"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void registerCallsRegisterAndNeverThePrivilegedCreate() throws Exception {
        when(userService.register(any())).thenReturn(new UserResponse(userId, "jane@example.com", "Jane Doe", null,
                AccountRole.ATTENDEE, com.thedavelopers.eventqr.shared.constants.AccountStatus.ACTIVE));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("{'email':'jane@example.com','fullName':'Jane Doe',"
                        + "'password':'Passw0rd!!'}")))
                .andExpect(status().isOk());

        verify(userService).register(any());
        verify(userService, never()).create(any());
    }
}
