package com.thedavelopers.eventqr.features.dashboard.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardSummary(long totalEvents, long totalRegistrations, long totalTransactions, long totalPoints,
                               long totalNotifications, String fullName,
                               List<DashboardUpcomingEvent> upcomingEvents) {

    public record DashboardUpcomingEvent(UUID eventId, UUID registrationId, String title, String location,
                                         Instant eventStartAt, String status, String category, String description,
                                         Instant eventEndAt, Integer capacity, Integer currentAttendeeCount,
                                         Boolean isRegistered) {
    }
}
