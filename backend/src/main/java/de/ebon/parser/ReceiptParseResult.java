package de.ebon.parser;

import de.ebon.persistence.model.ParseStatus;

public record ReceiptParseResult(
        ParseStatus parseStatus,
        ParsedReceipt receipt,
        String errorMessage) {

    public boolean parsed() {
        return parseStatus == ParseStatus.PARSED;
    }
}
