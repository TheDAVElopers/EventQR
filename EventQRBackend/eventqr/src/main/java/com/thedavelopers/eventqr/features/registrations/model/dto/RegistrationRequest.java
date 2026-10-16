package com.thedavelopers.eventqr.features.registrations.model.dto;

import java.util.UUID;

import com.thedavelopers.eventqr.shared.utils.PhilippinePhone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegistrationRequest(@NotNull UUID eventId, @NotBlank @Email String email, @NotBlank String fullName,
                                  @PhilippinePhone String phoneNumber) {
}
