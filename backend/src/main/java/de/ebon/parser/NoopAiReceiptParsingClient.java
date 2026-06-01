package de.ebon.parser;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class NoopAiReceiptParsingClient implements AiReceiptParsingClient {

    @Override
    public Optional<String> parseReceipt(String rawText) {
        return Optional.empty();
    }
}
