package com.thedavelopers.eventqr.features.staff.controller;

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
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.features.rewards.service.RewardService;
import com.thedavelopers.eventqr.features.scanning.service.ScanPurposeService;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest;
import com.thedavelopers.eventqr.features.transactions.service.TransactionService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.EventStatus;
import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.interfaces.ScanPurposePort.ScanPurposeSnapshot;
import com.thedavelopers.eventqr.shared.security.JwtService;

/**
 * Staff endpoints beyond the role matrix (covered by StaffScanAuthorizationTest): what the
 * assigned-events list tells the app, and that scan requests are normalised safely.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaffControllerTest {

    private static final String AUTH = "Bearer staff";

    @Mock private EventService eventService;
    @Mock private RegistrationService registrationService;
    @Mock private TransactionService transactionService;
    @Mock private RewardService rewardService;
    @Mock private EventStaffAssignmentRepository assignmentRepository;
    @Mock private ScanPurposeService scanPurposeService;
    @Mock private JwtService jwtService;

    private MockMvc mvc;
    private final UUID staffId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID purposeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StaffController(eventService, registrationService, transactionService,
                        rewardService, assignmentRepository, scanPurposeService, jwtService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(jwtService.extractUserIdFromBearer(AUTH)).thenReturn(staffId);
        when(jwtService.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.STAFF);
    }

    private EventStaffAssignment assignment(boolean canScan) {
        EventStaffAssignment a = new EventStaffAssignment();
        a.setId(UUID.randomUUID());
        a.setEventId(eventId);
        a.setStaffUserId(staffId);
        a.setActive(true);
        a.setCanScan(canScan);
        a.setCanPrintId(true);
        a.setCanViewLogs(false);
        a.setCanManageRewards(false);
        return a;
    }

    private EventResponse event(EventStatus status) {
        return new EventResponse(eventId, "Tech Conf", "desc", "Tech", "Hall A", null, null, null,
                Instant.now().plusSeconds(60), Instant.now().plusSeconds(3_600), 100, 0, status, false,
                UUID.randomUUID(), null, null, null);
    }

    private void assigned(boolean canScan, EventStatus status) {
        EventStaffAssignment a = assignment(canScan);
        when(assignmentRepository.findByStaffUserIdAndActiveTrue(staffId)).thenReturn(List.of(a));
        when(assignmentRepository.findByEventIdAndStaffUserIdAndActiveTrue(eventId, staffId)).thenReturn(Optional.of(a));
        when(eventService.findOne(eventId)).thenReturn(event(status));
    }

    private static String json(String body) {
        return body.replace('\'', '"');
    }

    // ----- assigned events -----

    @Test
    void theAssignedEventsCarryThePermissionFlagsTheAppNeeds() throws Exception {
        assigned(true, EventStatus.ACTIVE);

        mvc.perform(get("/api/v1/staff/events").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Tech Conf"))
                .andExpect(jsonPath("$.data[0].canScan").value(true))
                .andExpect(jsonPath("$.data[0].canPrintId").value(true))
                .andExpect(jsonPath("$.data[0].canViewLogs").value(false))
                .andExpect(jsonPath("$.data[0].canManageRewards").value(false));
    }

    @Test
    void scanningIsReportedAsOffOnceTheEventHasEndedEvenIfThePermissionIsOn() throws Exception {
        assigned(true, EventStatus.ENDED);

        mvc.perform(get("/api/v1/staff/events").header("Authorization", AUTH))
                .andExpect(jsonPath("$.data[0].canScan").value(false));
    }

    @Test
    void aStaffMemberWithoutScanPermissionSeesTheEventButCannotScan() throws Exception {
        assigned(false, EventStatus.ACTIVE);

        mvc.perform(get("/api/v1/staff/events").header("Authorization", AUTH))
                .andExpect(jsonPath("$.data[0].canScan").value(false));
    }

    @Test
    void aStaffMemberWithNoAssignmentsGetsAnEmptyList() throws Exception {
        when(assignmentRepository.findByStaffUserIdAndActiveTrue(staffId)).thenReturn(List.of());

        mvc.perform(get("/api/v1/staff/events").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ----- scan purposes -----

    @Test
    void onlyActiveScanPurposesAreOffered() throws Exception {
        assigned(true, EventStatus.ACTIVE);
        when(scanPurposeService.listByEventId(eventId)).thenReturn(List.of(
                new ScanPurposeSnapshot(UUID.randomUUID(), eventId, "Entry", ScanPurposeCode.ENTRY, true, false, null),
                new ScanPurposeSnapshot(UUID.randomUUID(), eventId, "Old booth", ScanPurposeCode.BOOTH_VISIT, false, false, null)));

        mvc.perform(get("/api/v1/staff/events/{id}/scan-purposes", eventId).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Entry"));
    }

    // ----- scan requests are normalised -----

    @Test
    void theEventInTheUrlWinsAndTheRetryKeyReachesTheService() throws Exception {
        assigned(true, EventStatus.ACTIVE);
        UUID otherEvent = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();

        mvc.perform(post("/api/v1/staff/events/{id}/scan/entry", eventId).header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'eventId':'" + otherEvent + "','scanPurposeId':'" + purposeId
                                + "','qrValue':'qr-1','clientRequestId':'" + clientRequestId + "'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Entry recorded"));

        ArgumentCaptor<TransactionRequest> captured = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).record(captured.capture());
        org.assertj.core.api.Assertions.assertThat(captured.getValue().eventId()).isEqualTo(eventId);
        org.assertj.core.api.Assertions.assertThat(captured.getValue().clientRequestId()).isEqualTo(clientRequestId);
        org.assertj.core.api.Assertions.assertThat(captured.getValue().qrValue()).isEqualTo("qr-1");
    }

    @Test
    void everyScanRouteReportsItsOwnOutcome() throws Exception {
        assigned(true, EventStatus.ACTIVE);
        String body = json("{'eventId':'" + eventId + "','scanPurposeId':'" + purposeId + "','qrValue':'qr-1'}");
        String[][] routes = {
                {"entry", "Entry recorded"}, {"attendance", "Attendance recorded"},
                {"benefit-claim", "Benefit claim recorded"}, {"booth-visit", "Booth visit recorded"},
                {"reward-redemption", "Reward redemption scan recorded"}, {"exit", "Exit recorded"},
                {"reject", "Scan rejected"}};

        for (String[] route : routes) {
            mvc.perform(post("/api/v1/staff/events/{id}/scan/" + route[0], eventId).header("Authorization", AUTH)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(route[1]));
        }
    }

    @Test
    void aScanWithoutAPurposeIsRejectedBeforeTheServiceIsCalled() throws Exception {
        assigned(true, EventStatus.ACTIVE);

        mvc.perform(post("/api/v1/staff/events/{id}/scan/entry", eventId).header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'eventId':'" + eventId + "','qrValue':'qr-1'}")))
                .andExpect(status().isBadRequest());

        verify(transactionService, never()).record(any());
    }

    @Test
    void verifyingAScanDoesNotRecordIt() throws Exception {
        assigned(true, EventStatus.ACTIVE);

        mvc.perform(post("/api/v1/staff/events/{id}/scan/verify", eventId).header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'eventId':'" + eventId + "','scanPurposeId':'" + purposeId + "','qrValue':'qr-1'}")))
                .andExpect(status().isOk());

        verify(transactionService).verify(any());
        verify(transactionService, never()).record(any());
    }

    // ----- transaction history -----

    @Test
    void anUnassignedStaffMemberCannotReadAnEventsTransactions() throws Exception {
        when(assignmentRepository.findByEventIdAndStaffUserIdAndActiveTrue(eventId, staffId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/staff/transactions").param("eventId", eventId.toString()).header("Authorization", AUTH))
                .andExpect(status().isForbidden());

        verify(transactionService, never()).findForStaff(any(), any(), any(), any());
    }

    @Test
    void anAssignedStaffMemberSeesTheirOwnTransactionsForTheEvent() throws Exception {
        assigned(true, EventStatus.ACTIVE);
        when(transactionService.findForStaff(eq(staffId), eq(eventId), eq(null), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty(org.springframework.data.domain.PageRequest.of(0, 20)));

        mvc.perform(get("/api/v1/staff/transactions").param("eventId", eventId.toString()).header("Authorization", AUTH))
                .andExpect(status().isOk());

        verify(transactionService).findForStaff(eq(staffId), eq(eventId), eq(null), any(org.springframework.data.domain.Pageable.class));
    }
}
