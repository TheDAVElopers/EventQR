package com.thedavelopers.eventqr.shared.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.admin.controller.AdminUserController;
import com.thedavelopers.eventqr.features.auth.controller.AuthController;
import com.thedavelopers.eventqr.features.auth.service.AuthService;
import com.thedavelopers.eventqr.features.auth.service.ChangePasswordService;
import com.thedavelopers.eventqr.shared.security.ForgotPasswordRateLimiter;
import com.thedavelopers.eventqr.shared.security.LoginRateLimiter;
import com.thedavelopers.eventqr.features.auth.service.PasswordResetService;
import com.thedavelopers.eventqr.shared.security.ResetCodeVerifyRateLimiter;
import com.thedavelopers.eventqr.shared.security.ResetPasswordRateLimiter;
import com.thedavelopers.eventqr.features.eventrequests.controller.EventRequestController;
import com.thedavelopers.eventqr.features.eventrequests.service.EventCreationRequestService;
import com.thedavelopers.eventqr.features.events.controller.EventController;
import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.features.uploads.service.FileStorageService;
import com.thedavelopers.eventqr.features.users.controller.UserController;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.features.users.service.UserService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Phone validation on every phone-writing endpoint: invalid values are a 400 in the standard validation shape
 * (message + fieldErrors) before any service call; every form the Android app sends is accepted.
 */
class PhoneValidationEndpointsTest {

    private UserService userService;
    private JwtService jwtService;
    private EventCreationRequestService eventCreationRequestService;
    private RegistrationService registrationService;
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        jwtService = mock(JwtService.class);
        eventCreationRequestService = mock(EventCreationRequestService.class);
        registrationService = mock(RegistrationService.class);
        given(jwtService.extractUserIdFromBearer(any())).willReturn(callerId);
        given(jwtService.extractRoleFromBearer(any())).willReturn(AccountRole.ATTENDEE);
        UserResponse user = new UserResponse(callerId, "jane@example.com", "Jane", "+639171234567",
                AccountRole.ATTENDEE, AccountStatus.ACTIVE);
        given(userService.register(any())).willReturn(user);
        given(userService.updateProfile(any(), any(), any())).willReturn(user);
    }

    private MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AuthController authController() {
        return new AuthController(mock(AuthService.class), userService, jwtService, mock(PasswordResetService.class),
                mock(ChangePasswordService.class), mock(ForgotPasswordRateLimiter.class), mock(LoginRateLimiter.class),
                mock(ResetCodeVerifyRateLimiter.class), mock(ResetPasswordRateLimiter.class),
                new com.thedavelopers.eventqr.shared.security.ChangePasswordRateLimiter(10, 2, 50));
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    // --- POST /auth/register --------------------------------------------------

    private String registerBody(String phone) {
        return "{\"email\":\"jane@example.com\",\"fullName\":\"Jane\",\"phoneNumber\":" + quoted(phone)
                + ",\"password\":\"Passw0rd!!\"}";
    }

    @Test
    void register_invalidPhone_isBadRequestWithFieldError() throws Exception {
        mvc(authController()).perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PhoneNumbers.MESSAGE))
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").value(PhoneNumbers.MESSAGE));
        verify(userService, never()).register(any());
    }

    @Test
    void register_blankPhone_isBadRequest() throws Exception {
        mvc(authController()).perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists());
    }

    @Test
    void register_appFormAndNullPhone_areAccepted() throws Exception {
        MockMvc mvc = mvc(authController());
        for (String phone : new String[] {"+639171234567", null}) {
            mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody(phone)))
                    .andExpect(status().isOk());
        }
    }

    // --- PATCH /users/me ----------------------------------------------------

    @Test
    void updateMe_validPhone_isPassedToTheServiceWhichNormalizes() throws Exception {
        mvc(new UserController(userService, jwtService, mock(FileStorageService.class)))
                .perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Jane\",\"phoneNumber\":\"+639171234567\"}")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        verify(userService).updateProfile(eq(callerId), eq("Jane"), eq("+639171234567"));
    }

    @Test
    void updateMe_invalidPhone_isBadRequest() throws Exception {
        mvc(new UserController(userService, jwtService, mock(FileStorageService.class)))
                .perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Jane\",\"phoneNumber\":\"09171234567\"}")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").value(PhoneNumbers.MESSAGE));
        verify(userService, never()).updateProfile(any(), any(), any());
    }

    // --- POST /event-requests -----------------------------------------------

    private String eventRequestBody(String contact) {
        return "{\"eventName\":\"Expo\",\"eventDescription\":\"Desc\",\"eventCategory\":\"Tech\","
                + "\"capacity\":100,\"venue\":\"Hall\",\"startDateTime\":\"2030-01-01T01:00:00Z\","
                + "\"endDateTime\":\"2030-01-01T05:00:00Z\",\"requesterName\":\"Jane\","
                + "\"contactEmail\":\"jane@example.com\",\"contactNumber\":" + quoted(contact) + ","
                + "\"reasonForRequest\":\"Because\"}";
    }

    @Test
    void eventRequest_spacedContactNumber_isBadRequest() throws Exception {
        mvc(new EventRequestController(eventCreationRequestService, jwtService))
                .perform(post("/api/v1/event-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequestBody("+63 9171234567"))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.contactNumber").value(PhoneNumbers.MESSAGE));
        verify(eventCreationRequestService, never()).create(any(), any());
    }

    @Test
    void eventRequest_e164ContactNumber_isAccepted() throws Exception {
        mvc(new EventRequestController(eventCreationRequestService, jwtService))
                .perform(post("/api/v1/event-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequestBody("+639171234567"))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        verify(eventCreationRequestService).create(eq(callerId), any());
    }

    // --- POST /events/{id}/registrations ------------------------------------

    private String registrationBody(String phone) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"email\":\"jane@example.com\",\"fullName\":\"Jane\","
                + "\"phoneNumber\":" + quoted(phone) + "}";
    }

    @Test
    void eventRegistration_legacyAppFormIsAccepted() throws Exception {
        UUID eventId = UUID.randomUUID();
        mvc(new EventController(mock(EventService.class), registrationService, jwtService))
                .perform(post("/api/v1/events/{eventId}/registrations", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("639171234567"))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        ArgumentCaptor<RegistrationRequest> captor = ArgumentCaptor.forClass(RegistrationRequest.class);
        verify(registrationService).registerAs(captor.capture(), eq(callerId), eq(AccountRole.ATTENDEE), any());
        // Stored as +639171234567 by UserService.findOrCreateAttendee (see UserServicePhoneNormalizationTest).
        assertThat(PhoneNumbers.normalize(captor.getValue().phoneNumber())).isEqualTo("+639171234567");
    }

    @Test
    void eventRegistration_invalidPhone_isBadRequest() throws Exception {
        mvc(new EventController(mock(EventService.class), registrationService, jwtService))
                .perform(post("/api/v1/events/{eventId}/registrations", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody("9171234567"))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").value(PhoneNumbers.MESSAGE));
        verify(registrationService, never()).registerAs(any(), any(), any(), any());
    }

    // --- POST /admin/users/admins -------------------------------------------

    @Test
    void createAdmin_invalidPhone_isBadRequestBeforeTheRoleCheck() throws Exception {
        // Caller is an ATTENDEE: a valid body would be 403, so a 400 proves validation runs first.
        mvc(new AdminUserController(userService, jwtService))
                .perform(post("/api/v1/admin/users/admins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"fullName\":\"Admin\","
                                + "\"phoneNumber\":\"+63-917-123-4567\",\"password\":\"Passw0rd!!\",\"role\":\"ADMIN\"}")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").value(PhoneNumbers.MESSAGE));
        verify(userService, never()).create(any());
    }
}
