package de.ebon.product;

import java.math.BigDecimal;

public record AiProductAssignmentResponse(
        Long productFamilyId,
        Long productVariantId,
        BigDecimal confidence,
        String modelUsed) {
}
