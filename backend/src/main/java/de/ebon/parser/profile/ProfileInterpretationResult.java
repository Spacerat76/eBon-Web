package de.ebon.parser.profile;

import de.ebon.parser.ParsedReceipt;
import java.util.List;

/** Interpretation errors are sanitized codes, never profile patterns or receipt text. */
public record ProfileInterpretationResult(ParsedReceipt receipt, List<ParsedLineTrace> traces, List<String> errors) {
    public ProfileInterpretationResult {
        traces = List.copyOf(traces);
        errors = List.copyOf(errors);
    }
}
