package de.ebon.parser;

public interface AiReceiptParsingClient {

    AiReceiptParsingClientResponse parseReceipt(AiReceiptParsingPrompt prompt, AiParsingSettings settings);
}
