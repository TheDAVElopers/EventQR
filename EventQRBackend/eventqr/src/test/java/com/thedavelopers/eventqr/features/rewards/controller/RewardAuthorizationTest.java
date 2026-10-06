package com.thedavelopers.eventqr.features.rewards.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
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

/**
 * Authorization coverage for RewardController: write/redeem/list/redemptions/balance guards.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RewardAuthorizationTest {

    @Mock
    private RewardService rewardService;
    @Mock
    private EventService eventService;
    @Mock
    private JwtService jwtService;
    @Mock
    private EventStaffAssignmentRepository eventStaffAssignmentRepository;
    @Mock
    private EventRegistrationRepository eventRegistrationRepository;

    private MockMvc mockMvc;

    private final UUID eventId = UUID.randomUUID();
    private final UUID rewardId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID attendeeId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new RewardController(rewardService, eventService, jwtService,
                                eventStaffAssignmentRepository, eventRegistrationRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // --- helpers -----------------------------------------------------------

    private void actingAs(UUID userId, AccountRole role) {
        given(jwtService.extractUserIdFromBearer(any())).willReturn(userId);
        given(jwtService.extractRoleFromBearer(any())).willReturn(role);
    }

    private void callerRegistered(boolean registered) {
        given(eventRegistrationRepository.existsByEventIdAndAttendeeUserId(eventId, callerId))
                .willReturn(registered);
    }

    private void attendeeRegistered(boolean registered) {
        given(eventRegistrationRepository.existsByEventIdAndAttendeeUserId(eventId, attendeeId))
                .willReturn(registered);
    }

    private void callerAssignedToEvent(boolean assigned) {
        given(eventStaffAssignmentRepository.existsByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId))
                .willReturn(assigned);
    }

    private EventResponse eventOwnedBy(UUID organizerUserId) {
        return new EventResponse(eventId, "Event", null, null, "Location",
                null, null, null, null, 100, 0, EventStatus.APPROVED, false,
                organizerUserId, null, null, null);
    }

    private String rewardJson() {
        return "{\"eventId\":\"" + eventId + "\",\"name\":\"Coffee\",\"description\":null,"
                + "\"pointsRequired\":10,\"stockQuantity\":null,\"allowDuplicateClaims\":false}";
    }

    private String redeemJson(UUID targetUserId) {
        return "{\"eventId\":\"" + eventId + "\",\"attendeeUserId\":\"" + targetUserId + "\","
                + "\"rewardId\":\"" + rewardId + "\"}";
    }

    // --- POST /api/v1/rewards (create) -------------------------------------

    @Test
    void createReward_attendee_isForbiddenEvenWhenRegistered() throws Exception {
        actingAs(attendeeId, AccountRole.ATTENDEE);
        attendeeRegistered(true);

        mockMvc.perform(post("/api/v1/rewards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rewardJson())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReward_organizerOwner_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(callerId));

        mockMvc.perform(post("/api/v1/rewards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rewardJson())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void createReward_organizerNonOwner_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(otherUserId));

        mockMvc.perform(post("/api/v1/rewards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rewardJson())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReward_staffWithManageRewards_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        EventStaffAssignment assignment = org.mockito.Mockito.mock(EventStaffAssignment.class);
        given(assignment.isCanManageRewards()).willReturn(true);
        given(eventStaffAssignmentRepository.findByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId))
                .willReturn(Optional.of(assignment));

        mockMvc.perform(post("/api/v1/rewards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rewardJson())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void createReward_staffWithoutManageRewards_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        EventStaffAssignment assignment = org.mockito.Mockito.mock(EventStaffAssignment.class);
        given(assignment.isCanManageRewards()).willReturn(false);
        given(eventStaffAssignmentRepository.findByEventIdAndStaffUserIdAndActiveTrue(eventId, callerId))
                .willReturn(Optional.of(assignment));

        mockMvc.perform(post("/api/v1/rewards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rewardJson())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReward_admin_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);

        mockMvc.perform(post("/api/v1/rewards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rewardJson())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void createReward_superAdmin_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.SUPER_ADMIN);

        mockMvc.perform(post("/api/v1/rewards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rewardJson())
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    // --- POST /api/v1/rewards/redeem ---------------------------------------

    @Test
    void redeem_attendeeForAnotherUser_isForbidden() throws Exception {
        actingAs(attendeeId, AccountRole.ATTENDEE);

        mockMvc.perform(post("/api/v1/rewards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemJson(otherUserId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void redeem_attendeeForSelfRegistered_isAllowed() throws Exception {
        actingAs(attendeeId, AccountRole.ATTENDEE);
        attendeeRegistered(true);

        mockMvc.perform(post("/api/v1/rewards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemJson(attendeeId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void redeem_attendeeForSelfNotRegistered_isForbidden() throws Exception {
        actingAs(attendeeId, AccountRole.ATTENDEE);
        attendeeRegistered(false);

        mockMvc.perform(post("/api/v1/rewards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemJson(attendeeId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void redeem_organizerOwnerForRegisteredAttendee_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(callerId));
        attendeeRegistered(true);

        mockMvc.perform(post("/api/v1/rewards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemJson(attendeeId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void redeem_organizerNonOwner_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(otherUserId));
        attendeeRegistered(true);

        mockMvc.perform(post("/api/v1/rewards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemJson(attendeeId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void redeem_staffAssignedForRegisteredAttendee_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedToEvent(true);
        attendeeRegistered(true);

        mockMvc.perform(post("/api/v1/rewards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemJson(attendeeId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void redeem_staffUnassigned_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedToEvent(false);

        mockMvc.perform(post("/api/v1/rewards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemJson(attendeeId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void redeem_adminForAnyUser_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);

        mockMvc.perform(post("/api/v1/rewards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemJson(attendeeId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void redeem_superAdminForAnyUser_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.SUPER_ADMIN);

        mockMvc.perform(post("/api/v1/rewards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemJson(attendeeId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void redeem_staffSelfOnUnrelatedEvent_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedToEvent(false);
        callerRegistered(false);

        mockMvc.perform(post("/api/v1/rewards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemJson(callerId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void redeem_organizerSelfOnNonOwnedEvent_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(otherUserId));
        callerRegistered(false);

        mockMvc.perform(post("/api/v1/rewards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemJson(callerId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void redeem_organizerSelfOnOwnedEvent_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(callerId));
        callerRegistered(true);

        mockMvc.perform(post("/api/v1/rewards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemJson(callerId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void redeem_staffSelfOnAssignedEvent_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedToEvent(true);
        callerRegistered(true);

        mockMvc.perform(post("/api/v1/rewards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemJson(callerId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    // --- GET /api/v1/rewards/event/{eventId} -------------------------------

    @Test
    void findRewards_attendee_isAllowed() throws Exception {
        given(rewardService.findRewards(eventId)).willReturn(List.of());
        actingAs(callerId, AccountRole.ATTENDEE);

        mockMvc.perform(get("/api/v1/rewards/event/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void findRewards_organizerRegisteredButNotOwner_isAllowed() throws Exception {
        given(rewardService.findRewards(eventId)).willReturn(List.of());
        actingAs(callerId, AccountRole.ORGANIZER);

        mockMvc.perform(get("/api/v1/rewards/event/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(eventService, never()).findOne(any());
    }

    @Test
    void findRewards_staffUnassigned_isAllowed() throws Exception {
        given(rewardService.findRewards(eventId)).willReturn(List.of());
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedToEvent(false);

        mockMvc.perform(get("/api/v1/rewards/event/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void findRewards_admin_isAllowed() throws Exception {
        given(rewardService.findRewards(eventId)).willReturn(List.of());
        actingAs(callerId, AccountRole.ADMIN);

        mockMvc.perform(get("/api/v1/rewards/event/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    // --- GET /api/v1/rewards/redemptions/{eventId} -------------------------

    @Test
    void findRedemptions_attendeeRegistered_seesOnlyOwnRows() throws Exception {
        given(rewardService.findRedemptions(eventId, callerId)).willReturn(List.of());
        actingAs(callerId, AccountRole.ATTENDEE);
        callerRegistered(true);

        mockMvc.perform(get("/api/v1/rewards/redemptions/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(rewardService).findRedemptions(eventId, callerId);
        verify(rewardService, never()).findRedemptions(eventId);
    }

    @Test
    void findRedemptions_attendeeNotRegistered_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ATTENDEE);
        callerRegistered(false);

        mockMvc.perform(get("/api/v1/rewards/redemptions/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findRedemptions_organizerNonOwner_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(otherUserId));

        mockMvc.perform(get("/api/v1/rewards/redemptions/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findRedemptions_organizerOwner_isAllowed() throws Exception {
        given(rewardService.findRedemptions(eventId)).willReturn(List.of());
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(callerId));

        mockMvc.perform(get("/api/v1/rewards/redemptions/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(rewardService).findRedemptions(eventId);
        verify(rewardService, never()).findRedemptions(eventId, callerId);
    }

    @Test
    void findRedemptions_admin_isAllowed() throws Exception {
        given(rewardService.findRedemptions(eventId)).willReturn(List.of());
        actingAs(callerId, AccountRole.ADMIN);

        mockMvc.perform(get("/api/v1/rewards/redemptions/{eventId}", eventId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(rewardService).findRedemptions(eventId);
    }

    // --- GET /api/v1/rewards/balance/{eventId}/{attendeeUserId} (unchanged) -

    @Test
    void balance_attendeeForAnotherUser_isForbidden() throws Exception {
        actingAs(attendeeId, AccountRole.ATTENDEE);

        mockMvc.perform(get("/api/v1/rewards/balance/{eventId}/{attendeeUserId}", eventId, otherUserId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void balance_attendeeForSelf_isAllowed() throws Exception {
        actingAs(attendeeId, AccountRole.ATTENDEE);

        mockMvc.perform(get("/api/v1/rewards/balance/{eventId}/{attendeeUserId}", eventId, attendeeId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }
}
