package de.ebon.parser.profile;

import de.ebon.persistence.model.ParseLineType;
import java.util.Map;

/** References the existing receipt text; profile identity/version is attached by the caller. */
public record ParsedLineTrace(
        int lineNumber,
        int startOffset,
        int endOffset,
        ParseLineType lineType,
        Integer positionIndex,
        Map<String, String> extractedFields,
        String reason) {
    public ParsedLineTrace {
        extractedFields = Map.copyOf(extractedFields);
    }

    public boolean needsReview() {
        return lineType == ParseLineType.UNRESOLVED;
    }
}
