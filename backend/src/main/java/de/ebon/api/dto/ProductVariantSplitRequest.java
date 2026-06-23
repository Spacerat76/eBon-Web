package de.ebon.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record ProductVariantSplitRequest(
        @NotNull Long sourceVariantId,
        @NotEmpty Set<Long> receiptItemIds,
        @NotNull @Valid ProductVariantRequest newVariant) {
}
