package de.ebon.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record ProductFamilyMergeApplyRequest(
        @NotNull Long sourceFamilyId,
        @NotNull Long targetFamilyId,
        @AssertTrue(message = "confirm muss true sein.") Boolean confirm) {
}
