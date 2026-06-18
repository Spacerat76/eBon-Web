package de.ebon.parser;

import java.math.BigDecimal;

public record AiParsingSettings(
        boolean fallbackEnabled,
        String openRouterBaseUrl,
        String openRouterApiKey,
        String model,
        int maxTokens,
        double temperature,
        BigDecimal minConfidence,
        int syncCallLimit,
        AiParsingTextMode textMode,
        boolean storeDebugSnippets) {

    public boolean hasApiKey() {
        return openRouterApiKey != null && !openRouterApiKey.isBlank();
    }
}
