package com.thedavelopers.eventqr.features.events.model.dto;

import java.util.UUID;

public record EventApprovalRequest(boolean approved, UUID reviewerUserId, String rejectionReason) {
}