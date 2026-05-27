package com.thedavelopers.eventqr.features.auth.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(@NotBlank @Email String email,
                                   @NotBlank String resetToken,
                                   @NotBlank String newPassword) {
}