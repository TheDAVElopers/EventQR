package com.thedavelopers.eventqr.features.auth.model.dto;

import java.util.UUID;

import com.thedavelopers.eventqr.shared.constants.AccountRole;

public record LoginResponse(String accessToken, UUID userId, String email, String fullName, AccountRole role,
                            String message) {
}