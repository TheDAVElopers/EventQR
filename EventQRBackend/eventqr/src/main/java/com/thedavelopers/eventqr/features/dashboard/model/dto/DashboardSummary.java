package com.thedavelopers.eventqr.features.dashboard.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Dashboard payload. The legacy field names are kept for client compatibility; their real meaning is:
 * <ul>
 *   <li>{@code totalEvents} - open events available platform-wide (APPROVED or ACTIVE); same as {@code availableEvents}</li>
 *   <li>{@code totalRegistrations} - the user's registrations excluding CANCELLED and NO_SHOW</li>
 *   <li>{@code totalTransactions} - the user's APPROVED scan transactions only</li>
 *   <li>{@code totalPoints} - the user's current points balance across events; same as {@code pointsBalance}</li>
 *   <li>{@code totalNotifications} - UNREAD notifications; same as {@code unreadNotifications}</li>
 *   <li>{@code completedEventsCount} - the user's counted registrations whose event has ENDED or that were attended</li>
 * </ul>
 */
public record DashboardSummary(long totalEvents, long totalRegistrations, long totalTransactions, long totalPoints,
                               long totalNotifications, String fullName,
                               List<DashboardUpcomingEvent> upcomingEvents,
                               long completedEventsCount, long unreadNotifications, long pointsBalance,
                               long availableEvents) {

    public record DashboardUpcomingEvent(UUID eventId, UUID registrationId, String title, String location,
                                         Instant eventStartAt, String status, String category, String description,
                                         Instant eventEndAt, Integer capacity, Integer currentAttendeeCount,
                                         Boolean isRegistered) {
    }
}
