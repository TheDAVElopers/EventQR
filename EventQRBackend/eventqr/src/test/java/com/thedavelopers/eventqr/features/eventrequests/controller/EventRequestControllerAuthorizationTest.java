package com.thedavelopers.eventqr.features.eventrequests.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.eventrequests.service.EventCreationRequestService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * B2: event request creation is limited to ATTENDEE/STAFF/ORGANIZER; admins may not file requests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventRequestControllerAuthorizationTest {

    @Mock
    private EventCreationRequestService eventCreationRequestService;
    @Mock
    private JwtService jwtService;

    private MockMvc mockMvc;
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EventRequestController(eventCreationRequestService, jwtService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        given(jwtService.extractUserIdFromBearer(any())).willReturn(callerId);
    }

    private void actingAs(AccountRole role) {
        given(jwtService.extractRoleFromBearer(any())).willReturn(role);
    }

    private String body() {
        return "{\"eventName\":\"Expo\",\"eventDescription\":\"Desc\",\"eventCategory\":\"Tech\","
                + "\"capacity\":100,\"venue\":\"Hall\",\"startDateTime\":\"2030-01-01T01:00:00Z\","
                + "\"endDateTime\":\"2030-01-01T05:00:00Z\",\"requesterName\":\"Jane\","
                + "\"contactEmail\":\"jane@example.com\",\"contactNumber\":\"+639171234567\","
                + "\"reasonForRequest\":\"Because\"}";
    }

    @Test
    void create_admin_isForbidden() throws Exception {
        actingAs(AccountRole.ADMIN);

        mockMvc.perform(post("/api/v1/event-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verify(eventCreationRequestService, never()).create(any(), any());
    }

    @Test
    void create_superAdmin_isForbidden() throws Exception {
        actingAs(AccountRole.SUPER_ADMIN);

        mockMvc.perform(post("/api/v1/event-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verify(eventCreationRequestService, never()).create(any(), any());
    }

    @Test
    void create_attendee_isAllowed() throws Exception {
        actingAs(AccountRole.ATTENDEE);

        mockMvc.perform(post("/api/v1/event-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(eventCreationRequestService).create(any(), any());
    }

    @Test
    void create_staff_isAllowed() throws Exception {
        actingAs(AccountRole.STAFF);

        mockMvc.perform(post("/api/v1/event-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(eventCreationRequestService).create(any(), any());
    }

    @Test
    void create_organizer_isAllowed() throws Exception {
        actingAs(AccountRole.ORGANIZER);

        mockMvc.perform(post("/api/v1/event-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(eventCreationRequestService).create(any(), any());
    }
}
