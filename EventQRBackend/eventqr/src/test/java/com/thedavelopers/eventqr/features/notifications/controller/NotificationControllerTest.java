package com.thedavelopers.eventqr.features.notifications.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.notifications.service.NotificationService;
import com.thedavelopers.eventqr.shared.security.JwtService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private JwtService jwtService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService, jwtService)).build();
        when(jwtService.extractUserIdFromBearer(org.mockito.ArgumentMatchers.any())).thenReturn(UUID.randomUUID());
        when(notificationService.findByRecipientFiltered(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(notificationService.findByRecipient(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
    }

    @Test
    void mine_withStatusParam_works() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                .param("status", "PENDING")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void mine_withEventId_filterParam_works() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                .param("eventId", UUID.randomUUID().toString())
                .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void mine_withNotificationType_filterParam_works() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                .param("notificationType", "REGISTRATION_NEW")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void noRecreatedPostEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/notifications"))
                .andExpect(status().isMethodNotAllowed());
    }
}
