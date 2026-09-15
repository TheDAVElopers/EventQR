package com.thedavelopers.eventqr.features.organizer.controller;

import com.thedavelopers.eventqr.features.organizer.dto.OrganizerDtos.BenefitClaimResponse;
import com.thedavelopers.eventqr.features.organizer.dto.OrganizerDtos.DashboardResponse;
import com.thedavelopers.eventqr.features.organizer.dto.OrganizerDtos.EventStaffAssignmentResponse;
import com.thedavelopers.eventqr.features.organizer.dto.OrganizerDtos.OrganizerAttendeeResponse;
import com.thedavelopers.eventqr.features.organizer.dto.OrganizerDtos.OrganizerDashboardResponse;
import com.thedavelopers.eventqr.features.organizer.dto.OrganizerDtos.OrganizerEventResponse;
import com.thedavelopers.eventqr.features.organizer.dto.OrganizerDtos.OrganizerTransactionResponse;
import com.thedavelopers.eventqr.features.organizer.dto.OrganizerDtos.RewardSettingsRequest;
import com.thedavelopers.eventqr.features.organizer.dto.OrganizerDtos.StaffAssignmentRequest;
import com.thedavelopers.eventqr.features.organizer.dto.OrganizerDtos.StaffAssignmentUpdateRequest;
import com.thedavelopers.eventqr.features.organizer.dto.OrganizerDtos.TransactionEntry;
import com.thedavelopers.eventqr.features.organizer.dto.OrganizerDtos.UserSearchResponse;
import com.thedavelopers.eventqr.features.organizer.service.OrganizerService;
import com.thedavelopers.eventqr.features.events.dto.EventResponse;
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
 * Test class for OrganizerController.
 */
@WebMvcTest(com.thedavelopers.eventqr.features.organizer.controller.OrganizerController.class)
class OrganizerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrganizerService organizerService;

    @MockBean
    private JwtService jwtService;

    private UUID organizerUserId;
    private UUID eventId;
    private String validToken = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        organizerUserId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(organizerUserId);
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.ORGANIZER);
    }

    @Test
    void testListEvents_Success() throws Exception {
        // Arrange
        OrganizerEventResponse event1 = new OrganizerEventResponse(
                UUID.randomUUID(),
                "Tech Conference 2026",
                "Organizer",
                "Sep 15, 2026 - Sep 20, 2026",
                "Sep 15, 2026",
                "Convention Center",
                "APPROVED",
                "2026-09-01",
                null,
                50,
                100,
                false,
                true
        );
        
        OrganizerEventResponse event2 = new OrganizerEventResponse(
                UUID.randomUUID(),
                "Music Festival 2026",
                "Organizer",
                "Oct 01, 2026 - Oct 05, 2026",
                "Oct 01, 2026",
                "Outdoor Arena",
                "ACTIVE",
                "2026-09-15",
                null,
                200,
                50,
                true,
                true
        );
        
        given(organizerService.listEvents(organizerUserId))
                .willReturn(List.of(event1, event2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/organizer/events")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].title").value("Tech Conference 2026"))
                .andExpect(jsonPath("$.data[1].title").value("Music Festival 2026"))
                .andExpect(jsonPath("$.message").value("Events retrieved"));
    }

    @Test
    void testGetEventDetails_Success() throws Exception {
        // Arrange
        OrganizerEventResponse response = new OrganizerEventResponse(
                eventId,
                "Tech Conference 2026",
                "Organizer",
                "Sep 15, 2026 - Sep 20, 2026",
                "Sep 15, 2026",
                "Convention Center",
                "APPROVED",
                "2026-09-01",
                null,
                100,
                75,
                true,
                true
        );
        
        given(organizerService.event(organizerUserId, eventId))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/v1/organizer/events/{eventId}", eventId)
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Tech Conference 2026"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.message").value("Event details retrieved"));
    }

    @Test
    void testUpdateEvent_Success() throws Exception {
        // Arrange
        com.thedavelopers.eventqr.features.events.model.dto.EventRequest request = new com.thedavelopers.eventqr.features.events.model.dto.EventRequest(
                "Updated Tech Conference",
                "Updated description",
                "Technology",
                "Updated Location",
                "http://example.com/new-logo.png",
                Instant.now().minusSeconds(86400),
                Instant.now().plusSeconds(86400 * 30),
                Instant.now().plusSeconds(86400 * 60),
                Instant.now().plusSeconds(86400 * 90),
                150,
                false
        );
        
        EventResponse response = new EventResponse(
                eventId,
                "Updated Tech Conference",
                "Updated description",
                "Technology",
                "Updated Location",
                "http://example.com/new-logo.png",
                Instant.now().minusSeconds(86400),
                Instant.now().plusSeconds(86400 * 30),
                Instant.now().plusSeconds(86400 * 60),
                Instant.now().plusSeconds(86400 * 90),
                150,
                false,
                EventStatus.APPROVED,
                organizerUserId
        );
        
        given(organizerService.updateEvent(organizerUserId, eventId, request))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(put("/api/v1/organizer/events/{eventId}", eventId)
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Updated Tech Conference",
                                    "description": "Updated description",
                                    "category": "Technology",
                                    "location": "Updated Location",
                                    "eventLogoUrl": "http://example.com/new-logo.png",
                                    "registrationOpenAt": "%s",
                                    "registrationCloseAt": "%s",
                                    "eventStartAt": "%s",
                                    "eventEndAt": "%s",
                                    "capacity": 150,
                                    "rewardsEnabled": false
                                }
                                """.formatted(
                                request.registrationOpenAt().toString(),
                                request.registrationCloseAt().toString(),
                                request.eventStartAt().toString(),
                                request.eventEndAt().toString()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated Tech Conference"))
                .andExpect(jsonPath("$.data.capacity").value(150))
                .andExpect(jsonPath("$.message").value("Event updated"));
    }

    @Test
    void testUpdateEvent_Forbidden_NotOrganizer() throws Exception {
        // Arrange
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(UUID.randomUUID()); // Different user
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.ATTENDEE);
        
        com.thedavelopers.eventqr.features.events.model.dto.EventRequest request = new com.thedavelopers.eventqr.features.events.model.dto.EventRequest(
                "Tech Conference", null, null, null, null,
                Instant.now(), Instant.now().plusSeconds(86400),
                Instant.now().plusSeconds(86400*2), Instant.now().plusSeconds(86400*3),
                100, true
        );

        // Act & Assert
        mockMvc.perform(put("/api/v1/organizer/events/{eventId}", eventId)
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Tech Conference",
                                    "capacity": 100
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void testUpdateEventStatus_Success() throws Exception {
        // Arrange
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
        
        given(organizerService.updateStatus(organizerUserId, eventId, EventStatus.ACTIVE))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/organizer/events/{eventId}/status", eventId)
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.message").value("Event status updated"));
    }

    @Test
    void testGetDashboard_Success() throws Exception {
        // Arrange
        OrganizerDashboardResponse response = new OrganizerDashboardResponse(
                5,
                List.of(
                        new OrganizerEventResponse(
                                UUID.randomUUID(),
                                "Event 1",
                                "Organizer",
                                "Date 1",
                                "Start 1",
                                "Location 1",
                                "APPROVED",
                                "Open 1",
                                null,
                                100,
                                50,
                                false,
                                true
                        ),
                        new OrganizerEventResponse(
                                UUID.randomUUID(),
                                "Event 2",
                                "Organizer",
                                "Date 2",
                                "Start 2",
                                "Location 2",
                                "ACTIVE",
                                "Open 2",
                                null,
                                200,
                                150,
                                true,
                                true
                        )
                ),
                List.of(
                        new TransactionEntry(
                                UUID.randomUUID(),
                                "Attendee 1",
                                "REGISTRATION",
                                "APPROVED",
                                10,
                                Instant.now()
                        ),
                        new TransactionEntry(
                                UUID.randomUUID(),
                                "Attendee 2",
                                "BENEFIT_CLAIM",
                                "APPROVED",
                                -5,
                                Instant.now().minusSeconds(3600)
                        )
                )
        );
        
        given(organizerService.dashboard(organizerUserId))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/v1/organizer/dashboard")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEvents").value(5))
                .andExpect(jsonPath("$.data.recentEvents.length()").value(2))
                .andExpect(jsonPath("$.data.recentTransactions.length()").value(2))
                .andExpect(jsonPath("$.message").value("Dashboard data retrieved"));
    }

    @Test
    void testGetEventAttendees_Success() throws Exception {
        // Arrange
        OrganizerAttendeeResponse attendee1 = new OrganizerAttendeeResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "John Doe",
                "john@example.com",
                "+1234567890",
                "REGISTERED",
                null,
                null,
                null,
                100,
                1001
        );
        
        OrganizerAttendeeResponse attendee2 = new OrganizerAttendeeResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Jane Smith",
                "jane@example.com",
                "+1234567891",
                "ATTENDED",
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600),
                Instant.now(),
                150,
                1002
        );
        
        given(organizerService.attendees(organizerUserId, eventId))
                .willReturn(List.of(attendee1, attendee2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/organizer/events/{eventId}/attendees", eventId)
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].fullName").value("John Doe"))
                .andExpect(jsonPath("$.data[1].fullName").value("Jane Smith"))
                .andExpect(jsonPath("$.message").value("Attendees retrieved"));
    }

    @Test
    void testUpdateAttendeeStatus_Success() throws Exception {
        // Arrange
        UUID attendeeId = UUID.randomUUID();
        OrganizerAttendeeResponse response = new OrganizerAttendeeResponse(
                attendeeId,
                eventId,
                "John Doe",
                "john@example.com",
                "+1234567890",
                "ATTENDED",
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600),
                Instant.now(),
                100,
                1001
        );
        
        given(organizerService.updateAttendeeStatus(organizerUserId, eventId, attendeeId, "ATTENDED"))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/organizer/events/{eventId}/attendees/{attendeeId}/status", eventId, attendeeId)
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "ATTENDED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ATTENDED"))
                .andExpect(jsonPath("$.message").value("Attendee status updated"));
    }

    @Test
    void testGetStaff_Success() throws Exception {
        // Arrange
        OrganizerStaffResponse staff1 = new OrganizerStaffResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Staff Member 1",
                "staff1@example.com",
                "+1234567890",
                true
        );
        
        OrganizerStaffResponse staff2 = new OrganizerStaffResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Staff Member 2",
                "staff2@example.com",
                "+1234567891",
                false
        );
        
        given(organizerService.staff(organizerUserId, eventId))
                .willReturn(List.of(staff1, staff2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/organizer/events/{eventId}/staff", eventId)
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].fullName").value("Staff Member 1"))
                .andExpect(jsonPath("$.data[1].fullName").value("Staff Member 2"))
                .andExpect(jsonPath("$.message").value("Staff retrieved"));
    }

    @Test
    void testAddStaff_Success() throws Exception {
        // Arrange
        UUID staffUserId = UUID.randomUUID();
        StaffAssignmentRequest request = new StaffAssignmentRequest(staffUserId, true);
        OrganizerStaffResponse response = new OrganizerStaffResponse(
                UUID.randomUUID(),
                staffUserId,
                "New Staff Member",
                "newstaff@example.com",
                "+1234567890",
                true
        );
        
        given(organizerService.addStaff(organizerUserId, eventId, staffUserId, request))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/organizer/events/{eventId}/staff", eventId)
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "staffUserId": "%s",
                                    "isActive": true
                                }
                                """.formatted(staffUserId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staffUserId").value(staffUserId.toString()))
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andExpect(jsonPath("$.message").value("Staff assigned"));
    }

    @Test
    void testUpdateStaffAssignment_Success() throws Exception {
        // Arrange
        UUID assignmentId = UUID.randomUUID();
        StaffAssignmentUpdateRequest request = new StaffAssignmentUpdateRequest(false);
        OrganizerStaffResponse response = new OrganizerStaffResponse(
                assignmentId,
                UUID.randomUUID(),
                "Staff Member",
                "staff@example.com",
                "+1234567890",
                false
        );
        
        given(organizerService.updateStaffAssignment(organizerUserId, eventId, assignmentId, request))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/organizer/events/{eventId}/staff/{assignmentId}", eventId, assignmentId)
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "isActive": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false))
                .andExpect(jsonPath("$.message").value("Staff assignment updated"));
    }

    @Test
    void testGetTransactions_Success() throws Exception {
        // Arrange
        OrganizerTransactionResponse tx1 = new OrganizerTransactionResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Attendee 1",
                "REGISTRATION",
                "APPROVED",
                10,
                "Ticket purchase",
                Instant.now()
        );
        
        OrganizerTransactionResponse tx2 = new OrganizerTransactionResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Attendee 2",
                "BENEFIT_CLAIM",
                "APPROVED",
                -5,
                "Free coffee",
                Instant.now().minusSeconds(3600)
        );
        
        given(organizerService.transactions(organizerUserId, eventId))
                .willReturn(List.of(tx1, tx2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/organizer/events/{eventId}/transactions", eventId)
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].transactionType").value("REGISTRATION"))
                .andExpect(jsonPath("$.data[1].transactionType").value("BENEFIT_CLAIM"))
                .andExpect(jsonPath("$.message").value("Transactions retrieved"));
    }

    @Test
    void testGetTransactionById_Success() throws Exception {
        // Arrange
        UUID transactionId = UUID.randomUUID();
        TransactionResponse response = new TransactionResponse(
                transactionId,
                eventId,
                "Tech Conference 2026",
                UUID.randomUUID(),
                "John Doe",
                UUID.randomUUID(),
                RegistrationStatus.REGISTERED.name(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Ticket Scan",
                TransactionType.REGISTRATION,
                TransactionResult.APPROVED,
                10,
                "Ticket purchase",
                Instant.now()
        );
        
        given(organizerService.transaction(organizerUserId, eventId, transactionId))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/v1/organizer/events/{eventId}/transactions/{transactionId}", eventId, transactionId)
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionType").value("REGISTRATION"))
                .andExpect(jsonPath("$.data.pointsDelta").value(10))
                .andExpect(jsonPath("$.message").value("Transaction retrieved"));
    }

    @Test
    void testUpdateRewardSettings_Success() throws Exception {
        // Arrange
        RewardSettingsRequest request = new RewardSettingsRequest(5, 10, 2);
        OrganizerEventResponse response = new OrganizerEventResponse(
                eventId,
                "Tech Conference 2026",
                "Organizer",
                "Sep 15, 2026 - Sep 20, 2026",
                "Sep 15, 2026",
                "Convention Center",
                "APPROVED",
                "2026-09-01",
                null,
                100,
                75,
                true,
                true
        );
        
        given(organizerService.updateRewardSettings(organizerUserId, eventId, request))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/organizer/events/{eventId}/reward-settings", eventId)
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "pointsPerRegistration": 5,
                                    "pointsPerAttendance": 10,
                                    "pointsPerBenefitClaim": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Tech Conference 2026"))
                .andExpect(jsonPath("$.message").value("Reward settings updated"));
    }

    @Test
    void testGetBenefitClaims_Success() throws Exception {
        // Arrange
        BenefitClaimResponse claim1 = new BenefitClaimResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Attendee 1",
                "Free Coffee",
                UUID.randomUUID(),
                1001,
                Instant.now().minusSeconds(7200),
                TransactionResult.APPROVED
        );
        
        BenefitClaimResponse claim2 = new BenefitClaimResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Attendee 2",
                "Free T-Shirt",
                UUID.randomUUID(),
                1002,
                Instant.now().minusSeconds(3600),
                TransactionResult.APPROVED
        );
        
        given(organizerService.benefitClaims(organizerUserId, eventId))
                .willReturn(List.of(claim1, claim2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/organizer/events/{eventId}/benefit-claims", eventId)
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].benefitName").value("Free Coffee"))
                .andExpect(jsonPath("$.data[1].benefitName").value("Free T-Shirt"))
                .andExpect(jsonPath("$.message").value("Benefit claims retrieved"));
    }

    @Test
    void testSearchAttendees_Success() throws Exception {
        // Arrange
        OrganizerAttendeeResponse attendee = new OrganizerAttendeeResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "John Doe",
                "john@example.com",
                "+1234567890",
                "REGISTERED",
                null,
                null,
                null,
                100,
                1001
        );
        
        given(organizerService.searchAttendees(organizerUserId, eventId, "John"))
                .willReturn(List.of(attendee));

        // Act & Assert
        mockMvc.perform(get("/api/v1/organizer/events/{eventId}/attendees/search", eventId)
                        .header("Authorization", validToken)
                        .param("query", "John"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fullName").value("John Doe"))
                .andExpect(jsonPath("$.message").value("Attendees searched"));
    }

    @Test
    void testGetUserSearch_Success() throws Exception {
        // Arrange
        UserSearchResponse user1 = new UserSearchResponse(
                UUID.randomUUID(),
                "john@example.com",
                "John Doe",
                AccountRole.ATTENDEE
        );
        
        UserSearchResponse user2 = new UserSearchResponse(
                UUID.randomUUID(),
                "jane@example.com",
                "Jane Smith",
                AccountRole.STAFF
        );
        
        given(organizerService.searchUsers(organizerUserId, "John"))
                .willReturn(List.of(user1, user2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/organizer/users/search")
                        .header("Authorization", validToken)
                        .param("query", "John"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].fullName").value("John Doe"))
                .andExpect(jsonPath("$.data[1].fullName").value("Jane Smith"))
                .andExpect(jsonPath("$.message").value("Users searched"));
    }
}