package de.ebon.parser;

import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.ParseSource;
import de.ebon.parser.profile.AppliedProfile;
import de.ebon.parser.profile.ParsedLineTrace;
import java.util.List;

public record ReceiptParseResult(
        ParseStatus parseStatus,
        ParsedReceipt receipt,
        String errorMessage,
        ParseSource parseSource,
        AppliedProfile appliedProfile,
        List<ParsedLineTrace> traces) {

    public ReceiptParseResult {
        traces = List.copyOf(traces);
    }

    public ReceiptParseResult(ParseStatus parseStatus, ParsedReceipt receipt, String errorMessage, ParseSource parseSource) {
        this(parseStatus, receipt, errorMessage, parseSource, null, List.of());
    }

    public ReceiptParseResult(ParseStatus parseStatus, ParsedReceipt receipt, String errorMessage) {
        this(parseStatus, receipt, errorMessage, null);
    }

    public boolean parsed() {
        return parseStatus == ParseStatus.PARSED;
    }

    public ReceiptParseResult withParseSource(ParseSource source) {
        return new ReceiptParseResult(parseStatus, receipt, errorMessage, source, appliedProfile, traces);
    }
}
