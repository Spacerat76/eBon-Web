package de.spacerat76.ebon.ai;

import java.util.Optional;

public interface AiClient {
    Optional<AiParseResult> parseReceipt(String rawText);
}
