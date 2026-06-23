package de.ebon.api.dto;

public record ProductRuleSuggestionAcceptResponse(
        ProductRuleDto rule,
        long changedItemsCount) {
}
