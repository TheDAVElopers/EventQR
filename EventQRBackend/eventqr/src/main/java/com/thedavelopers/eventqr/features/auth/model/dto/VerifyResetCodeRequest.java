package com.thedavelopers.eventqr.features.auth.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyResetCodeRequest(@NotBlank @Email @Size(max = 254) String email,
                                     @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Reset code must be 6 digits") String code) {
}
