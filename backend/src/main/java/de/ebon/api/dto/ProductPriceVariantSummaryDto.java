package de.ebon.api.dto;

import java.math.BigDecimal;

public record ProductPriceVariantSummaryDto(
        Long productVariantId,
        String productVariantName,
        BigDecimal latestEffectivePrice,
        BigDecimal minimumEffectivePrice,
        long observationCount) {
}
