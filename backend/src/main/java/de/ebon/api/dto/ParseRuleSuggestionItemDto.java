package de.ebon.api.dto;

import java.math.BigDecimal;

public record ParseRuleSuggestionItemDto(
        int positionIndex,
        String description,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        BigDecimal discountAmount) {
}
