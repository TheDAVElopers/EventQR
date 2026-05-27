package com.thedavelopers.eventqr.shared.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.EventStatus;

public interface EventLookupPort {

    EventSnapshot requireEvent(UUID eventId);

    Optional<EventSnapshot> findById(UUID eventId);

    List<EventSnapshot> listAll();

    record EventSnapshot(UUID eventId, String title, String location, EventStatus status, Instant registrationOpenAt,
                         Instant registrationCloseAt, Instant eventStartAt, Instant eventEndAt, int capacity,
                         int currentAttendeeCount, boolean rewardsEnabled, UUID organizerUserId) {

        public boolean registrationOpen() {
            Instant now = Instant.now();
            boolean windowOpen = (registrationOpenAt == null || !now.isBefore(registrationOpenAt))
                    && (registrationCloseAt == null || !now.isAfter(registrationCloseAt));
            return (status == EventStatus.APPROVED || status == EventStatus.ACTIVE) && windowOpen;
        }

        public boolean isFull() {
            return capacity > 0 && currentAttendeeCount >= capacity;
        }
    }
}