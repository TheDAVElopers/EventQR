package com.thedavelopers.eventqr.features.rewards.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.rewards.service.RewardService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/** Organizer reward routes: writes need owner/admin or canManageRewards staff; reads need any active assignment. */
class RewardRoutesWriteAccessTest {

    private final UUID eventId = UUID.randomUUID();
    private final UUID rewardId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private RewardService rewardService;
    private JwtService jwtService;
    private EventService eventService;
    private EventStaffAssignmentRepository staffRepo;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        rewardService = mock(RewardService.class);
        jwtService = mock(JwtService.class);
        eventService = mock(EventService.class);
        staffRepo = mock(EventStaffAssignmentRepository.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RewardRoutesController(rewardService, jwtService, eventService,
                        staffRepo, mock(EventRegistrationRepository.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        given(jwtService.extractUserIdFromBearer(any())).willReturn(callerId);
    }

    private void actingAs(AccountRole role) {
        given(jwtService.extractRoleFromBearer(any())).willReturn(role);
    }

    private void staffAssigned(boolean canManageRewards) {
        EventStaffAssignment a = new EventStaffAssignment();
        a.setEventId(eventId);
        a.setStaffUserId(callerId);
        a.setActive(true);
        a.setCanManageRewards(canManageRewards);
        given(staffRepo.findByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId)).willReturn(Optional.of(a));
        given(staffRepo.existsByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId)).willReturn(true);
    }

    private void eventOwnedBy(UUID owner) {
        given(eventService.findOne(eventId)).willReturn(new EventResponse(eventId, "E", null, null, "L",
                null, null, null, null, 100, 0, EventStatus.APPROVED, false, owner, null, null, null));
    }

    private String body() {
        return "{\"eventId\":\"" + eventId + "\",\"name\":\"Coffee\",\"pointsRequired\":10,\"allowDuplicateClaims\":false}";
    }

    private int doPost() throws Exception {
        return mockMvc.perform(post("/api/v1/organizer/events/{e}/rewards", eventId)
                .contentType(MediaType.APPLICATION_JSON).content(body()).header("Authorization", "Bearer t"))
                .andReturn().getResponse().getStatus();
    }

    private int doPatch() throws Exception {
        return mockMvc.perform(patch("/api/v1/organizer/events/{e}/rewards/{r}", eventId, rewardId)
                .contentType(MediaType.APPLICATION_JSON).content(body()).header("Authorization", "Bearer t"))
                .andReturn().getResponse().getStatus();
    }

    private int doDelete() throws Exception {
        return mockMvc.perform(delete("/api/v1/organizer/events/{e}/rewards/{r}", eventId, rewardId)
                .header("Authorization", "Bearer t")).andReturn().getResponse().getStatus();
    }

    private void assertAll(int expected) throws Exception {
        org.assertj.core.api.Assertions.assertThat(new int[] {doPost(), doPatch(), doDelete()})
                .containsOnly(expected);
    }

    @Test
    void staffWithoutCanManageRewardsIsForbiddenOnAllWrites() throws Exception {
        actingAs(AccountRole.STAFF);
        staffAssigned(false);
        assertAll(403);
        verify(rewardService, never()).saveReward(any());
        verify(rewardService, never()).updateReward(any(), any(), any());
        verify(rewardService, never()).deleteReward(any(), any());
    }

    @Test
    void staffWithCanManageRewardsSucceeds() throws Exception {
        actingAs(AccountRole.STAFF);
        staffAssigned(true);
        assertAll(200);
    }

    @Test
    void ownerSucceeds() throws Exception {
        actingAs(AccountRole.ORGANIZER);
        eventOwnedBy(callerId);
        assertAll(200);
    }

    @Test
    void adminSucceeds() throws Exception {
        actingAs(AccountRole.ADMIN);
        assertAll(200);
    }

    @Test
    void otherOrganizerIsForbidden() throws Exception {
        actingAs(AccountRole.ORGANIZER);
        eventOwnedBy(otherUserId);
        assertAll(403);
    }

    @Test
    void unassignedStaffIsForbidden() throws Exception {
        actingAs(AccountRole.STAFF);
        given(staffRepo.findByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId)).willReturn(Optional.empty());
        assertAll(403);
    }

    @Test
    void readRoutesStillWorkForAssignedStaffWithoutManageFlag() throws Exception {
        actingAs(AccountRole.STAFF);
        staffAssigned(false);
        mockMvc.perform(get("/api/v1/organizer/events/{e}/rewards", eventId).header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/events/{e}/rewards/{r}", eventId, rewardId).header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test
    void negativeLegacyStockQuantityIsRejectedWith400() throws Exception {
        actingAs(AccountRole.ADMIN);
        String bad = "{\"eventId\":\"" + eventId + "\",\"name\":\"Coffee\",\"pointsRequired\":10,"
                + "\"stockQuantity\":-3,\"allowDuplicateClaims\":false}";
        mockMvc.perform(post("/api/v1/organizer/events/{e}/rewards", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content(bad).header("Authorization", "Bearer t"))
                .andExpect(status().isBadRequest());
    }
}
