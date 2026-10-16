package com.thedavelopers.eventqr.features.rewards.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionGrantRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionResultResponse;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanRequest;
import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanResponse;
import com.thedavelopers.eventqr.features.rewards.service.RewardRedemptionScanService;
import com.thedavelopers.eventqr.features.rewards.service.RewardRedemptionService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.RedemptionStatus;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Authorization coverage for staff-driven redemption endpoints:
 * POST /api/v1/rewards/redeem-staff and POST /api/v1/rewards/redemption-scan.
 * Both require an active assignment with can_scan (or ownership / admin), and both take the staff identity from the
 * token, never from the body.
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
    private final UUID scanLogId = UUID.randomUUID();
    private final UUID spoofedStaffId = UUID.randomUUID();

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
        given(rewardRedemptionScanService.scan(any(RewardRedemptionScanRequest.class)))
                .willReturn(new RewardRedemptionScanResponse(eventId, attendeeId, UUID.randomUUID(), UUID.randomUUID(),
                        "Jane", "jane@example.com", RegistrationStatus.REGISTERED, 50, purposeId, scanLogId,
                        List.of(), false, null));
        given(eventStaffAssignmentRepository.findByEventIdAndStaffUserIdAndActiveTrue(any(), any()))
                .willReturn(Optional.empty());
    }

    // --- helpers -----------------------------------------------------------

    private void actingAs(UUID userId, AccountRole role) {
        given(jwtService.extractUserIdFromBearer(any())).willReturn(userId);
        given(jwtService.extractRoleFromBearer(any())).willReturn(role);
    }

    private void callerAssignedTo(UUID scopeEventId, boolean canScan) {
        EventStaffAssignment assignment = new EventStaffAssignment();
        assignment.setEventId(scopeEventId);
        assignment.setStaffUserId(callerId);
        assignment.setActive(true);
        assignment.setCanScan(canScan);
        given(eventStaffAssignmentRepository.findByEventIdAndStaffUserIdAndActiveTrue(scopeEventId, callerId))
                .willReturn(Optional.of(assignment));
    }

    private EventResponse eventOwnedBy(UUID organizerUserId) {
        return new EventResponse(eventId, "Event", null, null, "Location",
                null, null, null, null, 100, 0, EventStatus.APPROVED, false,
                organizerUserId, null, null, null);
    }

    /** What the Android app sends: its own user id as staffUserId and the scan log id from the scan response. */
    private String grantJson(UUID scopeEventId, UUID bodyStaffId, UUID bodyScanLogId) {
        return "{\"eventId\":\"" + scopeEventId + "\",\"attendeeUserId\":\"" + attendeeId + "\","
                + "\"rewardId\":\"" + rewardId + "\","
                + "\"staffUserId\":" + (bodyStaffId == null ? "null" : "\"" + bodyStaffId + "\"") + ","
                + "\"redemptionScanLogId\":" + (bodyScanLogId == null ? "null" : "\"" + bodyScanLogId + "\"") + "}";
    }

    private String grantJson(UUID scopeEventId) {
        return grantJson(scopeEventId, null, scanLogId);
    }

    private String scanJson(UUID scopeEventId, UUID bodyStaffId) {
        return "{\"eventId\":\"" + scopeEventId + "\",\"scanPurposeId\":\"" + purposeId + "\","
                + "\"qrValue\":\"qr-1\",\"shortId\":null,"
                + "\"staffUserId\":" + (bodyStaffId == null ? "null" : "\"" + bodyStaffId + "\"") + "}";
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
        verify(rewardRedemptionService, never()).redeem(any());
    }

    private void postScan(MockMvc mvc, UUID scopeEventId, String token) throws Exception {
        mvc.perform(post("/api/v1/rewards/redemption-scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanJson(scopeEventId, null))
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    private void postScanForbidden(MockMvc mvc, UUID scopeEventId, String token) throws Exception {
        mvc.perform(post("/api/v1/rewards/redemption-scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanJson(scopeEventId, null))
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
        verify(rewardRedemptionScanService, never()).scan(any());
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

    // --- staff event scoping and can_scan -----------------------------------

    @Test
    void redeemStaff_staffAssignedWithCanScan_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedTo(eventId, true);
        postGrant(redeemMvc, eventId, "Bearer token");
    }

    @Test
    void redemptionScan_staffAssignedWithCanScan_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedTo(eventId, true);
        postScan(scanMvc, eventId, "Bearer token");
    }

    @Test
    void redeemStaff_staffAssignedWithoutCanScan_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedTo(eventId, false);
        redeemMvc.perform(post("/api/v1/rewards/redeem-staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantJson(eventId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Staff user is not allowed to scan for this event"));
        verify(rewardRedemptionService, never()).redeem(any());
    }

    @Test
    void redemptionScan_staffAssignedWithoutCanScan_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedTo(eventId, false);
        scanMvc.perform(post("/api/v1/rewards/redemption-scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanJson(eventId, null))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Staff user is not allowed to scan for this event"));
        verify(rewardRedemptionScanService, never()).scan(any());
    }

    @Test
    void redeemStaff_staffAssignedElsewhereButNotToBodyEvent_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedTo(otherEventId, true);
        postGrantForbidden(redeemMvc, eventId, "Bearer token");
    }

    @Test
    void redemptionScan_staffAssignedElsewhereButNotToBodyEvent_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedTo(otherEventId, true);
        postScanForbidden(scanMvc, eventId, "Bearer token");
    }

    @Test
    void redeemStaff_staffUnassigned_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        postGrantForbidden(redeemMvc, eventId, "Bearer token");
    }

    @Test
    void redemptionScan_staffUnassigned_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        postScanForbidden(scanMvc, eventId, "Bearer token");
    }

    // --- staff identity comes from the token --------------------------------

    @Test
    void redemptionScan_bodyStaffUserIdIsReplacedByCaller() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedTo(eventId, true);

        scanMvc.perform(post("/api/v1/rewards/redemption-scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanJson(eventId, spoofedStaffId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        ArgumentCaptor<RewardRedemptionScanRequest> captor = ArgumentCaptor.forClass(RewardRedemptionScanRequest.class);
        verify(rewardRedemptionScanService).scan(captor.capture());
        assertThat(captor.getValue().staffUserId()).isEqualTo(callerId);
        assertThat(captor.getValue().eventId()).isEqualTo(eventId);
        assertThat(captor.getValue().qrValue()).isEqualTo("qr-1");
    }

    @Test
    void redeemStaff_bodyStaffUserIdIsReplacedByCaller() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedTo(eventId, true);

        redeemMvc.perform(post("/api/v1/rewards/redeem-staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantJson(eventId, spoofedStaffId, scanLogId))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        ArgumentCaptor<RewardRedemptionGrantRequest> captor = ArgumentCaptor.forClass(RewardRedemptionGrantRequest.class);
        verify(rewardRedemptionService).redeem(captor.capture());
        assertThat(captor.getValue().staffUserId()).isEqualTo(callerId);
        assertThat(captor.getValue().redemptionScanLogId()).isEqualTo(scanLogId);
        assertThat(captor.getValue().attendeeUserId()).isEqualTo(attendeeId);
    }

    @Test
    void redeemStaff_nullRedemptionScanLogId_isBadRequest() throws Exception {
        actingAs(callerId, AccountRole.STAFF);
        callerAssignedTo(eventId, true);

        redeemMvc.perform(post("/api/v1/rewards/redeem-staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grantJson(eventId, callerId, null))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.redemptionScanLogId").exists());
        verify(rewardRedemptionService, never()).redeem(any());
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
        postGrantForbidden(redeemMvc, eventId, "Bearer token");
    }

    @Test
    void redemptionScan_organizerNonOwnerUnassigned_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(UUID.randomUUID()));
        postScanForbidden(scanMvc, eventId, "Bearer token");
    }

    @Test
    void redeemStaff_organizerNonOwnerAssignedWithCanScan_isAllowed() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(UUID.randomUUID()));
        callerAssignedTo(eventId, true);
        postGrant(redeemMvc, eventId, "Bearer token");
    }

    @Test
    void redeemStaff_organizerNonOwnerAssignedWithoutCanScan_isForbidden() throws Exception {
        actingAs(callerId, AccountRole.ORGANIZER);
        given(eventService.findOne(eventId)).willReturn(eventOwnedBy(UUID.randomUUID()));
        callerAssignedTo(eventId, false);
        postGrantForbidden(redeemMvc, eventId, "Bearer token");
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
