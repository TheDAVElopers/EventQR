package com.thedavelopers.eventqr.features.users.model.dto;

import com.thedavelopers.eventqr.shared.utils.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(@NotBlank @Size(max = 128) String currentPassword, @NotBlank @StrongPassword String newPassword) {
}
