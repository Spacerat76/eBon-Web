package de.ebon.api.dto;

import de.ebon.persistence.model.RuleMatchField;
import de.ebon.persistence.model.RuleMatchType;
import java.time.OffsetDateTime;

public record CategorizationRuleDto(
        Long id,
        Long categoryId,
        String categoryName,
        RuleMatchField matchField,
        RuleMatchType matchType,
        String matchValue,
        int priority,
        boolean isActive,
        OffsetDateTime createdAt,
        String storeName) {
}
