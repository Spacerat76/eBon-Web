package de.ebon.parser;

import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.ParseSource;

public record ReceiptParseResult(
        ParseStatus parseStatus,
        ParsedReceipt receipt,
        String errorMessage,
        ParseSource parseSource) {

    public ReceiptParseResult(ParseStatus parseStatus, ParsedReceipt receipt, String errorMessage) {
        this(parseStatus, receipt, errorMessage, null);
    }

    public boolean parsed() {
        return parseStatus == ParseStatus.PARSED;
    }

    public ReceiptParseResult withParseSource(ParseSource source) {
        return new ReceiptParseResult(parseStatus, receipt, errorMessage, source);
    }
}
