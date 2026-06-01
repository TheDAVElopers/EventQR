package com.thedavelopers.eventqr.features.events.model.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EventRequest(@NotBlank String title, String description, String location, String eventLogoUrl,
                           Instant registrationOpenAt, Instant registrationCloseAt, Instant eventStartAt,
                           Instant eventEndAt, @NotNull Integer capacity, @NotNull Boolean rewardsEnabled,
                           @NotNull UUID organizerUserId) {
}
