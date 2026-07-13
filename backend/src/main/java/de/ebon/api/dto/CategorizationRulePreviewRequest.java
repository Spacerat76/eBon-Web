package de.ebon.api.dto;

import de.ebon.persistence.model.RuleMatchField;
import de.ebon.persistence.model.RuleMatchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategorizationRulePreviewRequest(
        Long categoryId,
        @NotNull
        RuleMatchField matchField,
        @NotNull
        RuleMatchType matchType,
        @NotBlank
        @Size(max = 512)
        String matchValue,
        @Size(max = 255)
        String storeName) {

    public CategorizationRulePreviewRequest(
            Long categoryId, RuleMatchField matchField, RuleMatchType matchType, String matchValue) {
        this(categoryId, matchField, matchType, matchValue, null);
    }

    @AssertTrue(message = "Eine zusätzliche Händlerbedingung ist nur für Beschreibungsregeln erlaubt.")
    public boolean isStoreConstraintValid() {
        return matchField != RuleMatchField.STORE_NAME || storeName == null || storeName.isBlank();
    }
}
