package com.thedavelopers.eventqr.features.registrations.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegistrationRequest(@NotNull UUID eventId, @NotBlank @Email String email, @NotBlank String fullName,
                                  String phoneNumber) {
}