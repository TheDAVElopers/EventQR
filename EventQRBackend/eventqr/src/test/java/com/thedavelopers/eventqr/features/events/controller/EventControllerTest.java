package com.thedavelopers.eventqr.features.events.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.events.model.dto.EventAvailabilityResponse;
import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.registrations.model.dto.RegistrationRequest;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.exceptions.ConflictException;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventControllerTest {

    private static final String AUTH = "Bearer token";

    @Mock private EventService eventService;
    @Mock private RegistrationService registrationService;
    @Mock private JwtService jwtService;

    private MockMvc mvc;
    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new EventController(eventService, registrationService, jwtService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(jwtService.extractUserIdFromBearer(AUTH)).thenReturn(userId);
        when(jwtService.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.ORGANIZER);
    }

    private static String json(String body) {
        return body.replace('\'', '"');
    }

    private EventResponse eventResponse() {
        return new EventResponse(eventId, "Tech Conf", "desc", "Tech", "Hall A", null, null, null, null, null, 100, 0,
                EventStatus.PENDING_REVIEW, false, userId, null, null, null);
    }

    // ----- create -----

    @Test
    void anOrganizerCanSubmitAnEventAndItIsOwnedByTheCaller() throws Exception {
        when(eventService.create(eq(userId), any())).thenReturn(eventResponse());

        mvc.perform(post("/api/v1/events").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'title':'Tech Conf','capacity':100,'rewardsEnabled':false,'organizerUserId':'" + UUID.randomUUID() + "'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Event submitted"))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));

        // The organizer is taken from the token; an organizerUserId in the body is ignored.
        verify(eventService).create(eq(userId), any());
    }

    @Test
    void anAttendeeCannotCreateAnEvent() throws Exception {
        when(jwtService.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.ATTENDEE);

        mvc.perform(post("/api/v1/events").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'title':'Tech Conf','capacity':100,'rewardsEnabled':false}")))
                .andExpect(status().isForbidden());

        verify(eventService, never()).create(any(), any());
    }

    @Test
    void anEventWithoutATitleOrCapacityIsRejected() throws Exception {
        mvc.perform(post("/api/v1/events").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'title':'  ','rewardsEnabled':false}")))
                .andExpect(status().isBadRequest());

        verify(eventService, never()).create(any(), any());
    }

    // ----- registration -----

    @Test
    void theEventInTheUrlWinsOverTheEventInTheBody() throws Exception {
        UUID otherEvent = UUID.randomUUID();

        mvc.perform(post("/api/v1/events/{id}/registrations", eventId).header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'eventId':'" + otherEvent + "','email':'jane@example.com','fullName':'Jane Doe'}")))
                .andExpect(status().isOk());

        ArgumentCaptor<RegistrationRequest> captured = ArgumentCaptor.forClass(RegistrationRequest.class);
        verify(registrationService).registerAs(captured.capture(), any(), any(), any());
        org.assertj.core.api.Assertions.assertThat(captured.getValue().eventId()).isEqualTo(eventId);
    }

    @Test
    void theRegistrationIsMadeAsTheCallerWithTheCallersRole() throws Exception {
        mvc.perform(post("/api/v1/events/{id}/registrations", eventId).header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'eventId':'" + eventId + "','email':'jane@example.com','fullName':'Jane Doe'}")))
                .andExpect(status().isOk());

        verify(registrationService).registerAs(any(), eq(userId), eq(AccountRole.ORGANIZER), any());
    }

    @Test
    void registeringAnotherUsersEmailTurnsIntoAGeneric403() throws Exception {
        when(registrationService.registerAs(any(), any(), any(), any()))
                .thenThrow(new com.thedavelopers.eventqr.shared.exceptions.ForbiddenException("You can only register using your own account email"));

        mvc.perform(post("/api/v1/events/{id}/registrations", eventId).header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'eventId':'" + eventId + "','email':'victim@example.com','fullName':'Victim'}")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only register using your own account email"));
    }

    @Test
    void aFullEventTurnsIntoA409() throws Exception {
        when(registrationService.registerAs(any(), any(), any(), any())).thenThrow(new ConflictException("Event is at capacity"));

        mvc.perform(post("/api/v1/events/{id}/registrations", eventId).header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'eventId':'" + eventId + "','email':'jane@example.com','fullName':'Jane Doe'}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Event is at capacity"));
    }

    @Test
    void registeringWithABadEmailIsRejectedBeforeTheServiceIsCalled() throws Exception {
        mvc.perform(post("/api/v1/events/{id}/registrations", eventId).header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'eventId':'" + eventId + "','email':'nope','fullName':'Jane Doe'}")))
                .andExpect(status().isBadRequest());

        verify(registrationService, never()).registerAs(any(), any(), any(), any());
    }

    // ----- reading -----

    @Test
    void theListIsPagedWithSensibleDefaults() throws Exception {
        when(eventService.findAllEvents(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(eventResponse()), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("Tech Conf"));

        verify(eventService).findAllEvents(PageRequest.of(0, 20));
    }

    @Test
    void pageAndSizeAreHonoured() throws Exception {
        when(eventService.findAllEvents(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

        mvc.perform(get("/api/v1/events").param("page", "2").param("size", "5")).andExpect(status().isOk());

        verify(eventService).findAllEvents(PageRequest.of(2, 5));
    }

    @Test
    void availabilityIsPublicAndReportsTheRules() throws Exception {
        when(eventService.availability(eventId)).thenReturn(new EventAvailabilityResponse(eventId, 10, 10, false, true,
                false, "Event is at capacity", Instant.now(), null, null));

        mvc.perform(get("/api/v1/events/{id}/availability", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.full").value(true))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.message").value("Event is at capacity"));
    }

    @Test
    void anUnknownEventIsA404() throws Exception {
        when(eventService.findAttendeeEvent(eq(eventId), any()))
                .thenThrow(new com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException("Event not found: " + eventId));

        mvc.perform(get("/api/v1/events/{id}", eventId).header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    @Test
    void theAttendeeViewIsBuiltForTheCallersOwnId() throws Exception {
        mvc.perform(get("/api/v1/events/{id}", eventId).header("Authorization", AUTH)).andExpect(status().isOk());

        verify(eventService).findAttendeeEvent(eventId, userId);
    }
}
