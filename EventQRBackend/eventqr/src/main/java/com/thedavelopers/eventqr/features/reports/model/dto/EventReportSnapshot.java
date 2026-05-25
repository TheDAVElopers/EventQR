package com.thedavelopers.eventqr.features.reports.model.dto;

import java.util.UUID;

public record EventReportSnapshot(UUID eventId, long totalAttendees, long registeredCount, long enteredCount,
                                  long exitedCount, long noShowCount, long attendanceCount, long claimsCount,
                                  long boothSessionVisits, long rewardsRedeemed, long totalPointsEarned,
                                  long approvedTransactions, long rejectedTransactions) {
}