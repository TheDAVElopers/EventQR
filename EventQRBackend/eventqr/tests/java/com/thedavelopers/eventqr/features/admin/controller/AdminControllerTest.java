package com.thedavelopers.eventqr.features.admin.controller;

import com.thedavelopers.eventqr.features.admin.dto.AdminDtos.DashboardStatsResponse;
import com.thedavelopers.eventqr.features.admin.dto.AdminDtos.EventListingResponse;
import com.thedavelopers.eventqr.features.admin.dto.AdminDtos.TransactionListingResponse;
import com.thedavelopers.eventqr.features.admin.dto.AdminDtos.UserListingResponse;
import com.thedavelopers.eventqr.features.admin.service.AdminService;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationResponse;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
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
 * Test class for AdminController.
 */
@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private JwtService jwtService;

    private UUID adminUserId;
    private String validToken = "Bearer valid.jwt.token";

    @BeforeEach
    void setUp() {
        adminUserId = UUID.randomUUID();
        
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(adminUserId);
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.ADMIN);
    }

    @Test
    void testGetDashboard_Success() throws Exception {
        // Arrange
        DashboardStatsResponse response = new DashboardStatsResponse(
                100, // totalUsers
                50,  // totalEvents
                200, // totalRegistrations
                300  // totalTransactions
        );
        
        given(adminService.dashboardStats())
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(100))
                .andExpect(jsonPath("$.data.totalEvents").value(50))
                .andExpect(jsonPath("$.data.totalRegistrations").value(200))
                .andExpect(jsonPath("$.data.totalTransactions").value(300))
                .andExpect(jsonPath("$.message").value("Dashboard stats retrieved"));
    }

    @Test
    void testGetDashboard_Forbidden_NotAdmin() throws Exception {
        // Arrange
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(UUID.randomUUID());
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.ORGANIZER);

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", validToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testListEvents_Success() throws Exception {
        // Arrange
        EventListingResponse event1 = new EventListingResponse(
                UUID.randomUUID(),
                "Tech Conference 2026",
                "Organizer",
                "Sep 15, 2026",
                "Sep 20, 2026",
                "APPROVED",
                100,
                75
        );
        
        EventListingResponse event2 = new EventListingResponse(
                UUID.randomUUID(),
                "Music Festival 2026",
                "Organizer 2",
                "Oct 01, 2026",
                "Oct 05, 2026",
                "ACTIVE",
                200,
                150
        );
        
        given(adminService.listEvents())
                .willReturn(List.of(event1, event2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/events")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].title").value("Tech Conference 2026"))
                .andExpect(jsonPath("$.data[1].title").value("Music Festival 2026"))
                .andExpect(jsonPath("$.message").value("Events listed"));
    }

    @Test
    void testListEvents_Paginated() throws Exception {
        // Arrange
        Page<EventListingResponse> page = new PageImpl<>(List.of(
                new EventListingResponse(
                        UUID.randomUUID(),
                        "Event 1",
                        "Organizer 1",
                        "Jan 01, 2026",
                        "Jan 05, 2026",
                        "APPROVED",
                        50,
                        25
                )
        ));
        
        given(adminService.listEventsPaginated(PageRequest.of(0, 10)))
                .willReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/events")
                        .header("Authorization", validToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Event 1"))
                .andExpect(jsonPath("$.message").value("Events listed"));
    }

    @Test
    void testListUsers_Success() throws Exception {
        // Arrange
        UserListingResponse user1 = new UserListingResponse(
                UUID.randomUUID(),
                "john@example.com",
                "John Doe",
                "+1234567890",
                AccountRole.ATTENDEE,
                "ACTIVE"
        );
        
        UserListingResponse user2 = new UserListingResponse(
                UUID.randomUUID(),
                "jane@example.com",
                "Jane Smith",
                "+1234567891",
                AccountRole.STAFF,
                "SUSPENDED"
        );
        
        given(adminService.listUsers())
                .willReturn(List.of(user1, user2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].email").value("john@example.com"))
                .andExpect(jsonPath("$.data[1].email").value("jane@example.com"))
                .andExpect(jsonPath("$.message").value("Users listed"));
    }

    @Test
    void testListUsers_Paginated() throws Exception {
        // Arrange
        Page<UserListingResponse> page = new PageImpl<>(List.of(
                new UserListingResponse(
                        UUID.randomUUID(),
                        "test@example.com",
                        "Test User",
                        "+1234567890",
                        AccountRole.ATTENDEE,
                        "ACTIVE"
                )
        ));
        
        given(adminService.listUsersPaginated(PageRequest.of(0, 10)))
                .willReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", validToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].email").value("test@example.com"))
                .andExpect(jsonPath("$.message").value("Users listed"));
    }

    @Test
    void testUpdateUserStatus_Success() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserListingResponse response = new UserListingResponse(
                userId,
                "test@example.com",
                "Test User",
                "+1234567890",
                AccountRole.ATTENDEE,
                "SUSPENDED"
        );
        
        given(adminService.updateUserStatus(userId, "SUSPENDED"))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", userId)
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "SUSPENDED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.message").value("User status updated"));
    }

    @Test
    void testUpdateUserStatus_Forbidden_NotAdmin() throws Exception {
        // Arrange
        given(jwtService.extractUserIdFromBearer(validToken))
                .willReturn(UUID.randomUUID());
        given(jwtService.extractRoleFromBearer(validToken))
                .willReturn(AccountRole.ORGANIZER);
        
        UUID userId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", userId)
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "SUSPENDED"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void testListRegistrations_Success() throws Exception {
        // Arrange
        RegistrationResponse reg1 = new RegistrationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "attendee@example.com",
                "John Doe",
                RegistrationStatus.REGISTERED,
                Instant.now(),
                UUID.randomUUID(),
                "Tech Conference 2026",
                "Convention Center",
                Instant.now().plusSeconds(86400),
                Instant.now().plusSeconds(172800),
                "+1234567890",
                null,
                null,
                null,
                100,
                1001,
                "ATTENDEE"
        );
        
        RegistrationResponse reg2 = new RegistrationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "jane@example.com",
                "Jane Smith",
                RegistrationStatus.ATTENDED,
                Instant.now().minusSeconds(7200),
                UUID.randomUUID(),
                "Tech Conference 2026",
                "Convention Center",
                Instant.now().minusSeconds(3600),
                Instant.now(),
                Instant.now(),
                "+1234567891",
                Instant.now().minusSeconds(3600),
                Instant.now(),
                Instant.now(),
                150,
                1002,
                "ATTENDEE"
        );
        
        given(adminService.listRegistrations())
                .willReturn(List.of(reg1, reg2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/registrations")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].attendeeName").value("John Doe"))
                .andExpect(jsonPath("$.data[1].attendeeName").value("Jane Smith"))
                .andExpect(jsonPath("$.message").value("Registrations listed"));
    }

    @Test
    void testListRegistrations_Paginated() throws Exception {
        // Arrange
        Page<RegistrationResponse> page = new PageImpl<>(List.of(
                new RegistrationResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "test@example.com",
                        "Test User",
                        RegistrationStatus.REGISTERED,
                        Instant.now(),
                        UUID.randomUUID(),
                        "Test Event",
                        "Test Location",
                        Instant.now().plusSeconds(86400),
                        Instant.now().plusSeconds(172800),
                        "+1234567890",
                        null,
                        null,
                        null,
                        100,
                        1001,
                        "ATTENDEE"
                )
        ));
        
        given(adminService.listRegistrationsPaginated(PageRequest.of(0, 10)))
                .willReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/registrations")
                        .header("Authorization", validToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].attendeeName").value("Test User"))
                .andExpect(jsonPath("$.message").value("Registrations listed"));
    }

    @Test
    void testListTransactions_Success() throws Exception {
        // Arrange
        TransactionResponse tx1 = new TransactionResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
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
        
        TransactionResponse tx2 = new TransactionResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Tech Conference 2026",
                UUID.randomUUID(),
                "Jane Smith",
                UUID.randomUUID(),
                RegistrationStatus.ATTENDED.name(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Benefit Claim",
                TransactionType.BENEFIT_CLAIM,
                TransactionResult.APPROVED,
                -5,
                "Free coffee",
                Instant.now().minusSeconds(3600)
        );
        
        given(adminService.listTransactions())
                .willReturn(List.of(tx1, tx2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/transactions")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].transactionType").value("REGISTRATION"))
                .andExpect(jsonPath("$.data[1].transactionType").value("BENEFIT_CLAIM"))
                .andExpect(jsonPath("$.message").value("Transactions listed"));
    }

    @Test
    void testListTransactions_Paginated() throws Exception {
        // Arrange
        Page<TransactionResponse> page = new PageImpl<>(List.of(
                new TransactionResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Test Event",
                        UUID.randomUUID(),
                        "Test User",
                        UUID.randomUUID(),
                        RegistrationStatus.REGISTERED.name(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Test Transaction",
                        TransactionType.REGISTRATION,
                        TransactionResult.APPROVED,
                        10,
                        "Test reason",
                        Instant.now()
                )
        ));
        
        given(adminService.listTransactionsPaginated(PageRequest.of(0, 10)))
                .willReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/admin/transactions")
                        .header("Authorization", validToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].transactionType").value("REGISTRATION"))
                .andExpect(jsonPath("$.message").value("Transactions listed"));
    }
}