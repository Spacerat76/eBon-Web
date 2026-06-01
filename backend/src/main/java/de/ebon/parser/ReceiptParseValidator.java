package de.ebon.parser;

import de.ebon.persistence.model.ParseStatus;
import java.math.BigDecimal;

class ReceiptParseValidator {

    private static final BigDecimal SUM_TOLERANCE = new BigDecimal("0.02");

    ReceiptParseResult validate(ParsedReceipt receipt) {
        if (receipt.totalAmount() == null) {
            return error(receipt, "total_amount fehlt.");
        }
        if (receipt.receiptDate() == null) {
            return error(receipt, "receipt_date fehlt.");
        }
        if (receipt.storeName() == null || receipt.storeName().isBlank()) {
            return error(receipt, "store_name fehlt.");
        }
        if (receipt.items().isEmpty() || receipt.items().stream().anyMatch(item -> item.totalPrice() == null)) {
            return error(receipt, "receipt_items fehlen oder enthalten ungueltige total_price-Werte.");
        }

        BigDecimal itemSum = receipt.items().stream()
                .map(ParsedReceiptItem::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal difference = itemSum.subtract(receipt.totalAmount()).abs();
        if (difference.compareTo(SUM_TOLERANCE) > 0) {
            return error(receipt, "Summe der Positionen weicht mehr als 0.02 vom total_amount ab.");
        }

        return new ReceiptParseResult(ParseStatus.PARSED, receipt, null);
    }

    ReceiptParseResult error(ParsedReceipt receipt, String message) {
        return new ReceiptParseResult(ParseStatus.PARSE_ERROR, receipt, message);
    }
}
