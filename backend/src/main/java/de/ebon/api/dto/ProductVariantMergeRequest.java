package de.ebon.api.dto;

import jakarta.validation.constraints.NotNull;

public record ProductVariantMergeRequest(
        @NotNull Long sourceVariantId,
        @NotNull Long targetVariantId) {
}
