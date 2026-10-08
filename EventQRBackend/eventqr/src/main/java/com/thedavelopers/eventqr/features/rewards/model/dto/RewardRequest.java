package com.thedavelopers.eventqr.features.rewards.model.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Stock semantics (resolved in order):
 * <ol>
 *   <li>{@code unlimitedStock == true}: stock becomes unlimited (null).</li>
 *   <li>{@code totalQuantity != null}: total ever available; remaining stock = totalQuantity - claimedCount
 *       (400 when below the already-claimed count).</li>
 *   <li>legacy {@code stockQuantity != null}: sets the REMAINING stock directly.</li>
 *   <li>otherwise: create = unlimited; update = stock unchanged.</li>
 * </ol>
 * Description on update: null/absent keeps the stored value, an empty string clears it, anything else replaces it.
 */
public record RewardRequest(@NotNull UUID eventId, @NotBlank String name, String description, @Min(0) int pointsRequired,
                            @Min(0) Integer stockQuantity, boolean allowDuplicateClaims,
                            @Min(0) Integer totalQuantity, Boolean unlimitedStock) {

    /** Legacy constructor kept for callers that predate totalQuantity/unlimitedStock. */
    public RewardRequest(UUID eventId, String name, String description, int pointsRequired,
                         Integer stockQuantity, boolean allowDuplicateClaims) {
        this(eventId, name, description, pointsRequired, stockQuantity, allowDuplicateClaims, null, null);
    }
}
