package com.thedavelopers.eventqr.features.registrations.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.qrcredentials.service.QrCredentialService;
import com.thedavelopers.eventqr.features.qremail.service.QREmailService;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

class RegistrationControllerTest {

    private static final String AUTH = "Bearer token";

    private RegistrationService registrationService;
    private JwtService jwtService;
    private MockMvc mvc;
    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        registrationService = mock(RegistrationService.class);
        jwtService = mock(JwtService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RegistrationController(registrationService,
                        mock(QrCredentialService.class), mock(QREmailService.class), jwtService,
                        mock(EventService.class), mock(EventStaffAssignmentRepository.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(jwtService.extractUserIdFromBearer(AUTH)).thenReturn(userId);
        when(jwtService.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.ATTENDEE);
    }

    private String body(String email) {
        return "{\"eventId\":\"" + eventId + "\",\"email\":\"" + email + "\",\"fullName\":\"Jane Doe\"}";
    }

    @Test
    void registrationIsMadeAsTheCallerWithTheTokenRole() throws Exception {
        mvc.perform(post("/api/v1/registrations").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(body("jane@example.com")))
                .andExpect(status().isOk());

        verify(registrationService).registerAs(any(RegistrationRequest.class), eq(userId), eq(AccountRole.ATTENDEE), any());
    }

    @Test
    void anotherUsersEmailIsA403WithAGenericMessage() throws Exception {
        when(registrationService.registerAs(any(), any(), any(), any()))
                .thenThrow(new ForbiddenException("You can only register using your own account email"));

        mvc.perform(post("/api/v1/registrations").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(body("victim@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only register using your own account email"));
    }

    @Test
    void anAdminCallerIsPassedThroughWithTheAdminRole() throws Exception {
        when(jwtService.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.ADMIN);

        mvc.perform(post("/api/v1/registrations").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(body("someone@example.com")))
                .andExpect(status().isOk());

        verify(registrationService).registerAs(any(RegistrationRequest.class), eq(userId), eq(AccountRole.ADMIN), any());
    }

    @Test
    void theEventRegistrationListClampsPageAndSize() throws Exception {
        when(jwtService.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.ADMIN);
        when(registrationService.findByEvent(eq(eventId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 1), 0));

        mvc.perform(get("/api/v1/registrations/event/{id}", eventId).header("Authorization", AUTH)
                        .param("page", "-5").param("size", "100000"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/registrations/event/{id}", eventId).header("Authorization", AUTH)
                        .param("size", "0"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(registrationService, org.mockito.Mockito.times(2)).findByEvent(eq(eventId), pageable.capture());
        org.assertj.core.api.Assertions.assertThat(pageable.getAllValues().get(0).getPageNumber()).isZero();
        org.assertj.core.api.Assertions.assertThat(pageable.getAllValues().get(0).getPageSize()).isEqualTo(100);
        org.assertj.core.api.Assertions.assertThat(pageable.getAllValues().get(1).getPageSize()).isEqualTo(1);
    }
}
