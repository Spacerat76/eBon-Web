package de.ebon.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductPriceStoreDto(
        String storeName,
        String storeBranch,
        String label,
        String priceUnit,
        BigDecimal latestPrice,
        LocalDate latestReceiptDate,
        BigDecimal minimumPrice,
        BigDecimal averagePrice,
        BigDecimal medianPrice,
        long observationCount) {
}
