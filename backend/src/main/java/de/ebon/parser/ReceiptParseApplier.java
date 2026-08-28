package de.ebon.parser;

import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.model.ReceiptFormatProfile;
import de.ebon.persistence.model.ReceiptParseTrace;
import de.ebon.persistence.repository.ReceiptFormatProfileRepository;
import de.ebon.persistence.repository.ReceiptParseTraceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
public class ReceiptParseApplier {

    private final ReceiptFormatProfileRepository profiles;
    private final ReceiptParseTraceRepository traces;
    private final ObjectMapper objectMapper;

    /** Compatibility for detached Legacy-only callers. Persisted profile application uses injected repositories. */
    public ReceiptParseApplier() {
        this(null, null, new ObjectMapper());
    }

    @Autowired
    public ReceiptParseApplier(ReceiptFormatProfileRepository profiles, ReceiptParseTraceRepository traces,
            ObjectMapper objectMapper) {
        this.profiles = profiles;
        this.traces = traces;
        this.objectMapper = objectMapper;
    }

    private static final int MAX_STORE_NAME_LENGTH = 255;
    private static final int MAX_STORE_BRANCH_LENGTH = 255;
    private static final int MAX_BONUS_TYPE_LENGTH = 64;
    private static final int MAX_ITEM_DESCRIPTION_LENGTH = 512;
    private static final int MAX_ITEM_UNIT_LENGTH = 32;
    private static final String FALLBACK_ITEM_DESCRIPTION = "Unbekannte Position";

    @Transactional
    public void apply(Receipt receipt, ReceiptParseResult parseResult) {
        ReceiptFormatProfile profile = selectedProfile(parseResult);
        receipt.clearItems();
        receipt.useFormatProfile(profile);
        if (traces != null && receipt.getId() != null) {
            traces.deleteAll(traces.findByReceipt_IdOrderByLineNumberAsc(receipt.getId()));
            // Delete old trace/item identities before inserts with the same receipt/line or position keys.
            traces.flush();
            traces.saveAll(parseResult.traces().stream().map(trace -> new ReceiptParseTrace(receipt, profile,
                    trace.lineNumber(), trace.lineType(), trace.positionIndex(),
                    objectMapper.writeValueAsString(trace.extractedFields()), trace.reason(), trace.needsReview())).toList());
        }
        ParsedReceipt parsedReceipt = parseResult.receipt();
        receipt.applyParseResult(
                parseResult.parseStatus(),
                parseResult.errorMessage(),
                parsedReceipt == null ? null : parsedReceipt.receiptDate(),
                parsedReceipt == null ? null : parsedReceipt.receiptTime(),
                parsedReceipt == null ? null : limitText(parsedReceipt.storeName(), MAX_STORE_NAME_LENGTH),
                parsedReceipt == null ? null : limitText(parsedReceipt.storeBranch(), MAX_STORE_BRANCH_LENGTH),
                parsedReceipt == null ? null : parsedReceipt.totalAmount(),
                parsedReceipt == null ? "EUR" : parsedReceipt.currency(),
                parsedReceipt == null ? null : parsedReceipt.bonusBalance(),
                parsedReceipt == null ? null : parsedReceipt.bonusPoints(),
                parsedReceipt == null ? null : limitText(parsedReceipt.bonusType(), MAX_BONUS_TYPE_LENGTH),
                parseResult.parseSource());

        if (parsedReceipt == null) {
            return;
        }

        for (ParsedReceiptItem parsedItem : parsedReceipt.items()) {
            ReceiptItem item = new ReceiptItem(
                    parsedItem.positionIndex(),
                    requiredLimitedText(
                            parsedItem.description(),
                            MAX_ITEM_DESCRIPTION_LENGTH,
                            FALLBACK_ITEM_DESCRIPTION),
                    parsedItem.totalPrice());
            item.updateParsedValues(
                    parsedItem.quantity(),
                    limitText(parsedItem.unit(), MAX_ITEM_UNIT_LENGTH),
                    parsedItem.unitPrice(),
                    parsedItem.discountAmount());
            receipt.addItem(item);
        }
    }

    private ReceiptFormatProfile selectedProfile(ReceiptParseResult result) {
        if (result.appliedProfile() == null) {
            return null;
        }
        if (profiles == null || traces == null) {
            throw new IllegalStateException("Profile application requires persistence repositories");
        }
        ReceiptFormatProfile profile = profiles.findById(result.appliedProfile().profileId()).orElseThrow();
        if (profile.getVersion() != result.appliedProfile().version()) {
            throw new IllegalArgumentException("Profile version does not match parse provenance");
        }
        return profile;
    }

    private String requiredLimitedText(String value, int maxLength, String fallback) {
        String limited = limitText(value, maxLength);
        return limited == null || limited.isBlank() ? fallback : limited;
    }

    private String limitText(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String normalized = value.strip();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength).stripTrailing();
    }
}
