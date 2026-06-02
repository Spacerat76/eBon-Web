package de.ebon.categorization;

import java.util.List;

public record AiCategorizationBatchResponse(
        String promptSent,
        String responseReceived,
        String modelUsed,
        List<AiCategorizationSuggestion> suggestions) {

    public AiCategorizationBatchResponse {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }
}
