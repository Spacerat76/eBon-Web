package de.ebon.api.dto;

import jakarta.validation.constraints.NotNull;

public record ProductAssignmentCorrectionRequest(
        @NotNull Long productFamilyId,
        Long productVariantId) {
}
