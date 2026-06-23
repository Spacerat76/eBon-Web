package de.ebon.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record ProductRuleSuggestionAcceptRequest(
        @NotNull @Valid ProductRuleRequest rule,
        Boolean applyToExisting,
        @AssertTrue(message = "confirm muss true sein.") Boolean confirm) {
}
