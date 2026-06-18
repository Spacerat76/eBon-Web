package de.ebon.parser;

import de.ebon.persistence.model.ParseRuleType;
import java.math.BigDecimal;

public record AiParseRuleSuggestionCandidate(
        ParseRuleType ruleType,
        String storeName,
        String matchRegex,
        String extractGroup,
        BigDecimal confidence,
        String problemDescription,
        String solutionRationale) {
}
