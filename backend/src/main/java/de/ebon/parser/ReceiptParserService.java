package de.ebon.parser;

import de.ebon.persistence.model.ParseSource;
import de.ebon.persistence.model.Receipt;
import de.ebon.parser.profile.ProfileAwareReceiptParser;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReceiptParserService {

    private final Function<String, ReceiptParseResult> deterministicParser;
    private final AiParsingFallbackService aiParsingFallbackService;

    @Autowired
    public ReceiptParserService(
            ProfileAwareReceiptParser profileAwareReceiptParser,
            AiParsingFallbackService aiParsingFallbackService) {
        this.deterministicParser = profileAwareReceiptParser::parse;
        this.aiParsingFallbackService = aiParsingFallbackService;
    }

    ReceiptParserService(
            RuleBasedReceiptParser ruleBasedReceiptParser,
            Function<String, Optional<String>> ignoredAiClient,
            AiReceiptJsonParser ignoredAiReceiptJsonParser) {
        this.deterministicParser = ruleBasedReceiptParser::parse;
        this.aiParsingFallbackService = null;
    }

    public ReceiptParseResult parse(String rawText) {
        ReceiptParseResult ruleResult = deterministicParser.apply(rawText);
        return ruleResult.parsed() ? ruleResult.withParseSource(ParseSource.RULE) : ruleResult;
    }

    public ReceiptParseResult parse(Receipt receipt, ParseExecutionOptions options) {
        ReceiptParseResult ruleResult = parse(receipt.getRawText());
        if (ruleResult.parsed()) {
            return ruleResult;
        }
        return aiParsingFallbackService == null ? ruleResult : aiParsingFallbackService.tryFallback(receipt, ruleResult, options);
    }

    public AiParsingBudget newSyncAiParsingBudget() {
        return aiParsingFallbackService == null ? AiParsingBudget.unlimited() : aiParsingFallbackService.newSyncBudget();
    }
}
