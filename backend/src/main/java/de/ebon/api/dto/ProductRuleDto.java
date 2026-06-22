package de.ebon.api.dto;

import de.ebon.persistence.model.RuleMatchType;

public record ProductRuleDto(
        Long id,
        Long productFamilyId,
        String productFamilyName,
        Long productVariantId,
        String productVariantName,
        String storeName,
        RuleMatchType matchType,
        String matchValue,
        int priority,
        boolean isActive) {
}
