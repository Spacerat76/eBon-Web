package de.ebon.parser;

import de.ebon.persistence.model.AiParsingLog;
import de.ebon.persistence.model.ParseRuleSuggestion;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.repository.ParseRuleSuggestionRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ParseRuleSuggestionWriter {

    private final ParseRuleSuggestionRepository suggestionRepository;
    private final ParseRuleSuggestionValidator validator;

    public ParseRuleSuggestionWriter(
            ParseRuleSuggestionRepository suggestionRepository,
            ParseRuleSuggestionValidator validator) {
        this.suggestionRepository = suggestionRepository;
        this.validator = validator;
    }

    public void saveSuggestions(
            AiParsingLog log,
            Receipt receipt,
            List<AiParseRuleSuggestionCandidate> candidates,
            String rawText) {
        for (AiParseRuleSuggestionCandidate candidate : candidates) {
            ParseRuleSuggestionValidator.ValidationResult validation = validator.validate(
                    rawText,
                    candidate.ruleType(),
                    candidate.matchRegex(),
                    candidate.extractGroup());
            suggestionRepository.save(new ParseRuleSuggestion(
                    log,
                    receipt,
                    candidate.storeName() == null ? receipt.getStoreName() : candidate.storeName(),
                    candidate.ruleType(),
                    candidate.matchRegex(),
                    candidate.extractGroup(),
                    candidate.confidence(),
                    log.getTrigger(),
                    candidate.problemDescription(),
                    candidate.solutionRationale(),
                    validation.status(),
                    validation.message()));
        }
    }
}
