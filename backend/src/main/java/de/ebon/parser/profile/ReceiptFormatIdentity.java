package de.ebon.parser.profile;

/** Contains identity fields only, never OCR lines or a serialized structural preimage. */
public record ReceiptFormatIdentity(
        String storeName,
        String storeNameKey,
        String storeBranch,
        String storeBranchKey,
        String fingerprint,
        int fingerprintVersion) {
}
