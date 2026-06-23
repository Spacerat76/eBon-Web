package de.ebon.api.dto;

public record ProductRuleSuggestionDto(
        ProductRuleRequest rule,
        ProductRulePreviewResponse preview) {
}
