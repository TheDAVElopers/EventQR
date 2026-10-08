package com.thedavelopers.eventqr.features.users.model.dto;

import com.thedavelopers.eventqr.shared.utils.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.thedavelopers.eventqr.shared.constants.AccountRole;

public record UserRequest(@NotBlank @Email String email, @NotBlank String fullName, String phoneNumber,
                          @NotBlank @StrongPassword String password, @NotNull AccountRole role) {
}
