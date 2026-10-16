package com.thedavelopers.eventqr.features.rewards.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import com.thedavelopers.eventqr.features.rewards.model.dto.RewardRedemptionScanRequest;
import com.thedavelopers.eventqr.features.rewards.repository.AttendeePointBalanceRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRepository;
import com.thedavelopers.eventqr.features.transactions.model.dto.ScanVerificationResponse;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionRequest;
import com.thedavelopers.eventqr.features.transactions.model.dto.TransactionResponse;
import com.thedavelopers.eventqr.features.transactions.service.TransactionService;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.constants.ScanPurposeCode;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;
import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;
import com.thedavelopers.eventqr.shared.interfaces.ScanPurposePort;

/** The redemption-scan route records only reward-redemption purposes; anything else is refused before record(). */
class RewardRedemptionScanServicePurposeTest {

    private final UUID eventId = UUID.randomUUID();
    private final UUID purposeId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID attendeeId = UUID.randomUUID();
    private TransactionService transactionService;
    private ScanPurposePort scanPurposePort;
    private RewardRedemptionScanService service;

    @BeforeEach
    void setUp() {
        transactionService = mock(TransactionService.class);
        AttendeePointBalanceRepository balances = mock(AttendeePointBalanceRepository.class);
        RewardRepository rewards = mock(RewardRepository.class);
        RewardRedemptionRepository redemptions = mock(RewardRedemptionRepository.class);
        when(balances.findByEventIdAndAttendeeUserId(any(), any())).thenReturn(Optional.empty());
        when(rewards.findByEventId(eventId)).thenReturn(List.of());
        when(redemptions.countByEventIdAndStatusGroupedByReward(any(), any())).thenReturn(List.of());
        scanPurposePort = mock(ScanPurposePort.class);
        service = new RewardRedemptionScanService(transactionService, balances, rewards, redemptions, scanPurposePort);
    }

    private void verifiedAs(ScanPurposeCode code) {
        when(scanPurposePort.requireActive(purposeId)).thenReturn(
                new ScanPurposePort.ScanPurposeSnapshot(purposeId, eventId, code.name(), code, true, false, null));
        when(transactionService.verify(any())).thenReturn(new ScanVerificationResponse(eventId, attendeeId,
                UUID.randomUUID(), UUID.randomUUID(), "EVQR-ABC", "Jane", "jane@example.com",
                RegistrationStatus.REGISTERED, purposeId, code, true, "ok", Instant.now(), true));
    }

    private RewardRedemptionScanRequest request() {
        return new RewardRedemptionScanRequest(eventId, purposeId, "EVQR-ABC", null, staffId);
    }

    @ParameterizedTest
    @EnumSource(value = ScanPurposeCode.class, names = {"REWARD_REDEMPTION_SCAN", "REWARD_REDEMPTION"},
            mode = EnumSource.Mode.EXCLUDE)
    void nonRedemptionPurposeFailsFastBeforeVerifyAndIsNeverRecorded(ScanPurposeCode code) {
        verifiedAs(code);

        assertThatThrownBy(() -> service.scan(request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Scan purpose " + code + " cannot be recorded on the reward redemption scan route");

        verify(transactionService, never()).verify(any());
        verify(transactionService, never()).record(any());
    }

    @ParameterizedTest
    @EnumSource(value = ScanPurposeCode.class, names = {"REWARD_REDEMPTION_SCAN", "REWARD_REDEMPTION"})
    void redemptionPurposeIsRecordedAsTheGivenStaff(ScanPurposeCode code) {
        verifiedAs(code);
        UUID scanLogId = UUID.randomUUID();
        when(transactionService.record(any())).thenReturn(new TransactionResponse(scanLogId, eventId, attendeeId,
                UUID.randomUUID(), UUID.randomUUID(), purposeId, TransactionType.valueOf(code.name()),
                TransactionResult.APPROVED, 0, null, Instant.now(), "Expo"));

        var response = service.scan(request());

        assertThat(response.redemptionScanLogId()).isEqualTo(scanLogId);
        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).record(captor.capture());
        assertThat(captor.getValue().staffUserId()).isEqualTo(staffId);
    }

    @Test
    void verifyFailureIsPropagatedWithoutRecording() {
        verifiedAs(ScanPurposeCode.REWARD_REDEMPTION_SCAN);
        when(transactionService.verify(any())).thenThrow(new BadRequestException("Invalid QR credential"));

        assertThatThrownBy(() -> service.scan(request())).isInstanceOf(BadRequestException.class);
        verify(transactionService, never()).record(any());
    }
}
