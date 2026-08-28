package de.ebon.parser.profile;

/** Offsets are zero-based UTF-16 indexes into the input, with an exclusive end. */
public record NormalizedReceiptLine(
        int originalLineNumber,
        String originalText,
        String matchText,
        int startOffset,
        int endOffset) {
}
