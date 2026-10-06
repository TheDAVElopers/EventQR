package com.thedavelopers.eventqr.features.rewards.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionGrantRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResultResponse;
import com.thedavelopers.eventqr.features.rewards.service.RewardRedemptionScanService;
import com.thedavelopers.eventqr.features.rewards.service.RewardRedemptionService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.RedemptionStatus;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Authorization coverage for staff-driven redemption endpoints:
 * POST /api/v1/rewards/redeem-staff and POST /api/v1/rewards/redemption-scan.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RewardRedemptionAuthorizationTest {

    @Mock
    private RewardRedemptionService rewardRedemptionService;
    @Mock
    private RewardRedemptionScanService rewardRedemptionScanService;
    @Mock
    private JwtService jwtService;
    @Mock
    private EventService eventService;
    @Mock
    private EventStaffAssignmentRepository eventStaffAssignmentRepository;

    private MockMvc redeemMvc;
    private MockMvc scanMvc;

    private final UUID eventId = UUID.randomUUID();
    private final UUID otherEventId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID attendeeId = UUID.randomUUID();
    private final UUID rewardId = UUID.randomUUID();
    private final UUID purposeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        redeemMvc = MockMvcBuilders.standaloneSetup(
                        new RewardRedemptionController(rewardRedemptionService, jwtService,
                                eventService, eventStaffAssignmentRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        scanMvc = MockMvcBuilders.standaloneSetup(
                        new RewardRedemptionScanController(rewardRedemptionScanService, jwtService,
                                eventService, eventStaffAssignmentRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        given(rewardRedemptionService.redeem(any(RewardRedemptionGrantRequest.class)))
                .willReturn(new RewardRedemptionResultResponse(UUID.randomUUID(), rewardId, "Coffee",
                        RedemptionStatus.REDEEMED, null, 10, Instant.now(), 5));
    }

    // --- helpers -----------------------------------------------------------

    private void actingAs(UUID userId, AccountRole role) {
        given(jwtService.extractUserIdFromBearer(any())).willReturn(userId);
        given(jwtService.extractRoleFromBearer(any())).willReturn(role);
    }

    private void callerAssignedTo(UUID scopeEventId, boolean assigned) {
        given(eventStaffAssignmentRepository
                .existsByEventIdAndStaffUserIdAndActiveTrue(scopeEventId, callerId))
                .willReturn(assigned);
    }

    private EventResponse eventOwnedBy(UUID organizerUserId) {
        return new EventResponse(eventId, "Event", null, null, "Location",
                null, null, null, null, 100, 0, EventStatus.APPROVED, false,
                organizerUserId, null, null, null);
    }

    private String grantJson(UUID scopeEventId) {
        return "{\"eventId\":\"" + scopeEventId + "\",\"attendeeUserId\":\"" + attendeeId + "\","
                + "\"rewardId\":\"" + rewardId + "\",\"staffUserId\":null,\"redemptionScanLogId\":null}";
    }

    private String scanJson(UUID scopeEventId) {
        return "{\"eventId\":\"" + scopeEventId + "\",\"scanPurposeId\":\"" + purposeId + "\","
                + "\"qrValue\":\"qr-1\",\"shortId\":null,\"staffUserId\":null}";
    }

    private void postGrant(MockMvc mvc, UUID scopeEventId, String token) throws Exception {
        mvc.perform(post("/api/v1/rewards/redeem-staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantJson(scopeEventId))
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    private void postGrantForbidden(MockMvc mvc, UUID scopeEventId, String token) throws Exception {
        mvc.perform(post("/api/v1/rewards/redeem-staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantJson(scopeEventId))
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    private void postScan(MockMvc mvc, UUID scopeEventId, String token) throws Exception {
        mvc.perform(post("/api/v1/rewards/redemption-scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanJson(scopeEventId))
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    private void postScanForbidden(MockMvc mvc, UUID scopeEventId, String token) throws Exception {
        mvc.perform(post("/api/v1/rewards/redemption-scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanJson(scopeEventId))
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    // --- super admin / admin ceiling ---------------------------------------

    @Test
    void redeemStaff_superAdmin_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.SUPER_ADMIN);
        postGrant(redeemMvc, eventId, "Bearer token");
    }

    @Test
    void redemptionScan_superAdmin_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.SUPER_ADMIN);
        postScan(scanMvc, eventId, "Bearer token");
    }

    @Test
    void redeemStaff_adminNonOwner_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);
        postGrant(redeemMvc, eventId, "Bearer token");
    }

    @Test
    void redemptionScan_adminNonOwner_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ADMIN);
        postScan(scanMvc, eventId, "Bearer token");
    }

    // --- staff event scoping -----------------------------------------------

    @Test
    void redeemStaff_staffAssignedToBodyEvent_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedTo(eventId, true);
        postGrant(redeemMvc, eventId, "Bearer token");
    }

    @Test
    void redemptionScan_staffAssignedToBodyEvent_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedTo(eventId, true);
        postScan(scanMvc, eventId, "Bearer token");
    }

    @Test
    void redeemStaff_staffAssignedElsewhereButNotToBodyEvent_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedTo(otherEventId, true);
        callerAssignedTo(eventId, false);
        postGrantForbidden(redeemMvc, eventId, "Bearer token");
    }

    @Test
    void redemptionScan_staffAssignedElsewhereButNotToBodyEvent_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedTo(otherEventId, true);
        callerAssignedTo(eventId, false);
        postScanForbidden(scanMvc, eventId, "Bearer token");
    }

    @Test
    void redeemStaff_staffUnassigned_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedTo(eventId, false);
        postGrantForbidden(redeemMvc, eventId, "Bearer token");
    }

    @Test
    void redemptionScan_staffUnassigned_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedTo(eventId, false);
        postScanForbidden(scanMvc, eventId, "Bearer token");
    }

    // --- organizer ownership ------------------------------------------------

    @Test
    void redeemStaff_organizerOwner_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(callerId));
        postGrant(redeemMvc, eventId, "Bearer token");
    }

    @Test
    void redemptionScan_organizerOwner_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(callerId));
        postScan(scanMvc, eventId, "Bearer token");
    }

    @Test
    void redeemStaff_organizerNonOwnerUnassigned_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(UUID.randomUUID()));
        callerAssignedTo(eventId, false);
        postGrantForbidden(redeemMvc, eventId, "Bearer token");
    }

    @Test
    void redemptionScan_organizerNonOwnerUnassigned_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(UUID.randomUUID()));
        callerAssignedTo(eventId, false);
        postScanForbidden(scanMvc, eventId, "Bearer token");
    }

    @Test
    void redeemStaff_organizerNonOwnerButAssigned_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(UUID.randomUUID()));
        callerAssignedTo(eventId, true);
        postGrant(redeemMvc, eventId, "Bearer token");
    }

    // --- attendee ceiling ---------------------------------------------------

    @Test
    void redeemStaff_attendee_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ATTENDEE);
        postGrantForbidden(redeemMvc, eventId, "Bearer token");
    }

    @Test
    void redemptionScan_attendee_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ATTENDEE);
        postScanForbidden(scanMvc, eventId, "Bearer token");
    }
}
