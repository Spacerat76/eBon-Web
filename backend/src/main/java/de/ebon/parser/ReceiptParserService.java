package de.ebon.parser;

import de.ebon.persistence.model.ParseStatus;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ReceiptParserService {

    private final RuleBasedReceiptParser ruleBasedReceiptParser;
    private final AiReceiptParsingClient aiReceiptParsingClient;
    private final AiReceiptJsonParser aiReceiptJsonParser;

    public ReceiptParserService(
            RuleBasedReceiptParser ruleBasedReceiptParser,
            AiReceiptParsingClient aiReceiptParsingClient,
            AiReceiptJsonParser aiReceiptJsonParser) {
        this.ruleBasedReceiptParser = ruleBasedReceiptParser;
        this.aiReceiptParsingClient = aiReceiptParsingClient;
        this.aiReceiptJsonParser = aiReceiptJsonParser;
    }

    public ReceiptParseResult parse(String rawText) {
        ReceiptParseResult ruleResult = ruleBasedReceiptParser.parse(rawText);
        if (ruleResult.parsed()) {
            return ruleResult;
        }

        Optional<String> aiJson = aiReceiptParsingClient.parseReceipt(rawText);
        if (aiJson.isEmpty()) {
            return ruleResult;
        }

        ReceiptParseResult aiResult = aiReceiptJsonParser.parse(aiJson.orElseThrow());
        if (aiResult.parseStatus() == ParseStatus.PARSED) {
            return aiResult;
        }

        ParsedReceipt partialReceipt = ruleResult.receipt() == null ? aiResult.receipt() : ruleResult.receipt();
        String message = aiResult.errorMessage() == null
                ? "KI-JSON entspricht nicht dem erwarteten Parser-Schema."
                : aiResult.errorMessage();
        return new ReceiptParseResult(ParseStatus.PARSE_ERROR, partialReceipt, message);
    }
}
