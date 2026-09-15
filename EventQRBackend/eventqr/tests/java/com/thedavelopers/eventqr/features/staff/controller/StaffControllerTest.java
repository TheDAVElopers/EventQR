package com.thedavelopers.eventqr.features.staff.controller;

import com.thedavelopers.eventqr.features.staff.dto.StaffDtos.EventResponse;
import com.thedavelopers.eventqr.features.staff.dto.StaffDtos.StaffAssignmentResponse;
import com.thedavelopers.eventqr.features.staff.dto.StaffDtos.StaffDashboardResponse;
import com.thedavelopers.eventqr.features.staff.dto.StaffDtos.StaffScanLogResponse;
import com.thedavelopers.eventqr.features.staff.dto.StaffDtos.TransactionResponse;
import com.thedavelopers.eventqr.features.staff.service.StaffService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.QrDisplayStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.constants.ScanPurpose;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
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
 * Test class for StaffController.
 */
@WebMvcTest(StaffController.class)
class StaffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StaffService staffService;

    @MockBean
    private JwtService jwtService;

    private UUID staffUserId;
    private UUID eventId;
    private String validToken = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        staffUserId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(staffUserId);
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.STAFF);
    }

    @Test
    void testGetAssignedEvents_Success() throws Exception {
        // Arrange
        StaffAssignmentResponse assignment1 = new StaffAssignmentResponse(
                UUID.randomUUID(),
                eventId,
                "Tech Conference 2026",
                true,
                Instant.now().minusSeconds(86400)
        );
        
        StaffAssignmentResponse assignment2 = new StaffAssignmentResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Music Festival 2026",
                true,
                Instant.now().minusSeconds(43200)
        );
        
        given(staffService.assignedEvents(staffUserId))
                .willReturn(List.of(assignment1, assignment2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/staff/events")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].eventName").value("Tech Conference 2026"))
                .andExpect(jsonPath("$.data[1].eventName").value("Music Festival 2026"))
                .andExpect(jsonPath("$.message").value("Assigned events retrieved"));
    }

    @Test
    void testGetAssignedEvents_Forbidden_NotStaff() throws Exception {
        // Arrange
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(UUID.randomUUID());
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.ATTENDEE);

        // Act & Assert
        mockMvc.perform(get("/api/v1/staff/events")
                        .header("Authorization", validToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetEventDetails_Success() throws Exception {
        // Arrange
        EventResponse response = new EventResponse(
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
        
        given(staffService.event(staffUserId, eventId))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/v1/staff/events/{eventId}", eventId)
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Tech Conference 2026"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.message").value("Event details retrieved"));
    }

    @Test
    void testGetEventDetails_Forbidden_NotAssigned() throws Exception {
        // Arrange
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(staffUserId);
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.STAFF);
        
        // staffService.event will throw ForbiddenException implicitly when not assigned

        // Act & Assert
        mockMvc.perform(get("/api/v1/staff/events/{eventId}", eventId)
                        .header("Authorization", validToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetDashboard_Success() throws Exception {
        // Arrange
        StaffDashboardResponse response = new StaffDashboardResponse(
                5, // eventsAssignedCount
                3, // eventsScannedTodayCount
                List.of(
                        new StaffAssignmentResponse(
                                UUID.randomUUID(),
                                eventId,
                                "Tech Conference 2026",
                                true,
                                Instant.now().minusSeconds(86400)
                        ),
                        new StaffAssignmentResponse(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "Music Festival 2026",
                                false,
                                Instant.now().minusSeconds(43200)
                        )
                ),
                List.of(
                        new StaffScanLogResponse(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "John Doe",
                                "Ticket Scan",
                                ScanPurpose.ENTRY.name(),
                                QrDisplayStatus.NOT_SHOWN,
                                Instant.now()
                        ),
                        new StaffScanLogResponse(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "Jane Smith",
                                "Benefit Scan",
                                ScanPurpose.BENEFIT_CLAIM.name(),
                                QrDisplayStatus.SHOWN_ONCE,
                                Instant.now().minusSeconds(3600)
                        )
                ),
                List.of(
                        new TransactionResponse(
                                UUID.randomUUID(),
                                eventId,
                                "Tech Conference 2026",
                                UUID.randomUUID(),
                                "Attendee 1",
                                RegistrationStatus.REGISTERED.name(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "Ticket Scan",
                                TransactionType.REGISTRATION,
                                TransactionResult.APPROVED,
                                10,
                                "Ticket purchase",
                                Instant.now()
                        ),
                        new TransactionResponse(
                                UUID.randomUUID(),
                                eventId,
                                "Tech Conference 2026",
                                UUID.randomUUID(),
                                "Attendee 2",
                                RegistrationStatus.ATTENDED.name(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "Benefit Claim",
                                TransactionType.BENEFIT_CLAIM,
                                TransactionResult.APPROVED,
                                -5,
                                "Free coffee",
                                Instant.now().minusSeconds(3600)
                        )
                )
        );
        
        given(staffService.dashboard(staffUserId))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/v1/staff/dashboard")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventsAssignedCount").value(5))
                .andExpect(jsonPath("$.data.eventsScannedTodayCount").value(3))
                .andExpect(jsonPath("$.data.recentAssignments.length()").value(2))
                .andExpect(jsonPath("$.data.recentScans.length()").value(2))
                .andExpect(jsonPath("$.data.recentTransactions.length()").value(2))
                .andExpect(jsonPath("$.message").value("Dashboard data retrieved"));
    }

    @Test
    void testGetScanLogs_Success() throws Exception {
        // Arrange
        StaffScanLogResponse scan1 = new StaffScanLogResponse(
                UUID.randomUUID(),
                eventId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "John Doe",
                "Ticket Scan",
                ScanPurpose.ENTRY.name(),
                QrDisplayStatus.NOT_SHOWN,
                Instant.now()
        );
        
        StaffScanLogResponse scan2 = new StaffScanLogResponse(
                UUID.randomUUID(),
                eventId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Jane Smith",
                "Benefit Scan",
                ScanPurpose.BENEFIT_CLAIM.name(),
                QrDisplayStatus.SHOWN_ONCE,
                Instant.now().minusSeconds(3600)
        );
        
        given(staffService.scanLogs(staffUserId, eventId))
                .willReturn(List.of(scan1, scan2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/staff/events/{eventId}/scans", eventId)
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].attendeeName").value("John Doe"))
                .andExpect(jsonPath("$.data[1].attendeeName").value("Jane Smith"))
                .andExpect(jsonPath("$.message").value("Scan logs retrieved"));
    }

    @Test
    void testGetScanLogs_Forbidden_NotAssigned() throws Exception {
        // Arrange
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(staffUserId);
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.STAFF);
        
        // staffService.scanLogs will throw ForbiddenException implicitly when not assigned

        // Act & Assert
        mockMvc.perform(get("/api/v1/staff/events/{eventId}/scans", eventId)
                        .header("Authorization", validToken))
                .andExpect(status().isForbidden());
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
        
        given(staffService.transaction(staffUserId, eventId, transactionId))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/v1/staff/events/{eventId}/transactions/{transactionId}", eventId, transactionId)
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionType").value("REGISTRATION"))
                .andExpect(jsonPath("$.data.pointsDelta").value(10))
                .andExpect(jsonPath("$.message").value("Transaction retrieved"));
    }

    @Test
    void testGetTransactionById_Forbidden_NotAssigned() throws Exception {
        // Arrange
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(staffUserId);
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.STAFF);
        
        UUID transactionId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(get("/api/v1/staff/events/{eventId}/transactions/{transactionId}", eventId, transactionId)
                        .header("Authorization", validToken))
                .andExpect(status().isForbidden());
    }
}