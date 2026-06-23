package de.ebon.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record ProductFamilySplitRequest(
        @NotNull Long sourceFamilyId,
        @NotEmpty Set<Long> receiptItemIds,
        @NotNull @Valid ProductFamilyRequest newFamily) {
}
