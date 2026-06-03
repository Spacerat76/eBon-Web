package de.ebon.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SearchResultDto(
        Long receiptId,
        Long receiptItemId,
        LocalDate receiptDate,
        String storeName,
        String description,
        BigDecimal totalPrice,
        Long categoryId,
        String categoryName,
        List<String> highlights) {
}
