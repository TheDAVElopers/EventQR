package com.thedavelopers.eventqr.features.notifications.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationRequest(@NotNull UUID eventId, @NotNull UUID recipientUserId, @NotBlank String title,
                                  @NotBlank String message) {
}