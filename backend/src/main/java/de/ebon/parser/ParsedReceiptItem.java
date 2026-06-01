package de.ebon.parser;

import java.math.BigDecimal;

public record ParsedReceiptItem(
        int positionIndex,
        String description,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        BigDecimal discountAmount) {
}
