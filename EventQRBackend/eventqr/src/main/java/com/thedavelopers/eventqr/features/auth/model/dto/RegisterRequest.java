package com.thedavelopers.eventqr.features.auth.model.dto;

import com.thedavelopers.eventqr.shared.utils.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(@NotBlank @Email String email, @NotBlank String fullName, String phoneNumber,
                              @NotBlank @StrongPassword String password) {
}
