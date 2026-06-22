package de.ebon.api.dto;

import de.ebon.persistence.model.RuleMatchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductRulePreviewRequest(
        @Size(max = 255)
        String storeName,
        @NotNull
        RuleMatchType matchType,
        @NotBlank
        @Size(max = 512)
        String matchValue) {
}
