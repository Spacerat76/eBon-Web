package de.ebon.parser.profile;

import de.ebon.parser.ParsedReceipt;
import de.ebon.parser.ParsedReceiptItem;
import de.ebon.parser.ReceiptParseResult;
import de.ebon.parser.ReceiptParseValidator;
import de.ebon.persistence.model.ParseLineType;
import de.ebon.persistence.model.ParseSource;
import de.ebon.persistence.model.ParseStatus;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class ProfileParseQualityGate {
    private final ReceiptParseValidator validator = new ReceiptParseValidator();

    public ProfileParseOutcome validate(ProfileInterpretationResult result) {
        ParsedReceipt receipt = result.receipt();
        String error = result.errors().isEmpty() ? validator.requiredDataError(receipt)
                : String.join(", ", result.errors());
        if (error == null) {
            for (int i = 0; i < receipt.items().size(); i++) {
                ParsedReceiptItem item = receipt.items().get(i);
                if (item.positionIndex() != i || item.description() == null || item.description().isBlank()) {
                    error = "INVALID_POSITION";
                    break;
                }
            }
        }
        if (error == null && !validTraceOwnership(result)) {
            error = "INVALID_TRACE_OWNERSHIP";
        }
        ReceiptParseResult parsed;
        if (error != null) {
            parsed = new ReceiptParseResult(ParseStatus.PARSE_ERROR, receipt, error);
        } else if (result.traces().stream().anyMatch(ParsedLineTrace::needsReview)) {
            // Unknown relevant lines may account for a missing subtotal. Never call this PARSED.
            parsed = new ReceiptParseResult(ParseStatus.PARSE_REVIEW, receipt, "UNRESOLVED_LINES");
        } else {
            parsed = validator.validate(receipt);
        }
        return new ProfileParseOutcome(parsed.withParseSource(ParseSource.RULE), result.traces());
    }

    private boolean validTraceOwnership(ProfileInterpretationResult result) {
        Set<Integer> lines = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        int lastLine = 0;
        int lastEnd = 0;
        for (ParsedLineTrace trace : result.traces()) {
            if (!lines.add(trace.lineNumber()) || trace.lineNumber() <= lastLine || trace.startOffset() < lastEnd
                    || trace.endOffset() < trace.startOffset() || trace.lineType() == null) {
                return false;
            }
            lastLine = trace.lineNumber();
            lastEnd = trace.endOffset();
            if (trace.lineType() == ParseLineType.POSITION) {
                if (trace.positionIndex() == null || trace.positionIndex() < 0
                        || trace.positionIndex() >= result.receipt().items().size()) {
                    return false;
                }
                positions.add(trace.positionIndex());
            } else if (trace.positionIndex() != null) {
                return false;
            }
        }
        return positions.size() == result.receipt().items().size();
    }
}
