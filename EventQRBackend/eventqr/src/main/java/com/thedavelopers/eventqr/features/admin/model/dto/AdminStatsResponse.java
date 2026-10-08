package com.thedavelopers.eventqr.features.admin.model.dto;

public record AdminStatsResponse(long totalAccounts, long activeEvents, long auditLogCount) {
}
