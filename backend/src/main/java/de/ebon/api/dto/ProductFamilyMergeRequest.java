package de.ebon.api.dto;

import jakarta.validation.constraints.NotNull;

public record ProductFamilyMergeRequest(
        @NotNull Long sourceFamilyId,
        @NotNull Long targetFamilyId) {
}
