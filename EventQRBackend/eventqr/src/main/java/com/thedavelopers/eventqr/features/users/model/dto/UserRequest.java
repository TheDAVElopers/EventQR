package com.thedavelopers.eventqr.features.users.model.dto;

import com.thedavelopers.eventqr.shared.constants.AccountRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserRequest(@NotBlank @Email String email, @NotBlank String fullName, String phoneNumber,
                          @NotNull AccountRole role) {
}