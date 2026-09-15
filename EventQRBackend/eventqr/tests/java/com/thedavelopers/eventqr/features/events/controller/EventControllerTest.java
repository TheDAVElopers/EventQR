package com.thedavelopers.eventqr.features.events.controller;

import com.thedavelopers.eventqr.features.events.model.dto.EventApprovalRequest;
import com.thedavelopers.eventqr.features.events.model.dto.EventRequest;
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.events.model.dto.EventAvailabilityResponse;
import com.thedavelopers.eventqr.features.events.model.dto.AttendeeEventResponse;
import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class for EventController.
 */
@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    @MockBean
    private JwtService jwtService;

    private UUID organizerUserId;
    private UUID adminUserId;
    private UUID eventId;
    private String validToken = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        organizerUserId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(organizerUserId);
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.ORGANIZER);
    }

    @Test
    void testCreateEvent_Success() throws Exception {
        // Arrange
        EventRequest request = new EventRequest(
                "Tech Conference 2026",
                "Annual tech conference",
                "Technology",
                "Convention Center",
                "http://example.com/logo.png",
                Instant.now().minusSeconds(86400),
                Instant.now().plusSeconds(86400 * 30),
                Instant.now().plusSeconds(86400 * 60),
                Instant.now().plusSeconds(86400 * 90),
                100,
                true
        );
        
        EventResponse response = new EventResponse(
                eventId,
                "Tech Conference 2026",
                "Annual tech conference",
                "Technology",
                "Convention Center",
                "http://example.com/logo.png",
                Instant.now().minusSeconds(86400),
                Instant.now().plusSeconds(86400 * 30),
                Instant.now().plusSeconds(86400 * 60),
                Instant.now().plusSeconds(86400 * 90),
                100,
                true,
                EventStatus.PENDING_REVIEW,
                organizerUserId
        );
        
        given(eventService.create(organizerUserId, request))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""" 
                                {
                                    "title": "Tech Conference 2026",
                                    "description": "Annual tech conference",
                                    "category": "Technology",
                                    "location": "Convention Center",
                                    "eventLogoUrl": "http://example.com/logo.png",
                                    "registrationOpenAt": "%s",
                                    "registrationCloseAt": "%s",
                                    "eventStartAt": "%s",
                                    "eventEndAt": "%s",
                                    "capacity": 100,
                                    "rewardsEnabled": true
                                }
                                """.formatted(
                                request.registrationOpenAt().toString(),
                                request.registrationCloseAt().toString(),
                                request.eventStartAt().toString(),
                                request.eventEndAt().toString()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Tech Conference 2026"))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.message").value("Event submitted"));
    }

    @Test
    void testCreateEvent_Unauthenticated() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Tech Conference 2026",
                                    "capacity": 100
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testCreateEvent_Forbidden_Attendee() throws Exception {
        // Arrange
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(UUID.randomUUID());
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.ATTENDEE);

        // Act & Assert
        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Tech Conference 2026",
                                    "capacity": 100
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void testReviewEvent_Success_Admin() throws Exception {
        // Arrange
        UUID adminId = UUID.randomUUID();
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(adminId);
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.ADMIN);
        
        EventApprovalRequest approvalRequest = new EventApprovalRequest(true, adminId, "Great event!");
        EventResponse response = new EventResponse(
                eventId,
                "Tech Conference 2026",
                "Description",
                "Tech",
                "Location",
                null,
                Instant.now(),
                Instant.now().plusSeconds(86400*30),
                Instant.now().plusSeconds(86400*60),
                Instant.now().plusSeconds(86400*90),
                100,
                true,
                EventStatus.APPROVED,
                organizerUserId
        );
        
        given(eventService.review(eventId, approvalRequest))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(put("/api/v1/events/{eventId}/review", eventId)
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "approved": true,
                                    "reviewerUserId": "%s",
                                    "rejectionReason": null
                                }
                                """.formatted(adminId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.message").value("Event reviewed"));
    }

    @Test
    void testReviewEvent_Forbidden_Organizer() throws Exception {
        // Arrange
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(organizerUserId);
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.ORGANIZER);
        
        EventApprovalRequest approvalRequest = new EventApprovalRequest(true, organizerUserId, null);

        // Act & Assert
        mockMvc.perform(put("/api/v1/events/{eventId}/review", eventId)
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "approved": true,
                                    "reviewerUserId": "%s"
                                }
                                """.format(organizerUserId.toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void testActivateEvent_Success_Admin() throws Exception {
        // Arrange
        UUID adminId = UUID.randomUUID();
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(adminId);
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.ADMIN);
        
        EventResponse response = new EventResponse(
                eventId,
                "Tech Conference 2026",
                "Description",
                "Tech",
                "Location",
                null,
                Instant.now(),
                Instant.now().plusSeconds(86400*30),
                Instant.now().plusSeconds(86400*60),
                Instant.now().plusSeconds(86400*90),
                100,
                true,
                EventStatus.ACTIVE,
                organizerUserId
        );
        
        given(eventService.activate(eventId))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(put("/api/v1/events/{eventId}/activate", eventId)
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.message").value("Event activated"));
    }

    @Test
    void testActivateEvent_Forbidden_Organizer() throws Exception {
        // Arrange
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(organizerUserId);
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.ORGANIZER);

        // Act & Assert
        mockMvc.perform(put("/api/v1/events/{eventId}/activate", eventId)
                        .header("Authorization", validToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testListEvents_Success() throws Exception {
        // Arrange
        EventResponse event1 = new EventResponse(
                UUID.randomUUID(),
                "Event 1",
                "Description 1",
                "Category 1",
                "Location 1",
                null,
                Instant.now(),
                Instant.now().plusSeconds(86400),
                Instant.now().plusSeconds(86400*2),
                Instant.now().plusSeconds(86400*3),
                50,
                true,
                EventStatus.APPROVED,
                organizerUserId
        );
        
        EventResponse event2 = new EventResponse(
                UUID.randomUUID(),
                "Event 2",
                "Description 2",
                "Category 2",
                "Location 2",
                null,
                Instant.now().plusSeconds(86400),
                Instant.now().plusSeconds(86400*2),
                Instant.now().plusSeconds(86400*3),
                Instant.now().plusSeconds(86400*4),
                100,
                false,
                EventStatus.ACTIVE,
                organizerUserId
        );
        
        Page<EventResponse> page = new PageImpl<>(List.of(event1, event2));
        given(eventService.findAllEvents(PageRequest.of(0, 20)))
                .willReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/events")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].title").value("Event 1"))
                .andExpect(jsonPath("$.data.content[1].title").value("Event 2"));
    }

    @Test
    void testGetEventById_Success() throws Exception {
        // Arrange
        AttendeeEventResponse response = new AttendeeEventResponse(
                eventId,
                "Tech Conference 2026",
                "Description",
                "Tech",
                "Location",
                "2026-09-15 to 2026-09-20",
                "2026-09-15",
                "Convention Center",
                "2026-09-01",
                null,
                EventStatus.APPROVED,
                true
        );
        
        given(eventService.findAttendeeEvent(eventId, organizerUserId))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/v1/events/{eventId}", eventId)
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Tech Conference 2026"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void testGetEventAvailability_Success() throws Exception {
        // Arrange
        EventAvailabilityResponse response = new EventAvailabilityResponse(
                eventId,
                true,
                75,
                100
        );
        
        given(eventService.getEventAvailability(eventId))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/v1/events/{eventId}/availability", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isAvailable").value(true))
                .andExpect(jsonPath("$.data.currentAttendeeCount").value(75))
                .andExpect(jsonPath("$.data.capacity").value(100));
    }
}