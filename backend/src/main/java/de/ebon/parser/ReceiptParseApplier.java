package de.ebon.parser;

import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import org.springframework.stereotype.Component;

@Component
public class ReceiptParseApplier {

    private static final int MAX_STORE_NAME_LENGTH = 255;
    private static final int MAX_STORE_BRANCH_LENGTH = 255;
    private static final int MAX_BONUS_TYPE_LENGTH = 64;
    private static final int MAX_ITEM_DESCRIPTION_LENGTH = 512;
    private static final int MAX_ITEM_UNIT_LENGTH = 32;
    private static final String FALLBACK_ITEM_DESCRIPTION = "Unbekannte Position";

    public void apply(Receipt receipt, ReceiptParseResult parseResult) {
        ParsedReceipt parsedReceipt = parseResult.receipt();
        receipt.applyParseResult(
                parseResult.parseStatus(),
                parseResult.errorMessage(),
                parsedReceipt == null ? null : parsedReceipt.receiptDate(),
                parsedReceipt == null ? null : parsedReceipt.receiptTime(),
                parsedReceipt == null ? null : limitText(parsedReceipt.storeName(), MAX_STORE_NAME_LENGTH),
                parsedReceipt == null ? null : limitText(parsedReceipt.storeBranch(), MAX_STORE_BRANCH_LENGTH),
                parsedReceipt == null ? null : parsedReceipt.totalAmount(),
                parsedReceipt == null ? "EUR" : parsedReceipt.currency(),
                parsedReceipt == null ? null : parsedReceipt.bonusBalance(),
                parsedReceipt == null ? null : parsedReceipt.bonusPoints(),
                parsedReceipt == null ? null : limitText(parsedReceipt.bonusType(), MAX_BONUS_TYPE_LENGTH));

        if (parsedReceipt == null) {
            return;
        }

        receipt.clearItems();
        for (ParsedReceiptItem parsedItem : parsedReceipt.items()) {
            ReceiptItem item = new ReceiptItem(
                    parsedItem.positionIndex(),
                    requiredLimitedText(
                            parsedItem.description(),
                            MAX_ITEM_DESCRIPTION_LENGTH,
                            FALLBACK_ITEM_DESCRIPTION),
                    parsedItem.totalPrice());
            item.updateParsedValues(
                    parsedItem.quantity(),
                    limitText(parsedItem.unit(), MAX_ITEM_UNIT_LENGTH),
                    parsedItem.unitPrice(),
                    parsedItem.discountAmount());
            receipt.addItem(item);
        }
    }

    private String requiredLimitedText(String value, int maxLength, String fallback) {
        String limited = limitText(value, maxLength);
        return limited == null || limited.isBlank() ? fallback : limited;
    }

    private String limitText(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String normalized = value.strip();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength).stripTrailing();
    }
}
