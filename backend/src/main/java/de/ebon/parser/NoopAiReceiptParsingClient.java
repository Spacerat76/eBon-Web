package de.ebon.parser;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(AiReceiptParsingClient.class)
class NoopAiReceiptParsingClient implements AiReceiptParsingClient {

    @Override
    public AiReceiptParsingClientResponse parseReceipt(AiReceiptParsingPrompt prompt, AiParsingSettings settings) {
        return new AiReceiptParsingClientResponse("", settings.model(), null, null, null);
    }
}
