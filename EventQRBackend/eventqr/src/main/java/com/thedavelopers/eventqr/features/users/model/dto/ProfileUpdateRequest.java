package com.thedavelopers.eventqr.features.users.model.dto;

import com.thedavelopers.eventqr.shared.utils.PhilippinePhone;

public record ProfileUpdateRequest(String fullName, @PhilippinePhone String phoneNumber) {
}
