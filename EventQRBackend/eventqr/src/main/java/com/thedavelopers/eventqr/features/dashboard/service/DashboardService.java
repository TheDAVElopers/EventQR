package com.thedavelopers.eventqr.features.dashboard.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.dashboard.model.dto.DashboardSummary;
import com.thedavelopers.eventqr.features.events.repository.EventRepository;
import com.thedavelopers.eventqr.features.notifications.repository.NotificationRepository;
import com.thedavelopers.eventqr.features.registrations.repository.EventRegistrationRepository;
import com.thedavelopers.eventqr.features.rewards.repository.RewardRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final EventRepository eventRepository;
    private final EventRegistrationRepository eventRegistrationRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final RewardRepository rewardRepository;
    private final NotificationRepository notificationRepository;

    public DashboardService(EventRepository eventRepository,
                            EventRegistrationRepository eventRegistrationRepository,
                            TransactionLogRepository transactionLogRepository,
                            RewardRepository rewardRepository,
                            NotificationRepository notificationRepository) {
        this.eventRepository = eventRepository;
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.transactionLogRepository = transactionLogRepository;
        this.rewardRepository = rewardRepository;
        this.notificationRepository = notificationRepository;
    }

    public DashboardSummary summary() {
        return new DashboardSummary(eventRepository.count(), eventRegistrationRepository.count(), transactionLogRepository.count(),
                rewardRepository.count(), notificationRepository.count());
    }
}