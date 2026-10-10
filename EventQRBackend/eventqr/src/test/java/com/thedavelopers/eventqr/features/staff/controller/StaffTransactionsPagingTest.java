package com.thedavelopers.eventqr.features.staff.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.events.service.EventService;
import com.thedavelopers.eventqr.features.organizer.model.entity.EventStaffAssignment;
import com.thedavelopers.eventqr.features.organizer.repository.EventStaffAssignmentRepository;
import com.thedavelopers.eventqr.features.registrations.service.RegistrationService;
import com.thedavelopers.eventqr.features.rewards.service.RewardService;
import com.thedavelopers.eventqr.features.scanning.service.ScanPurposeService;
import com.thedavelopers.eventqr.features.transactions.model.dto.StaffTodaySummary;
import com.thedavelopers.eventqr.features.transactions.model.dto.StaffTransactionSummary;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse;
import com.thedavelopers.eventqr.features.transactions.service.TransactionService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/** Paging, summary and attendee-filter wiring of the staff transaction endpoints. */
class StaffTransactionsPagingTest {

    private static final String AUTH = "Bearer staff";

    private TransactionService transactionService;
    private EventStaffAssignmentRepository assignments;
    private MockMvc mvc;
    private final UUID staffId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID attendeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        transactionService = mock(TransactionService.class);
        assignments = mock(EventStaffAssignmentRepository.class);
        JwtService jwt = mock(JwtService.class);
        mvc = MockMvcBuilders.standaloneSetup(new StaffController(mock(EventService.class), mock(RegistrationService.class),
                        transactionService, mock(RewardService.class), assignments, mock(ScanPurposeService.class), jwt))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(jwt.extractUserIdFromBearer(AUTH)).thenReturn(staffId);
        when(jwt.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.STAFF);
        when(assignments.findByEventIdAndStaffUserIdAndActiveTrue(eventId, staffId))
                .thenReturn(Optional.of(new EventStaffAssignment()));
    }

    private TransactionResponse tx() {
        return new TransactionResponse(UUID.randomUUID(), eventId, "Event", attendeeId, "Jane", UUID.randomUUID(), "ENTERED",
                UUID.randomUUID(), UUID.randomUUID(), "Entry", TransactionType.ENTRY, TransactionResult.APPROVED, 0, null, null);
    }

    private Pageable captureFindForStaff(UUID expectedEvent) {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionService).findForStaff(eq(staffId), eq(expectedEvent), eq(null), pageable.capture());
        return pageable.getValue();
    }

    @Test
    void myTransactionsDefaultToPageZeroSizeTwentyAndReturnThePageShape() throws Exception {
        Page<TransactionResponse> page = new PageImpl<>(List.of(tx()), PageRequest.of(0, 20), 41);
        when(transactionService.findForStaff(eq(staffId), eq(null), eq(null), any(Pageable.class))).thenReturn(page);

        mvc.perform(get("/api/v1/staff/transactions").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(41))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.last").value(false));

        Pageable pageable = captureFindForStaff(null);
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    @Test
    void sizeIsClampedToOneToHundredAndNegativePageToZero() throws Exception {
        when(transactionService.findForStaff(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty(PageRequest.of(0, 20)));

        mvc.perform(get("/api/v1/staff/transactions").param("size", "5000").param("page", "-4").header("Authorization", AUTH))
                .andExpect(status().isOk());
        Pageable big = captureFindForStaff(null);
        assertThat(big.getPageSize()).isEqualTo(100);
        assertThat(big.getPageNumber()).isZero();

        mvc.perform(get("/api/v1/staff/transactions").param("size", "0").param("page", "3").header("Authorization", AUTH))
                .andExpect(status().isOk());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionService, org.mockito.Mockito.times(2)).findForStaff(eq(staffId), eq(null), eq(null), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
    }

    @Test
    void scopingToAnEventStillRequiresAnActiveAssignment() throws Exception {
        UUID otherEvent = UUID.randomUUID();
        when(assignments.findByEventIdAndStaffUserIdAndActiveTrue(otherEvent, staffId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/staff/transactions").param("eventId", otherEvent.toString()).header("Authorization", AUTH))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/staff/transactions/summary").param("eventId", otherEvent.toString()).header("Authorization", AUTH))
                .andExpect(status().isForbidden());

        verify(transactionService, never()).findForStaff(any(), any(), any(), any());
        verify(transactionService, never()).summarizeForStaff(any(), any(), any());
    }

    @Test
    void summaryReturnsTotalApprovedRejectedForTheCaller() throws Exception {
        when(transactionService.summarizeForStaff(staffId, null, null)).thenReturn(new StaffTransactionSummary(10, 7, 3));
        when(transactionService.summarizeForStaff(staffId, eventId, null)).thenReturn(new StaffTransactionSummary(4, 4, 0));

        mvc.perform(get("/api/v1/staff/transactions/summary").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(10))
                .andExpect(jsonPath("$.data.approved").value(7))
                .andExpect(jsonPath("$.data.rejected").value(3));
        mvc.perform(get("/api/v1/staff/transactions/summary").param("eventId", eventId.toString()).header("Authorization", AUTH))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.rejected").value(0));
    }

    @Test
    void summaryHonorsThePurposeFilterTheListUses() throws Exception {
        UUID purposeId = UUID.randomUUID();
        when(transactionService.summarizeForStaff(staffId, eventId, purposeId)).thenReturn(new StaffTransactionSummary(2, 1, 1));

        mvc.perform(get("/api/v1/staff/transactions/summary").param("eventId", eventId.toString())
                        .param("purposeId", purposeId.toString()).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.approved").value(1))
                .andExpect(jsonPath("$.data.rejected").value(1));
    }

    @Test
    void todaySummaryExposesScannedTodayAndDistinctCheckIns() throws Exception {
        when(transactionService.summarizeTodayForStaff(staffId)).thenReturn(new StaffTodaySummary(9, 5));

        mvc.perform(get("/api/v1/staff/transactions/today/summary").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scannedToday").value(9))
                .andExpect(jsonPath("$.data.successfulCheckIns").value(5));
    }

    @Test
    void eventTransactionsArePagedAndCanBeFilteredToOneAttendee() throws Exception {
        Page<TransactionResponse> page = new PageImpl<>(List.of(tx(), tx()), PageRequest.of(0, 1), 12);
        when(transactionService.findForEventStaff(eq(eventId), eq(attendeeId), eq(null), any(Pageable.class))).thenReturn(page);

        mvc.perform(get("/api/v1/staff/events/{id}/transactions", eventId)
                        .param("attendeeUserId", attendeeId.toString()).param("size", "1").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(12))
                .andExpect(jsonPath("$.data.content.length()").value(2));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionService).findForEventStaff(eq(eventId), eq(attendeeId), eq(null), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void eventTransactionsResultParamIsPassedToService() throws Exception {
        when(transactionService.findForEventStaff(eq(eventId), eq(attendeeId), eq(com.thedavelopers.eventqr.shared.constants.TransactionResult.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx()), PageRequest.of(0, 1), 4));

        mvc.perform(get("/api/v1/staff/events/{id}/transactions", eventId)
                        .param("attendeeUserId", attendeeId.toString()).param("result", "APPROVED").param("size", "1")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4));

        verify(transactionService).findForEventStaff(eq(eventId), eq(attendeeId), eq(TransactionResult.APPROVED), any(Pageable.class));
    }

    @Test
    void eventTransactionsRejectsUnknownResultValueWithoutCallingService() throws Exception {
        mvc.perform(get("/api/v1/staff/events/{id}/transactions", eventId)
                        .param("result", "bogus").header("Authorization", AUTH))
                .andExpect(status().isBadRequest());

        verify(transactionService, never()).findForEventStaff(any(), any(), any(), any(Pageable.class));
    }

    @Test
    void eventTransactionsWithoutAFilterPassNullAndStillNeedAssignment() throws Exception {
        when(transactionService.findForEventStaff(eq(eventId), eq(null), eq(null), any(Pageable.class))).thenReturn(Page.empty(PageRequest.of(0, 20)));
        mvc.perform(get("/api/v1/staff/events/{id}/transactions", eventId).header("Authorization", AUTH))
                .andExpect(status().isOk());

        UUID unassigned = UUID.randomUUID();
        when(assignments.findByEventIdAndStaffUserIdAndActiveTrue(unassigned, staffId)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/staff/events/{id}/transactions", unassigned).header("Authorization", AUTH))
                .andExpect(status().isForbidden());
    }
}
