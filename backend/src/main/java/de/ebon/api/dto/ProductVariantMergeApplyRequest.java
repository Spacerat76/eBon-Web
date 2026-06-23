package de.ebon.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record ProductVariantMergeApplyRequest(
        @NotNull Long sourceVariantId,
        @NotNull Long targetVariantId,
        @AssertTrue(message = "confirm muss true sein.") Boolean confirm) {
}
