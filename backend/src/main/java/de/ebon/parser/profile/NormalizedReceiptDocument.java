package de.ebon.parser.profile;

import java.util.List;

public record NormalizedReceiptDocument(List<NormalizedReceiptLine> lines) {

    public NormalizedReceiptDocument {
        lines = List.copyOf(lines);
    }
}
