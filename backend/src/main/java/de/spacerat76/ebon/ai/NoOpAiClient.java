package de.spacerat76.ebon.ai;

import java.util.Optional;

public class NoOpAiClient implements AiClient {

    @Override
    public Optional<AiParseResult> parseReceipt(String rawText) {
        return Optional.empty();
    }
}
