package de.ebon.parser;

import java.math.BigDecimal;
import java.util.List;

public record AiReceiptJsonParseResult(
        ReceiptParseResult parseResult,
        BigDecimal overallConfidence,
        String fieldConfidenceJson,
        String warningsJson,
        List<AiParseRuleSuggestionCandidate> ruleSuggestions) {

    public AiReceiptJsonParseResult {
        ruleSuggestions = ruleSuggestions == null ? List.of() : List.copyOf(ruleSuggestions);
    }
}
