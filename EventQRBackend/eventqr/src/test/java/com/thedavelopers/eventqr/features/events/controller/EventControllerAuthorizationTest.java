package com.thedavelopers.eventqr.features.events.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Admin-only event endpoints: review and activate must reject non-admin roles.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventControllerAuthorizationTest {

    @Mock
    private EventService eventService;
    @Mock
    private RegistrationService registrationService;
    @Mock
    private JwtService jwtService;

    private MockMvc mockMvc;

    private final UUID eventId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new EventController(eventService, registrationService, jwtService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void actingAs(UUID userId, AccountRole role) {
        given(jwtService.extractUserIdFromBearer(any())).willReturn(userId);
        given(jwtService.extractRoleFromBearer(any())).willReturn(role);
    }

    private String reviewBody() {
        return "{\"approved\":true,\"reviewerUserId\":null,\"rejectionReason\":null}";
    }

    @Test
    void review_attendee_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ATTENDEE);

        mockMvc.perform(put("/api/v1/events/{eventId}/review", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verify(eventService, never()).review(any(), any());
    }

    @Test
    void review_organizer_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);

        mockMvc.perform(put("/api/v1/events/{eventId}/review", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void review_admin_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);

        mockMvc.perform(put("/api/v1/events/{eventId}/review", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(eventService).review(any(), any());
    }

    @Test
    void activate_attendee_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ATTENDEE);

        mockMvc.perform(put("/api/v1/events/{eventId}/activate", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verify(eventService, never()).activate(any());
    }

    @Test
    void activate_admin_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);

        mockMvc.perform(put("/api/v1/events/{eventId}/activate", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(eventService).activate(eventId);
    }
}
