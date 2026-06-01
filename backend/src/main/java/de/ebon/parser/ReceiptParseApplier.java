package de.ebon.parser;

import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import org.springframework.stereotype.Component;

@Component
public class ReceiptParseApplier {

    public void apply(Receipt receipt, ReceiptParseResult parseResult) {
        ParsedReceipt parsedReceipt = parseResult.receipt();
        receipt.applyParseResult(
                parseResult.parseStatus(),
                parseResult.errorMessage(),
                parsedReceipt == null ? null : parsedReceipt.receiptDate(),
                parsedReceipt == null ? null : parsedReceipt.receiptTime(),
                parsedReceipt == null ? null : parsedReceipt.storeName(),
                parsedReceipt == null ? null : parsedReceipt.storeBranch(),
                parsedReceipt == null ? null : parsedReceipt.totalAmount(),
                parsedReceipt == null ? "EUR" : parsedReceipt.currency(),
                parsedReceipt == null ? null : parsedReceipt.bonusBalance(),
                parsedReceipt == null ? null : parsedReceipt.bonusPoints(),
                parsedReceipt == null ? null : parsedReceipt.bonusType());

        if (parsedReceipt == null) {
            return;
        }

        receipt.clearItems();
        for (ParsedReceiptItem parsedItem : parsedReceipt.items()) {
            ReceiptItem item = new ReceiptItem(
                    parsedItem.positionIndex(),
                    parsedItem.description(),
                    parsedItem.totalPrice());
            item.updateParsedValues(
                    parsedItem.quantity(),
                    parsedItem.unit(),
                    parsedItem.unitPrice(),
                    parsedItem.discountAmount());
            receipt.addItem(item);
        }
    }
}
