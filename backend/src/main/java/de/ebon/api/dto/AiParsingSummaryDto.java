package de.ebon.api.dto;

import de.ebon.persistence.model.AiParsingStatus;
import de.ebon.persistence.model.AiParsingTrigger;
import java.math.BigDecimal;

public record AiParsingSummaryDto(
        AiParsingStatus lastStatus,
        AiParsingTrigger lastTrigger,
        String modelUsed,
        BigDecimal overallConfidence,
        boolean hasOpenRuleSuggestions) {
}
