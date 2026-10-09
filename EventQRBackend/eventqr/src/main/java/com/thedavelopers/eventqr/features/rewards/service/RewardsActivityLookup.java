package com.thedavelopers.eventqr.features.rewards.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.shared.interfaces.ActivityLookupPort;

/**
 * Points earned or spent (redemptions also write point transactions) count as recorded activity. Scope: the check is
 * keyed on event + attendee, and any point transaction blocks an attendee self-cancel.
 */
@Component
class RewardsActivityLookup implements ActivityLookupPort {

    private final PointTransactionRepository pointTransactionRepository;

    RewardsActivityLookup(PointTransactionRepository pointTransactionRepository) {
        this.pointTransactionRepository = pointTransactionRepository;
    }

    @Override
    public boolean hasRecordedActivity(UUID eventId, UUID attendeeUserId, UUID registrationId) {
        return pointTransactionRepository.existsByEventIdAndAttendeeUserId(eventId, attendeeUserId);
    }
}
