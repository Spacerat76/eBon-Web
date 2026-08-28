package de.ebon.parser;

import de.ebon.persistence.model.ParseStatus;
import java.math.BigDecimal;

public class ReceiptParseValidator {

    private static final BigDecimal SUM_TOLERANCE = new BigDecimal("0.02");

    public ReceiptParseResult validate(ParsedReceipt receipt) {
        String requiredDataError = requiredDataError(receipt);
        if (requiredDataError != null) {
            return error(receipt, requiredDataError);
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

    /** Shared required-data check, independent of a possibly incomplete item subtotal. */
    public String requiredDataError(ParsedReceipt receipt) {
        if (receipt.totalAmount() == null) {
            return "total_amount fehlt.";
        }
        if (receipt.receiptDate() == null) {
            return "receipt_date fehlt.";
        }
        if (receipt.storeName() == null || receipt.storeName().isBlank()) {
            return "store_name fehlt.";
        }
        if (receipt.items().isEmpty() || receipt.items().stream().anyMatch(item -> item.totalPrice() == null)) {
            return "receipt_items fehlen oder enthalten ungueltige total_price-Werte.";
        }
        return null;
    }

    ReceiptParseResult error(ParsedReceipt receipt, String message) {
        return new ReceiptParseResult(ParseStatus.PARSE_ERROR, receipt, message);
    }
}
