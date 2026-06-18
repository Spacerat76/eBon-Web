package de.ebon.parser;

public record AiReceiptParsingPrompt(
        String rawTextForModel,
        String ruleParserPartial,
        String ruleParserError,
        AiParsingTextMode textMode) {
}
