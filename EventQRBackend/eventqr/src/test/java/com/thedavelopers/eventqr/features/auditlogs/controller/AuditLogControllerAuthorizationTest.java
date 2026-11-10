package com.thedavelopers.eventqr.features.auditlogs.controller;

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

import com.thedavelopers.eventqr.features.auditlogs.service.AuditLogService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * B3: audit log injection is admin-only.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogControllerAuthorizationTest {

    @Mock private AuditLogService auditLogService;
    @Mock private JwtService jwtService;

    private MockMvc mockMvc;
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuditLogController(auditLogService, jwtService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        given(jwtService.extractUserIdFromBearer(any())).willReturn(callerId);
    }

    private void actingAs(AccountRole role) {
        given(jwtService.extractRoleFromBearer(any())).willReturn(role);
    }

    private String body() {
        return "{\"action\":\"MANUAL_LOG\",\"details\":\"injected\",\"eventId\":null,\"targetUserId\":null}";
    }

    @Test
    void create_asAttendee_isForbidden() throws Exception {
        actingAs(AccountRole.ATTENDEE);

        mockMvc.perform(post("/api/v1/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());

        verify(auditLogService, never()).log(any(com.thedavelopers.eventqr.features.auditlogs.model.dto.AuditLogRequest.class), any(), any());
    }

    @Test
    void create_asOrganizer_isForbidden() throws Exception {
        actingAs(AccountRole.ORGANIZER);

        mockMvc.perform(post("/api/v1/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_asAdmin_isAllowed() throws Exception {
        actingAs(AccountRole.ADMIN);

        mockMvc.perform(post("/api/v1/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(auditLogService).log(any(com.thedavelopers.eventqr.features.auditlogs.model.dto.AuditLogRequest.class), any(), any());
    }

    @Test
    void create_asSuperAdmin_isAllowed() throws Exception {
        actingAs(AccountRole.SUPER_ADMIN);

        mockMvc.perform(post("/api/v1/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }
}
