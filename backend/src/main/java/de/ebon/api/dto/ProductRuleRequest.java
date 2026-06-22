package de.ebon.api.dto;

import de.ebon.persistence.model.RuleMatchType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductRuleRequest(
        @NotNull
        Long productFamilyId,
        Long productVariantId,
        @Size(max = 255)
        String storeName,
        @NotNull
        RuleMatchType matchType,
        @NotBlank
        @Size(max = 512)
        String matchValue,
        @Min(0)
        @Max(10000)
        Integer priority,
        Boolean isActive) {
}
