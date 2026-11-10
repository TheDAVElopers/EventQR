package com.thedavelopers.eventqr.features.auth.model.dto;

import com.thedavelopers.eventqr.shared.utils.PhilippinePhone;
import com.thedavelopers.eventqr.shared.utils.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@NotBlank @Email @Size(max = 255, message = "Email must be at most 255 characters") String email,
                              @NotBlank @Size(max = 255, message = "Full name must be at most 255 characters") String fullName,
                              @PhilippinePhone String phoneNumber,
                              @NotBlank @StrongPassword String password) {

    /** Normalize before bean validation runs, so the limit is checked against the value the service stores. */
    public RegisterRequest {
        fullName = fullName == null ? null : fullName.trim();
    }
}
