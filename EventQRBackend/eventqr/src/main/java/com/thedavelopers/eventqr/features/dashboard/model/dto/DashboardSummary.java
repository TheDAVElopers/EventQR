package com.thedavelopers.eventqr.features.dashboard.model.dto;

public record DashboardSummary(long totalEvents, long totalRegistrations, long totalTransactions, long totalRewards,
                               long totalNotifications) {
}