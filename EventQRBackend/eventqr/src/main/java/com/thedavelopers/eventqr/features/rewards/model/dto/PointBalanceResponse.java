package com.thedavelopers.eventqr.features.rewards.model.dto;

import java.util.UUID;

public record PointBalanceResponse(UUID eventId, UUID attendeeUserId, int pointsBalance) {
}