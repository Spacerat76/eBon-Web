package de.ebon.api.dto;

import de.ebon.persistence.model.RuleMatchType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProductRuleSuggestionRequest(
        @NotNull RuleMatchType matchType,
        Boolean storeSpecific,
        @Min(0) @Max(10000) Integer priority) {
}
