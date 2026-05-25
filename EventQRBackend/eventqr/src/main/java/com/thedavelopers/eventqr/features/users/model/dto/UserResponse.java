package com.thedavelopers.eventqr.features.users.model.dto;

import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.constants.AccountStatus;

public record UserResponse(UUID userId, String email, String fullName, String phoneNumber, AccountRole role,
                           AccountStatus status) {
}