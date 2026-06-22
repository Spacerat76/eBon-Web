package de.ebon.product;

import java.math.BigDecimal;
import java.util.List;

public record AiProductAssignmentRequest(
        Long receiptItemId,
        String description,
        String storeName,
        BigDecimal totalPrice,
        BigDecimal quantity,
        String unit,
        List<AiProductCandidate> candidates,
        BigDecimal minimumConfidence) {
}
