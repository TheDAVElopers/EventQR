package com.thedavelopers.eventqr.features.rewards.model.dto;

import jakarta.validation.constraints.NotBlank;

public record EventBenefitRequest(@NotBlank String name, String description, boolean active) {
}