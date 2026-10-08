package com.thedavelopers.eventqr.features.transactions.model.dto;

/**
 * Today's (business-zone day) figures for the calling staff member. {@code successfulCheckIns} counts
 * distinct attendees with an APPROVED ENTRY/ATTENDANCE scan, not transactions.
 */
public record StaffTodaySummary(long scannedToday, long successfulCheckIns) {
}
