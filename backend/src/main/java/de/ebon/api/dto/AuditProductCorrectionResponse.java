package de.ebon.api.dto;

import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import java.math.BigDecimal;

public record AuditProductCorrectionResponse(
        Long receiptItemId,
        Long productFamilyId,
        Long productVariantId,
        ProductAssignmentSource source,
        ProductAssignmentStatus status,
        BigDecimal confidence,
        boolean familyCreated,
        boolean variantCreated) {
}
