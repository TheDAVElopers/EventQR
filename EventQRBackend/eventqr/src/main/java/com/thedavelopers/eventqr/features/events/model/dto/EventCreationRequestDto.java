package com.thedavelopers.eventqr.features.events.model.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EventCreationRequestDto(
        @NotBlank String eventName,
        @NotBlank String eventDescription,
        @NotBlank String eventCategory,
        String targetAudience,
        @NotNull @Positive Integer capacity,
        @NotBlank String venue,
        @NotNull Instant startDateTime,
        @NotNull Instant endDateTime,
        Instant registrationStartDateTime,
        Instant registrationEndDateTime,
        @NotBlank String requesterName,
        @NotBlank @Email String contactEmail,
        @NotBlank String contactNumber,
        List<String> requestedFeatures,
        String eventLogoUrl,
        String additionalNotes,
        @NotBlank String reasonForRequest) {
}
