package de.ebon.parser.profile;

import de.ebon.parser.ReceiptParseResult;
import java.util.List;

public record ProfileParseOutcome(ReceiptParseResult parseResult, List<ParsedLineTrace> traces) {
    public ProfileParseOutcome {
        traces = List.copyOf(traces);
    }
}
