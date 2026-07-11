package de.ebon.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductPriceTrendPointDto(
        Long receiptItemId,
        LocalDate receiptDate,
        String label,
        BigDecimal price,
        String priceUnit,
        boolean outlier) {
}
