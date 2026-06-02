package de.ebon.categorization;

import java.math.BigDecimal;

public record AiCategorizationSuggestion(
        Long itemId,
        String categoryName,
        BigDecimal confidence) {
}
