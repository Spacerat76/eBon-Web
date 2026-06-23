package de.ebon.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record ProductVariantSplitApplyRequest(
        @NotNull Long sourceVariantId,
        @NotEmpty Set<Long> receiptItemIds,
        @NotNull @Valid ProductVariantRequest newVariant,
        @AssertTrue(message = "confirm muss true sein.") Boolean confirm) {
}
