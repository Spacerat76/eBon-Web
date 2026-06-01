package de.ebon.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ParsedReceipt(
        LocalDate receiptDate,
        LocalTime receiptTime,
        String storeName,
        String storeBranch,
        BigDecimal totalAmount,
        String currency,
        BigDecimal bonusBalance,
        BigDecimal bonusPoints,
        String bonusType,
        List<ParsedReceiptItem> items) {

    public ParsedReceipt {
        currency = currency == null || currency.isBlank() ? "EUR" : currency;
        items = items == null ? List.of() : List.copyOf(items);
    }
}
