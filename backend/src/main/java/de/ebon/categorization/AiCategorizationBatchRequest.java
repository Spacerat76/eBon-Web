package de.ebon.categorization;

import java.util.List;

public record AiCategorizationBatchRequest(
        List<AiCategorizationItem> items,
        List<String> categoryNames) {
}
