package com.thedavelopers.eventqr.features.reports.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.reports.model.dto.EventReportSnapshot;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRedemptionRepository;
import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.shared.constants.RegistrationStatus;
import com.thedavelopers.eventqr.shared.constants.TransactionResult;
import com.thedavelopers.eventqr.shared.constants.TransactionType;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final EventRegistrationRepository eventRegistrationRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final RewardRedemptionRepository rewardRedemptionRepository;
    private final PointTransactionRepository pointTransactionRepository;

    public ReportService(EventRegistrationRepository eventRegistrationRepository,
                         TransactionLogRepository transactionLogRepository,
                         RewardRedemptionRepository rewardRedemptionRepository,
                         PointTransactionRepository pointTransactionRepository) {
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.rewardRedemptionRepository = rewardRedemptionRepository;
        this.pointTransactionRepository = pointTransactionRepository;
    }

    public EventReportSnapshot generate(UUID eventId) {
        List<com.thedavelopers.eventqr.features.registrations.model.entity.EventRegistration> registrations = eventRegistrationRepository.findByEventId(eventId);
        List<com.thedavelopers.eventqr.features.transactions.model.entity.TransactionLog> transactions = transactionLogRepository.findByEventId(eventId);
        long registered = registrations.size();
        long entered = registrations.stream().filter(registration -> registration.getStatus() == RegistrationStatus.ENTERED).count();
        long exited = registrations.stream().filter(registration -> registration.getStatus() == RegistrationStatus.EXITED).count();
        long noShow = registrations.stream().filter(registration -> registration.getStatus() == RegistrationStatus.NO_SHOW).count();
        long attendance = transactions.stream().filter(transaction -> transaction.getTransactionType() == TransactionType.ATTENDANCE && transaction.getTransactionResult() == TransactionResult.APPROVED).count();
        long claims = transactions.stream().filter(transaction -> transaction.getTransactionType() == TransactionType.BENEFIT_CLAIM && transaction.getTransactionResult() == TransactionResult.APPROVED).count();
        long boothSessionVisits = transactions.stream().filter(transaction -> (transaction.getTransactionType() == TransactionType.BOOTH_VISIT || transaction.getTransactionType() == TransactionType.SESSION_VISIT) && transaction.getTransactionResult() == TransactionResult.APPROVED).count();
        long approved = transactions.stream().filter(transaction -> transaction.getTransactionResult() == TransactionResult.APPROVED).count();
        long rejected = transactions.stream().filter(transaction -> transaction.getTransactionResult() == TransactionResult.REJECTED).count();
        long rewardsRedeemed = rewardRedemptionRepository.findByEventId(eventId).stream().filter(redemption -> redemption.getStatus() == com.thedavelopers.eventqr.shared.constants.RedemptionStatus.REDEEMED).count();
        long totalPoints = pointTransactionRepository.findByEventId(eventId).stream().mapToLong(com.thedavelopers.eventqr.features.rewards.model.entity.PointTransaction::getPointsChanged).sum();
        return new EventReportSnapshot(eventId, registered, registered, entered, exited, noShow, attendance, claims,
                boothSessionVisits, rewardsRedeemed, totalPoints, approved, rejected);
    }
}