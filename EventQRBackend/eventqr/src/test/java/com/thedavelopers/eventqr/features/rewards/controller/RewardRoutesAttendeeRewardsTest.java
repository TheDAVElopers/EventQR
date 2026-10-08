package com.thedavelopers.eventqr.features.rewards.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.rewards.service.RewardService;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/** attendees/me/events/{eventId}/rewards: claimable by default, all rewards with includeUnavailable=true. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RewardRoutesAttendeeRewardsTest {

    @Mock
    private RewardService rewardService;
    @Mock
    private JwtService jwtService;
    @Mock
    private EventService eventService;
    @Mock
    private EventStaffAssignmentRepository eventStaffAssignmentRepository;
    @Mock
    private EventRegistrationRepository eventRegistrationRepository;

    private MockMvc mockMvc;
    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new RewardRoutesController(rewardService, jwtService, eventService, eventStaffAssignmentRepository,
                                eventRegistrationRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        given(jwtService.extractUserIdFromBearer(any())).willReturn(userId);
        // Registered by default; any registration status (incl. cancelled) satisfies the exists query.
        given(eventRegistrationRepository.existsByEventIdAndAttendeeUserId(eventId, userId)).willReturn(true);
    }

    @Test
    void defaultReturnsClaimableOnly() throws Exception {
        given(rewardService.findClaimableRewards(eventId)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/attendees/me/events/{id}/rewards", eventId)
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());

        verify(rewardService).findClaimableRewards(eventId);
        verify(rewardService, never()).findRewards(any());
    }

    @Test
    void includeUnavailableReturnsAllRewards() throws Exception {
        given(rewardService.findRewards(eventId)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/attendees/me/events/{id}/rewards", eventId)
                        .param("includeUnavailable", "true")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());

        verify(rewardService).findRewards(eventId);
        verify(rewardService, never()).findClaimableRewards(any());
    }

    @Test
    void unregisteredUserForbiddenInDefaultMode() throws Exception {
        given(eventRegistrationRepository.existsByEventIdAndAttendeeUserId(eventId, userId)).willReturn(false);

        mockMvc.perform(get("/api/v1/attendees/me/events/{id}/rewards", eventId)
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isForbidden());

        verify(rewardService, never()).findClaimableRewards(any());
        verify(rewardService, never()).findRewards(any());
    }

    @Test
    void unregisteredUserForbiddenWithIncludeUnavailable() throws Exception {
        given(eventRegistrationRepository.existsByEventIdAndAttendeeUserId(eventId, userId)).willReturn(false);

        mockMvc.perform(get("/api/v1/attendees/me/events/{id}/rewards", eventId)
                        .param("includeUnavailable", "true")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isForbidden());

        verify(rewardService, never()).findClaimableRewards(any());
        verify(rewardService, never()).findRewards(any());
    }

    @Test
    void cancelledRegistrationUserStillAllowed() throws Exception {
        // Status-agnostic exists query: a cancelled registration row still returns true.
        given(eventRegistrationRepository.existsByEventIdAndAttendeeUserId(eventId, userId)).willReturn(true);
        given(rewardService.findRewards(eventId)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/attendees/me/events/{id}/rewards", eventId)
                        .param("includeUnavailable", "true")
                        .header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }
}
