package de.ebon.api.dto;

import de.ebon.persistence.model.RuleMatchField;
import de.ebon.persistence.model.RuleMatchType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategorizationRuleRequest(
        @NotNull
        Long categoryId,
        @NotNull
        RuleMatchField matchField,
        @NotNull
        RuleMatchType matchType,
        @NotBlank
        @Size(max = 512)
        String matchValue,
        @Min(0)
        @Max(10000)
        Integer priority,
        Boolean isActive,
        Boolean applyToExisting,
        @Size(max = 255)
        String storeName) {

    public CategorizationRuleRequest(
            Long categoryId, RuleMatchField matchField, RuleMatchType matchType, String matchValue,
            Integer priority, Boolean isActive, Boolean applyToExisting) {
        this(categoryId, matchField, matchType, matchValue, priority, isActive, applyToExisting, null);
    }

    @AssertTrue(message = "Eine zusätzliche Händlerbedingung ist nur für Beschreibungsregeln erlaubt.")
    public boolean isStoreConstraintValid() {
        return matchField != RuleMatchField.STORE_NAME || storeName == null || storeName.isBlank();
    }
}
