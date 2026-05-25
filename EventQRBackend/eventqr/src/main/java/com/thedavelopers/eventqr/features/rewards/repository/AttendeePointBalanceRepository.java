package com.thedavelopers.eventqr.features.rewards.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thedavelopers.eventqr.features.rewards.model.entity.AttendeePointBalance;

public interface AttendeePointBalanceRepository extends JpaRepository<AttendeePointBalance, UUID> {

    Optional<AttendeePointBalance> findByEventIdAndAttendeeUserId(UUID eventId, UUID attendeeUserId);
}