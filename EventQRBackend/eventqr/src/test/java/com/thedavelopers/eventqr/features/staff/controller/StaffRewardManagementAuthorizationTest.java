package com.thedavelopers.eventqr.features.staff.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.events.model.dto.EventResponse;
import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.features.rewards.model.dto.PointBalanceResponse;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResponse;
import com.thedavelopers.eventqr.features.rewards.service.RewardService;
import com.thedavelopers.eventqr.features.scanning.service.ScanPurposeService;
import com.thedavelopers.eventqr.features.transactions.service.TransactionService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.RedemptionStatus;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Scan-less value operations on StaffController (manual points assign/deduct and redeem on an attendee's behalf)
 * require can_manage_rewards for assigned staff, plus bounded point amounts.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaffRewardManagementAuthorizationTest {

    @Mock
    private EventService eventService;
    @Mock
    private RegistrationService registrationService;
    @Mock
    private TransactionService transactionService;
    @Mock
    private RewardService rewardService;
    @Mock
    private EventStaffAssignmentRepository eventStaffAssignmentRepository;
    @Mock
    private ScanPurposeService scanPurposeService;
    @Mock
    private JwtService jwtService;

    private MockMvc mockMvc;

    private final UUID eventId = UUID.randomUUID();
    private final UUID rewardId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID attendeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new StaffController(eventService, registrationService, transactionService,
                                rewardService, eventStaffAssignmentRepository, scanPurposeService, jwtService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        given(rewardService.assignPoints(any(), any(), anyInt(), any(), any()))
                .willReturn(new PointBalanceResponse(eventId, attendeeId, 10));
        given(rewardService.deductPoints(any(), any(), anyInt(), any(), any()))
                .willReturn(new PointBalanceResponse(eventId, attendeeId, 0));
        given(rewardService.redeem(any())).willReturn(new RewardRedemptionResponse(UUID.randomUUID(), eventId,
                attendeeId, rewardId, 10, RedemptionStatus.REDEEMED, Instant.now(), null));
        eventOwnedBy(UUID.randomUUID());
        given(eventStaffAssignmentRepository.findByEventIdAndStaffUserIdAndActiveTrue(any(), any()))
                .willReturn(Optional.empty());
    }

    // --- helpers -----------------------------------------------------------

    private void actingAs(AccountRole role) {
        given(jwtService.extractUserIdFromBearer(any())).willReturn(callerId);
        given(jwtService.extractRoleFromBearer(any())).willReturn(role);
    }

    private void eventOwnedBy(UUID organizerUserId) {
        given(eventService.findOne(eventId)).willReturn(new EventResponse(eventId, "Event", null, null,
                "Location", null, null, null, null, 100, 0, EventStatus.APPROVED, true,
                organizerUserId, null, null, null));
    }

    private void assignment(boolean canManageRewards) {
        EventStaffAssignment assignment = new EventStaffAssignment();
        assignment.setEventId(eventId);
        assignment.setStaffUserId(callerId);
        assignment.setActive(true);
        assignment.setCanScan(true);
        assignment.setCanManageRewards(canManageRewards);
        given(eventStaffAssignmentRepository.findByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId))
                .willReturn(Optional.of(assignment));
    }

    private String pointsBody(int points) {
        return "{\"attendeeUserId\":\"" + attendeeId + "\",\"points\":" + points + ",\"reason\":\"Bonus\"}";
    }

    private ResultActions assign(int points) throws Exception {
        return mockMvc.perform(post("/api/v1/staff/events/{eventId}/points/assign", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(pointsBody(points))
                .header("Authorization", "Bearer token"));
    }

    private ResultActions deduct(int points) throws Exception {
        return mockMvc.perform(post("/api/v1/staff/events/{eventId}/points/deduct", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(pointsBody(points))
                .header("Authorization", "Bearer token"));
    }

    private ResultActions redeem() throws Exception {
        return mockMvc.perform(post("/api/v1/staff/events/{eventId}/rewards/{rewardId}/redeem", eventId, rewardId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + eventId + "\",\"attendeeUserId\":\"" + attendeeId + "\","
                        + "\"rewardId\":\"" + rewardId + "\"}")
                .header("Authorization", "Bearer token"));
    }

    // --- can_manage_rewards -------------------------------------------------

    @Test
    void staffWithoutManageRewards_isForbiddenOnAllThree() throws Exception {
        actingAs(AccountRole.STAFF);
        assignment(false);

        assign(10).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Staff user is not allowed to manage rewards for this event"));
        deduct(10).andExpect(status().isForbidden());
        redeem().andExpect(status().isForbidden());

        verify(rewardService, never()).assignPoints(any(), any(), anyInt(), any(), any());
        verify(rewardService, never()).deductPoints(any(), any(), anyInt(), any(), any());
        verify(rewardService, never()).redeem(any());
    }

    @Test
    void staffWithManageRewards_isAllowedAndActorIsTheCaller() throws Exception {
        actingAs(AccountRole.STAFF);
        assignment(true);

        assign(10).andExpect(status().isOk());
        deduct(10).andExpect(status().isOk());
        redeem().andExpect(status().isOk());

        verify(rewardService).assignPoints(eq(eventId), eq(attendeeId), eq(10), eq("Bonus"), eq(callerId));
        verify(rewardService).deductPoints(eq(eventId), eq(attendeeId), eq(10), eq("Bonus"), eq(callerId));
    }

    @Test
    void unassignedStaff_isForbidden() throws Exception {
        actingAs(AccountRole.STAFF);

        assign(10).andExpect(status().isForbidden());
        redeem().andExpect(status().isForbidden());
    }

    @Test
    void owningOrganizer_isAllowed() throws Exception {
        actingAs(AccountRole.ORGANIZER);
        eventOwnedBy(callerId);

        assign(10).andExpect(status().isOk());
        deduct(10).andExpect(status().isOk());
        redeem().andExpect(status().isOk());
    }

    @Test
    void assignedNonOwnerOrganizerWithoutManageRewards_isForbidden() throws Exception {
        actingAs(AccountRole.ORGANIZER);
        assignment(false);

        assign(10).andExpect(status().isForbidden());
    }

    @Test
    void admin_isAllowed() throws Exception {
        actingAs(AccountRole.ADMIN);

        assign(10).andExpect(status().isOk());
        deduct(10).andExpect(status().isOk());
        redeem().andExpect(status().isOk());
    }

    @Test
    void attendee_isForbidden() throws Exception {
        actingAs(AccountRole.ATTENDEE);

        assign(10).andExpect(status().isForbidden());
    }

    // --- points bounds ------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1001, Integer.MAX_VALUE})
    void outOfRangePoints_areBadRequest(int points) throws Exception {
        actingAs(AccountRole.ADMIN);

        assign(points).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.points").value("Points must be between 1 and 1000"));
        deduct(points).andExpect(status().isBadRequest());

        verify(rewardService, never()).assignPoints(any(), any(), anyInt(), any(), any());
        verify(rewardService, never()).deductPoints(any(), any(), anyInt(), any(), any());
    }

    @Test
    void boundaryPoints_areAccepted() throws Exception {
        actingAs(AccountRole.ADMIN);

        assign(1000).andExpect(status().isOk());
        assign(1).andExpect(status().isOk());
        deduct(1000).andExpect(status().isOk());
    }
}
