package com.thedavelopers.eventqr.features.admin.model.dto;

import java.util.List;

public record AdminStatsResponse(
        long totalAccounts,
        long activeEvents,
        long pendingEventRequests,
        List<PendingEventRequestPreview> pendingPreview) {
}
