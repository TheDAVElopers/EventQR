package com.thedavelopers.eventqr.features.organizer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.organizer.model.dto.OrganizerDtos.OrganizerEventResponse;
import com.thedavelopers.eventqr.features.organizer.model.dto.RewardSettingsRequest;
import com.thedavelopers.eventqr.features.organizer.service.OrganizerService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * The controller's job is to gate every route on role and hand the caller's id and role to
 * OrganizerService, which enforces event ownership (see OrganizerServiceAuthorizationTest).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganizerControllerTest {

    private static final String ORGANIZER = "Bearer organizer";
    private static final String ADMIN = "Bearer admin";
    private static final String STAFF = "Bearer staff";
    private static final String ATTENDEE = "Bearer attendee";

    @Mock private OrganizerService organizerService;
    @Mock private JwtService jwtService;

    private MockMvc mvc;
    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new OrganizerController(organizerService, jwtService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        for (String[] token : new String[][] {{ORGANIZER, "ORGANIZER"}, {ADMIN, "ADMIN"}, {STAFF, "STAFF"}, {ATTENDEE, "ATTENDEE"}}) {
            when(jwtService.extractUserIdFromBearer(token[0])).thenReturn(userId);
            when(jwtService.extractRoleFromBearer(token[0])).thenReturn(AccountRole.valueOf(token[1]));
        }
    }

    private static String json(String body) {
        return body.replace('\'', '"');
    }

    private MockHttpServletRequestBuilder request(HttpMethod method, String path, String token) {
        MockHttpServletRequestBuilder builder = switch (method.name()) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PATCH" -> patch(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException(method.name());
        };
        return builder.header("Authorization", token);
    }

    /** Every route that needs no request body, with the query parameters it requires. */
    private List<Object[]> routesWithoutABody() {
        String e = "/api/v1/organizer/events/" + eventId;
        UUID a = UUID.randomUUID();
        return List.of(
                new Object[] {HttpMethod.GET, "/api/v1/organizer/events"},
                new Object[] {HttpMethod.GET, "/api/v1/organizer/dashboard"},
                new Object[] {HttpMethod.GET, e},
                new Object[] {HttpMethod.PATCH, e + "/status?status=ACTIVE"},
                new Object[] {HttpMethod.GET, e + "/dashboard"},
                new Object[] {HttpMethod.GET, e + "/attendees"},
                new Object[] {HttpMethod.GET, e + "/attendees/search?query=jane"},
                new Object[] {HttpMethod.GET, e + "/attendees/" + a},
                new Object[] {HttpMethod.PATCH, e + "/attendees/" + a + "/status?status=ENTERED"},
                new Object[] {HttpMethod.GET, e + "/transactions"},
                new Object[] {HttpMethod.GET, e + "/transactions/" + a},
                new Object[] {HttpMethod.GET, e + "/staff"},
                new Object[] {HttpMethod.GET, e + "/staff/search?query=bob"},
                new Object[] {HttpMethod.DELETE, e + "/staff/" + a},
                new Object[] {HttpMethod.DELETE, e + "/scan-purposes/" + a},
                new Object[] {HttpMethod.GET, "/api/v1/organizer/users/search?query=bob"},
                new Object[] {HttpMethod.GET, e + "/scan-purposes"});
    }

    // ----- gating: every route -----

    @Test
    void attendeesAndStaffAreTurnedAwayFromEveryOrganizerRouteWithoutTouchingTheService() throws Exception {
        for (Object[] route : routesWithoutABody()) {
            for (String token : new String[] {ATTENDEE, STAFF}) {
                mvc.perform(request((HttpMethod) route[0], (String) route[1], token))
                        .andExpect(status().isForbidden());
            }
        }

        verifyNoInteractions(organizerService);
    }

    @Test
    void bodyCarryingRoutesAreGatedToo() throws Exception {
        String e = "/api/v1/organizer/events/" + eventId;
        String eventBody = json("{'title':'Tech Conf','capacity':10,'rewardsEnabled':false}");

        mvc.perform(patch(e).header("Authorization", ATTENDEE).contentType(MediaType.APPLICATION_JSON).content(eventBody))
                .andExpect(status().isForbidden());
        mvc.perform(post(e + "/staff").header("Authorization", STAFF).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'bob@example.com'}")))
                .andExpect(status().isForbidden());
        mvc.perform(patch(e + "/reward-settings").header("Authorization", ATTENDEE)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'enabled':true}")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(organizerService);
    }

    @Test
    void organizersAndAdminsAreLetInOnEveryRoute() throws Exception {
        for (Object[] route : routesWithoutABody()) {
            for (String token : new String[] {ORGANIZER, ADMIN}) {
                int code = mvc.perform(request((HttpMethod) route[0], (String) route[1], token))
                        .andReturn().getResponse().getStatus();
                org.assertj.core.api.Assertions.assertThat(code)
                        .as("%s %s as %s", route[0], route[1], token)
                        .isNotEqualTo(403);
            }
        }
    }

    // ----- identity handed to the service -----

    @Test
    void theServiceIsToldWhoIsAskingAndInWhatRoleSoItCanEnforceOwnership() throws Exception {
        mvc.perform(get("/api/v1/organizer/events/{id}/attendees", eventId).header("Authorization", ORGANIZER))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/organizer/events/{id}/attendees", eventId).header("Authorization", ADMIN))
                .andExpect(status().isOk());

        verify(organizerService).attendees(userId, eventId, AccountRole.ORGANIZER);
        verify(organizerService).attendees(userId, eventId, AccountRole.ADMIN);
    }

    @Test
    void aRefusalFromTheServiceBecomes403() throws Exception {
        when(organizerService.attendees(any(), eq(eventId), any()))
                .thenThrow(new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException("Not your event"));

        mvc.perform(get("/api/v1/organizer/events/{id}/attendees", eventId).header("Authorization", ORGANIZER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Not your event"));
    }

    // ----- input handling -----

    @Test
    void anUnknownEventStatusIsA400AndNeverReachesTheService() throws Exception {
        mvc.perform(patch("/api/v1/organizer/events/{id}/status", eventId).param("status", "NOT_A_STATUS")
                        .header("Authorization", ORGANIZER))
                .andExpect(status().isBadRequest());

        verify(organizerService, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    void aKnownEventStatusIsPassedThrough() throws Exception {
        mvc.perform(patch("/api/v1/organizer/events/{id}/status", eventId).param("status", "ENDED")
                        .header("Authorization", ORGANIZER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Event status updated"));

        verify(organizerService).updateStatus(userId, eventId, AccountRole.ORGANIZER, EventStatus.ENDED);
    }

    @Test
    void anEventUpdateWithoutATitleIsRejected() throws Exception {
        mvc.perform(patch("/api/v1/organizer/events/{id}", eventId).header("Authorization", ORGANIZER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'title':'','capacity':10,'rewardsEnabled':false}")))
                .andExpect(status().isBadRequest());

        verify(organizerService, never()).updateEvent(any(), any(), any(), any());
    }

    @Test
    void removingStaffReportsSuccessAndCallsTheService() throws Exception {
        UUID assignmentId = UUID.randomUUID();

        mvc.perform(delete("/api/v1/organizer/events/{id}/staff/{a}", eventId, assignmentId)
                        .header("Authorization", ORGANIZER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Staff assignment removed"));

        verify(organizerService).removeStaff(userId, eventId, AccountRole.ORGANIZER, assignmentId);
    }

    @Test
    void togglingRewardsPassesTheFlagToTheService() throws Exception {
        mvc.perform(patch("/api/v1/organizer/events/{id}/reward-settings", eventId).header("Authorization", ORGANIZER)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'enabled':true}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reward settings updated"));

        verify(organizerService).updateRewardSettings(userId, eventId, AccountRole.ORGANIZER, new RewardSettingsRequest(true));
    }

    @Test
    void rewardSettingsReadAsTrueOnlyWhenRewardsAreEnabled() throws Exception {
        OrganizerEventResponse enabled = mock(OrganizerEventResponse.class);
        when(enabled.rewardsStatus()).thenReturn("Enabled");
        OrganizerEventResponse disabled = mock(OrganizerEventResponse.class);
        when(disabled.rewardsStatus()).thenReturn("Disabled");

        when(organizerService.event(userId, eventId, AccountRole.ORGANIZER)).thenReturn(enabled);
        mvc.perform(get("/api/v1/organizer/events/{id}/reward-settings", eventId).header("Authorization", ORGANIZER))
                .andExpect(jsonPath("$.data").value(true));

        when(organizerService.event(userId, eventId, AccountRole.ORGANIZER)).thenReturn(disabled);
        mvc.perform(get("/api/v1/organizer/events/{id}/reward-settings", eventId).header("Authorization", ORGANIZER))
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void deletingScanPurposeReportsSuccessAndCallsTheService() throws Exception {
        UUID purposeId = UUID.randomUUID();

        mvc.perform(delete("/api/v1/organizer/events/{id}/scan-purposes/{purposeId}", eventId, purposeId)
                        .header("Authorization", ORGANIZER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Scan purpose deleted"));

        verify(organizerService).deleteScanPurpose(userId, eventId, AccountRole.ORGANIZER, purposeId);
    }

    @Test
    void deletingScanPurposeWhenTransactionsExistReturns409() throws Exception {
        UUID purposeId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new com.thedavelopers.eventqr.shared.exceptions.ConflictException(
                        "Scan purpose cannot be deleted because transaction logs exist"))
                .when(organizerService).deleteScanPurpose(userId, eventId, AccountRole.ORGANIZER, purposeId);

        mvc.perform(delete("/api/v1/organizer/events/{id}/scan-purposes/{purposeId}", eventId, purposeId)
                        .header("Authorization", ORGANIZER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Scan purpose cannot be deleted because transaction logs exist"));
    }
}
