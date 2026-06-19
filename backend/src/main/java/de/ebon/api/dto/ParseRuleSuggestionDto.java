package de.ebon.api.dto;

import de.ebon.persistence.model.AiParsingTrigger;
import de.ebon.persistence.model.ParseRuleSuggestionStatus;
import de.ebon.persistence.model.ParseRuleType;
import de.ebon.persistence.model.ParseRuleValidationStatus;
import java.math.BigDecimal;

public record ParseRuleSuggestionDto(
        Long id,
        Long receiptId,
        Long aiParsingLogId,
        String storeName,
        ParseRuleType ruleType,
        String matchRegex,
        String extractGroup,
        BigDecimal confidence,
        AiParsingTrigger trigger,
        String problemDescription,
        String solutionRationale,
        ParseRuleValidationStatus validationStatus,
        String validationMessage,
        ParseRuleSuggestionStatus status,
        String rejectionReason,
        Long acceptedParseRuleId,
        ParseRuleSuggestionReceiptContextDto receiptContext) {
}
