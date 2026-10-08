package com.thedavelopers.eventqr.features.auth.model.dto;

import com.thedavelopers.eventqr.shared.utils.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(@NotBlank String token,
                                   @NotBlank @StrongPassword String newPassword,
                                   @NotBlank @Size(max = 128) String confirmPassword) {
}
