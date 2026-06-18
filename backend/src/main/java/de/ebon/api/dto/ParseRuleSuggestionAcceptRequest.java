package de.ebon.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ParseRuleSuggestionAcceptRequest(
        @Valid
        ParseRuleSuggestionUpdateRequest suggestion,
        @NotNull
        ReparseScope reparseScope) {

    public enum ReparseScope {
        NONE,
        CURRENT_RECEIPT,
        PARSE_ERROR_BY_STORE,
        ALL_PARSE_ERROR
    }
}
