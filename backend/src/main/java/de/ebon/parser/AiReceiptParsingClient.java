package de.ebon.parser;

import java.util.Optional;

public interface AiReceiptParsingClient {

    Optional<String> parseReceipt(String rawText);
}
