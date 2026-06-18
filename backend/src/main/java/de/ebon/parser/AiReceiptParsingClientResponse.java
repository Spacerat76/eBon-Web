package de.ebon.parser;

public record AiReceiptParsingClientResponse(
        String content,
        String modelUsed,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {
}
